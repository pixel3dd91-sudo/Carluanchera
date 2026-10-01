package com.carlauncher.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** نمایش بارهای موزیک. اگر Visualizer واقعی کار نکند، انیمیشن شبیه‌سازی می‌شود. */
class BarsView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val count = 44
    private val levels = FloatArray(count)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var playing = false
    private var visualizer: Visualizer? = null
    private var visualizerOk = false
    @Volatile private var lastFftTime = 0L
    private var lastSim = 0L

    fun setPlaying(p: Boolean) { playing = p; invalidate() }

    fun attach(sessionId: Int) {
        release()
        try {
            val v = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(if (range[1] >= 512) 512 else range[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, r: Int) {
                    if (fft == null) return
                    val bins = fft.size / 2
                    var any = false
                    for (i in 0 until count) {
                        val lo = 1 + ((bins * 0.5f - 1) * (i.toFloat() / count) * (i.toFloat() / count)).toInt()
                        val hi = max(lo + 1, 1 + ((bins * 0.5f - 1) * ((i + 1f) / count) * ((i + 1f) / count)).toInt())
                        var sum = 0.0
                        var n = 0
                        for (k in lo until hi) {
                            if (2 * k + 1 >= fft.size) break
                            sum += hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble()); n++
                        }
                        val mag = if (n > 0) sum / n else 0.0
                        if (mag > 1.0) any = true
                        val lv = sqrt(mag / 128.0).toFloat().coerceIn(0f, 1f)
                        levels[i] = max(lv, levels[i] * 0.8f)
                    }
                    if (any) lastFftTime = SystemClock.uptimeMillis()
                    postInvalidate()
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.setEnabled(true)
            visualizer = v
            visualizerOk = true
        } catch (t: Throwable) {
            visualizerOk = false
        }
    }

    fun release() {
        try { visualizer?.setEnabled(false); visualizer?.release() } catch (_: Throwable) {}
        visualizer = null
        visualizerOk = false
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val simulate = playing && (!visualizerOk || now - lastFftTime > 1500)
        if (simulate) {
            if (now - lastSim > 70) {
                lastSim = now
                for (i in 0 until count) {
                    val t = Random.nextFloat() * (1f - 0.75f * i / count)
                    levels[i] = levels[i] * 0.55f + t * 0.45f
                }
            }
        } else if (!playing) {
            for (i in 0 until count) levels[i] *= 0.9f
        }

        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        val slot = w.toFloat() / count
        val minH = context.dp(4).toFloat()
        var active = false
        for (i in 0 until count) {
            val bh = max(minH, levels[i] * h)
            if (levels[i] > 0.01f) active = true
            val left = paddingLeft + i * slot + slot * 0.2f
            val right = paddingLeft + i * slot + slot * 0.8f
            canvas.drawRect(left, paddingTop + h - bh, right, paddingTop + h.toFloat(), paint)
        }
        if (playing || active) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
