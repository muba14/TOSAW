package com.muba.floattimer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.muba.floattimer.OverlaySettings
import com.muba.floattimer.R
import com.muba.floattimer.TimeFormat
import com.muba.floattimer.TimerEngine
import com.muba.floattimer.ui.MainActivity

object Notifications {

    private const val CH_ONGOING = "floattimer_ongoing"
    private const val CH_ALERT = "floattimer_alert"
    const val ID_ONGOING = 1001
    private const val ID_ALERT = 1002

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Çalışan sayaç", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Kilit ekranında ve bildirim panelinde canlı sayaç"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Süre doldu", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Zamanlayıcı bittiğinde uyarı"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // sesi AlarmPlayer çalar
                enableVibration(false)
            }
        )
    }

    private fun action(ctx: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, requestCode,
            Intent(ctx, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Kaydırılarak kapatılamayan, kilit ekranında görünen canlı sayaç bildirimi.
     * Özel görünüm: [başlat/duraklat] [sıfırla] ........ 34:32  (iPhone'daki canlı etkinlik gibi)
     */
    @SuppressLint("InlinedApi")
    fun ongoing(ctx: Context, s: TimerEngine.State, o: OverlaySettings.Values): Notification {
        val isTimer = s.mode == TimerEngine.Mode.TIMER
        val small = widgetViews(ctx, s, o)
        val big = widgetViews(ctx, s, o)

        val b = NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (isTimer) "Zamanlayıcı" else "Kronometre")
            .setContentText(statusText(s))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(o.color)
            .setContentIntent(openApp(ctx))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setCustomHeadsUpContentView(small)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // Kilit ekranında "içeriği gizle" seçiliyse bile widget görünsün.
        b.setPublicVersion(
            NotificationCompat.Builder(ctx, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setOngoing(true)
                .setShowWhen(false)
                .setColor(o.color)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(widgetViews(ctx, s, o))
                .build()
        )
        return b.build()
    }

    private fun statusText(s: TimerEngine.State) = when {
        s.finished -> "Süre doldu!"
        s.running -> "Çalışıyor"
        s.isActive -> "Duraklatıldı"
        else -> "Hazır"
    }

    private fun widgetViews(ctx: Context, s: TimerEngine.State, o: OverlaySettings.Values): RemoteViews {
        val now = SystemClock.elapsedRealtime()
        val isTimer = s.mode == TimerEngine.Mode.TIMER
        val accent = if (s.finished) Color.parseColor("#FF4D5E") else o.color
        val v = RemoteViews(ctx.packageName, R.layout.notif_timer)

        v.setTextViewText(
            R.id.label,
            when {
                s.finished -> "SÜRE DOLDU"
                isTimer -> "ZAMANLAYICI"
                else -> "KRONOMETRE"
            }
        )

        if (s.finished) {
            v.setViewVisibility(R.id.chrono, View.GONE)
            v.setViewVisibility(R.id.static_time, View.VISIBLE)
            v.setTextViewText(R.id.static_time, "00:00")
            v.setTextColor(R.id.static_time, accent)
        } else {
            v.setViewVisibility(R.id.chrono, View.VISIBLE)
            v.setViewVisibility(R.id.static_time, View.GONE)
            v.setTextColor(R.id.chrono, accent)
            // Chronometer saniyeyi kendisi sayar → servis her saniye bildirimi yenilemez (pil dostu).
            if (isTimer) {
                v.setChronometerCountDown(R.id.chrono, true)
                v.setChronometer(R.id.chrono, now + TimeFormat.ceilSecond(s.remainingMs(now)), null, s.running)
            } else {
                v.setChronometerCountDown(R.id.chrono, false)
                v.setChronometer(R.id.chrono, now - s.elapsed(now), null, s.running)
            }
        }

        v.setImageViewResource(
            R.id.btn_toggle,
            when {
                s.finished -> R.drawable.ic_close
                s.running -> R.drawable.ic_pause
                else -> R.drawable.ic_play
            }
        )
        v.setInt(R.id.btn_toggle, "setColorFilter", accent)
        v.setInt(R.id.btn_reset, "setColorFilter", Color.WHITE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setColorStateList(
                R.id.btn_toggle, "setBackgroundTintList",
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x52))
            )
        }

        v.setOnClickPendingIntent(R.id.btn_toggle, action(ctx, TimerActionReceiver.ACTION_TOGGLE, 1))
        v.setOnClickPendingIntent(R.id.btn_reset, action(ctx, TimerActionReceiver.ACTION_RESET, 2))
        return v
    }

    @SuppressLint("MissingPermission")
    fun showFinished(ctx: Context) {
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Süre doldu!")
            .setContentText("Zamanlayıcı tamamlandı.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .setDeleteIntent(action(ctx, TimerActionReceiver.ACTION_STOP_ALARM, 5))
            .addAction(0, "Durdur", action(ctx, TimerActionReceiver.ACTION_STOP_ALARM, 4))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }

    fun cancelFinished(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_ALERT)
    }
}
