package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Popup
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.FloatingText
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.TextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * The fun pass's little moments, in world space: snoring guards, the box getting kicked off
 * you, smooth elevator jazz and the GHOST who slips away.
 *
 * The engine announces these with one-shot popups ([FloatingText]); the renderer never sees
 * the [com.bradflaugher.aboutthataction.engine.GameEvent]s (the app drains those for sound). So
 * [scan] watches the popup list once a frame, notices each new one by identity, and times the
 * animation from the popup's own age: deterministic, and right in a screenshot's first frame.
 */
internal class Moments(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly

    // The box kick: who kicked, when (real seconds), and where the box flies from.
    private var lastHey: FloatingText? = null
    var kickId = -1
        private set
    var kickAt = -99f
        private set
    private var kickX = 0f
    private var kickGy = 0f
    private var kickDir = 1
    private var kickHall = 0
    private var kickFloor = -1

    // Smooth jazz: the shaft whose car is playing it.
    private var lastJazz: FloatingText? = null
    private var jazzShaft = -1
    private var jazzAt = -99f

    // GHOST: a little bedsheet ghost floats up out of the spot you left from.
    private var lastGhost: FloatingText? = null
    private var ghostAt = -99f
    private var ghostX = 0f
    private var ghostGy = 0f

    private fun age(ft: FloatingText) = ft.t * ft.maxLife

    fun scan() {
        val w = f.w
        val list = w.fx.texts
        val p = w.player
        for (i in list.indices) {
            val ft = list[i]
            when {
                ft.style == TextStyle.WARN && ft.text == HEY -> if (ft !== lastHey) {
                    lastHey = ft
                    kickAt = f.t - age(ft)
                    kickX = p.x
                    kickFloor = p.floor
                    kickHall = p.hall
                    kickGy = Geo.groundY(p.floor)
                    kickDir = if (p.x >= ft.x) 1 else -1
                    // The kicker: the closest guard to where the shout came from.
                    var best = -1
                    var bd = 1.2f
                    for (e in w.enemies) {
                        if (e.floor != p.floor || e.hall != p.hall || !e.alive) continue
                        val d = abs(e.x - ft.x)
                        if (d < bd) { bd = d; best = e.id }
                    }
                    kickId = best
                }
                ft.text == JAZZ -> if (ft !== lastJazz) {
                    lastJazz = ft
                    jazzShaft = if (p.state == PlayerState.ELEVATOR) p.elevatorShaft else -1
                    jazzAt = f.t - age(ft)
                }
                ft.style == TextStyle.COMBO && ft.text.startsWith(GHOST) -> if (ft !== lastGhost) {
                    lastGhost = ft
                    ghostAt = f.t - age(ft)
                    ghostX = ft.x
                    ghostGy = Geo.groundY(p.floor)
                }
            }
        }
        if (jazzShaft >= 0 && !(p.state == PlayerState.ELEVATOR && p.elevatorShaft == jazzShaft)) jazzShaft = -1
    }

    /** Seconds since [e] was rudely woken (the "?!" over him), or -1. */
    fun wokeAge(e: Enemy): Float {
        if (e.state != EnemyState.ALERT || e.stateTime > 0.8f) return -1f
        val list = f.w.fx.texts
        val gy = Geo.groundY(e.floor)
        for (i in list.indices) {
            val ft = list[i]
            if (ft.text != WAKE || abs(ft.x - e.x) > 0.3f || abs(ft.y - (gy - 2f)) > 1.6f) continue
            return age(ft)
        }
        return -1f
    }

    /** Seconds since [e] kicked the box off the player, or -1 once the kick is over. */
    fun kickAge(e: Enemy): Float {
        if (e.id != kickId) return -1f
        val a = f.t - kickAt
        return if (a in 0f..KICK_POSE) a else -1f
    }

    fun world() {
        muzak()
        tumblingBox()
        ghost()
    }

    // ---------------------------------------------------------------- Zzz

    /**
     * A napping guard's snore: three hand-drawn Zs drifting up and back from his head, growing
     * as they go, and a bubble at the nose that swells with every breath.
     */
    fun zzz(e: Enemy, hx: Float, hy: Float, dir: Int, r: Float, alpha: Float) {
        if (alpha <= 0.02f) return
        val ink = Col.alpha(ActorPaint.INK, 0.9f * alpha)
        for (i in 0 until 3) {
            val ph = fract(f.t * 0.42f + i / 3f + e.id * 0.13f)
            val a = min(1f, ph / 0.15f) * min(1f, (1f - ph) / 0.4f) * alpha
            if (a <= 0.01f) continue
            val s = 0.1f + 0.17f * ph
            val x = hx - dir * (0.18f + 0.55f * ph) + sin(ph * 7f + i) * 0.06f
            val y = hy - 0.25f - 1.05f * ph
            zGlyph(x, y, s, sin(ph * 5f + i) * 12f - dir * 8f, Col.alpha(ink, a * 0.9f), Col.alpha(ZZZ, a))
        }
        // The snot bubble: swells on the in-breath, pops, starts again.
        val cyc = fract(f.t * 0.38f + e.id * 0.29f)
        val nx = hx + dir * r * 1.05f
        val ny = hy + r * 0.35f
        if (cyc < 0.9f) {
            val br = 0.025f + 0.075f * sin(cyc / 0.9f * PI.toFloat() * 0.5f)
            val bx = nx + dir * br * 0.8f
            g.fillCircle(bx, ny + br * 0.2f, br, Col.alpha(0xFFBFE8FF.toInt(), 0.35f * alpha))
            g.strokeCircle(bx, ny + br * 0.2f, br, 0.014f, Col.alpha(0xFFE8F8FF.toInt(), 0.8f * alpha))
            g.fillCircle(bx - br * 0.35f, ny - br * 0.2f, br * 0.25f, Col.alpha(0xFFFFFFFF.toInt(), 0.85f * alpha))
        } else {
            // Pop: a few droplets.
            val k = (cyc - 0.9f) / 0.1f
            for (j in 0 until 4) {
                val ang = j * 1.57f + 0.4f
                g.fillCircle(nx + dir * 0.06f + kotlin.math.cos(ang) * 0.1f * k, ny + sin(ang) * 0.1f * k, 0.012f, Col.alpha(0xFFE8F8FF.toInt(), (1f - k) * alpha))
            }
        }
    }

    /** A chunky shape-drawn Z, [s] tall, tilted [deg], outlined. */
    private fun zGlyph(x: Float, y: Float, s: Float, deg: Float, ink: Int, color: Int) {
        g.save()
        g.translate(x, y)
        g.rotate(deg)
        val h = s / 2f
        val w = s * 0.42f
        val sw = s * 0.2f
        val o = ActorPaint.OUT * 0.9f
        for (pass in 0..1) {
            val lw = if (pass == 0) sw + o * 2f else sw
            val c = if (pass == 0) ink else color
            g.line(-w, -h, w, -h, lw, c)
            g.line(w, -h, -w, h, lw, c)
            g.line(-w, h, w, h, lw, c)
        }
        g.restore()
    }

    // ------------------------------------------------------------- muzak

    /** Little notes bubbling out of the car that's playing smooth jazz. */
    private fun muzak() {
        if (jazzShaft < 0) return
        val car = f.w.elevators[jazzShaft] ?: return
        val sx = car.shaft.x
        val yb = Geo.groundY(car.pos)
        val top = yb - 2.45f
        if (!f.visibleY(top - 2f, yb)) return
        val intro = HudType.clamp01((f.t - jazzAt) / 0.4f)
        for (i in 0 until 7) {
            val ph = fract(f.t * 0.38f + i / 7f)
            val side = if (i % 2 == 0) -1f else 1f
            val a = min(1f, ph / 0.12f) * min(1f, (1f - ph) / 0.35f) * intro
            if (a <= 0.01f) continue
            val x = sx + side * (0.62f + ph * 0.55f) + sin(ph * 9f + i * 1.7f) * 0.14f
            val y = top + 0.9f - ph * 1.9f
            val c = NOTE_COLORS[i % NOTE_COLORS.size]
            note(x, y, 0.2f + 0.06f * (i % 3), sin(ph * 6f + i) * 14f, i % 3 == 1, c, a)
        }
    }

    /** A quaver (or a beamed pair when [pair]), [s] tall, head at the bottom. */
    private fun note(x: Float, y: Float, s: Float, deg: Float, pair: Boolean, color: Int, a: Float) {
        g.save()
        g.translate(x, y)
        g.rotate(deg)
        val hr = s * 0.24f
        val ink = Col.alpha(ActorPaint.INK, 0.85f * a)
        val c = Col.alpha(color, a)
        val o = ActorPaint.OUT
        val gap = if (pair) s * 0.62f else 0f
        for (pass in 0..1) {
            val grow = if (pass == 0) o else 0f
            val col = if (pass == 0) ink else c
            for (k in 0..(if (pair) 1 else 0)) {
                val hx = k * gap
                g.save()
                g.translate(hx, 0f)
                g.rotate(-22f)
                g.scale(1.3f, 1f)
                g.fillCircle(0f, 0f, hr + grow, col)
                g.restore()
                g.line(hx + hr * 1.05f, 0f, hx + hr * 1.05f, -s, s * 0.09f + grow * 2f, col)
            }
            if (pair) {
                poly.quad(g, hr * 1.05f - grow, -s - grow, gap + hr * 1.05f + grow, -s - grow, gap + hr * 1.05f + grow, -s + s * 0.2f + grow, hr * 1.05f - grow, -s + s * 0.2f + grow, col)
            } else {
                g.line(hr * 1.05f, -s, hr * 1.05f + s * 0.34f, -s * 0.62f, s * 0.12f + grow * 2f, col)
            }
        }
        g.restore()
    }

    // ------------------------------------------------------------ the kick

    /** The box, booted off you: it cartwheels away, bounces once and flops over. */
    private fun tumblingBox() {
        val u = f.t - kickAt
        if (u < 0f || u > TUMBLE) return
        if (f.w.viewHall(kickFloor) != kickHall) return
        if (!f.visibleY(kickGy - 4f, kickGy)) return
        val dir = kickDir.toFloat()
        // Two parabolic hops, then a slide to rest.
        val t1 = 2f * V0 / GRAV
        val t2 = 2f * V1 / GRAV
        val z: Float
        val spin: Float
        if (u < t1) {
            z = V0 * u - 0.5f * GRAV * u * u
            spin = 470f * u
        } else if (u < t1 + t2) {
            val v = u - t1
            z = V1 * v - 0.5f * GRAV * v * v
            spin = 470f * t1 + 260f * v
        } else {
            z = 0f
            spin = 470f * t1 + 260f * t2
        }
        val travel = if (u < t1 + t2) u * 3.1f else (t1 + t2) * 3.1f + (1f - kotlin.math.exp(-(u - t1 - t2) * 6f)) * 0.25f
        var x = kickX + dir * travel
        x = x.coerceIn(0.5f, Geo.FLOOR_W - 0.5f)
        // Settle upside down-ish: snap the spin to the nearest quarter turn once it's landed.
        val rest = kotlin.math.round(spin / 90f) * 90f
        val ang = if (u < t1 + t2) spin else rest + (spin - rest) * kotlin.math.exp(-(u - t1 - t2) * 10f)
        val a = if (u > TUMBLE - 0.35f) (TUMBLE - u) / 0.35f else 1f
        val h = 0.8f
        val cy = kickGy - z - h / 2f
        // Shadow on the floor.
        g.save()
        g.translate(x, kickGy - 0.02f)
        g.scale(1f, 0.2f)
        g.fillCircle(0f, 0f, 0.5f * (1f - min(0.6f, z / 2f)), Col.alpha(0xFF000000.toInt(), 0.35f * a))
        g.restore()
        g.save()
        g.translate(x, cy)
        g.rotate(ang * dir)
        val hw = 0.48f
        val o = ActorPaint.OUT
        val ink = Col.alpha(ActorPaint.INK, a)
        // Open flaps (it's empty now), front face, tape.
        val flap = 0.2f + 0.1f * sin(u * 30f)
        poly.quad(g, -hw - o, -h / 2f, -hw + 0.3f, -h / 2f, -hw + 0.1f - o, -h / 2f - 0.3f - o, -hw - 0.28f - o, -h / 2f - flap - o, ink)
        poly.quad(g, hw + o, -h / 2f, hw - 0.3f, -h / 2f, hw - 0.1f + o, -h / 2f - 0.3f - o, hw + 0.28f + o, -h / 2f - flap - o, ink)
        poly.quad(g, -hw, -h / 2f, -hw + 0.28f, -h / 2f, -hw + 0.1f, -h / 2f - 0.28f, -hw - 0.26f, -h / 2f - flap, Col.alpha(0xFFCC9660.toInt(), a))
        poly.quad(g, hw, -h / 2f, hw - 0.28f, -h / 2f, hw - 0.1f, -h / 2f - 0.28f, hw + 0.26f, -h / 2f - flap, Col.alpha(0xFFB07A46.toInt(), a))
        g.fillRect(-hw - o, -h / 2f - o, hw + o, h / 2f + o, ink)
        g.fillRect(-hw, -h / 2f, hw, h / 2f, Col.alpha(0xFFC08A52.toInt(), a))
        g.fillRect(-hw, h / 2f - 0.12f, hw, h / 2f, Col.alpha(0xFF8E6036.toInt(), a))
        g.fillRect(-0.06f, -h / 2f, 0.06f, h / 2f, Col.alpha(0xFFD9B77C.toInt(), a))
        // The empty peek slot, and a boot print right on the front.
        g.fillRoundRect(0.09f, -h / 2f + 0.15f, 0.39f, -h / 2f + 0.28f, 0.065f, Col.alpha(0xFF140A04.toInt(), a))
        g.fillRoundRect(-0.36f, -0.05f, -0.14f, 0.25f, 0.08f, Col.alpha(0xFF5A3A1E.toInt(), 0.8f * a))
        g.fillRect(-0.34f, 0.02f, -0.16f, 0.04f, Col.alpha(0xFFC08A52.toInt(), a))
        g.fillRect(-0.34f, 0.12f, -0.16f, 0.14f, Col.alpha(0xFFC08A52.toInt(), a))
        g.restore()
        // Impact star where the boot connected.
        if (u < 0.22f) {
            val k = u / 0.22f
            val sx = kickX - dir * 0.3f
            val sy = kickGy - 0.5f
            g.blend(Gfx.Blend.ADD)
            g.glow(sx, sy, 0.8f * (1f - k) + 0.2f, Col.alpha(0xFFFFE6B0.toInt(), 0.7f * (1f - k)))
            g.blend(Gfx.Blend.NORMAL)
            for (i in 0 until 8) {
                val ang = i * 0.785f + 0.2f
                val r0 = 0.15f + 0.6f * HudType.outCubic(k)
                val r1 = r0 + 0.3f * (1f - k)
                g.line(sx + kotlin.math.cos(ang) * r0, sy + sin(ang) * r0, sx + kotlin.math.cos(ang) * r1, sy + sin(ang) * r1, 0.05f * (1f - k) + 0.01f, Col.alpha(0xFFFFFFFF.toInt(), 1f - k))
            }
        }
    }

    // --------------------------------------------------------------- ghost

    /** GHOST: a bedsheet ghost in the agent's shades floats up and away, waving. */
    private fun ghost() {
        val u = f.t - ghostAt
        if (u < 0f || u > GHOST_TIME) return
        if (!f.visibleY(ghostGy - 5f, ghostGy)) return
        val k = u / GHOST_TIME
        val a = min(1f, u / 0.15f) * (1f - HudType.inCubic(k)) * 0.85f
        // Off to the side of the GHOST stamp, drifting up and away toward the open side.
        val away = if (ghostX < Geo.FLOOR_W / 2f) 1f else -1f
        val x = ghostX + away * (0.9f + 1.4f * HudType.outCubic(k)) + sin(u * 5f) * 0.15f
        val y = ghostGy - 0.9f - HudType.outCubic(k) * 1.6f
        val s = 0.8f + 0.2f * HudType.outBack(min(1f, u / 0.3f))
        g.save()
        g.translate(x, y)
        g.scale(s, s)
        g.rotate(sin(u * 4f) * 8f)
        g.blend(Gfx.Blend.ADD)
        g.glow(0f, 0f, 1.1f, Col.alpha(0xFF9FF6FF.toInt(), 0.35f * a))
        g.blend(Gfx.Blend.NORMAL)
        val sheet = Col.alpha(0xFFF2F6FF.toInt(), a)
        val shade = Col.alpha(0xFFB8C8E8.toInt(), a)
        val ink = Col.alpha(ActorPaint.INK, 0.8f * a)
        val o = ActorPaint.OUT
        for (pass in 0..1) {
            val grow = if (pass == 0) o else 0f
            val c = if (pass == 0) ink else sheet
            g.fillCircle(0f, -0.2f, 0.3f + grow, c)
            val pb = poly.begin()
            pb.add(-0.3f - grow, -0.2f).add(0.3f + grow, -0.2f).add(0.34f + grow, 0.32f + grow)
            // Wavy hem, rippling.
            for (j in 0..6) {
                val hx = 0.34f - j * (0.68f / 6f)
                val hy = 0.32f + (if (j % 2 == 0) 0.09f else -0.02f) + sin(u * 12f + j) * 0.03f
                pb.add(hx + (if (j == 0) grow else if (j == 6) -grow else 0f), hy + grow)
            }
            pb.add(-0.34f - grow, 0.32f + grow)
            pb.fill(g, c)
        }
        g.fillRect(-0.3f, 0.12f, 0.3f, 0.3f, Col.alpha(shade, 0.35f * a))
        // The agent's shades and a waving little arm.
        val d = away
        g.fillRoundRect(-0.2f + 0.05f * d, -0.28f, 0.22f + 0.05f * d, -0.16f, 0.05f, Col.alpha(0xFF0A0C14.toInt(), a))
        g.line(-0.1f + 0.05f * d, -0.25f, 0.14f + 0.05f * d, -0.25f, 0.02f, Col.alpha(0xFF3CF4FF.toInt(), a))
        val wave = sin(u * 16f) * 0.12f
        g.line(0.26f * d, 0f, 0.46f * d, -0.18f + wave, 0.09f + o, ink)
        g.line(0.26f * d, 0f, 0.46f * d, -0.18f + wave, 0.09f, sheet)
        g.fillCircle(0f, 0.02f, 0.04f, Col.alpha(0xFF2A2A3A.toInt(), a))
        g.restore()
    }

    companion object {
        const val HEY = Popup.HEY
        const val WAKE = Popup.WAKE
        const val JAZZ = Popup.JAZZ
        const val GHOST = Popup.GHOST
        const val KICK_POSE = 0.45f
        const val TUMBLE = 1.5f
        const val GHOST_TIME = 1.6f
        private const val V0 = 5.2f
        private const val V1 = 2.0f
        private const val GRAV = 20f
        private const val ZZZ = 0xFFDCD4FF.toInt()
        private val NOTE_COLORS = intArrayOf(0xFFFF7AC8.toInt(), 0xFF7AF0FF.toInt(), 0xFFFFD86A.toInt(), 0xFFB8A0FF.toInt())
    }
}
