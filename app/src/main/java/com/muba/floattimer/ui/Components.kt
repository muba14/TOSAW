package com.muba.floattimer.ui

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muba.floattimer.R
import com.muba.floattimer.TimerEngine
import com.muba.floattimer.ui.theme.Palette
import com.muba.floattimer.ui.theme.onColor

/** Sayaç çalışırken her karede güncellenen zaman damgası. */
@Composable
fun rememberNow(running: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        now.longValue = SystemClock.elapsedRealtime()
        while (running) {
            withFrameMillis { }
            now.longValue = SystemClock.elapsedRealtime()
        }
    }
    return now
}

@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean = true,
    size: Dp = 60.dp,
    container: Color = Palette.Surface2,
    tint: Color = Palette.Text,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

@Composable
fun RoundTextButton(label: String, enabled: Boolean, size: Dp = 60.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Palette.Surface2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Palette.Text else Palette.Text.copy(alpha = 0.3f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Büyük başlat / duraklat / durdur butonu. */
@Composable
fun PrimaryTimerButton(state: TimerEngine.State, size: Dp = 88.dp, onClick: () -> Unit) {
    val accent = if (state.finished) Palette.Danger else MaterialTheme.colorScheme.primary
    val canStart = !(state.mode == TimerEngine.Mode.TIMER && !state.isActive && state.durationMs <= 0L)
    val icon = when {
        state.finished -> R.drawable.ic_close
        state.running -> R.drawable.ic_pause
        else -> R.drawable.ic_play
    }
    val desc = when {
        state.finished -> "Durdur"
        state.running -> "Duraklat"
        else -> "Başlat"
    }
    Box(
        modifier = Modifier
            .size(size)
            .shadow(20.dp, CircleShape, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(if (canStart) accent else accent.copy(alpha = 0.35f))
            .clickable(enabled = canStart, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = desc,
            tint = accent.onColor(),
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/** Uygulama içinde yüzen pencerenin canlı önizlemesi. */
@Composable
fun OverlayPill(color: Color, scale: Float, opacity: Float, text: String, running: Boolean = true) {
    val fg = color.onColor()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = opacity))
            .padding(horizontal = (14 * scale).dp, vertical = (9 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            Box(Modifier.size((9 * scale).dp).clip(CircleShape).background(fg))
        } else {
            Row {
                Box(Modifier.size(width = (3 * scale).dp, height = (9 * scale).dp).background(fg))
                Spacer(Modifier.width((3 * scale).dp))
                Box(Modifier.size(width = (3 * scale).dp, height = (9 * scale).dp).background(fg))
            }
        }
        Spacer(Modifier.width((9 * scale).dp))
        Text(
            text,
            style = TextStyle(
                color = fg,
                fontSize = (20 * scale).sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}
