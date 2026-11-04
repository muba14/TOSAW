package com.muba.floattimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muba.floattimer.OverlaySettings
import com.muba.floattimer.R
import com.muba.floattimer.TimeFormat
import com.muba.floattimer.TimerEngine
import com.muba.floattimer.TimerEngine.Mode
import com.muba.floattimer.service.TimerControls
import com.muba.floattimer.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun TimerScreen() {
    val state by TimerEngine.state.collectAsState()
    val now by rememberNow(state.running)
    val scroll = rememberScrollState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dialSize: Dp = min(280f, maxHeight.value * 0.4f).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (state.mode == Mode.TIMER) Modifier.verticalScroll(scroll) else Modifier)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header()
            Spacer(Modifier.height(16.dp))
            ModeSwitch(mode = state.mode, enabled = !state.isActive) { TimerEngine.setMode(it) }
            Spacer(Modifier.height(24.dp))
            TimeDial(state, now, dialSize)
            Spacer(Modifier.height(24.dp))
            Controls(state)
            Spacer(Modifier.height(22.dp))
            when {
                state.mode == Mode.TIMER && !state.isActive -> DurationPicker(state.durationMs)
                state.mode == Mode.STOPWATCH -> LapList(state.laps, Modifier.weight(1f))
                else -> Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Header() {
    val settings by OverlaySettings.flow.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("TOSAW", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Her ekranda yüzen sayaç", color = Palette.TextDim, fontSize = 13.sp)
        }
        if (settings.enabled) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.size(6.dp))
                Text("Yüzüyor", color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: Mode, enabled: Boolean, onSelect: (Mode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Surface)
            .padding(4.dp)
    ) {
        listOf(Mode.STOPWATCH to "Kronometre", Mode.TIMER to "Zamanlayıcı").forEach { (m, label) ->
            val selected = m == mode
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "seg"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .alpha(if (!selected && !enabled) 0.4f else 1f)
                    .clickable(enabled = enabled && !selected) { onSelect(m) }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else Palette.TextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TimeDial(state: TimerEngine.State, now: Long, size: Dp) {
    val accent = if (state.finished) Palette.Danger else MaterialTheme.colorScheme.primary
    val progress = if (state.finished) 1f else state.progress(now)
    val isStopwatch = state.mode == Mode.STOPWATCH
    val shown = if (isStopwatch) state.elapsed(now) else TimeFormat.ceilSecond(state.remainingMs(now))
    val main = TimeFormat.clock(shown)

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val r = arc.width / 2
            val c = center

            // dakika çizgileri
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90)
                val r1 = r - stroke * 1.4f
                val r2 = r1 - if (i % 5 == 0) 9.dp.toPx() else 4.dp.toPx()
                drawLine(
                    color = if (i % 5 == 0) Palette.TextDim.copy(alpha = 0.6f) else Palette.Line,
                    start = Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                    end = Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            drawArc(Palette.Surface2, -90f, 360f, false, topLeft, arc, style = Stroke(stroke))
            val sweep = 360f * progress
            if (sweep > 0.5f) {
                drawArc(accent, -90f, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val a = Math.toRadians((-90f + sweep).toDouble())
                drawCircle(
                    Color.White,
                    radius = stroke * 0.32f,
                    center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isStopwatch) "KRONOMETRE" else "ZAMANLAYICI",
                color = Palette.TextDim, fontSize = 12.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    main,
                    modifier = Modifier.alignByBaseline(),
                    style = TextStyle(
                        color = Palette.Text,
                        fontSize = if (main.length > 5) 46.sp else 60.sp,
                        fontWeight = FontWeight.Light,
                        fontFeatureSettings = "tnum",
                    ),
                )
                if (isStopwatch) {
                    Text(
                        TimeFormat.centis(shown),
                        modifier = Modifier.alignByBaseline(),
                        style = TextStyle(
                            color = Palette.TextDim,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
            val sub = when {
                state.finished -> "Süre doldu!"
                !isStopwatch && state.running -> "Bitiş " + SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(System.currentTimeMillis() + state.remainingMs(now)))
                state.running -> "Çalışıyor"
                state.isActive -> "Duraklatıldı"
                else -> "Hazır"
            }
            Text(sub, color = if (state.finished) Palette.Danger else accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Controls(state: TimerEngine.State) {
    val ctx = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        RoundIconButton(R.drawable.ic_reset, "Sıfırla", enabled = state.isActive) {
            TimerControls.dismiss(ctx)
        }
        PrimaryTimerButton(state) { TimerControls.primary(ctx) }
        if (state.mode == Mode.STOPWATCH) {
            RoundIconButton(R.drawable.ic_flag, "Tur", enabled = state.running) { TimerEngine.lap() }
        } else {
            RoundTextButton("+1:00", enabled = state.isActive) { TimerEngine.addTime(60_000L) }
        }
    }
}

@Composable
private fun DurationPicker(durationMs: Long) {
    val total = durationMs / 1000
    val h = (total / 3600).toInt()
    val m = ((total % 3600) / 60).toInt()
    val s = (total % 60).toInt()
    fun set(hh: Int, mm: Int, ss: Int) = TimerEngine.setDuration((hh * 3600L + mm * 60L + ss) * 1000L)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stepper("Saat", h, 23) { set(it, m, s) }
            Stepper("Dakika", m, 59) { set(h, it, s) }
            Stepper("Saniye", s, 59) { set(h, m, it) }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 3, 5, 10, 15, 25, 30, 45, 60).forEach { min ->
                val selected = durationMs == min * 60_000L
                Text(
                    if (min < 60) "$min dk" else "1 sa",
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else Palette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Palette.Surface)
                        .clickable { TimerEngine.setDuration(min * 60_000L) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Stepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = { onChange(if (value >= max) 0 else value + 1) }) {
            Icon(painterResource(R.drawable.ic_add), "Artır", tint = Palette.TextDim)
        }
        Text(
            String.format(Locale.US, "%02d", value),
            style = TextStyle(
                color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
        )
        Text(label, color = Palette.TextDim, fontSize = 11.sp)
        IconButton(onClick = { onChange(if (value <= 0) max else value - 1) }) {
            Icon(painterResource(R.drawable.ic_remove), "Azalt", tint = Palette.TextDim)
        }
    }
}

@Composable
private fun LapList(laps: List<Long>, modifier: Modifier) {
    if (laps.isEmpty()) {
        Text("Tur eklemek için bayrağa dokun", color = Palette.TextDim, fontSize = 13.sp)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(laps) { i, totalMs ->
            val prev = laps.getOrElse(i + 1) { 0L }
            val split = totalMs - prev
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tur ${laps.size - i}", color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    "+" + TimeFormat.clock(split) + TimeFormat.centis(split),
                    style = TextStyle(color = Palette.TextDim, fontSize = 14.sp, fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(end = 16.dp),
                )
                Text(
                    TimeFormat.clock(totalMs) + TimeFormat.centis(totalMs),
                    style = TextStyle(
                        color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
        }
    }
}
