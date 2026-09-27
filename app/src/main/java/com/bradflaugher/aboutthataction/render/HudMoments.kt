package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.FloatingText
import com.bradflaugher.aboutthataction.engine.FloorEvent
import com.bradflaugher.aboutthataction.engine.FloorLabel
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.TextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen-space cards for the fun pass: the special-floor stinger (a snappy cousin of the zone
 * title card) and the coach tip plate. Both keep off the stage, the player's own floor.
 */
internal class HudMoments(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly

    // ======================================================= special floors

    private var kickerFloor = -1
    private var kicker = ""

    /** The engine's BIG popup naming a special floor, if one is up (it's drawn as this card instead). */
    private fun eventText(): FloatingText? {
        val list = f.w.fx.texts
        for (i in list.indices) {
            val ft = list[i]
            if (ft.style == TextStyle.BIG && eventOf(ft.text) != FloorEvent.NONE) return ft
        }
        return null
    }

    /**
     * BLACKOUT / NAP TIME / PAYDAY: a skewed band that slams in from the left with the event's
     * icon in a coloured block, the name in big type, a one-line joke, and a little flourish of
     * its own (the power cutting out, a lullaby bob, gold glints), then whips off to the right.
     */
    fun floorEvent() {
        val ft = eventText() ?: return
        val ev = eventOf(ft.text)
        val w = f.w
        val W = g.width
        val H = g.height
        val u = Hud.unit(W)
        val life = ft.maxLife
        val age = ft.t * life
        val inK = HudType.outBack(HudType.clamp01(age / 0.3f), 1.4f)
        val outK = HudType.inCubic(HudType.clamp01((age - (life - 0.32f)) / 0.32f))
        val a = HudType.clamp01(age / 0.1f) * (1f - outK)
        if (a <= 0f) return
        val color = eventColor(ev)

        // Under the stage (like the zone card), or under the zone card if that's up too.
        val stageBottom = (Geo.groundY(w.player.floorF) + Building.SLAB - f.camY) * f.s
        var cy = min(H * 0.72f, max(H * 0.3f, stageBottom + 17f * u))
        if (w.bannerZone != null && w.bannerTime > 0f) cy = min(H * 0.82f, cy + 30f * u)
        val bh = 15f * u
        val bw = 86f * u
        val sk = 2.2f * u
        val cx = W / 2f - (1f - inK) * W * 0.9f + outK * W * 1.1f
        val l = cx - bw / 2f
        val r = cx + bw / 2f
        val t = cy - bh / 2f
        val b = cy + bh / 2f

        // Entry pop: a quick flash in the event colour.
        if (age < 0.18f) {
            g.blend(Gfx.Blend.ADD)
            g.fillRect(0f, 0f, W, H, Col.alpha(color, 0.12f * (1f - age / 0.18f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        // Shadow, plate, glow.
        poly.quad(g, l + sk + 0.8f * u, t + 1.2f * u, r + sk + 0.8f * u, t + 1.2f * u, r - sk + 0.8f * u, b + 1.2f * u, l - sk + 0.8f * u, b + 1.2f * u, Col.alpha(0xFF000000.toInt(), 0.5f * a))
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(cx, cy)
        g.scale(1f, 0.32f)
        g.glow(0f, 0f, bw * 0.62f, Col.alpha(color, 0.3f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        poly.quad(g, l + sk, t, r + sk, t, r - sk, b, l - sk, b, Col.alpha(0xFF08060E.toInt(), 0.94f * a))
        g.save()
        g.clipRect(l - sk, t, r + sk, b)
        g.fillVerticalGradient(l - sk, t, r + sk, b, Col.alpha(color, 0.14f * a), Col.alpha(color, 0.02f * a))
        g.restore()
        // Edges: bright top rule, event-specific bottom trim.
        g.line(l + sk, t, r + sk, t, 0.35f * u, Col.alpha(color, a))
        when (ev) {
            FloorEvent.BLACKOUT -> hazardTrim(l, r, b, sk, u, a)
            else -> g.line(l - sk, b, r - sk, b, 0.25f * u, Col.alpha(color, 0.6f * a))
        }

        // Icon block.
        val iw = 15f * u
        poly.quad(g, l + sk, t, l + iw + sk, t, l + iw - sk, b, l - sk, b, Col.alpha(color, a))
        val icx = l + iw / 2f
        eventIcon(ev, icx, cy, 9.5f * u, Col.alpha(0xFF0A0810.toInt(), a), age)

        // Kicker, title, joke.
        val tx = l + iw + 3f * u
        if (w.player.floor != kickerFloor) {
            kickerFloor = w.player.floor
            kicker = "SPECIAL FLOOR  ·  " + FloorLabel.of(kickerFloor)
        }
        HudType.tracked(g, kicker, tx, t + 3.6f * u, 2f * u, Col.alpha(color, 0.9f * a), Gfx.Font.HUD, Gfx.Align.LEFT, 0.45f * u)
        titleLetters(ev, ft.text, tx, t + 9.9f * u, 6.2f * u, u, a, age, color)
        HudType.tracked(g, jokeOf(ev), tx, t + 13.1f * u, 2.1f * u, Col.alpha(0xFFE8E4F4.toInt(), 0.8f * a * HudType.clamp01((age - 0.2f) / 0.25f)), Gfx.Font.HUD, Gfx.Align.LEFT, 0.25f * u)

        if (ev == FloorEvent.PAYDAY) glints(l, r, t, b, u, a, age)
    }

    private fun titleLetters(ev: FloorEvent, title: String, x0: Float, y: Float, size: Float, u: Float, a: Float, age: Float, color: Int) {
        val tracking = 0.6f * u
        var x = x0
        val tick = (f.t * 20f).toInt()
        for (i in title.indices) {
            val c = title[i]
            val cs = charStr(c)
            val cw = g.textWidth(cs, size, Gfx.Font.TITLE)
            val k = HudType.clamp01((age - 0.08f - i * 0.03f) / 0.2f)
            if (k > 0f && c != ' ') {
                var la = k * a
                var dy = (1f - HudType.outCubic(k)) * -2.5f * u
                when (ev) {
                    // The power stutters: letters drop out and come back, then hold.
                    FloorEvent.BLACKOUT -> if (age < 0.9f && hash(tick * 13 + i, 91) < 0.35f) la *= 0.15f
                    // A lullaby sway: the whole word rocks, gently.
                    FloorEvent.NAP_TIME -> dy += sin(f.t * 2.4f + i * 0.35f) * 0.18f * u
                    else -> Unit
                }
                g.text(cs, x + 0.35f * u, y + dy + 0.45f * u, size, Col.alpha(0xFF000000.toInt(), 0.6f * la), Gfx.Font.TITLE)
                if (k < 1f) {
                    val split = (1f - k) * 1.6f * u
                    g.blend(Gfx.Blend.ADD)
                    g.text(cs, x - split, y + dy, size, Col.alpha(color, 0.7f * la), Gfx.Font.TITLE)
                    g.text(cs, x + split, y + dy, size, Col.alpha(0xFF2BE8FF.toInt(), 0.5f * la), Gfx.Font.TITLE)
                    g.blend(Gfx.Blend.NORMAL)
                }
                g.text(cs, x, y + dy, size, Col.alpha(0xFFFFFFFF.toInt(), la), Gfx.Font.TITLE)
            }
            x += cw + tracking
        }
    }

    /** Red-and-black hazard tape along the band's bottom edge. */
    private fun hazardTrim(l: Float, r: Float, b: Float, sk: Float, u: Float, a: Float) {
        val h = 1.1f * u
        g.save()
        g.clipRect(l - sk, b - h, r - sk + h, b)
        g.fillRect(l - sk, b - h, r, b, Col.alpha(0xFF100406.toInt(), a))
        var x = l - sk - h + fract(f.t * 0.6f) * 3f * u
        while (x < r) {
            poly.quad(g, x, b, x + 1.5f * u, b, x + 1.5f * u + h, b - h, x + h, b - h, Col.alpha(0xFFFF3348.toInt(), 0.9f * a))
            x += 3f * u
        }
        g.restore()
    }

    /** Twinkling four-point glints skating across the PAYDAY band. */
    private fun glints(l: Float, r: Float, t: Float, b: Float, u: Float, a: Float, age: Float) {
        g.blend(Gfx.Blend.ADD)
        for (i in 0 until 6) {
            val ph = fract(age * 0.9f + i / 6f)
            // Along the edges and over the coin, never over the type.
            val edge = i % 3
            val x = if (edge == 0) l + 7.5f * u + (hash(i, 72) - 0.5f) * 8f * u else l + (r - l) * (0.3f + 0.65f * hash(i, 71))
            val y = if (edge == 0) t + (b - t) * hash(i, 74) else if (edge == 1) t else b
            val s = sin(ph * PI.toFloat()) * (1.2f + 0.8f * hash(i, 73)) * u
            sparkle(x, y, s, Col.alpha(0xFFFFF0B0.toInt(), a))
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    private fun sparkle(x: Float, y: Float, s: Float, c: Int) {
        if (s <= 0.05f) return
        poly.begin().add(x, y - s).add(x + s * 0.22f, y - s * 0.22f).add(x + s, y).add(x + s * 0.22f, y + s * 0.22f)
            .add(x, y + s).add(x - s * 0.22f, y + s * 0.22f).add(x - s, y).add(x - s * 0.22f, y - s * 0.22f).fill(g, c)
    }

    private fun eventIcon(ev: FloorEvent, cx: Float, cy: Float, s: Float, c: Int, age: Float) {
        val k = s / 2f
        when (ev) {
            FloorEvent.BLACKOUT -> {
                // A light bulb, struck through.
                g.fillCircle(cx, cy - k * 0.18f, k * 0.5f, c)
                g.fillRect(cx - k * 0.24f, cy + k * 0.2f, cx + k * 0.24f, cy + k * 0.52f, c)
                g.fillRect(cx - k * 0.2f, cy + k * 0.58f, cx + k * 0.2f, cy + k * 0.66f, c)
                g.line(cx - k * 0.72f, cy - k * 0.78f, cx + k * 0.72f, cy + k * 0.78f, k * 0.16f, eventColor(ev))
                g.line(cx - k * 0.72f, cy - k * 0.78f, cx + k * 0.72f, cy + k * 0.78f, k * 0.09f, c)
            }
            FloorEvent.NAP_TIME -> {
                // Crescent moon (a disc with a bite of the block colour) and a little z.
                g.fillCircle(cx - k * 0.1f, cy + k * 0.05f, k * 0.62f, c)
                g.fillCircle(cx + k * 0.18f, cy - k * 0.12f, k * 0.52f, eventColor(ev))
                val zy = cy - k * 0.55f - fract(age * 0.8f) * k * 0.2f
                val zx = cx + k * 0.5f
                val zs = k * 0.22f
                g.line(zx - zs, zy - zs, zx + zs, zy - zs, k * 0.1f, c)
                g.line(zx + zs, zy - zs, zx - zs, zy + zs, k * 0.1f, c)
                g.line(zx - zs, zy + zs, zx + zs, zy + zs, k * 0.1f, c)
            }
            else -> {
                // A coin, flipping.
                val flip = abs(cos(age * 5f))
                g.save()
                g.translate(cx, cy)
                g.scale(0.25f + 0.75f * flip, 1f)
                g.fillCircle(0f, 0f, k * 0.72f, c)
                g.strokeCircle(0f, 0f, k * 0.52f, k * 0.07f, eventColor(ev))
                g.restore()
                if (flip > 0.5f) g.text("$", cx, cy + k * 0.32f, k * 0.95f * flip, eventColor(ev), Gfx.Font.TITLE, Gfx.Align.CENTER)
            }
        }
    }

    private fun eventOf(text: String): FloorEvent = when (text) {
        FloorEvent.BLACKOUT.title -> FloorEvent.BLACKOUT
        FloorEvent.NAP_TIME.title -> FloorEvent.NAP_TIME
        FloorEvent.PAYDAY.title -> FloorEvent.PAYDAY
        else -> FloorEvent.NONE
    }

    private fun eventColor(ev: FloorEvent): Int = when (ev) {
        FloorEvent.BLACKOUT -> 0xFFFF3348.toInt()
        FloorEvent.NAP_TIME -> 0xFFB6A4FF.toInt()
        else -> 0xFFFFC83A.toInt()
    }

    private fun jokeOf(ev: FloorEvent): String = when (ev) {
        FloorEvent.BLACKOUT -> "LIGHTS OUT. THEY CAN'T SEE YOU EITHER."
        FloorEvent.NAP_TIME -> "SHHH. SOMEBODY'S ON HIS BREAK."
        else -> "SOMEBODY LEFT THE LOOT OUT."
    }

    private val charCache = Array(128) { it.toChar().toString() }
    private fun charStr(c: Char) = if (c.code < 128) charCache[c.code] else c.toString()

    // ============================================================ coach tips

    private var tipSrc: String? = null
    private var tipKicker = ""
    private var tipMain = ""
    private var tipGlyph = GLYPH_TAP

    /** Splits "SWIPE DOWN: HIDE" into gesture and verb once, when the tip changes. */
    private fun parseTip(tip: String) {
        if (tip === tipSrc) return
        tipSrc = tip
        val cut = tip.indexOf(": ")
        tipKicker = if (cut > 0) tip.substring(0, cut) else if (tip.startsWith("WALK")) "SNEAK UP BEHIND" else "TIP"
        tipMain = if (cut > 0) tip.substring(cut + 2) else tip
        tipGlyph = when {
            tip.contains("SWIPE DOWN") -> GLYPH_DOWN
            tip.contains("SWIPE UP") -> GLYPH_UP
            tip.contains("GRENADE") -> GLYPH_GRENADE
            tip.startsWith("WALK") -> GLYPH_WALK
            tip.contains("DOOR") -> GLYPH_DOOR
            else -> GLYPH_TAP
        }
    }

    /**
     * The coach tip: a small plate hanging just under the player's floor (or over it, when the
     * floor sits low on screen), pointing at them, with an animated gesture glyph and the move.
     */
    fun coachTip() {
        val w = f.w
        val tip = w.coachTip ?: return
        val age = f.wt - w.coachTipAt
        if (w.coachTipAt < 0f || age < 0f || age > TIP_TIME) return
        parseTip(tip)
        val W = g.width
        val H = g.height
        val u = Hud.unit(W)
        val appear = HudType.outBack(HudType.clamp01(age / 0.28f), 1.8f)
        val a = HudType.clamp01(age / 0.12f) * HudType.clamp01((TIP_TIME - age) / 0.4f)
        if (a <= 0f) return
        val p = w.player
        val px = (p.x + 0.6f) * f.s + f.shakeX
        val gy = Geo.groundY(p.floorF)
        val stageBottom = (gy + Building.SLAB - f.camY) * f.s
        val stageTop = (gy - Geo.FLOOR_H + Building.SLAB - f.camY) * f.s

        val ks = 1.7f * u
        val ms = 3f * u
        val kw = HudType.trackedWidth(g, tipKicker, ks, Gfx.Font.HUD, 0.4f * u)
        val mw = HudType.trackedWidth(g, tipMain, ms, Gfx.Font.TITLE, 0.3f * u)
        val gd = 5.8f * u
        val ch = 8.4f * u
        val cw = 1.3f * u + gd + 1.8f * u + max(kw, mw) + 2.6f * u
        val below = stageBottom + 3.2f * u + ch < H - f.bottomInset - 24f * u
        val cy = if (below) stageBottom + 3.2f * u + ch / 2f else stageTop - 3.2f * u - ch / 2f
        val cx = px.coerceIn(cw / 2f + 4f * u, W - cw / 2f - 4f * u)
        val color = TIP

        g.save()
        g.translate(cx, cy)
        g.scale(appear, appear)
        val l = -cw / 2f
        val r = cw / 2f
        val t = -ch / 2f
        val b = ch / 2f
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.scale(1f, 0.45f)
        g.glow(0f, 0f, cw * 0.7f, Col.alpha(color, 0.22f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        g.fillRoundRect(l, t + 0.8f * u, r, b + 0.8f * u, 2.2f * u, Col.alpha(0xFF000000.toInt(), 0.55f * a))
        g.fillRoundRect(l, t, r, b, 2.2f * u, Col.alpha(0xFF0B0913.toInt(), 0.97f * a))
        g.fillRoundRect(l + 0.3f * u, t + 0.25f * u, r - 0.3f * u, t + ch * 0.42f, 2f * u, Col.alpha(0xFFFFFFFF.toInt(), 0.05f * a))
        g.strokeRoundRect(l, t, r, b, 2.2f * u, 0.22f * u, Col.alpha(color, 0.85f * a))
        // Pointer at the player.
        val tx = (px - cx).coerceIn(l + 3f * u, r - 3f * u) / appear.coerceAtLeast(0.2f)
        val tipY = if (below) t else b
        val dirY = if (below) -1f else 1f
        poly.tri(g, tx - 1.3f * u, tipY, tx + 1.3f * u, tipY, tx, tipY + dirY * 1.6f * u, Col.alpha(color, 0.85f * a))

        // Gesture glyph in its own well.
        val gx = l + 1.3f * u + gd / 2f
        g.fillCircle(gx, 0f, gd / 2f, Col.alpha(color, 0.16f * a))
        g.strokeCircle(gx, 0f, gd / 2f, 0.18f * u, Col.alpha(color, 0.5f * a))
        gestureGlyph(gx, 0f, gd, color, a, age)

        val x0 = gx + gd / 2f + 1.8f * u
        HudType.tracked(g, tipKicker, x0, -0.6f * u, ks, Col.alpha(color, 0.9f * a), Gfx.Font.HUD, Gfx.Align.LEFT, 0.4f * u)
        HudType.tracked(g, tipMain, x0, 2.9f * u, ms, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.LEFT, 0.3f * u)
        g.restore()
    }

    /** A fingertip acting out the move: swipe down/up, drag across, the grenade button, or a door. */
    private fun gestureGlyph(cx: Float, cy: Float, d: Float, color: Int, a: Float, age: Float) {
        val k = d / 2f
        val white = Col.alpha(0xFFFFFFFF.toInt(), a)
        val ph = fract(age * 1.1f)
        when (tipGlyph) {
            GLYPH_DOWN, GLYPH_UP, GLYPH_WALK -> {
                val dx = if (tipGlyph == GLYPH_WALK) 1f else 0f
                val dy = if (tipGlyph == GLYPH_DOWN) 1f else if (tipGlyph == GLYPH_UP) -1f else 0f
                val e = HudType.outCubic(HudType.clamp01(ph / 0.7f))
                val fa = HudType.clamp01((1f - ph) / 0.3f) * a
                val sx = cx - dx * k * 0.55f - dy * 0f
                val sy = cy - dy * k * 0.55f
                val ex = sx + dx * k * 1.1f * e
                val ey = sy + dy * k * 1.1f * e
                g.line(sx, sy, ex, ey, k * 0.3f, Col.alpha(color, 0.45f * fa))
                Glyphs.arrow(g, cx + dx * k * 0.25f, cy + dy * k * 0.25f, k * 0.5f, dx, dy, k * 0.12f, Col.alpha(color, 0.35f * a))
                g.fillCircle(ex, ey, k * 0.26f, Col.alpha(0xFFFFFFFF.toInt(), fa))
            }
            GLYPH_GRENADE -> {
                // The button's grenade, pressed: a ring pulses out of it.
                HudIcons.grenade(g, cx, cy + k * 0.05f, d * 0.6f, Col.alpha(GRENADE_LIME, a))
                g.strokeCircle(cx, cy, k * (0.55f + 0.35f * ph), k * 0.08f, Col.alpha(GRENADE_LIME, (1f - ph) * a))
            }
            GLYPH_DOOR -> {
                HudIcons.door(g, cx - k * 0.1f, cy, d * 0.62f, Col.alpha(Building.PASSAGE, a))
                val nudge = sin(age * 6f) * k * 0.08f
                Glyphs.arrow(g, cx + k * 0.5f + nudge, cy, k * 0.22f, 1f, 0f, k * 0.09f, Col.alpha(Building.PASSAGE, a))
            }
            else -> {
                g.fillCircle(cx, cy, k * 0.26f, white)
                g.strokeCircle(cx, cy, k * (0.3f + 0.5f * ph), k * 0.1f, Col.alpha(color, (1f - ph) * a))
            }
        }
    }

    private companion object {
        /** Seconds a coach tip stays up. */
        const val TIP_TIME = 3.4f
        const val TIP = 0xFFFFC14A.toInt()
        const val GLYPH_TAP = 0
        const val GLYPH_DOWN = 1
        const val GLYPH_UP = 2
        const val GLYPH_GRENADE = 3
        const val GRENADE_LIME = 0xFF9AE040.toInt()
        const val GLYPH_WALK = 4
        const val GLYPH_DOOR = 5
    }
}
