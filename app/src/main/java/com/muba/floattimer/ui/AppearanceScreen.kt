package com.muba.floattimer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.muba.floattimer.OverlaySettings
import com.muba.floattimer.TimeFormat
import com.muba.floattimer.TimerEngine
import com.muba.floattimer.ui.theme.Palette
import com.muba.floattimer.ui.theme.onColor
import kotlin.math.roundToInt

private val Swatches = listOf(
    0xFF7C5CFF, 0xFF3D8BFF, 0xFF18C8C1, 0xFF34D27B, 0xFFB8F34A, 0xFFFFC53D,
    0xFFFF8A3D, 0xFFFF4D6D, 0xFFFF5CC8, 0xFFFFFFFF, 0xFF2A2D36,
).map { it.toInt() }

@Composable
fun AppearanceScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val s by OverlaySettings.flow.collectAsState()
    val timer by TimerEngine.state.collectAsState()
    val now by rememberNow(timer.running)

    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var canNotify by remember { mutableStateOf(hasNotificationPermission(ctx)) }
    var waitingForOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(resumeTick) {
        canOverlay = Settings.canDrawOverlays(ctx)
        canNotify = hasNotificationPermission(ctx)
        if (waitingForOverlay && canOverlay) {
            OverlaySettings.setEnabled(true)
            waitingForOverlay = false
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("Görünüm", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Yüzen sayacı istediğin gibi ayarla", color = Palette.TextDim, fontSize = 13.sp)
        }

        PreviewCard(
            color = Color(s.color),
            scale = s.scale,
            opacity = s.opacity,
            text = if (timer.isActive) TimeFormat.display(timer, now) else "12:34",
            running = timer.running || !timer.isActive,
        )

        SettingsCard {
            ToggleRow(
                title = "Ekranda yüzsün",
                subtitle = if (canOverlay) "Tüm uygulamaların üstünde kalır, sürükleyip taşıyabilirsin"
                else "“Diğer uygulamaların üzerinde göster” izni gerekiyor",
                checked = s.enabled && canOverlay,
            ) { on ->
                if (on && !canOverlay) {
                    waitingForOverlay = true
                    Toast.makeText(ctx, "TOSAW için izni aç ve geri dön", Toast.LENGTH_LONG).show()
                    openOverlaySettings(ctx)
                } else {
                    OverlaySettings.setEnabled(on)
                }
            }
            HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(vertical = 6.dp))
            ToggleRow(
                title = "Kilit ekranında göster",
                subtitle = "Kilit ekranının altında duraklat · sıfırla widget’ı",
                checked = s.lockScreen,
            ) { OverlaySettings.setLockScreen(it) }
        }

        SettingsCard(title = "Renk") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Swatches.forEach { c ->
                    val selected = c == s.color
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) Palette.Text else Palette.Line,
                                shape = CircleShape,
                            )
                            .clickable { OverlaySettings.setColor(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(c).onColor()))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Özel ton", color = Palette.TextDim, fontSize = 12.sp)
            HueSlider(currentColor = s.color)
        }

        SettingsCard(title = "Boyut", trailing = "${(s.scale * 100).roundToInt()}%") {
            Slider(
                value = s.scale,
                onValueChange = { OverlaySettings.setScale(it) },
                valueRange = OverlaySettings.MIN_SCALE..OverlaySettings.MAX_SCALE,
                colors = sliderColors(),
            )
            Text("İpucu: yüzen pencereyi iki parmakla da büyütüp küçültebilirsin.", color = Palette.TextDim, fontSize = 12.sp)
        }

        SettingsCard(title = "Saydamlık", trailing = "${(s.opacity * 100).roundToInt()}%") {
            Slider(
                value = s.opacity,
                onValueChange = { OverlaySettings.setOpacity(it) },
                valueRange = 0.3f..1f,
                colors = sliderColors(),
            )
        }

        if (!canNotify || !canOverlay) {
            SettingsCard(title = "İzinler") {
                if (!canOverlay) {
                    PermissionRow("Diğer uygulamaların üzerinde göster", "Yüzen pencere için") {
                        waitingForOverlay = true
                        openOverlaySettings(ctx)
                    }
                }
                if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow("Bildirimler", "Kilit ekranındaki canlı sayaç için") {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }

        SettingsCard(title = "Kısayollar") {
            Tip("Dokun", "Başlat / duraklat")
            Tip("Çift dokun", "Uygulamayı aç")
            Tip("Sürükle", "Ekranda taşı")
            Tip("İki parmak", "Boyutlandır")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PreviewCard(color: Color, scale: Float, opacity: Float, text: String, running: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1C1F29), Color(0xFF0E1015))))
            .border(1.dp, Palette.Line, RoundedCornerShape(24.dp))
    ) {
        // sahte "arka plandaki uygulama" içeriği
        Column(
            Modifier.padding(20.dp).align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0.7f, 0.5f, 0.85f).forEach { w ->
                Box(
                    Modifier
                        .fillMaxWidth(w)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.06f))
                )
            }
        }
        Box(Modifier.offset(x = 18.dp, y = 18.dp)) {
            OverlayPill(color = color, scale = scale, opacity = opacity, text = text, running = running)
        }
    }
}

@Composable
private fun HueSlider(currentColor: Int) {
    var hue by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(currentColor, hsv)
        mutableFloatStateOf(hsv[0])
    }
    val rainbow = (0..6).map { Color.hsv(it * 60f, 0.62f, 1f) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(rainbow))
        )
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                OverlaySettings.setColor(Color.hsv(it.coerceIn(0f, 360f), 0.62f, 1f).toArgb())
            },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = Palette.Surface2,
)

@Composable
private fun SettingsCard(
    title: String? = null,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .padding(18.dp)
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (trailing != null) Text(trailing, color = Palette.TextDim, fontSize = 13.sp)
            }
        }
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Palette.TextDim, fontSize = 12.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = Palette.Surface2,
                uncheckedThumbColor = Palette.TextDim,
                uncheckedBorderColor = Palette.Line,
            ),
        )
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
        }
        Text(
            "İzin ver",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onGrant)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Tip(gesture: String, action: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            gesture,
            color = Palette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Surface2)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .width(76.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(action, color = Palette.TextDim, fontSize = 13.sp)
    }
}

private fun hasNotificationPermission(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun openOverlaySettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        )
    }
}
