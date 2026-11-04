package com.muba.floattimer

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.muba.floattimer.service.FloatingTimerService
import com.muba.floattimer.service.Notifications
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class FloatTimerApp : Application() {

    private val appScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
        OverlaySettings.init(this)
        Notifications.ensureChannels(this)

        // Sayaç aktifse ya da yüzen pencere açıksa servis çalışmalı.
        appScope.launch {
            combine(TimerEngine.state, OverlaySettings.flow) { t, s -> t.isActive || s.enabled }
                .distinctUntilChanged()
                .collect { needed -> if (needed) startTimerService() }
        }
    }

    private fun startTimerService() {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, FloatingTimerService::class.java))
        }
    }
}
