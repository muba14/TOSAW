package com.muba.floattimer.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.ceil

/**
 * Ekranda yüzen hap şeklindeki sayaç.
 * Solda durum işareti (● çalışıyor / ‖ duraklatıldı), sağda süre.
 */
@SuppressLint("ViewConstructor")
class TimerOverlayView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fontFeatureSettings = "tnum" // rakamlar eşit genişlikte → titreme yok
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var text = "00:00"
    private var running = false
    private var flash = false
    private var color = Color.parseColor("#7C5CFF")
    private var scale = 1f
    private var opacity = 0.92f

    fun setStyle(color: Int, scale: Float, opacity: Float) {
        if (color == this.color && scale == this.scale && opacity == this.opacity) return
        val sizeChanged = scale != this.scale
        this.color = color
        this.scale = scale
        this.opacity = opacity
        if (sizeChanged) requestLayout()
        invalidate()
    }

    fun update(text: String, running: Boolean, flash: Boolean) {
        if (text == this.text && running == this.running && flash == this.flash) return
        val lengthChanged = text.length != this.text.length
        this.text = text
        this.running = running
        this.flash = flash
        if (lengthChanged) requestLayout()
        invalidate()
    }

    private fun textSizePx() =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics) * scale

    private val padH get() = 14 * dp * scale
    private val padV get() = 9 * dp * scale
    private val indicator get() = 9 * dp * scale
    private val gap get() = 9 * dp * scale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.textSize = textSizePx()
        val fm = textPaint.fontMetrics
        val template = text.replace(Regex("[0-9]"), "0")
        val w = padH * 2 + indicator + gap + textPaint.measureText(template)
        val h = (fm.descent - fm.ascent) + padV * 2
        setMeasuredDimension(ceil(w).toInt(), ceil(h).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val base = if (flash) Color.parseColor("#FF4D5E") else color

        bgPaint.color = base
        bgPaint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2, h / 2, bgPaint)

        val fg = if (ColorUtils.calculateLuminance(base) > 0.55) Color.parseColor("#111216") else Color.WHITE
        textPaint.color = fg
        iconPaint.color = fg
        textPaint.textSize = textSizePx()

        val cy = h / 2
        val ix = padH
        val ind = indicator
        if (running) {
            canvas.drawCircle(ix + ind / 2, cy, ind / 2, iconPaint)
        } else {
            val bw = ind * 0.32f
            rect.set(ix, cy - ind / 2, ix + bw, cy + ind / 2)
            canvas.drawRoundRect(rect, bw / 2, bw / 2, iconPaint)
            rect.set(ix + ind - bw, cy - ind / 2, ix + ind, cy + ind / 2)
            canvas.drawRoundRect(rect, bw / 2, bw / 2, iconPaint)
        }

        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2
        canvas.drawText(text, ix + ind + gap, baseline, textPaint)
    }
}
