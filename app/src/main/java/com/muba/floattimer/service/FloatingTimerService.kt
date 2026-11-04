package com.muba.floattimer.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.muba.floattimer.OverlaySettings
import com.muba.floattimer.TimeFormat
import com.muba.floattimer.TimerEngine
import com.muba.floattimer.ui.LockScreenActivity
import com.muba.floattimer.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Ön plan servisi:
 *  • diğer uygulamaların üstünde yüzen sayacı çizer (sürükle, dokun, iki parmakla büyüt)
 *  • kilit ekranında görünen, kaydırılamayan canlı bildirimi tutar
 *  • ekran kapanınca kilit ekranı sayacını açar
 *  • zamanlayıcı bitince alarmı çalar
 */
class FloatingTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    private var overlay: TimerOverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var receiverRegistered = false
    private var tickCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            if (!OverlaySettings.current.lockScreen || !TimerEngine.current.isActive) return
            runCatching {
                startActivity(
                    Intent(this@FloatingTimerService, LockScreenActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Notifications.ensureChannels(this)
        goForeground()

        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true

        // Bildirimi yalnızca durum değişince yenile (saniyeyi sistem kronometresi sayar).
        scope.launch {
            TimerEngine.state.map { it.copy(laps = emptyList()) }
                .distinctUntilChanged()
                .collect { refreshNotification() }
        }
        scope.launch {
            OverlaySettings.flow.map { it.color }.distinctUntilChanged().collect { refreshNotification() }
        }
        scope.launch { OverlaySettings.flow.collect { syncOverlay(it) } }
        scope.launch {
            combine(TimerEngine.state, OverlaySettings.flow) { t, s -> t.isActive || s.enabled }
                .distinctUntilChanged()
                .collect { needed -> if (!needed) stopSelf() }
        }

        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun goForeground() {
        val n = Notifications.ongoing(this, TimerEngine.current, OverlaySettings.current)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_ONGOING, n)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                Notifications.ID_ONGOING,
                Notifications.ongoing(this, TimerEngine.current, OverlaySettings.current)
            )
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val s = TimerEngine.current
        if (s.mode == TimerEngine.Mode.TIMER && s.running && s.remainingMs(now) <= 0L) {
            TimerEngine.markFinished()
            AlarmPlayer.start(this)
            Notifications.showFinished(this)
        }

        // İzin sonradan verildiyse pencereyi göster (her ~2 sn kontrol).
        if (++tickCount % 10 == 0 && overlay == null) syncOverlay(OverlaySettings.current)

        val cur = TimerEngine.current
        overlay?.update(
            text = TimeFormat.display(cur, now),
            running = cur.running,
            flash = cur.finished && (now / 450) % 2 == 0L
        )
    }

    private fun syncOverlay(s: OverlaySettings.Values) {
        val allowed = s.enabled && Settings.canDrawOverlays(this)
        if (allowed && overlay == null) addOverlay(s)
        if (!allowed && overlay != null) removeOverlay()
        overlay?.setStyle(s.color, s.scale, s.opacity)
    }

    private fun addOverlay(s: OverlaySettings.Values) {
        val view = TimerOverlayView(this)
        view.setStyle(s.color, s.scale, s.opacity)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = s.x
            y = s.y
        }
        attachTouch(view, p)
        runCatching { windowManager.addView(view, p) }.onSuccess {
            overlay = view
            params = p
            tick()
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        params = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(view: TimerOverlayView, p: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var multiTouch = false

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                TimerControls.primary(this@FloatingTimerService)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                runCatching {
                    startActivity(
                        Intent(this@FloatingTimerService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                }
                return true
            }
        }).apply { setIsLongpressEnabled(false) }

        val scaler = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                OverlaySettings.setScale(OverlaySettings.current.scale * detector.scaleFactor, persist = false)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                OverlaySettings.persist()
            }
        })

        view.setOnTouchListener { _, e ->
            scaler.onTouchEvent(e)
            if (!multiTouch) gestures.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = p.x
                    startY = p.y
                    dragging = false
                    multiTouch = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
                MotionEvent.ACTION_MOVE -> if (!multiTouch && !scaler.isInProgress) {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        val dm = resources.displayMetrics
                        p.x = (startX + dx.toInt()).coerceIn(0, (dm.widthPixels - view.width).coerceAtLeast(0))
                        p.y = (startY + dy.toInt()).coerceIn(0, (dm.heightPixels - view.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(view, p) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) OverlaySettings.savePosition(p.x, p.y)
                    dragging = false
                }
            }
            true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        scope.cancel()
        removeOverlay()
        if (receiverRegistered) runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    private companion object {
        const val TICK_MS = 200L
    }
}
