package com.bradflaugher.aboutthataction.render

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File

/** [Gfx] on java.awt, so the unit tests can render real frames headlessly. */
class AwtGfx(private val image: BufferedImage) : Gfx {
    private val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        composite = AlphaComposite.SrcOver
    }
    private val stack = ArrayList<Pair<AffineTransform, Shape?>>()
    private val rect = Rectangle2D.Float()
    private val rrect = RoundRectangle2D.Float()
    private val ellipse = Ellipse2D.Float()
    private val line2 = Line2D.Float()
    private val path = Path2D.Float()
    private val strokes = HashMap<Float, BasicStroke>()
    private val fonts = HashMap<Long, Font>()

    override val width: Float get() = image.width.toFloat()
    override val height: Float get() = image.height.toFloat()

    fun dispose() = g.dispose()

    override fun save() {
        stack += g.transform to g.clip
    }

    override fun restore() {
        val (t, c) = stack.removeAt(stack.size - 1)
        g.transform = t
        g.clip = c
    }

    override fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())
    override fun scale(sx: Float, sy: Float) = g.scale(sx.toDouble(), sy.toDouble())
    override fun rotate(degrees: Float) = g.rotate(Math.toRadians(degrees.toDouble()))

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {
        rect.setRect(left, top, right - left, bottom - top)
        g.clip(rect)
    }

    private fun color(c: Int) = Color(c, true)

    private fun stroke(w: Float): BasicStroke = strokes.getOrPut(w) { BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) }

    override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        g.color = color(color)
        rect.setRect(left, top, right - left, bottom - top)
        g.fill(rect)
    }

    override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        g.color = color(color)
        rrect.setRoundRect(left, top, right - left, bottom - top, radius * 2, radius * 2)
        g.fill(rrect)
    }

    override fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, strokeWidth: Float, color: Int) {
        g.color = color(color)
        g.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
        rect.setRect(left, top, right - left, bottom - top)
        g.draw(rect)
    }

    override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, strokeWidth: Float, color: Int) {
        g.color = color(color)
        g.stroke = stroke(strokeWidth)
        rrect.setRoundRect(left, top, right - left, bottom - top, radius * 2, radius * 2)
        g.draw(rrect)
    }

    override fun fillCircle(cx: Float, cy: Float, radius: Float, color: Int) {
        g.color = color(color)
        ellipse.setFrame(cx - radius, cy - radius, radius * 2, radius * 2)
        g.fill(ellipse)
    }

    override fun strokeCircle(cx: Float, cy: Float, radius: Float, strokeWidth: Float, color: Int) {
        g.color = color(color)
        g.stroke = stroke(strokeWidth)
        ellipse.setFrame(cx - radius, cy - radius, radius * 2, radius * 2)
        g.draw(ellipse)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Int) {
        g.color = color(color)
        g.stroke = stroke(strokeWidth)
        line2.setLine(x1, y1, x2, y2)
        g.draw(line2)
    }

    override fun fillPolygon(xy: FloatArray, color: Int) {
        if (xy.size < 6) return
        path.reset()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) {
            path.lineTo(xy[i], xy[i + 1])
            i += 2
        }
        path.closePath()
        g.color = color(color)
        g.fill(path)
    }

    override fun fillVerticalGradient(left: Float, top: Float, right: Float, bottom: Float, colorTop: Int, colorBottom: Int) {
        if (bottom <= top) return
        g.paint = GradientPaint(0f, top, color(colorTop), 0f, bottom, color(colorBottom))
        rect.setRect(left, top, right - left, bottom - top)
        g.fill(rect)
    }

    override fun fillRadialGradient(cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int) {
        if (radius <= 0f) return
        g.paint = radial(cx, cy, radius, colorCenter, colorEdge)
        ellipse.setFrame(cx - radius, cy - radius, radius * 2, radius * 2)
        g.fill(ellipse)
    }

    override fun fillRectRadial(
        left: Float, top: Float, right: Float, bottom: Float,
        cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int,
    ) {
        if (radius <= 0f) return
        g.paint = radial(cx, cy, radius, colorCenter, colorEdge)
        rect.setRect(left, top, right - left, bottom - top)
        g.fill(rect)
    }

    private val fractions = floatArrayOf(0f, 1f)

    private fun radial(cx: Float, cy: Float, radius: Float, c0: Int, c1: Int) = RadialGradientPaint(
        Point2D.Float(cx, cy), radius, fractions, arrayOf(color(c0), color(c1)), MultipleGradientPaint.CycleMethod.NO_CYCLE,
    )

    private fun font(size: Float, font: Gfx.Font): Font {
        val key = (font.ordinal.toLong() shl 32) or size.toRawBits().toLong().and(0xffffffffL)
        return fonts.getOrPut(key) { base(font).deriveFont(size) }
    }

    override fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Gfx.Font, align: Gfx.Align) {
        val f = font(size, font)
        g.font = f
        g.color = color(color)
        val w = if (align == Gfx.Align.LEFT) 0f else f.getStringBounds(text, g.fontRenderContext).width.toFloat()
        val dx = when (align) {
            Gfx.Align.LEFT -> 0f
            Gfx.Align.CENTER -> -w / 2f
            Gfx.Align.RIGHT -> -w
        }
        g.drawString(text, x + dx, y)
    }

    override fun textWidth(text: String, size: Float, font: Gfx.Font): Float =
        font(size, font).getStringBounds(text, g.fontRenderContext).width.toFloat()

    companion object {
        private val baseFonts = HashMap<Gfx.Font, Font>()

        private fun base(font: Gfx.Font): Font = baseFonts.getOrPut(font) {
            val name = if (font == Gfx.Font.TITLE) "audiowide.ttf" else "share_tech_mono.ttf"
            val file = listOf("src/main/res/font/$name", "app/src/main/res/font/$name", "../app/src/main/res/font/$name")
                .map(::File).firstOrNull { it.isFile }
            if (file != null) Font.createFont(Font.TRUETYPE_FONT, file) else Font(Font.MONOSPACED, Font.BOLD, 12)
        }
    }
}
