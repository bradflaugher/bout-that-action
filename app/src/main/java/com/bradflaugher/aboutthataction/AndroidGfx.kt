package com.bradflaugher.aboutthataction

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.bradflaugher.aboutthataction.render.Gfx

/**
 * [Gfx] on android.graphics.Canvas. Allocation-free per frame: every Paint, Path
 * and RectF is reused, and gradients are cached as unit-sized shaders that are
 * drawn through a temporary canvas transform instead of being rebuilt.
 */
class AndroidGfx(context: Context) : Gfx {
    private var canvas: Canvas? = null
    private val c: Canvas get() = canvas ?: error("AndroidGfx.begin() not called")

    private val titleFace: Typeface = runCatching { context.resources.getFont(R.font.audiowide) }.getOrDefault(Typeface.DEFAULT_BOLD)
    private val hudFace: Typeface = runCatching { context.resources.getFont(R.font.chakra_petch) }.getOrDefault(Typeface.DEFAULT)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rectStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
    }
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val path = Path()

    private val linear = HashMap<Long, Shader>()
    private val radial = HashMap<Long, Shader>()

    override val width: Float get() = c.width.toFloat()
    override val height: Float get() = c.height.toFloat()

    /** Call once per frame before handing this to the renderer. */
    fun begin(canvas: Canvas) {
        this.canvas = canvas
        blend(Gfx.Blend.NORMAL)
    }

    override fun save() {
        c.save()
    }

    override fun restore() = c.restore()
    override fun translate(dx: Float, dy: Float) = c.translate(dx, dy)
    override fun scale(sx: Float, sy: Float) = c.scale(sx, sy)
    override fun rotate(degrees: Float) = c.rotate(degrees)

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {
        c.clipRect(left, top, right, bottom)
    }

    override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        fill.color = color
        c.drawRect(left, top, right, bottom, fill)
    }

    override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        fill.color = color
        c.drawRoundRect(left, top, right, bottom, radius, radius, fill)
    }

    override fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, strokeWidth: Float, color: Int) {
        rectStroke.color = color
        rectStroke.strokeWidth = strokeWidth
        c.drawRect(left, top, right, bottom, rectStroke)
    }

    override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, strokeWidth: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = strokeWidth
        c.drawRoundRect(left, top, right, bottom, radius, radius, stroke)
    }

    override fun fillCircle(cx: Float, cy: Float, radius: Float, color: Int) {
        fill.color = color
        c.drawCircle(cx, cy, radius, fill)
    }

    override fun strokeCircle(cx: Float, cy: Float, radius: Float, strokeWidth: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = strokeWidth
        c.drawCircle(cx, cy, radius, stroke)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = strokeWidth
        c.drawLine(x1, y1, x2, y2, stroke)
    }

    override fun fillPolygon(xy: FloatArray, color: Int) {
        if (xy.size < 6) return
        path.rewind()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) {
            path.lineTo(xy[i], xy[i + 1])
            i += 2
        }
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun key(a: Int, b: Int) = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

    private fun linearShader(top: Int, bottom: Int): Shader {
        if (linear.size > 192) linear.clear()
        return linear.getOrPut(key(top, bottom)) { LinearGradient(0f, 0f, 0f, 1f, top, bottom, Shader.TileMode.CLAMP) }
    }

    private fun radialShader(center: Int, edge: Int): Shader {
        if (radial.size > 192) radial.clear()
        return radial.getOrPut(key(center, edge)) { RadialGradient(0f, 0f, 1f, center, edge, Shader.TileMode.CLAMP) }
    }

    // Three-stop gradients, cached by colours in an open-addressed table (no boxed keys): each
    // shader is a unit gradient along x drawn through a canvas transform, so its matrix never
    // changes and HWUI never rebuilds it. The strongest stop's alpha rides on the paint and the
    // stops keep their alpha relative to it, so a stop fading to clear still fades, while a
    // whole-shape fade (a death, a doorway) reuses the same shader.
    private val triKeys = LongArray(TRI_SLOTS)
    private val triShaders = arrayOfNulls<LinearGradient>(TRI_SLOTS)
    private var triCount = 0
    private val triStops = FloatArray(3)
    private val triColors = IntArray(3)

    /** 19 bits of a colour for a cache key: 4 bits of alpha and 5-5-5 RGB (close enough to share a shader). */
    private fun q(c: Int): Long = (((c ushr 28) shl 15) or (((c shr 19) and 0x1F) shl 10) or (((c shr 11) and 0x1F) shl 5) or ((c shr 3) and 0x1F)).toLong()

    /** [c] with its alpha rescaled so that alpha [top] becomes fully opaque. */
    private fun lift(c: Int, top: Int): Int {
        val a = ((c ushr 24) * 255 + top / 2) / top
        return (a.coerceAtMost(255) shl 24) or (c and 0xFFFFFF)
    }

    private fun triShader(c0: Int, c1: Int, c2: Int, m: Int): LinearGradient {
        // The sign bit is always set, so a key is never 0, the empty slot.
        val key = Long.MIN_VALUE or (q(c0) shl 44) or (q(c1) shl 25) or (q(c2) shl 6) or m.toLong()
        var i = ((key xor (key ushr 29)) * -0x61c8864680b583ebL ushr 54).toInt() and (TRI_SLOTS - 1)
        while (true) {
            val k = triKeys[i]
            if (k == key) return triShaders[i]!!
            if (k == 0L) break
            i = (i + 1) and (TRI_SLOTS - 1)
        }
        if (triCount >= TRI_SLOTS * 3 / 4) {
            triKeys.fill(0L)
            triShaders.fill(null)
            triCount = 0
            return triShader(c0, c1, c2, m)
        }
        triColors[0] = c0; triColors[1] = c1; triColors[2] = c2
        triStops[0] = 0f; triStops[1] = m / 63f; triStops[2] = 1f
        val sh = LinearGradient(0f, 0f, 1f, 0f, triColors, triStops, Shader.TileMode.CLAMP)
        triKeys[i] = key
        triShaders[i] = sh
        triCount++
        return sh
    }

    override fun fillPolygonGradient(xy: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, c2: Int, mid: Float) {
        if (xy.size < 6) return
        val dx = x1 - x0
        val dy = y1 - y0
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-12f) {
            fillPolygon(xy, c1)
            return
        }
        val m = (mid.coerceIn(0.02f, 0.98f) * 63f).toInt().coerceIn(1, 62)
        val top = maxOf(c0 ushr 24, c1 ushr 24, c2 ushr 24)
        if (top == 0) return
        shaderPaint.shader = triShader(lift(c0, top), lift(c1, top), lift(c2, top), m)
        shaderPaint.alpha = top
        // The polygon in the gradient's own frame: u along (x0,y0)->(x1,y1), v across, both in
        // units of its length; the canvas transform maps it back.
        val inv = 1f / len2
        path.rewind()
        var i = 0
        while (i + 1 < xy.size) {
            val px = xy[i] - x0
            val py = xy[i + 1] - y0
            val u = (px * dx + py * dy) * inv
            val v = (py * dx - px * dy) * inv
            if (i == 0) path.moveTo(u, v) else path.lineTo(u, v)
            i += 2
        }
        path.close()
        c.save()
        c.translate(x0, y0)
        c.rotate(Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat())
        val len = kotlin.math.sqrt(len2)
        c.scale(len, len)
        c.drawPath(path, shaderPaint)
        c.restore()
        shaderPaint.alpha = 255
    }

    override fun fillVerticalGradient(left: Float, top: Float, right: Float, bottom: Float, colorTop: Int, colorBottom: Int) {
        val h = bottom - top
        if (h <= 0f) return
        shaderPaint.shader = linearShader(colorTop, colorBottom)
        c.save()
        c.translate(0f, top)
        c.scale(1f, h)
        c.drawRect(left, 0f, right, 1f, shaderPaint)
        c.restore()
    }

    override fun fillRadialGradient(cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int) {
        if (radius <= 0f) return
        shaderPaint.shader = radialShader(colorCenter, colorEdge)
        c.save()
        c.translate(cx, cy)
        c.scale(radius, radius)
        c.drawCircle(0f, 0f, 1f, shaderPaint)
        c.restore()
    }

    override fun fillRectRadial(
        left: Float, top: Float, right: Float, bottom: Float,
        cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int,
    ) {
        if (radius <= 0f) return
        shaderPaint.shader = radialShader(colorCenter, colorEdge)
        val inv = 1f / radius
        c.save()
        c.translate(cx, cy)
        c.scale(radius, radius)
        c.drawRect((left - cx) * inv, (top - cy) * inv, (right - cx) * inv, (bottom - cy) * inv, shaderPaint)
        c.restore()
    }

    private fun textSetup(size: Float, font: Gfx.Font) {
        textPaint.typeface = if (font == Gfx.Font.TITLE) titleFace else hudFace
        textPaint.textSize = size
    }

    override fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Gfx.Font, align: Gfx.Align) {
        textSetup(size, font)
        textPaint.color = color
        textPaint.textAlign = when (align) {
            Gfx.Align.LEFT -> Paint.Align.LEFT
            Gfx.Align.CENTER -> Paint.Align.CENTER
            Gfx.Align.RIGHT -> Paint.Align.RIGHT
        }
        c.drawText(text, x, y, textPaint)
    }

    override fun textWidth(text: String, size: Float, font: Gfx.Font): Float {
        textSetup(size, font)
        return textPaint.measureText(text)
    }

    private var blendMode = Gfx.Blend.NORMAL

    override fun blend(mode: Gfx.Blend) {
        if (mode == blendMode) return
        blendMode = mode
        val bm = when (mode) {
            Gfx.Blend.NORMAL -> null
            Gfx.Blend.ADD -> BlendMode.PLUS
            Gfx.Blend.SCREEN -> BlendMode.SCREEN
            Gfx.Blend.MULTIPLY -> BlendMode.MULTIPLY
        }
        fill.blendMode = bm
        stroke.blendMode = bm
        rectStroke.blendMode = bm
        shaderPaint.blendMode = bm
        glowPaint.blendMode = bm
        textPaint.blendMode = bm
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glows = HashMap<Int, Shader>()
    private val glowStops = floatArrayOf(0f, 0.16f, 0.42f, 1f)

    private fun glowShader(rgb: Int): Shader {
        if (glows.size > 96) glows.clear()
        return glows.getOrPut(rgb) {
            val c = rgb or 0xFF000000.toInt()
            val colors = intArrayOf(c, (0x8C shl 24) or rgb, (0x2E shl 24) or rgb, rgb and 0xFFFFFF)
            RadialGradient(0f, 0f, 1f, colors, glowStops, Shader.TileMode.CLAMP)
        }
    }

    override fun glow(cx: Float, cy: Float, radius: Float, color: Int) {
        val a = color ushr 24
        if (radius <= 0f || a == 0) return
        // Quantised hue so animated colours (the Void's cycling neon) reuse shaders instead of churning.
        glowPaint.shader = glowShader(color and 0xF8F8F8)
        glowPaint.alpha = a
        c.save()
        c.translate(cx, cy)
        c.scale(radius, radius)
        c.drawCircle(0f, 0f, 1f, glowPaint)
        c.restore()
    }

    override fun strokeArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, strokeWidth: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = strokeWidth
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        c.drawArc(rect, startDeg, sweepDeg, false, stroke)
    }

    override fun fillArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int) {
        fill.color = color
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        c.drawArc(rect, startDeg, sweepDeg, true, fill)
    }

    private companion object {
        const val TRI_SLOTS = 1024
    }
}
