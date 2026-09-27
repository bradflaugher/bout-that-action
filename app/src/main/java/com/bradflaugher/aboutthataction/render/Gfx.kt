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

    /** Vertical gradient fill from [top] (colorTop) to [bottom] (colorBottom). */
    fun fillVerticalGradient(left: Float, top: Float, right: Float, bottom: Float, colorTop: Int, colorBottom: Int)
    /** Radial gradient from [colorCenter] at the center to [colorEdge] at [radius]. */
    fun fillRadialGradient(cx: Float, cy: Float, radius: Float, colorCenter: Int, colorEdge: Int)

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font = Font.HUD, align: Align = Align.LEFT)
    fun textWidth(text: String, size: Float, font: Font = Font.HUD): Float

    enum class Font { TITLE, HUD }
    enum class Align { LEFT, CENTER, RIGHT }
}
