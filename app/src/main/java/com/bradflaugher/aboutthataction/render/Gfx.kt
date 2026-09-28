package com.bradflaugher.aboutthataction.render

/**
 * The tiny immediate-mode drawing surface the whole game renders through.
 * The app implements it on android.graphics.Canvas; the unit tests implement
 * it on java.awt so screenshots render headlessly from the very same code.
 *
 * Colors are 0xAARRGGBB ints. Coordinates are in pixels after the current
 * transform. Implementations must support nested [save]/[restore].
 */
interface Gfx {
    val width: Float
    val height: Float

    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float)
    fun rotate(degrees: Float)
    /** Intersects the clip with a rectangle. */
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float)

    fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int)
    fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int)
    fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, strokeWidth: Float, color: Int)
    fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, strokeWidth: Float, color: Int)
    fun fillCircle(cx: Float, cy: Float, radius: Float, color: Int)
    fun strokeCircle(cx: Float, cy: Float, radius: Float, strokeWidth: Float, color: Int)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, strokeWidth: Float, color: Int)
    /** Filled polygon; [xy] is x0, y0, x1, y1, ... */
    fun fillPolygon(xy: FloatArray, color: Int)

    /**
     * Fills a polygon ([xy] as in [fillPolygon]) with a three-stop linear gradient: [c0] at
     * (x0, y0), [c1] at [mid] of the way along, [c2] at (x1, y1), clamped beyond both ends.
     * The painter's brush: cylinders, lit-to-shadow planes and glassy visors in one call.
     */
    fun fillPolygonGradient(xy: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, c2: Int, mid: Float = 0.5f)

    /** Vertical gradient fill from [top] (colorTop) to [bottom] (colorBottom). */
    fun fillVerticalGradient(left: Float, top: Float, right: Float, bottom: Float, colorTop: Int, colorBottom: Int)
    /** Radial gradient from [colorCenter] at the center to [colorEdge] at [radius]. */
    fun fillRadialGradient(cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int)
    /**
     * Fills a whole rectangle with a radial gradient centred on (cx, cy); beyond [radius] the
     * [colorEdge] continues. One cheap call for light pools, darkness-with-a-hole and vignettes.
     */
    fun fillRectRadial(
        left: Float, top: Float, right: Float, bottom: Float,
        cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int,
    )

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font = Font.HUD, align: Align = Align.LEFT)
    fun textWidth(text: String, size: Float, font: Font = Font.HUD): Float

    /**
     * Sets how every following draw call combines with what is already there, until the
     * next [blend] call. It is NOT part of [save]/[restore]: whoever switches away from
     * [Blend.NORMAL] switches back. [Blend.ADD] is the cheap bloom: soft sprites and lines
     * drawn with it light the scene up instead of covering it.
     */
    fun blend(mode: Blend)

    /**
     * A soft round light: [color] at the centre falling off smoothly to nothing at [radius],
     * shaped like a bloom halo rather than a flat disc. The alpha of [color] scales it.
     * Cheap: one cached gradient per RGB, so vary alpha freely, not the hue.
     */
    fun glow(cx: Float, cy: Float, radius: Float, color: Int)

    /**
     * An arc of the circle at (cx, cy). Angles in degrees, 0 = 3 o'clock, positive sweep =
     * clockwise on screen (y down). Round caps.
     */
    fun strokeArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, strokeWidth: Float, color: Int)

    /** A filled pie wedge; angles as in [strokeArc]. */
    fun fillArc(cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int)

    enum class Font { TITLE, HUD }
    enum class Align { LEFT, CENTER, RIGHT }

    /**
     * NORMAL = source over. ADD = additive light (bloom, sparks, tracers). SCREEN = lighten
     * without blowing out (haze, lifted shadows). MULTIPLY = colour grade / tint (never lightens).
     */
    enum class Blend { NORMAL, ADD, SCREEN, MULTIPLY }
}
