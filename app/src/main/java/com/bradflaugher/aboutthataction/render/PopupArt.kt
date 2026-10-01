package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Popup
import com.bradflaugher.aboutthataction.engine.FloatingText
import com.bradflaugher.aboutthataction.engine.FloorEvent
import com.bradflaugher.aboutthataction.engine.TextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Comic treatments for the engine's funnier popups, in screen pixels: speech bubbles for what
 * guards say ("HUH?", "HEY!", "?!"), a starburst for BONK!, a cardboard slab for BOX'D!, a
 * rubber stamp for GHOST, a lullaby for NIGHT NIGHT and so on. Everything else keeps the plain
 * outlined popup in [Effects]. Matching is by text, allocation-free.
 */
internal class PopupArt(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly

    /** Which treatment [ft] gets; [HIDE] = drawn some other way (or not at all). */
    fun kind(ft: FloatingText): Int {
        val s = ft.text
        if (s == Popup.SNORE) return HIDE // napping guards draw their own Zs
        if (ft.style == TextStyle.WARN && s === f.w.coachTip) return HIDE // the coach plate
        if (ft.style == TextStyle.BIG && (s == FloorEvent.BLACKOUT.title || s == FloorEvent.NAP_TIME.title || s == FloorEvent.PAYDAY.title)) return HIDE
        return when (s) {
            Popup.HUH, Popup.OOK -> BUBBLE
            Popup.HEY, Popup.WAKE -> SHOUT
            Popup.BONK -> BONK
            Popup.BOXD -> BOXD
            Popup.NIGHT_NIGHT -> DREAMY
            Popup.LIGHTS_OUT -> FLICKER
            Popup.OOPS -> WOBBLE
            Popup.NOT_TODAY -> SHIELD
            Popup.JAZZ -> JAZZ
            else -> if (ft.style == TextStyle.COMBO && s.startsWith(Popup.GHOST)) STAMP else NORMAL
        }
    }

    /** World-unit font size for [kind], or 0 to keep the style's. */
    fun size(kind: Int): Float = when (kind) {
        BUBBLE, SHOUT -> 0.42f
        BONK -> 0.56f
        BOXD -> 0.6f
        STAMP -> 0.6f
        DREAMY -> 0.42f
        FLICKER -> 0.48f
        WOBBLE -> 0.46f
        SHIELD -> 0.36f
        JAZZ -> 0.34f
        else -> 0f
    }

    /** How much wider than its text the treatment is (for the popup layout). */
    fun widthK(kind: Int): Float = when (kind) {
        BUBBLE, SHOUT -> 1.7f
        BONK, BOXD -> 1.5f
        STAMP -> 1.35f
        DREAMY, JAZZ -> 1.45f
        SHIELD -> 1.4f
        else -> 1.1f
    }

    /** Half the treatment's height around its centre (baseline - 0.36 size), in font sizes. */
    fun halfH(kind: Int): Float = when (kind) {
        BUBBLE, SHOUT -> 0.95f
        BONK -> 1.15f
        BOXD -> 0.85f
        STAMP -> 1.05f
        SHIELD -> 0.7f
        DREAMY -> 0.6f
        else -> 0.45f
    }

    fun draw(kind: Int, ft: FloatingText, x: Float, y: Float, sz: Float, a: Float) {
        val age = ft.t * ft.maxLife
        when (kind) {
            BUBBLE -> bubble(ft.text, x, y, sz, a, age, 0xFFF4F2FF.toInt(), 0xFF1A1A30.toInt(), false)
            SHOUT -> if (ft.text == Popup.HEY) bubble(ft.text, x, y, sz, a, age, 0xFFFF3A48.toInt(), 0xFFFFFFFF.toInt(), true)
            else bubble(ft.text, x, y, sz, a, age, 0xFFFFD21E.toInt(), 0xFF1A0A00.toInt(), true)
            BONK -> bonk(ft.text, x, y, sz, a, age)
            BOXD -> boxd(ft.text, x, y, sz, a, age)
            STAMP -> stamp(ft.text, x, y, sz, a, age)
            DREAMY -> dreamy(ft.text, x, y, sz, a, age)
            FLICKER -> flicker(ft.text, x, y, sz, a, age)
            WOBBLE -> wobble(ft.text, x, y, sz, a, age)
            SHIELD -> shield(ft.text, x, y, sz, a)
            JAZZ -> jazz(ft.text, x, y, sz, a, age)
        }
    }

    private fun tw(s: String, sz: Float) = g.textWidth(s, sz, Gfx.Font.TITLE)

    // ------------------------------------------------------------ bubbles

    /** A speech bubble (or a jagged shout) with a tail down to the speaker's head. */
    private fun bubble(s: String, x: Float, y: Float, sz: Float, a: Float, age: Float, fill: Int, text: Int, shout: Boolean) {
        val w = tw(s, sz)
        val cy = y - sz * 0.36f
        val hw = w / 2f + sz * 0.45f
        val hh = sz * 0.72f
        val o = max(2f, sz * 0.09f)
        val ink = Col.alpha(ActorPaint.INK, a)
        val c = Col.alpha(fill, a)
        val jit = if (shout && age < 0.25f) sin(age * 90f) * sz * 0.06f else 0f
        g.save()
        g.translate(x + jit, cy)
        g.rotate(if (shout) -4f else sin(age * 6f) * 3f - 3f)
        // Tail toward the speaker below.
        poly.tri(g, -hw * 0.25f - o, hh * 0.55f, hw * 0.05f + o, hh * 0.55f, -hw * 0.4f - o * 0.5f, hh + sz * 0.55f + o, ink)
        if (shout) {
            burst(0f, 0f, hw + o, hh + o, 14, 0.8f, ink, age * 0.4f)
            burst(0f, 0f, hw, hh, 14, 0.8f, c, age * 0.4f)
        } else {
            g.fillRoundRect(-hw - o, -hh - o, hw + o, hh + o, hh + o, ink)
            g.fillRoundRect(-hw, -hh, hw, hh, hh, c)
            g.fillRoundRect(-hw * 0.8f, -hh * 0.8f, hw * 0.3f, -hh * 0.45f, hh * 0.2f, Col.alpha(0xFFFFFFFF.toInt(), 0.4f * a))
        }
        poly.tri(g, -hw * 0.25f, hh * 0.5f, hw * 0.05f, hh * 0.5f, -hw * 0.4f, hh + sz * 0.55f, c)
        g.text(s, 0f, sz * 0.36f, sz, Col.alpha(text, a), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.restore()
    }

    /** A spiky starburst ellipse: [n] points alternating full and [inner] radius. */
    private fun burst(cx: Float, cy: Float, rx: Float, ry: Float, n: Int, inner: Float, color: Int, spin: Float) {
        val pb = poly.begin()
        for (i in 0 until n * 2) {
            val ang = i * PI.toFloat() / n + spin
            val k = if (i % 2 == 0) 1f else inner
            pb.add(cx + cos(ang) * rx * k, cy + sin(ang) * ry * k)
        }
        pb.fill(g, color)
    }

    private fun outlinedText(s: String, x: Float, y: Float, sz: Float, fill: Int, outline: Int, o: Float) {
        g.text(s, x - o, y, sz, outline, Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(s, x + o, y, sz, outline, Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(s, x, y - o, sz, outline, Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(s, x, y + o, sz, outline, Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(s, x + o, y + o * 1.8f, sz, outline, Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(s, x, y, sz, fill, Gfx.Font.TITLE, Gfx.Align.CENTER)
    }

    // --------------------------------------------------------------- comic

    /** BONK!: a yellow comic starburst with speed lines and red lettering. */
    private fun bonk(s: String, x: Float, y: Float, sz: Float, a: Float, age: Float) {
        val w = tw(s, sz)
        val cy = y - sz * 0.36f
        val rx = w * 0.72f
        val ry = sz * 1.05f
        g.save()
        g.translate(x, cy)
        g.rotate(-9f)
        val lines = HudType.decay(age / 0.4f)
        if (lines > 0.01f) for (i in 0 until 12) {
            val ang = i * PI.toFloat() / 6f + 0.2f
            val r0 = rx * (1.05f + 0.3f * (1f - lines))
            val r1 = r0 + rx * 0.35f * lines
            g.line(cos(ang) * r0, sin(ang) * r0 * ry / rx, cos(ang) * r1, sin(ang) * r1 * ry / rx, sz * 0.08f, Col.alpha(0xFFFFFFFF.toInt(), lines * a))
        }
        val o = max(2f, sz * 0.08f)
        burst(0f, 0f, rx + o * 1.5f, ry + o * 1.5f, 11, 0.72f, Col.alpha(ActorPaint.INK, a), 0.15f)
        burst(0f, 0f, rx, ry, 11, 0.72f, Col.alpha(0xFFFFE14A.toInt(), a), 0.15f)
        burst(0f, 0f, rx * 0.72f, ry * 0.72f, 11, 0.8f, Col.alpha(0xFFFFF4A8.toInt(), a), 0.15f)
        outlinedText(s, 0f, sz * 0.36f, sz, Col.alpha(0xFFFF2E4A.toInt(), a), Col.alpha(ActorPaint.INK, a), o)
        g.restore()
    }

    /** BOX'D!: the word stamped on a slab of cardboard, taped, with cardboard-coloured rays. */
    private fun boxd(s: String, x: Float, y: Float, sz: Float, a: Float, age: Float) {
        val w = tw(s, sz)
        val cy = y - sz * 0.36f
        val hw = w / 2f + sz * 0.45f
        val hh = sz * 0.75f
        val o = max(2f, sz * 0.08f)
        g.save()
        g.translate(x, cy)
        g.rotate(-6f + HudType.decay(age / 0.3f) * 10f)
        // Rays.
        val ray = HudType.outCubic(min(1f, age / 0.35f))
        g.blend(Gfx.Blend.ADD)
        for (i in 0 until 10) {
            val ang = i * PI.toFloat() / 5f + age * 0.8f
            val r1 = hw * (1.1f + 0.5f * ray)
            poly.tri(g, 0f, 0f, cos(ang - 0.12f) * r1, sin(ang - 0.12f) * r1 * 0.7f, cos(ang + 0.12f) * r1, sin(ang + 0.12f) * r1 * 0.7f, Col.alpha(0xFFFFC080.toInt(), 0.22f * a * (1f - 0.5f * ray)))
        }
        g.blend(Gfx.Blend.NORMAL)
        g.fillRect(-hw - o, -hh - o, hw + o, hh + o, Col.alpha(ActorPaint.INK, a))
        g.fillRect(-hw, -hh, hw, hh, Col.alpha(0xFFC08A52.toInt(), a))
        g.fillRect(-hw, hh * 0.55f, hw, hh, Col.alpha(0xFF9E6C3C.toInt(), a))
        g.fillRect(-hw, -hh, hw, -hh * 0.82f, Col.alpha(0xFFDDA86C.toInt(), a))
        // Tape across the corner.
        g.save()
        g.translate(hw * 0.78f, -hh * 0.85f)
        g.rotate(38f)
        g.fillRect(-sz * 0.55f, -sz * 0.14f, sz * 0.55f, sz * 0.14f, Col.alpha(0xE6E8D6A8.toInt(), a))
        g.restore()
        // "This side up" arrows.
        Glyphs.arrow(g, -hw + sz * 0.3f, hh * 0.3f, sz * 0.18f, 0f, -1f, sz * 0.06f, Col.alpha(0xFF5A3A1E.toInt(), 0.8f * a))
        outlinedText(s, 0f, sz * 0.36f, sz, Col.alpha(0xFF2E1A0A.toInt(), a), Col.alpha(0xFFF4DDB0.toInt(), 0.8f * a), o * 0.6f)
        g.restore()
    }

    // --------------------------------------------------------------- stamp

    private var ghostSrc: String? = null
    private var ghostBonus = ""

    /** GHOST: a rubber stamp that slams down big and settles, with the bonus under it. */
    private fun stamp(s: String, x: Float, y: Float, sz0: Float, a: Float, age: Float) {
        if (s !== ghostSrc) {
            ghostSrc = s
            ghostBonus = s.substring(Popup.GHOST.length).trim()
        }
        val slam = HudType.clamp01(age / 0.16f)
        val sc = 1f + 1.2f * (1f - HudType.inCubic(slam))
        val sz = sz0 * sc
        val word = Popup.GHOST
        val w = tw(word, sz)
        val cy = y - sz0 * 0.5f
        val hw = w / 2f + sz * 0.35f
        val hh = sz * 0.95f
        val ink = Col.alpha(0xFFBFF8FF.toInt(), a * (0.4f + 0.6f * slam))
        g.save()
        g.translate(x, cy)
        g.rotate(-10f)
        // Landing shock ring.
        if (slam >= 1f && age < 0.45f) {
            val k = (age - 0.16f) / 0.29f
            g.strokeRoundRect(-hw * (1f + 0.4f * k), -hh * (1f + 0.4f * k), hw * (1f + 0.4f * k), hh * (1f + 0.4f * k), sz * 0.3f, sz * 0.06f, Col.alpha(0xFFBFF8FF.toInt(), (1f - k) * 0.6f * a))
        }
        g.fillRoundRect(-hw, -hh, hw, hh, sz * 0.25f, Col.alpha(0xFF06101A.toInt(), 0.6f * a))
        g.strokeRoundRect(-hw, -hh, hw, hh, sz * 0.25f, sz * 0.09f, ink)
        g.strokeRoundRect(-hw + sz * 0.16f, -hh + sz * 0.16f, hw - sz * 0.16f, hh - sz * 0.16f, sz * 0.14f, sz * 0.035f, ink)
        g.blend(Gfx.Blend.ADD)
        g.glow(0f, -sz * 0.15f, w * 0.6f, Col.alpha(0xFF9FF6FF.toInt(), 0.2f * a))
        g.blend(Gfx.Blend.NORMAL)
        g.text(word, 0f, sz * 0.12f, sz, ink, Gfx.Font.TITLE, Gfx.Align.CENTER)
        // Stamp ink wear: a few gaps across the letters.
        for (i in 0 until 4) {
            val lx = -w / 2f + w * hash(i, 911)
            g.fillRect(lx, -sz * 0.6f + sz * hash(i, 912) * 0.6f, lx + sz * 0.3f, -sz * 0.55f + sz * hash(i, 912) * 0.6f, Col.alpha(0xFF06101A.toInt(), 0.5f * a))
        }
        HudType.tracked(g, ghostBonus, 0f, hh - sz * 0.28f, sz * 0.36f, ink, Gfx.Font.HUD, Gfx.Align.CENTER, sz * 0.06f)
        g.restore()
    }

    // --------------------------------------------------------------- others

    /** NIGHT NIGHT: lavender lettering rocking gently beside a crescent moon and two stars. */
    private fun dreamy(s: String, x: Float, y: Float, sz: Float, a: Float, age: Float) {
        val w = tw(s, sz)
        val sway = sin(age * 4f) * sz * 0.08f
        val c = Col.alpha(0xFFCBB8FF.toInt(), a)
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(x, y - sz * 0.36f)
        g.scale(1f, 0.45f)
        g.glow(0f, 0f, w * 0.8f, Col.alpha(0xFF9A7CFF.toInt(), 0.35f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        HudType.outlined(g, s, x, y + sway, sz, c, Col.alpha(0xFF0A0616.toInt(), 0.85f * a))
        // Moon on the left.
        val mx = x - w / 2f - sz * 0.55f
        val my = y - sz * 0.45f - sway
        g.fillCircle(mx, my, sz * 0.36f, Col.alpha(0xFF0A0616.toInt(), 0.85f * a))
        g.fillCircle(mx, my, sz * 0.3f, Col.alpha(0xFFFFF0B8.toInt(), a))
        g.fillCircle(mx + sz * 0.14f, my - sz * 0.08f, sz * 0.25f, Col.alpha(0xFF0A0616.toInt(), a))
        for (i in 0 until 2) {
            val tw2 = 0.5f + 0.5f * sin(age * 9f + i * 2f)
            val sx = x + w / 2f + sz * (0.35f + 0.35f * i)
            val sy = y - sz * (0.9f - 0.55f * i)
            star(sx, sy, sz * (0.12f + 0.08f * tw2), Col.alpha(0xFFFFF0B8.toInt(), a * (0.5f + 0.5f * tw2)))
        }
    }

    private fun star(x: Float, y: Float, r: Float, c: Int) {
        poly.begin().add(x, y - r).add(x + r * 0.28f, y - r * 0.28f).add(x + r, y).add(x + r * 0.28f, y + r * 0.28f)
            .add(x, y + r).add(x - r * 0.28f, y + r * 0.28f).add(x - r, y).add(x - r * 0.28f, y - r * 0.28f).fill(g, c)
    }

    /** LIGHTS OUT: warm tube-light lettering that stutters like a dying fixture. */
    private fun flicker(s: String, x: Float, y: Float, sz: Float, a: Float, age: Float) {
        val tick = (f.t * 24f).toInt()
        val on = age > 0.5f || hash(tick, 77) > 0.35f
        val k = if (on) 1f else 0.25f
        val w = tw(s, sz)
        if (on) {
            g.blend(Gfx.Blend.ADD)
            g.save()
            g.translate(x, y - sz * 0.36f)
            g.scale(1f, 0.5f)
            g.glow(0f, 0f, w * 0.75f, Col.alpha(0xFFFFD86A.toInt(), 0.4f * a))
            g.restore()
            g.blend(Gfx.Blend.NORMAL)
        }
        HudType.outlined(g, s, x, y, sz, Col.alpha(Col.lerp(0xFF6A5A40.toInt(), 0xFFFFF4C8.toInt(), k), a), Col.alpha(0xFF0A0604.toInt(), 0.85f * a))
    }

    /** OOPS: wobbling orange, with a bead of sweat. */
    private fun wobble(s: String, x: Float, y: Float, sz: Float, a: Float, age: Float) {
        val rot = sin(age * 16f) * 12f * HudType.decay(age / 0.8f)
        g.save()
        g.translate(x, y - sz * 0.36f)
        g.rotate(rot)
        HudType.outlined(g, s, 0f, sz * 0.36f, sz, Col.alpha(0xFFFFA23A.toInt(), a), Col.alpha(0xFF140600.toInt(), 0.85f * a))
        val w = tw(s, sz)
        val dx = w / 2f + sz * 0.2f
        val dy = -sz * 0.35f + fract(age * 1.5f) * sz * 0.4f
        g.fillCircle(dx, dy, sz * 0.13f, Col.alpha(0xFFBFEFFF.toInt(), a))
        poly.tri(g, dx - sz * 0.12f, dy - sz * 0.03f, dx + sz * 0.12f, dy - sz * 0.03f, dx, dy - sz * 0.3f, Col.alpha(0xFFBFEFFF.toInt(), a))
        g.restore()
    }

    /** NOT TODAY: gold on a dark shield-blue plate (the VEST / shield just ate a hit). */
    private fun shield(s: String, x: Float, y: Float, sz: Float, a: Float) {
        val w = tw(s, sz)
        val cy = y - sz * 0.36f
        val hw = w / 2f + sz * 0.9f
        val hh = sz * 0.68f
        g.fillRoundRect(x - hw, cy - hh, x + hw, cy + hh, hh, Col.alpha(0xFF071826.toInt(), 0.92f * a))
        g.strokeRoundRect(x - hw, cy - hh, x + hw, cy + hh, hh, max(1.5f, sz * 0.07f), Col.alpha(0xFF3CC8FF.toInt(), a))
        HudIcons.shield(g, x - hw + sz * 0.62f, cy, sz * 0.72f, Col.alpha(0xFF3CC8FF.toInt(), a), Col.alpha(0xFF071826.toInt(), a))
        g.text(s, x + sz * 0.3f, y, sz, Col.alpha(0xFFFFD24A.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER)
    }

    /** SMOOTH JAZZ: lounge-pink lettering between two little notes that bob to the beat. */
    private fun jazz(s: String, x: Float, y: Float, sz: Float, a: Float, age: Float) {
        val w = tw(s, sz)
        val c = Col.lerp(0xFFFF7AC8.toInt(), 0xFF7AF0FF.toInt(), 0.5f + 0.5f * sin(age * 5f))
        g.save()
        g.translate(x, y)
        g.rotate(-4f)
        HudType.outlined(g, s, 0f, 0f, sz, Col.alpha(c, a), Col.alpha(0xFF10061A.toInt(), 0.85f * a))
        for (side in -1..1 step 2) {
            val nx = side * (w / 2f + sz * 0.55f)
            val ny = -sz * 0.35f + sin(age * 8f + side) * sz * 0.15f
            g.fillCircle(nx, ny + sz * 0.12f, sz * 0.14f, Col.alpha(0xFFFFD86A.toInt(), a))
            g.line(nx + sz * 0.13f, ny + sz * 0.12f, nx + sz * 0.13f, ny - sz * 0.4f, sz * 0.05f, Col.alpha(0xFFFFD86A.toInt(), a))
            g.line(nx + sz * 0.13f, ny - sz * 0.4f, nx + sz * 0.32f, ny - sz * 0.22f, sz * 0.06f, Col.alpha(0xFFFFD86A.toInt(), a))
        }
        g.restore()
    }

    companion object {
        const val NORMAL = 0
        const val HIDE = 1
        const val BUBBLE = 2
        const val SHOUT = 3
        const val BONK = 4
        const val BOXD = 5
        const val DREAMY = 6
        const val FLICKER = 7
        const val WOBBLE = 8
        const val SHIELD = 9
        const val STAMP = 10
        const val JAZZ = 11
    }
}
