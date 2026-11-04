package com.muba.floattimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.muba.floattimer.OverlaySettings

/** Bildirim butonları (Başlat/Duraklat, Sıfırla, Kapat, Durdur). */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> TimerControls.primary(context)
            ACTION_RESET, ACTION_STOP_ALARM -> TimerControls.dismiss(context)
            ACTION_CLOSE -> {
                TimerControls.dismiss(context)
                OverlaySettings.setEnabled(false)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.muba.floattimer.action.TOGGLE"
        const val ACTION_RESET = "com.muba.floattimer.action.RESET"
        const val ACTION_CLOSE = "com.muba.floattimer.action.CLOSE"
        const val ACTION_STOP_ALARM = "com.muba.floattimer.action.STOP_ALARM"
    }
}
