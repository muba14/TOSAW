package com.muba.floattimer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Yüzen pencerenin görünüm ayarları (renk, boyut, saydamlık, konum...). */
object OverlaySettings {

    val DEFAULT_COLOR: Int = 0xFF7C5CFF.toInt()

    data class Values(
        val enabled: Boolean = false,
        val color: Int = DEFAULT_COLOR,
        val scale: Float = 1f,
        val opacity: Float = 0.92f,
        val lockScreen: Boolean = true,
        val x: Int = 48,
        val y: Int = 280,
    )

    private var prefs: SharedPreferences? = null
    private val _flow = MutableStateFlow(Values())
    val flow: StateFlow<Values> = _flow.asStateFlow()
    val current: Values get() = _flow.value

    fun init(context: Context) {
        val p = context.getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        prefs = p
        _flow.value = Values(
            enabled = p.getBoolean("enabled", false),
            color = p.getInt("color", DEFAULT_COLOR),
            scale = p.getFloat("scale", 1f),
            opacity = p.getFloat("opacity", 0.92f),
            lockScreen = p.getBoolean("lockScreen", true),
            x = p.getInt("x", 48),
            y = p.getInt("y", 280),
        )
    }

    private fun set(v: Values, persist: Boolean = true) {
        _flow.value = v
        if (persist) persist()
    }

    fun persist() {
        val v = current
        prefs?.edit()
            ?.putBoolean("enabled", v.enabled)
            ?.putInt("color", v.color)
            ?.putFloat("scale", v.scale)
            ?.putFloat("opacity", v.opacity)
            ?.putBoolean("lockScreen", v.lockScreen)
            ?.putInt("x", v.x)
            ?.putInt("y", v.y)
            ?.apply()
    }

    fun setEnabled(enabled: Boolean) = set(current.copy(enabled = enabled))
    fun setColor(color: Int) = set(current.copy(color = color))
    fun setScale(scale: Float, persist: Boolean = true) =
        set(current.copy(scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)), persist)
    fun setOpacity(opacity: Float) = set(current.copy(opacity = opacity.coerceIn(0.3f, 1f)))
    fun setLockScreen(enabled: Boolean) = set(current.copy(lockScreen = enabled))
    fun savePosition(x: Int, y: Int) = set(current.copy(x = x, y = y))

    const val MIN_SCALE = 0.6f
    const val MAX_SCALE = 2.5f
}
