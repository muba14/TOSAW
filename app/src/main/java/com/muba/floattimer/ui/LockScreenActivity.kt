package com.muba.floattimer.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muba.floattimer.OverlaySettings
import com.muba.floattimer.R
import com.muba.floattimer.TimeFormat
import com.muba.floattimer.TimerEngine
import com.muba.floattimer.service.TimerControls
import com.muba.floattimer.ui.theme.FloatTimerTheme
import com.muba.floattimer.ui.theme.Palette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekran kilitliyken sayacı büyük gösteren ekran.
 * Ekran kapanınca servis tarafından açılır; kilidin üstünde durur.
 */
class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val settings by OverlaySettings.flow.collectAsState()
            val state by TimerEngine.state.collectAsState()
            FloatTimerTheme(Color(settings.color)) {
                LockScreenContent(state, onClose = { finish() }, onUnlock = { unlock() })
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
            })
        } else {
            finish()
        }
    }
}

@Composable
private fun LockScreenContent(state: TimerEngine.State, onClose: () -> Unit, onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val now by rememberNow(state.running)
    val accent = if (state.finished) Palette.Danger else MaterialTheme.colorScheme.primary
    val wallClock by produceState(initialValue = Date()) {
        while (true) {
            delay(1000)
            value = Date()
        }
    }
    val isStopwatch = state.mode == TimerEngine.Mode.STOPWATCH
    val time = if (isStopwatch) {
        val e = state.elapsed(now)
        TimeFormat.clock(e) + TimeFormat.centis(e).replace('.', ',')
    } else {
        TimeFormat.clock(TimeFormat.ceilSecond(state.remainingMs(now)))
    }

    // Süre dolunca widget kırmızı yanıp söner.
    val flash = state.finished && (now / 450) % 2 == 0L
    val widgetBg by animateColorAsState(
        if (flash) Color(0xD9781420) else Color(0xD1141418), label = "widgetBg"
    )

    Box(
        Modifier
            .fillMaxSize()
            // Duvar kağıdı görünür kalsın; sadece altta hafif gölge.
            .background(Brush.verticalGradient(listOf(Color(0x14000000), Color(0x00000000), Color(0x59000000))))
            .systemBarsPadding()
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        // iPhone kilit ekranı gibi: tarih + büyük saat
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                SimpleDateFormat("d MMM EEE", Locale.forLanguageTag("tr-TR")).format(wallClock),
                color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(wallClock),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    letterSpacing = (-2).sp,
                ),
            )
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        state.finished -> "SÜRE DOLDU"
                        isStopwatch -> "KRONOMETRE"
                        else -> "ZAMANLAYICI"
                    },
                    color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp,
                    letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                SmallChip("Kilidi aç", R.drawable.ic_lock_open, onUnlock)
                Spacer(Modifier.width(8.dp))
                SmallChip("Gizle", R.drawable.ic_close, onClose)
            }

            // Widget: [duraklat] [sıfırla] ........ 34:32,42
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(widgetBg)
                    .padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetButton(
                    icon = when {
                        state.finished -> R.drawable.ic_close
                        state.running -> R.drawable.ic_pause
                        else -> R.drawable.ic_play
                    },
                    description = if (state.running) "Duraklat" else "Başlat",
                    container = accent.copy(alpha = 0.32f),
                    tint = accent,
                ) { TimerControls.primary(ctx) }
                Spacer(Modifier.width(10.dp))
                WidgetButton(
                    icon = R.drawable.ic_reset,
                    description = "Sıfırla",
                    container = Color.White.copy(alpha = 0.14f),
                    tint = Color.White,
                ) { TimerControls.dismiss(ctx) }
                Spacer(Modifier.weight(1f))
                Text(
                    time,
                    maxLines = 1,
                    style = TextStyle(
                        color = accent,
                        fontSize = if (time.length > 8) 32.sp else 40.sp,
                        fontWeight = FontWeight.Light,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
        }
    }
}

@Composable
private fun WidgetButton(icon: Int, description: String, container: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SmallChip(label: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x66000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
