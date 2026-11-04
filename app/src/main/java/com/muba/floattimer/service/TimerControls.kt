package com.muba.floattimer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.muba.floattimer.TimerEngine

/** Her yerden (uygulama, yüzen pencere, bildirim, kilit ekranı) aynı davranış. */
object TimerControls {

    /** Ana buton: başlat / duraklat; süre dolduysa alarmı sustur. */
    fun primary(ctx: Context) {
        if (TimerEngine.current.finished) dismiss(ctx) else TimerEngine.toggle()
    }

    /** Alarmı sustur ve sıfırla. */
    fun dismiss(ctx: Context) {
        AlarmPlayer.stop()
        Notifications.cancelFinished(ctx)
        TimerEngine.reset()
    }
}

/** Zamanlayıcı bitince alarm sesi + titreşim (en fazla 1 dk). */
object AlarmPlayer {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        stop()
        val app = ctx.applicationContext
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = runCatching {
            RingtoneManager.getRingtone(app, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }.getOrNull()

        vibrator = runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                app.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Vibrator::class.java)
            }
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 350), 0))
            v
        }.getOrNull()

        handler.postDelayed({ stop() }, 60_000L)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }
}
