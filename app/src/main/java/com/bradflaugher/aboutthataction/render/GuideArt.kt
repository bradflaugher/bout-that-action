package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.GuideGesture
import com.bradflaugher.aboutthataction.engine.GuideSpot
import com.bradflaugher.aboutthataction.engine.Lesson
import com.bradflaugher.aboutthataction.engine.PlayerState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The guide ([com.bradflaugher.aboutthataction.engine.Guide]) on screen: brackets on what the
 * lesson is about, a prompt plate with the move, a ghost thumb acting it out where your thumb
 * goes, a "you did it" pop, and the walkthrough's step dots and SKIP pill. CALM SCREEN keeps
 * it steady: no pulsing, no flash.
 */
internal class GuideArt(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val buf = FloatArray(4)

    private val gd get() = f.w.guide

    /** Seconds the lesson on screen has been up, in world time. */
    private val age: Float get() = f.wt - gd.shownAt

    private fun color(): Int = when (gd.spot) {
        GuideSpot.LIFT -> Building.LIFT_CYAN
        GuideSpot.DOOR -> if (gd.lesson == Lesson.PASSAGE) Building.PASSAGE else if (gd.lesson == Lesson.STASH) STASH else TIP
        GuideSpot.MODE_BUTTON -> if (f.w.silent) Hud.QUIET else Hud.HOT
        GuideSpot.GRENADE_BUTTON -> Hud.LIME
        else -> TIP
    }

    private fun pulse(hz: Float): Float = if (f.calm) 0.5f else 0.5f + 0.5f * sin(f.t * hz)

    // ================================================================ world

    /** World space (inside the camera): brackets and a bobbing arrow on the lesson's target. */
    fun world() {
        val l = gd.lesson ?: return
        val spot = gd.spot
        if (spot != GuideSpot.ENEMY && spot != GuideSpot.LIFT && spot != GuideSpot.DOOR) return
        val p = f.w.player
        if (p.state == PlayerState.ELEVATOR || p.state == PlayerState.PASSAGE) return
        val done = gd.doneAt >= 0f
        val a = HudType.clamp01(age / 0.25f) * if (done) HudType.clamp01(1f - (f.wt - gd.doneAt) / 0.5f) else 1f
        if (a <= 0f) return
        val c = if (done) OK else color()
        val gy = Geo.groundY(p.floorF)
        val x = gd.focusX
        val h = if (spot == GuideSpot.ENEMY) 2.0f else 2.75f
        val hw = if (spot == GuideSpot.ENEMY) 0.75f else 0.85f
        val grow = (1f - HudType.outCubic(HudType.clamp01(age / 0.35f))) * 0.6f
        val l0 = x - hw - grow
        val r0 = x + hw + grow
        val t0 = gy - h - grow
        val b0 = gy + 0.08f
        val k = 0.32f
        val sw = 0.06f
        val ca = Col.alpha(c, (0.75f + 0.25f * pulse(5f)) * a)
        // Four corner brackets.
        g.line(l0, t0, l0 + k, t0, sw, ca); g.line(l0, t0, l0, t0 + k, sw, ca)
        g.line(r0, t0, r0 - k, t0, sw, ca); g.line(r0, t0, r0, t0 + k, sw, ca)
        g.line(l0, b0, l0 + k, b0, sw, ca); g.line(l0, b0, l0, b0 - k, sw, ca)
        g.line(r0, b0, r0 - k, b0, sw, ca); g.line(r0, b0, r0, b0 - k, sw, ca)
        // A glow ring on the floor, and the arrow overhead.
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(x, gy - 0.02f)
        g.scale(1f, 0.25f)
        g.glow(0f, 0f, 1.4f, Col.alpha(c, 0.35f * a))
        g.strokeCircle(0f, 0f, 0.9f + 0.25f * pulse(4f), 0.12f, Col.alpha(c, 0.6f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        if (!done) {
            val bob = if (f.calm) 0f else sin(f.t * 5f) * 0.12f
            val ay = t0 - 0.45f + bob
            poly.tri(g, x - 0.28f, ay - 0.28f, x + 0.28f, ay - 0.28f, x, ay + 0.06f, Col.alpha(0xFF000000.toInt(), 0.45f * a))
            poly.tri(g, x - 0.24f, ay - 0.32f, x + 0.24f, ay - 0.32f, x, ay, Col.alpha(c, a))
        }
        if (l == Lesson.TAKEDOWN && !done) {
            // Which side is his back: a little arrow on the floor pointing the way in.
            val e = f.w.enemies.firstOrNull { it.alive && kotlin.math.abs(it.x - x) < 0.01f }
            if (e != null) Glyphs.arrow(g, x - e.facing * 1.25f, gy - 0.9f, 0.3f, e.facing.toFloat(), 0f, 0.07f, Col.alpha(c, 0.8f * a))
        }
    }

    // =============================================================== screen

    /** Screen space: the plate, the ghost thumb, HUD highlights and the walkthrough's SKIP. */
    fun screen() {
        val w = f.w
        if (w.guide.walkthrough) skipPill()
        val l = gd.lesson ?: return
        val W = g.width
        val u = Hud.unit(W)
        val done = gd.doneAt >= 0f
        val sinceDone = if (done) f.wt - gd.doneAt else 0f
        val a = HudType.clamp01(age / 0.12f) * if (done) HudType.clamp01((com.bradflaugher.aboutthataction.engine.Guide.PRAISE_TIME - sinceDone) / 0.3f) else 1f
        if (a <= 0f) return
        val p = w.player
        if (p.state == PlayerState.ELEVATOR && !done) return
        hudSpot(u, a, done)
        if (!done) ghost(u, a)
        plate(l, u, a, done, sinceDone)
    }

    /** The prompt: next to the HUD part it's about, else hanging off the player's floor. */
    private fun plate(l: Lesson, u: Float, a: Float, done: Boolean, sinceDone: Float) {
        val w = f.w
        val W = g.width
        val H = g.height
        val ts = f.textScale
        val kicker = if (done) "" else gd.kicker
        val main = if (done) (l.praise ?: "") else gd.text
        if (main.isEmpty()) return
        val ks = 1.8f * u * ts
        val ms = 3.1f * u * ts
        val kw = if (kicker.isEmpty()) 0f else HudType.trackedWidth(g, kicker, ks, Gfx.Font.HUD, 0.4f * u)
        val mw = HudType.trackedWidth(g, main, ms, Gfx.Font.TITLE, 0.3f * u)
        val iconD = 5.6f * u * min(ts, 1.25f)
        val dots = gd.stepIndex > 0
        val ch = (if (done) 7f else 8.6f) * u * ts + (if (dots && !done) 2f * u else 0f)
        val cw = min(W - 6f * u, 1.4f * u + iconD + 1.8f * u + max(kw, mw) + 2.8f * u)
        // Where: by a HUD spot, or under (else over) the player's floor.
        val spot = gd.spot
        val cx: Float
        val cy: Float
        var pointX = Float.NaN
        var pointUp = true
        when (spot) {
            GuideSpot.MODE_BUTTON, GuideSpot.GRENADE_BUTTON -> {
                if (spot == GuideSpot.MODE_BUTTON) Hud.modeCenter(W, f.topInset, buf) else Hud.grenadeCenter(W, f.topInset, buf)
                cx = buf[0] - buf[2] * 1.9f - cw / 2f
                cy = buf[1]
            }
            GuideSpot.HEAT, GuideSpot.ZONE, GuideSpot.COMBO -> {
                cy = f.topInset + 42f * u + ch / 2f
                cx = if (spot == GuideSpot.COMBO) W - 4f * u - cw / 2f else 4f * u + cw / 2f
                hudRect(spot, u, buf)
                pointX = (buf[0] + buf[2]) / 2f
            }
            else -> {
                val gy = Geo.groundY(w.player.floorF)
                val stageBottom = (gy + Building.SLAB - f.camY) * f.s
                val stageTop = (gy - Geo.FLOOR_H + Building.SLAB - f.camY) * f.s
                val below = stageBottom + 3.2f * u + ch < H - f.bottomInset - 34f * u
                cy = if (below) stageBottom + 3.2f * u + ch / 2f else stageTop - 3.2f * u - ch / 2f
                pointUp = below
                val px = (w.player.x + 0.6f) * f.s + f.shakeX
                cx = px.coerceIn(cw / 2f + 3f * u, W - cw / 2f - 3f * u)
                pointX = px
            }
        }
        val pop = if (done) 1f + 0.12f * HudType.decay(HudType.clamp01(sinceDone / 0.3f)) else HudType.outBack(HudType.clamp01(age / 0.28f), 1.8f)
        val c = if (done) OK else color()
        g.save()
        g.translate(cx, cy)
        g.scale(pop, pop)
        val l0 = -cw / 2f
        val r0 = cw / 2f
        val t0 = -ch / 2f
        val b0 = ch / 2f
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.scale(1f, 0.45f)
        g.glow(0f, 0f, cw * 0.7f, Col.alpha(c, (if (done && !f.calm) 0.4f else 0.22f) * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        g.fillRoundRect(l0, t0 + 0.8f * u, r0, b0 + 0.8f * u, 2.2f * u, Col.alpha(0xFF000000.toInt(), 0.55f * a))
        g.fillRoundRect(l0, t0, r0, b0, 2.2f * u, Col.alpha(0xFF0B0913.toInt(), 0.97f * a))
        g.fillRoundRect(l0 + 0.3f * u, t0 + 0.25f * u, r0 - 0.3f * u, t0 + ch * 0.42f, 2f * u, Col.alpha(0xFFFFFFFF.toInt(), 0.05f * a))
        g.strokeRoundRect(l0, t0, r0, b0, 2.2f * u, 0.24f * u, Col.alpha(c, 0.9f * a))
        // The pointer: at the player (or the HUD part above), or at the button to the right.
        if (spot == GuideSpot.MODE_BUTTON || spot == GuideSpot.GRENADE_BUTTON) {
            poly.tri(g, r0, -1.3f * u, r0, 1.3f * u, r0 + 1.6f * u, 0f, Col.alpha(c, 0.9f * a))
        } else if (!pointX.isNaN()) {
            val tx = ((pointX - cx) / pop).coerceIn(l0 + 3f * u, r0 - 3f * u)
            val tipY = if (pointUp) t0 else b0
            val dir = if (pointUp) -1f else 1f
            poly.tri(g, tx - 1.3f * u, tipY, tx + 1.3f * u, tipY, tx, tipY + dir * 1.6f * u, Col.alpha(c, 0.9f * a))
        }
        // Icon well: the move in miniature, or a big check once it's done.
        val textTop = if (dots && !done) -1f * u else 0f
        val gx = l0 + 1.4f * u + iconD / 2f
        g.fillCircle(gx, textTop, iconD / 2f, Col.alpha(c, 0.16f * a))
        g.strokeCircle(gx, textTop, iconD / 2f, 0.18f * u, Col.alpha(c, 0.55f * a))
        if (done) check(gx, textTop, iconD * 0.55f, Col.alpha(OK, a), HudType.clamp01(sinceDone / 0.25f))
        else glyph(gd.gesture, gx, textTop, iconD, c, a)
        val x0 = gx + iconD / 2f + 1.8f * u
        if (kicker.isEmpty()) {
            HudType.tracked(g, main, x0, textTop + ms * 0.36f, ms, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.LEFT, 0.3f * u)
        } else {
            HudType.tracked(g, kicker, x0, textTop - 0.5f * u * ts, ks, Col.alpha(c, 0.95f * a), Gfx.Font.HUD, Gfx.Align.LEFT, 0.4f * u)
            HudType.tracked(g, main, x0, textTop + 3f * u * ts, ms, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.LEFT, 0.3f * u)
        }
        if (dots && !done) {
            // The walkthrough's progress: one dot per step, the current one lit.
            val n = gd.stepCount
            val dy = b0 - 2f * u
            val step = 2.4f * u
            val dx0 = -(n - 1) * step / 2f
            for (i in 0 until n) {
                val on = i + 1 == gd.stepIndex
                val past = i + 1 < gd.stepIndex
                val col = if (on) c else if (past) OK else 0xFF4A456B.toInt()
                g.fillCircle(dx0 + i * step, dy, (if (on) 0.6f else 0.42f) * u, Col.alpha(col, a))
            }
        }
        g.restore()
    }

    /** A check mark drawn on as [k] goes 0..1. */
    private fun check(cx: Float, cy: Float, s: Float, c: Int, k: Float) {
        val x1 = cx - s * 0.45f
        val y1 = cy
        val x2 = cx - s * 0.12f
        val y2 = cy + s * 0.32f
        val x3 = cx + s * 0.5f
        val y3 = cy - s * 0.38f
        val sw = s * 0.16f
        val k1 = HudType.clamp01(k / 0.4f)
        g.line(x1, y1, x1 + (x2 - x1) * k1, y1 + (y2 - y1) * k1, sw, c)
        if (k > 0.4f) {
            val k2 = HudType.clamp01((k - 0.4f) / 0.6f)
            g.line(x2, y2, x2 + (x3 - x2) * k2, y2 + (y3 - y2) * k2, sw, c)
        }
    }

    /** The move in miniature, inside the plate's icon well. */
    private fun glyph(gesture: GuideGesture, cx: Float, cy: Float, d: Float, color: Int, a: Float) {
        val k = d / 2f
        val ph = if (f.calm) 0.6f else fract(f.wt * 1.1f)
        when (gesture) {
            GuideGesture.DRAG, GuideGesture.SWIPE_UP, GuideGesture.SWIPE_DOWN -> {
                val dx = if (gesture == GuideGesture.DRAG) 1f else 0f
                val dy = if (gesture == GuideGesture.SWIPE_DOWN) 1f else if (gesture == GuideGesture.SWIPE_UP) -1f else 0f
                val e = HudType.outCubic(HudType.clamp01(ph / 0.7f))
                val fa = HudType.clamp01((1f - ph) / 0.3f) * a
                val sx = cx - dx * k * 0.55f
                val sy = cy - dy * k * 0.55f
                val ex = sx + dx * k * 1.1f * e
                val ey = sy + dy * k * 1.1f * e
                g.line(sx, sy, ex, ey, k * 0.3f, Col.alpha(color, 0.45f * fa))
                Glyphs.arrow(g, cx + dx * k * 0.25f, cy + dy * k * 0.25f, k * 0.5f, dx, dy, k * 0.12f, Col.alpha(color, 0.35f * a))
                g.fillCircle(ex, ey, k * 0.26f, Col.alpha(0xFFFFFFFF.toInt(), fa))
            }
            GuideGesture.GRENADE_BUTTON -> HudIcons.grenade(g, cx, cy + k * 0.05f, d * 0.6f, Col.alpha(Hud.LIME, a))
            GuideGesture.MODE_BUTTON -> {
                g.strokeCircle(cx, cy, k * 0.42f, k * 0.1f, Col.alpha(color, a))
                g.line(cx - k * 0.7f, cy, cx + k * 0.7f, cy, k * 0.1f, Col.alpha(color, a))
                g.line(cx, cy - k * 0.7f, cx, cy + k * 0.7f, k * 0.1f, Col.alpha(color, a))
            }
            GuideGesture.TAP -> {
                g.fillCircle(cx, cy, k * 0.26f, Col.alpha(0xFFFFFFFF.toInt(), a))
                g.strokeCircle(cx, cy, k * (0.3f + 0.5f * ph), k * 0.1f, Col.alpha(color, (1f - ph) * a))
            }
            GuideGesture.NONE -> when (gd.spot) {
                GuideSpot.HEAT -> for (i in 0 until 4) {
                    val bx = cx - k * 0.6f + i * k * 0.32f
                    g.fillRect(bx, cy - k * 0.15f, bx + k * 0.24f, cy + k * 0.15f, Col.alpha(heat(i / 3f), a))
                }
                GuideSpot.COMBO -> g.text("×2", cx, cy + k * 0.3f, k * 0.9f, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER)
                else -> g.fillCircle(cx, cy, k * 0.3f, Col.alpha(color, a))
            }
        }
    }

    private fun heat(t: Float): Int = when {
        t < 0.4f -> 0xFF5CFF8A.toInt()
        t < 0.75f -> 0xFFFFC14A.toInt()
        else -> 0xFFFF4A3A.toInt()
    }

    /**
     * The ghost thumb: a see-through fingertip doing the move where your thumb plays, low and
     * centred (or pressing the HUD button the lesson is about).
     */
    private fun ghost(u: Float, a: Float) {
        val W = g.width
        val H = g.height
        val gesture = gd.gesture
        if (gesture == GuideGesture.NONE) return
        val period = if (gesture == GuideGesture.DRAG) 2.2f else 1.5f
        val ph = fract(age / period)
        var cx = W / 2f
        var cy = H - f.bottomInset - 26f * u
        val r = 5.4f * u
        val fa = 0.75f * a
        val white = 0xFFFFFFFF.toInt()
        val c = color()
        when (gesture) {
            GuideGesture.DRAG -> {
                // Thumb down, slide right, back left: a run drag. The trail shows the way.
                val dir = if (gd.spot == GuideSpot.PLAYER || gd.focusX >= f.w.player.x) 1f else -1f
                val s = sin(ph * 2f * Math.PI.toFloat())
                val x = cx + dir * s * 12f * u
                g.line(cx - 12f * u, cy, cx + 12f * u, cy, 0.5f * u, Col.alpha(white, 0.12f * a))
                Glyphs.arrow(g, cx + dir * 19f * u, cy, 2.2f * u, dir, 0f, 0.6f * u, Col.alpha(c, 0.85f * a))
                Glyphs.arrow(g, cx - dir * 19f * u, cy, 1.5f * u, -dir, 0f, 0.45f * u, Col.alpha(c, 0.4f * a))
                finger(x, cy, r, fa, c, pressed = true)
            }
            GuideGesture.SWIPE_UP, GuideGesture.SWIPE_DOWN -> {
                val dy = if (gesture == GuideGesture.SWIPE_UP) -1f else 1f
                val e = HudType.outCubic(HudType.clamp01(ph / 0.45f))
                val fade = HudType.clamp01((0.85f - ph) / 0.25f)
                val y0 = cy - dy * 7f * u
                val y = y0 + dy * 14f * u * e
                if (ph < 0.85f) {
                    g.line(cx, y0, cx, y, 2.2f * u, Col.alpha(white, 0.14f * a * fade))
                    finger(cx, y, r, fa * fade, c, pressed = true)
                }
                Glyphs.arrow(g, cx + 9f * u, cy, 2.4f * u, 0f, dy, 0.55f * u, Col.alpha(c, 0.75f * a))
            }
            GuideGesture.TAP, GuideGesture.MODE_BUTTON, GuideGesture.GRENADE_BUTTON -> {
                if (gesture != GuideGesture.TAP) {
                    if (gesture == GuideGesture.MODE_BUTTON) Hud.modeCenter(W, f.topInset, buf) else Hud.grenadeCenter(W, f.topInset, buf)
                    cx = buf[0] + buf[2] * 0.35f
                    cy = buf[1] + buf[2] * 0.55f
                }
                val press = ph in 0.35f..0.55f
                if (ph > 0.35f) {
                    val rk = HudType.clamp01((ph - 0.35f) / 0.5f)
                    g.strokeCircle(cx, cy, r * (0.6f + 1.2f * rk), 0.45f * u, Col.alpha(c, 0.7f * (1f - rk) * a))
                }
                finger(cx, cy - (if (press) 0f else 1.2f * u), r * (if (press) 0.9f else 1f), fa, c, pressed = press)
            }
            GuideGesture.NONE -> Unit
        }
    }

    /** A fingertip seen from above: a soft pad with a nail highlight. */
    private fun finger(x: Float, y: Float, r: Float, a: Float, c: Int, pressed: Boolean) {
        if (a <= 0f) return
        g.fillCircle(x, y + r * 0.12f, r * 1.05f, Col.alpha(0xFF000000.toInt(), 0.25f * a))
        g.fillCircle(x, y, r, Col.alpha(0xFFF4ECFF.toInt(), 0.55f * a))
        g.strokeCircle(x, y, r, r * 0.09f, Col.alpha(c, (if (pressed) 0.9f else 0.6f) * a))
        g.fillRoundRect(x - r * 0.42f, y - r * 0.72f, x + r * 0.42f, y - r * 0.1f, r * 0.3f, Col.alpha(0xFFFFFFFF.toInt(), 0.35f * a))
    }

    /** Brackets on the HUD part a lesson is about (the heat bar, the combo, the zone), or a ring on a button. */
    private fun hudSpot(u: Float, a: Float, done: Boolean) {
        val spot = gd.spot
        val c = if (done) OK else color()
        when (spot) {
            GuideSpot.MODE_BUTTON, GuideSpot.GRENADE_BUTTON -> {
                if (spot == GuideSpot.MODE_BUTTON) Hud.modeCenter(g.width, f.topInset, buf) else Hud.grenadeCenter(g.width, f.topInset, buf)
                val r = buf[2] * (1.25f + 0.12f * pulse(6f))
                g.strokeCircle(buf[0], buf[1], r, 0.4f * u, Col.alpha(c, 0.85f * a))
            }
            GuideSpot.HEAT, GuideSpot.ZONE, GuideSpot.COMBO -> {
                hudRect(spot, u, buf)
                val k = 1.6f * u
                val sw = 0.35f * u
                val ca = Col.alpha(c, (0.7f + 0.3f * pulse(5f)) * a)
                val l0 = buf[0]; val t0 = buf[1]; val r0 = buf[2]; val b0 = buf[3]
                g.line(l0, t0, l0 + k, t0, sw, ca); g.line(l0, t0, l0, t0 + k, sw, ca)
                g.line(r0, t0, r0 - k, t0, sw, ca); g.line(r0, t0, r0, t0 + k, sw, ca)
                g.line(l0, b0, l0 + k, b0, sw, ca); g.line(l0, b0, l0, b0 - k, sw, ca)
                g.line(r0, b0, r0 - k, b0, sw, ca); g.line(r0, b0, r0, b0 - k, sw, ca)
            }
            else -> Unit
        }
    }

    /** Screen rect (l, t, r, b) of a HUD part, matching [Hud.draw]'s layout. */
    private fun hudRect(spot: GuideSpot, u: Float, out: FloatArray) {
        val top = f.topInset + 2.5f * u
        val left = 4f * u
        when (spot) {
            GuideSpot.HEAT -> { out[0] = left - 1f * u; out[1] = top + 10.8f * u; out[2] = left + 31f * u; out[3] = top + 14f * u }
            GuideSpot.ZONE -> { out[0] = left - 1f * u; out[1] = top - 0.5f * u; out[2] = left + 36f * u; out[3] = top + 10.8f * u }
            else -> {
                val right = g.width - 4f * u - 9.2f * u - 5f * u
                out[0] = right - 16f * u; out[1] = top + 9f * u; out[2] = right + 1f * u; out[3] = top + 19f * u
            }
        }
    }

    /** SKIP: one tap ends the walkthrough. Under the grenade button, clear of the thumbs. */
    private fun skipPill() {
        val W = g.width
        val u = Hud.unit(W)
        Hud.skipRect(W, f.topInset, buf)
        val l0 = buf[0]; val t0 = buf[1]; val r0 = buf[2]; val b0 = buf[3]
        val h = b0 - t0
        g.fillRoundRect(l0, t0 + 0.4f * u, r0, b0 + 0.4f * u, h / 2f, 0x66000000)
        g.fillRoundRect(l0, t0, r0, b0, h / 2f, 0xE60B0913.toInt())
        g.strokeRoundRect(l0, t0, r0, b0, h / 2f, 0.2f * u, 0x99E8E4F4.toInt())
        val ts = 2.2f * u * min(f.textScale, 1.2f)
        HudType.tracked(g, "SKIP", (l0 + r0) / 2f - 1.2f * u, (t0 + b0) / 2f + ts * 0.36f, ts, 0xFFE8E4F4.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER, 0.3f * u)
        Glyphs.arrow(g, r0 - 2.6f * u, (t0 + b0) / 2f, 0.9f * u, 1f, 0f, 0.3f * u, 0xFFE8E4F4.toInt())
    }

    private fun fract(x: Float) = x - kotlin.math.floor(x)

    private companion object {
        const val TIP = 0xFFFFC14A.toInt()
        const val OK = 0xFF5CFF9A.toInt()
        const val STASH = 0xFFFFD27A.toInt()
    }
}
