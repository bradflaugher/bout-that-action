package com.bradflaugher.aboutthataction.render

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Composite
import java.awt.CompositeContext
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBuffer
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.WritableRaster
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

    override fun fillPolygonGradient(xy: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, c2: Int, mid: Float) {
        if (xy.size < 6) return
        if ((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) < 1e-12f) {
            fillPolygon(xy, c1)
            return
        }
        path.reset()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) {
            path.lineTo(xy[i], xy[i + 1])
            i += 2
        }
        path.closePath()
        g.paint = java.awt.LinearGradientPaint(
            x0, y0, x1, y1,
            floatArrayOf(0f, mid.coerceIn(0.02f, 0.98f), 1f),
            arrayOf(color(c0), color(c1), color(c2)),
        )
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

    override fun blend(mode: Gfx.Blend) {
        g.composite = when (mode) {
            Gfx.Blend.NORMAL -> AlphaComposite.SrcOver
            else -> BlendComposite.of(mode)
        }
    }

    private val glowFractions = floatArrayOf(0f, 0.16f, 0.42f, 1f)

    override fun glow(cx: Float, cy: Float, radius: Float, color: Int) {
        val a = (color ushr 24) / 255f
        if (radius <= 0f || a <= 0f) return
        val rgb = color and 0xFFFFFF
        fun c(k: Float) = Color(((k * a * 255f).toInt().coerceIn(0, 255) shl 24) or rgb, true)
        g.paint = RadialGradientPaint(
            Point2D.Float(cx, cy), radius, glowFractions, arrayOf(c(1f), c(0.55f), c(0.18f), c(0f)),
            MultipleGradientPaint.CycleMethod.NO_CYCLE,
        )
        ellipse.setFrame(cx - radius, cy - radius, radius * 2, radius * 2)
        g.fill(ellipse)
    }

    private val arc = Arc2D.Float()

    override fun strokeArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, strokeWidth: Float, color: Int) {
        g.color = color(color)
        g.stroke = stroke(strokeWidth)
        arc.setArc((cx - radius).toDouble(), (cy - radius).toDouble(), (radius * 2).toDouble(), (radius * 2).toDouble(), -startDeg.toDouble(), -sweepDeg.toDouble(), Arc2D.OPEN)
        g.draw(arc)
    }

    override fun fillArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int) {
        g.color = color(color)
        arc.setArc((cx - radius).toDouble(), (cy - radius).toDouble(), (radius * 2).toDouble(), (radius * 2).toDouble(), -startDeg.toDouble(), -sweepDeg.toDouble(), Arc2D.PIE)
        g.fill(arc)
    }

    /** Additive / screen / multiply compositing, so headless screenshots match the phone. */
    private class BlendComposite(private val mode: Gfx.Blend) : Composite {
        override fun createContext(srcColorModel: ColorModel, dstColorModel: ColorModel, hints: RenderingHints?): CompositeContext =
            Ctx(mode, srcColorModel, dstColorModel)

        private class Ctx(val mode: Gfx.Blend, val srcCm: ColorModel, val dstCm: ColorModel) : CompositeContext {
            override fun dispose() = Unit

            override fun compose(src: Raster, dstIn: Raster, dstOut: WritableRaster) {
                val w = minOf(src.width, dstIn.width)
                val h = minOf(src.height, dstIn.height)
                val fast = srcCm is DirectColorModel && dstCm is DirectColorModel &&
                    src.transferType == DataBuffer.TYPE_INT && dstIn.transferType == DataBuffer.TYPE_INT
                val sPre = srcCm.isAlphaPremultiplied
                val dPre = dstCm.isAlphaPremultiplied
                val sRow = IntArray(w)
                val dRow = IntArray(w)
                for (y in 0 until h) {
                    if (fast) {
                        src.getDataElements(0, y, w, 1, sRow)
                        dstIn.getDataElements(0, y, w, 1, dRow)
                        for (x in 0 until w) {
                            val s = if (sPre) unpre(sRow[x]) else sRow[x]
                            val d = if (dPre) unpre(dRow[x]) else dRow[x]
                            val o = mix(s, d)
                            dRow[x] = if (dPre) pre(o) else o
                        }
                        dstOut.setDataElements(0, y, w, 1, dRow)
                    } else {
                        for (x in 0 until w) {
                            val s = srcCm.getRGB(src.getDataElements(x, y, null))
                            val d = dstCm.getRGB(dstIn.getDataElements(x, y, null))
                            dstOut.setDataElements(x, y, dstCm.getDataElements(mix(s, d), null))
                        }
                    }
                }
            }

            private fun unpre(c: Int): Int {
                val a = c ushr 24
                if (a == 0 || a == 255) return c
                val r = minOf(255, ((c shr 16) and 0xFF) * 255 / a)
                val g = minOf(255, ((c shr 8) and 0xFF) * 255 / a)
                val b = minOf(255, (c and 0xFF) * 255 / a)
                return (a shl 24) or (r shl 16) or (g shl 8) or b
            }

            private fun pre(c: Int): Int {
                val a = c ushr 24
                if (a == 255) return c
                val r = ((c shr 16) and 0xFF) * a / 255
                val g = ((c shr 8) and 0xFF) * a / 255
                val b = (c and 0xFF) * a / 255
                return (a shl 24) or (r shl 16) or (g shl 8) or b
            }

            private fun mix(s: Int, d: Int): Int {
                val sa = (s ushr 24) / 255f
                if (sa <= 0f) return d
                val da = d ushr 24
                val r = ch(s shr 16, d shr 16, sa)
                val gg = ch(s shr 8, d shr 8, sa)
                val b = ch(s, d, sa)
                val a = maxOf(da, (255 * sa).toInt())
                return (a shl 24) or (r shl 16) or (gg shl 8) or b
            }

            private fun ch(s: Int, d: Int, sa: Float): Int {
                val sc = (s and 0xFF) / 255f
                val dc = (d and 0xFF) / 255f
                val v = when (mode) {
                    Gfx.Blend.ADD -> dc + sc * sa
                    Gfx.Blend.SCREEN -> dc + sc * sa * (1f - dc)
                    Gfx.Blend.MULTIPLY -> dc * (1f - sa) + dc * sc * sa
                    Gfx.Blend.NORMAL -> dc * (1f - sa) + sc * sa
                }
                return (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            }
        }

        companion object {
            private val cache = HashMap<Gfx.Blend, BlendComposite>()
            fun of(mode: Gfx.Blend) = cache.getOrPut(mode) { BlendComposite(mode) }
        }
    }

    companion object {
        private val baseFonts = HashMap<Gfx.Font, Font>()

        private fun base(font: Gfx.Font): Font = baseFonts.getOrPut(font) {
            val name = if (font == Gfx.Font.TITLE) "audiowide.ttf" else "chakra_petch.ttf"
            val file = listOf("src/main/res/font/$name", "app/src/main/res/font/$name", "../app/src/main/res/font/$name")
                .map(::File).firstOrNull { it.isFile }
            if (file != null) Font.createFont(Font.TRUETYPE_FONT, file) else Font(Font.MONOSPACED, Font.BOLD, 12)
        }
    }
}
