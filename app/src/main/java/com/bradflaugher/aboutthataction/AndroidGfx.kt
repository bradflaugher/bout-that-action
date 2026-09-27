package com.bradflaugher.aboutthataction

import android.content.Context
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
    private val hudFace: Typeface = runCatching { context.resources.getFont(R.font.share_tech_mono) }.getOrDefault(Typeface.MONOSPACE)

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
}
