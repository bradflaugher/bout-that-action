package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Popup
import com.bradflaugher.aboutthataction.engine.Bullet
import com.bradflaugher.aboutthataction.engine.Command
import com.bradflaugher.aboutthataction.engine.Flash
import com.bradflaugher.aboutthataction.engine.FloatingText
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.ParticleKind
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.TextStyle
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bullets, particles, floating text, and full-screen post effects.
 *
 * No shaders: the "bloom" is additive soft sprites and lines ([Gfx.Blend.ADD]), the colour
 * grades are full-screen MULTIPLY / SCREEN fills. Every loop here is allocation-free and
 * particles only ever draw flat circles, lines and quads (no per-particle gradients).
 */
internal class Effects(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val art = PopupArt(f)

    /** Optional film grain over everything (a settings toggle). */
    var grain = false
    /** Optional CRT scanlines over everything (a settings toggle). */
    var scanlines = false

    // Smoothed presentation state, advanced in real time.
    private var slowK = 0f
    private var impact = 0f

    fun world() {
        bullets()
        particles()
        muzzleGlow()
        closeCall()
        bufferCue()
        kills()
    }

    /**
     * Light that lives in the building: a soft bloom on every live lamp and unused STASH door
     * in view. A handful of cached glow sprites per floor, drawn additively over the scene.
     */
    fun bloom() {
        val w = f.w
        g.blend(Gfx.Blend.ADD)
        for (i in f.first..f.last) {
            if (i == 0) continue
            val rt = i * Geo.FLOOR_H + 0.35f
            val gy = Geo.groundY(i)
            if (!f.visibleY(rt - 1f, gy + 1f)) continue
            f.views(i) { fs -> bloomHall(fs, i, rt, gy) }
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    private fun bloomHall(fs: com.bradflaugher.aboutthataction.engine.HallState, i: Int, rt: Float, gy: Float) {
        run {
            val plan = fs.plan
            val lamp = f.palette(fs).lamp
            for (k in plan.lights.indices) {
                if (!fs.lightAlive[k]) continue
                val lx = plan.lights[k]
                val flick = 0.92f + 0.08f * sin(f.t * 7f + lx * 3.1f + i)
                g.glow(lx, rt + 0.45f, 1.5f, Col.alpha(lamp, 0.09f * flick))
                g.glow(lx, rt + 0.45f, 0.55f, Col.alpha(lamp, 0.3f * flick))
            }
            for (d in plan.doors.indices) {
                val door = plan.doors[d]
                if (door.kind != com.bradflaugher.aboutthataction.engine.DoorKind.STASH || fs.stashUsed[d] || f.w.stashLocked(fs, d)) continue
                val pulse = 0.8f + 0.2f * sin(f.t * 3f + d)
                g.glow(door.x, gy - 1.15f, 1.35f, Col.alpha(0xFFFFB02E.toInt(), 0.1f * pulse))
            }
        }
    }

    /** The frame an enemy drops: a white-hot flash over the body and a spray of sparks away from the hit. */
    private fun kills() {
        val list = f.w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.state != com.bradflaugher.aboutthataction.engine.EnemyState.DEAD || e.stateTime > KILL_TIME) continue
            if (!f.shows(e.floor, e.hall)) continue
            val gy = Geo.groundY(e.floor)
            if (!f.visibleY(gy - 2f, gy)) continue
            val h = e.height
            val cx = e.x
            val cy = gy - e.z - h * 0.5f
            val t = e.stateTime / KILL_TIME
            val dir = if (e.deathVx != 0f) kotlin.math.sign(e.deathVx) else -e.facing.toFloat()
            g.blend(Gfx.Blend.ADD)
            if (e.stateTime < 0.05f) {
                val k = 1f - e.stateTime / 0.05f
                g.save()
                g.translate(cx, cy)
                g.scale(0.42f, 1f)
                g.glow(0f, 0f, h * 0.75f, Col.alpha(0xFFFFFFFF.toInt(), 0.85f * k))
                g.restore()
                g.save()
                g.translate(cx, cy)
                g.scale(0.28f, 1f)
                g.glow(0f, 0f, h * 0.55f, Col.alpha(0xFFFFFFFF.toInt(), 0.9f * k))
                g.restore()
            }
            val a = 1f - t
            val ease = HudType.outCubic(t)
            for (k in 0 until 7) {
                val ang = (hash(e.id * 7 + k, 811) - 0.5f) * 1.4f
                val sp = 0.6f + hash(e.id * 7 + k, 812) * 0.9f
                val ca = cos(ang) * dir
                val sa = sin(ang)
                val y0 = cy + (hash(e.id + k, 813) - 0.5f) * h * 0.4f
                val r1 = (0.15f + 1.1f * ease) * sp
                val r0 = r1 - 0.35f * sp * a
                g.line(cx + ca * r0, y0 + sa * r0, cx + ca * r1, y0 + sa * r1, 0.035f * a + 0.005f, Col.alpha(0xFFFFE6B0.toInt(), a))
            }
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    // ---------------------------------------------------------------- bullets

    private fun bullets() {
        val top = f.camY - 1f
        val bottom = f.camY + f.viewH + 1f
        val list = f.w.bullets
        for (i in list.indices) {
            val b = list[i]
            if (!f.shows(b.floor, b.hall)) continue
            val gy = Geo.groundY(b.floor)
            val y = gy - b.z
            if (y < top || y > bottom) continue
            if (b.gravity) {
                fireball(b.x, y, b.vx, b.vz)
                continue
            }
            // Tracer: the trail grows over the first frames so a fresh shot starts at the muzzle.
            val len = min(1f, b.life * 25f) * 0.05f
            val tx = b.x - b.vx * len
            val ty = y + b.vz * len
            val mx = b.x - b.vx * len * 0.45f
            val my = y + b.vz * len * 0.45f
            if (b.byPlayer) {
                g.blend(Gfx.Blend.ADD)
                g.line(tx, ty, b.x, y, 0.07f, 0x2A1EC8FF)
                g.line(mx, my, b.x, y, 0.13f, 0x3040E8FF)
                g.line(mx, my, b.x, y, 0.055f, 0xB03CF4FF.toInt())
                g.glow(b.x, y, 0.38f, 0x8C3CF4FF.toInt())
                g.blend(Gfx.Blend.NORMAL)
                g.line(mx, my, b.x, y, 0.028f, 0xFFE8FFFF.toInt())
                g.fillCircle(b.x, y, 0.035f, 0xFFFFFFFF.toInt())
            } else {
                if (b.graze == Bullet.DODGED) {
                    // Slipped: a long pale wake so the near miss reads.
                    g.blend(Gfx.Blend.ADD)
                    val wx = b.x - b.vx * 0.12f
                    val wy = y + b.vz * 0.12f
                    g.line(wx, wy, b.x, y, 0.03f, 0x8CFFFFFF.toInt())
                    g.line(wx, wy - 0.05f, b.x, y - 0.02f, 0.02f, 0x5C3CF4FF)
                    g.line(wx, wy + 0.05f, b.x, y + 0.02f, 0.02f, 0x5CFF3D9A)
                    g.blend(Gfx.Blend.NORMAL)
                } else if (b.graze >= 0f) {
                    // It has you, for a few frames: white-hot, with a ring closing in on the hit.
                    val k = (b.graze / World.HIT_GRACE).coerceIn(0f, 1f)
                    g.blend(Gfx.Blend.ADD)
                    g.glow(b.x, y, 0.9f, Col.alpha(0xFFFFE0C0.toInt(), 0.7f))
                    g.strokeCircle(b.x, y, 0.55f * (1f - k) + 0.1f, 0.035f, Col.alpha(0xFFFFFFFF.toInt(), 0.5f + 0.5f * k))
                    g.blend(Gfx.Blend.NORMAL)
                }
                // Enemy rounds: fat, hot, and impossible to miss.
                g.blend(Gfx.Blend.ADD)
                g.line(tx, ty, b.x, y, 0.1f, 0x40FF3A1A)
                g.line(mx, my, b.x, y, 0.2f, 0x40FF4020)
                g.line(mx, my, b.x, y, 0.085f, 0xC0FF3A2A.toInt())
                g.glow(b.x, y, 0.55f, 0xA6FF4A20.toInt())
                g.blend(Gfx.Blend.NORMAL)
                g.line(mx, my, b.x, y, 0.04f, 0xFFFFE0A0.toInt())
                g.fillCircle(b.x, y, 0.075f, 0xFFFFF4D0.toInt())
            }
        }
    }

    private fun fireball(x: Float, y: Float, vx: Float, vz: Float) {
        val fl = 0.85f + 0.15f * sin(f.t * 40f + x * 3f)
        g.blend(Gfx.Blend.ADD)
        g.glow(x, y, 0.9f * fl, 0x99FF5A10.toInt())
        for (i in 1..5) {
            val tx = x - vx * 0.022f * i
            val ty = y + vz * 0.022f * i
            g.fillCircle(tx, ty, 0.15f * (1f - i * 0.15f), Col.alpha(0xFFFF6A10.toInt(), 0.55f - i * 0.09f))
        }
        g.blend(Gfx.Blend.NORMAL)
        g.fillCircle(x, y, 0.2f * fl, 0xFFFF6A10.toInt())
        g.fillCircle(x, y, 0.13f * fl, 0xFFFFD060.toInt())
        g.fillCircle(x, y, 0.06f, 0xFFFFFFFF.toInt())
    }

    /** A soft light at the gun the instant it fires, so muzzle flashes bloom into the room. */
    private fun muzzleGlow() {
        val p = f.w.player
        if (p.sinceShot >= 0.07f || p.state == PlayerState.BOX || p.state == PlayerState.DOOR) return
        val k = 1f - p.sinceShot / 0.07f
        val x = p.x + p.facing * 0.62f
        val y = Geo.groundY(p.floorF) - p.z - 1.05f
        g.blend(Gfx.Blend.ADD)
        g.glow(x, y, 1.4f * (0.7f + 0.3f * k), Col.alpha(0xFFFFB050.toInt(), 0.45f * k))
        g.glow(x, y, 0.45f, Col.alpha(0xFFFFF0C0.toInt(), 0.7f * k))
        g.blend(Gfx.Blend.NORMAL)
    }

    /** "CLOSE!": a ting of light at the torso and a thin ring, the instant a round is slipped. */
    private fun closeCall() {
        val p = f.w.player
        val t = p.sinceCloseCall
        if (t >= CLOSE_TIME) return
        val k = t / CLOSE_TIME
        val e = HudType.outCubic(k)
        val x = p.x
        val y = Geo.groundY(p.floorF) - p.z - (if (p.state == PlayerState.BOX) 0.4f else 0.95f)
        val a = 1f - k
        g.blend(Gfx.Blend.ADD)
        if (k < 0.4f) g.glow(x, y, 0.9f, Col.alpha(0xFFE8FFFF.toInt(), 0.6f * (1f - k / 0.4f)))
        g.strokeCircle(x, y, 0.3f + 1.1f * e, 0.04f * a + 0.005f, Col.alpha(0xFF9FF6FF.toInt(), 0.8f * a))
        for (i in 0 until 8) {
            val ang = i * 0.785f + 0.39f
            val c = cos(ang)
            val sn = sin(ang)
            val r0 = 0.25f + 0.55f * e
            val r1 = r0 + 0.3f * a
            g.line(x + c * r0, y + sn * r0, x + c * r1, y + sn * r1, 0.035f * a, Col.alpha(0xFFFFFFFF.toInt(), a))
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    /** A faint pulse at the feet while a gesture waits in the input buffer. */
    private fun bufferCue() {
        val p = f.w.player
        val c = p.bufferedCommand ?: return
        val k = (p.bufferAge / World.BUFFER_TIME).coerceIn(0f, 1f)
        val a = (1f - k) * 0.7f
        val x = p.x
        val y = Geo.groundY(p.floorF) - p.z - 0.02f
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(x, y)
        g.scale(1f, 0.28f)
        g.strokeCircle(0f, 0f, 0.35f + 0.25f * k, 0.09f, Col.alpha(0xFF3CF4FF.toInt(), a))
        g.restore()
        when (c) {
            Command.SWIPE_UP -> Glyphs.arrow(g, x, y - 0.35f - 0.2f * k, 0.12f, 0f, -1f, 0.035f, Col.alpha(0xFF3CF4FF.toInt(), a))
            Command.SWIPE_DOWN -> Glyphs.arrow(g, x, y - 0.3f + 0.1f * k, 0.12f, 0f, 1f, 0.035f, Col.alpha(0xFF3CF4FF.toInt(), a))
            else -> Unit
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    // -------------------------------------------------------------- particles

    private fun particles() {
        val top = f.camY - 1f
        val bottom = f.camY + f.viewH + 1f
        val list = f.w.fx.particles
        // Pass 1: solid matter over the scene.
        for (i in list.indices) {
            val p = list[i]
            if (p.y < top || p.y > bottom) continue
            val t = p.t
            val fade = 1f - t
            val age = p.maxLife - p.life
            when (p.kind) {
                ParticleKind.SMOKE -> {
                    val r = p.size * (0.5f + t * 1.1f)
                    val a = 0.38f * fade * min(1f, t * 8f + 0.3f)
                    g.fillCircle(p.x, p.y, r * 1.25f, Col.alpha(0xFF1C1A24.toInt(), a * 0.45f))
                    g.fillCircle(p.x, p.y, r, Col.alpha(0xFF2E2B38.toInt(), a))
                    g.fillCircle(p.x - r * 0.25f, p.y - r * 0.3f, r * 0.55f, Col.alpha(0xFF4A4658.toInt(), a * 0.5f))
                }
                ParticleKind.DUST -> g.fillCircle(p.x, p.y, p.size * (0.6f + t * 1.2f), Col.alpha(0xFFB8A8C8.toInt(), 0.32f * fade))
                ParticleKind.SHARD -> {
                    val c = if (i % 3 == 0) 0xFFFFFFFF.toInt() else 0xFFFF2A48.toInt()
                    spinQuad(p.x, p.y, p.size, p.size * 0.5f, p.spin * age, Col.alpha(c, fade))
                }
                ParticleKind.GLASS -> spinQuad(p.x, p.y, p.size, p.size * 0.35f, p.spin * age, Col.alpha(0xFFBFEFFF.toInt(), 0.85f * fade))
                ParticleKind.CASING -> {
                    val a = p.spin * age
                    spinQuad(p.x, p.y, p.size, p.size * 0.42f, a, Col.alpha(0xFFC8902C.toInt(), fade))
                    spinQuad(p.x, p.y, p.size * 0.8f, p.size * 0.14f, a, Col.alpha(0xFFFFE08A.toInt(), fade))
                }
                ParticleKind.CARDBOARD -> spinQuad(p.x, p.y, p.size, p.size * 0.7f, p.spin * age, Col.alpha(0xFFC08A52.toInt(), fade))
                ParticleKind.PEANUT -> {
                    // A foam packing peanut: a fat off-white squiggle, turning.
                    val a = p.spin * age
                    spinQuad(p.x, p.y, p.size, p.size * 0.5f, a, Col.alpha(0xFFF2EAD6.toInt(), min(1f, fade * 2f)))
                    spinQuad(p.x, p.y, p.size * 0.5f, p.size * 0.56f, a + 1.1f, Col.alpha(0xFFD8CCB0.toInt(), min(1f, fade * 2f)))
                }
                ParticleKind.RING -> {
                    // The dark lip just inside a shockwave reads as air being displaced.
                    val e = HudType.outCubic(t)
                    val r = p.size * (0.15f + 0.85f * e)
                    g.strokeCircle(p.x, p.y, r * 0.93f, p.size * 0.06f * fade + 0.01f, Col.alpha(0xFF000000.toInt(), 0.22f * fade))
                }
                else -> Unit
            }
        }
        // Pass 2: light. Everything hot is additive, so overlapping sparks burn white.
        g.blend(Gfx.Blend.ADD)
        for (i in list.indices) {
            val p = list[i]
            if (p.y < top || p.y > bottom) continue
            val t = p.t
            val fade = 1f - t
            when (p.kind) {
                ParticleKind.SPARK -> {
                    val c = Col.lerp(0xFFFFF6D8.toInt(), 0xFFFF8A20.toInt(), t)
                    val tx = p.x - p.vx * 0.045f
                    val ty = p.y - p.vy * 0.045f
                    g.line(p.x, p.y, tx, ty, p.size * 1.1f, Col.alpha(c, 0.18f * fade))
                    g.line(p.x, p.y, tx, ty, p.size * 0.45f, Col.alpha(c, fade))
                }
                ParticleKind.EMBER -> {
                    val c = Col.lerp(0xFFFFE070.toInt(), 0xFFFF3A10.toInt(), t)
                    val flick = 0.75f + 0.25f * sin(f.t * 30f + i * 1.7f)
                    g.fillCircle(p.x, p.y, p.size * 2.2f, Col.alpha(c, 0.09f * fade * flick))
                    g.fillCircle(p.x, p.y, p.size * 0.65f, Col.alpha(c, fade * flick))
                }
                ParticleKind.GLASS -> {
                    // Glints as the shard turns to the light.
                    val glint = sin(p.spin * (p.maxLife - p.life) * 1.3f)
                    if (glint > 0.75f) g.fillCircle(p.x, p.y, p.size * 0.5f, Col.alpha(0xFFFFFFFF.toInt(), (glint - 0.75f) * 4f * fade))
                }
                ParticleKind.RING -> {
                    val e = HudType.outCubic(t)
                    val r = p.size * (0.15f + 0.85f * e)
                    if (t < 0.35f) {
                        val k = 1f - t / 0.35f
                        g.glow(p.x, p.y, p.size * (0.5f + 0.5f * e), Col.alpha(0xFFFFD0A0.toInt(), 0.5f * k))
                    }
                    g.strokeCircle(p.x, p.y, r, p.size * 0.14f * fade + 0.02f, Col.alpha(0xFFFF9A50.toInt(), 0.22f * fade))
                    g.strokeCircle(p.x, p.y, r, p.size * 0.035f * fade + 0.01f, Col.alpha(0xFFB8F6FF.toInt(), 0.9f * fade))
                    g.strokeCircle(p.x, p.y, r * 0.72f, p.size * 0.02f * fade + 0.005f, Col.alpha(0xFFFFFFFF.toInt(), 0.35f * fade * fade))
                }
                else -> Unit
            }
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    private fun spinQuad(x: Float, y: Float, w: Float, h: Float, angle: Float, color: Int) {
        val c = cos(angle)
        val s = sin(angle)
        val hw = w / 2f
        val hh = h / 2f
        poly.quad(
            g,
            x + (-hw * c - -hh * s), y + (-hw * s + -hh * c),
            x + (hw * c - -hh * s), y + (hw * s + -hh * c),
            x + (hw * c - hh * s), y + (hw * s + hh * c),
            x + (-hw * c - hh * s), y + (-hw * s + hh * c),
            color,
        )
    }

    // ------------------------------------------------------------------ texts

    private val slotRef = arrayOfNulls<FloatingText>(SLOTS)
    private val slotOff = FloatArray(SLOTS)
    private val slotSeen = BooleanArray(SLOTS)
    private val placed = FloatArray((SLOTS + 1) * 4)
    private val order = IntArray(SLOTS)

    /**
     * Floating text in screen space, laid out so popups never pile up: the newest keeps its
     * spot and older ones are nudged upward out of its way, easing rather than jumping.
     */
    fun texts() {
        val list = f.w.fx.texts
        val n = min(list.size, SLOTS)
        if (n == 0) {
            for (i in 0 until SLOTS) slotRef[i] = null
            return
        }
        val W = g.width
        val s = f.s
        java.util.Arrays.fill(slotSeen, false)
        // Map each text to a persistent slot (for its smoothed offset).
        for (k in 0 until n) {
            val ft = list[list.size - n + k]
            var slot = -1
            for (i in 0 until SLOTS) if (slotRef[i] === ft) { slot = i; break }
            if (slot < 0) {
                for (i in 0 until SLOTS) if (slotRef[i] == null || !contains(list, slotRef[i]!!)) { slot = i; break }
                if (slot < 0) slot = k
                slotRef[slot] = ft
                slotOff[slot] = 0f
            }
            slotSeen[slot] = true
            order[k] = slot
        }
        for (i in 0 until SLOTS) if (!slotSeen[i]) slotRef[i] = null

        // Layout newest → oldest, around anything the HUD reserved.
        var placedN = 0
        if (f.hasReserved) {
            System.arraycopy(f.reserved, 0, placed, 0, 4)
            placedN = 1
        }
        for (k in n - 1 downTo 0) {
            val slot = order[k]
            val ft = slotRef[slot] ?: continue
            val kind = art.kind(ft)
            if (kind == PopupArt.HIDE) continue
            val ks = art.size(kind)
            val sz = (if (ks > 0f) ks else sizeOf(ft)) * s * popScale(ft)
            val tw = Glyphs.width(g, ft.text, sz, Gfx.Font.TITLE) * art.widthK(kind)
            // The box it occupies, relative to the baseline: up to [above], down to [below].
            val hh = art.halfH(kind) * sz
            val above = sz * 0.36f + hh
            val below = max(0f, hh - sz * 0.36f)
            val cx = worldToX(ft.x).coerceIn(tw / 2f + 0.03f * W, W - tw / 2f - 0.03f * W)
            val base = worldToY(ft.y) + riseOffset(ft) * s
            var y = base
            val pad = sz * 0.12f
            var moved = true
            var guard = 0
            while (moved && guard++ < 12) {
                moved = false
                for (j in 0 until placedN) {
                    val l = placed[j * 4]
                    val t = placed[j * 4 + 1]
                    val r = placed[j * 4 + 2]
                    val b = placed[j * 4 + 3]
                    if (cx + tw / 2f > l && cx - tw / 2f < r && y + below + pad > t && y - above - pad < b) {
                        y = t - pad - below
                        moved = true
                    }
                }
            }
            val target = y - base
            slotOff[slot] += (target - slotOff[slot]) * min(1f, if (f.dt == 0f) 1f else f.dt * 16f)
            val fy = base + slotOff[slot]
            placed[placedN * 4] = cx - tw / 2f
            placed[placedN * 4 + 1] = fy - above
            placed[placedN * 4 + 2] = cx + tw / 2f
            placed[placedN * 4 + 3] = fy + below
            placedN++
            if (kind == PopupArt.NORMAL) drawText(ft, cx, fy, sz) else art.draw(kind, ft, cx, fy, sz, textAlpha(ft))
        }
    }

    private fun contains(list: List<FloatingText>, ft: FloatingText): Boolean {
        for (i in list.indices) if (list[i] === ft) return true
        return false
    }

    private fun worldToX(x: Float) = (x + 0.6f) * f.s + f.shakeX
    private fun worldToY(y: Float) = (y - f.camY) * f.s + f.shakeY

    private fun styleSize(st: TextStyle) = when (st) {
        TextStyle.SCORE -> 0.3f
        TextStyle.TAKEDOWN -> 0.42f
        TextStyle.COMBO -> 0.4f
        TextStyle.PICKUP -> 0.34f
        TextStyle.WARN -> 0.34f
        TextStyle.BIG -> 0.58f
    }

    private fun isClose(ft: FloatingText) = ft.style == TextStyle.WARN && ft.text == CLOSE_LABEL

    private fun sizeOf(ft: FloatingText) = if (isClose(ft)) 0.44f else styleSize(ft.style)

    private fun styleColor(st: TextStyle) = when (st) {
        TextStyle.SCORE -> 0xFFFFF0C8.toInt()
        TextStyle.TAKEDOWN -> 0xFF3CF4FF.toInt()
        TextStyle.COMBO -> 0xFFFF3D9A.toInt()
        TextStyle.PICKUP -> 0xFF7CFF9A.toInt()
        TextStyle.WARN -> 0xFFFF8A3A.toInt()
        TextStyle.BIG -> 0xFFFFD24A.toInt()
    }

    private fun age(ft: FloatingText) = ft.t * ft.maxLife

    /** Slams in oversized and springs to rest, then shrinks a touch as it fades. */
    private fun popScale(ft: FloatingText): Float {
        val a = age(ft)
        val from = when (ft.style) {
            TextStyle.SCORE -> 1.35f
            TextStyle.COMBO, TextStyle.BIG -> 1.3f
            else -> 1.3f
        }
        val inK = HudType.spring(a / 0.4f, from)
        val outK = if (ft.t > 0.7f) 1f - 0.18f * (ft.t - 0.7f) / 0.3f else 1f
        return max(0.05f, inK) * outK
    }

    /** Extra ease-out lift on top of the engine's linear rise, in world units. */
    private fun riseOffset(ft: FloatingText) = -HudType.outCubic(age(ft) / 0.6f) * 0.22f

    private fun textAlpha(ft: FloatingText) = if (ft.t > 0.72f) max(0f, (1f - ft.t) / 0.28f) else 1f

    private fun drawText(ft: FloatingText, x: Float, y: Float, sz: Float) {
        val a = textAlpha(ft)
        if (a <= 0f) return
        val base = styleColor(ft.style)
        val color = if (ft.style == TextStyle.COMBO) Col.lerp(Col.lerp(0xFFFFFFFF.toInt(), base, 0.8f), base, age(ft) / 0.1f) else base
        if (isClose(ft)) {
            // Split into cyan and magenta that snap together, over a white core.
            val split = sz * 0.09f * HudType.decay(age(ft) / 0.25f) + sz * 0.02f
            g.blend(Gfx.Blend.ADD)
            g.save()
            g.translate(x, y - sz * 0.36f)
            g.scale(1f, 0.45f)
            g.glow(0f, 0f, Glyphs.width(g, ft.text, sz, Gfx.Font.TITLE) * 0.75f, Col.alpha(0xFF3CF4FF.toInt(), 0.3f * a))
            g.restore()
            g.blend(Gfx.Blend.NORMAL)
            HudType.outlined(g, ft.text, x, y, sz, Col.alpha(0xFF08040C.toInt(), 0.85f * a), Col.alpha(0xFF08040C.toInt(), 0.85f * a), Gfx.Font.TITLE, Gfx.Align.CENTER)
            g.blend(Gfx.Blend.ADD)
            Glyphs.text(g, ft.text, x - split, y, sz, Col.alpha(0xFF2BD8FF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER)
            Glyphs.text(g, ft.text, x + split, y, sz, Col.alpha(0xFFFF2B8A.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER)
            g.blend(Gfx.Blend.NORMAL)
            Glyphs.text(g, ft.text, x, y, sz, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER)
            return
        }
        val loud = ft.style == TextStyle.COMBO || ft.style == TextStyle.BIG || ft.style == TextStyle.TAKEDOWN
        if (loud) {
            val tw = Glyphs.width(g, ft.text, sz, Gfx.Font.TITLE)
            g.blend(Gfx.Blend.ADD)
            g.save()
            g.translate(x, y - sz * 0.36f)
            g.scale(1f, 0.45f)
            g.glow(0f, 0f, tw * 0.75f, Col.alpha(base, 0.32f * a))
            g.restore()
            g.blend(Gfx.Blend.NORMAL)
        }
        HudType.outlined(g, ft.text, x, y, sz, Col.fade(color, a), Col.alpha(0xFF08040C.toInt(), 0.85f * a), Gfx.Font.TITLE, Gfx.Align.CENTER)
    }

    // ------------------------------------------------------------ post effects

    fun screen() {
        val w = f.w
        val W = g.width
        val H = g.height
        val u = W / 100f
        val playing = w.phase == Phase.PLAYING
        val slow = w.slowMo && playing
        val dying = w.phase == Phase.DYING || w.phase == Phase.OVER
        val dt = f.dt

        // Smooth the grade in and out; flash on the engine's own hit-stop, never on a
        // render that merely landed between two fixed simulation steps.
        slowK += ((if (slow) 1f else 0f) - slowK) * min(1f, if (dt == 0f) 1f else dt * 7f)
        val frozen = playing && dt > 0f && w.hitStopping
        impact = if (frozen) 1f else max(0f, impact - dt * 9f)

        // --- slow-mo: cool grade, lifted violet shadows, chromatic edges.
        if (slowK > 0.01f) {
            g.blend(Gfx.Blend.MULTIPLY)
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFF8CA8FF.toInt(), 0.7f * slowK))
            g.blend(Gfx.Blend.SCREEN)
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFF1C1040.toInt(), 0.4f * slowK))
            g.blend(Gfx.Blend.NORMAL)
            chroma(W, H, 0.35f * slowK, 3.4f * u)
            lines(W, H, Col.alpha(0xFF000000.toInt(), 0.1f * slowK))
        }

        // --- vignette: always a little, heavier in slow-mo and when dying.
        var vig = 0.42f + 0.25f * slowK
        if (dying) vig = 0.95f
        val lowHp = w.player.hp == 1 && playing
        val beat = if (lowHp) HudType.heartbeat(fract(f.t * 1.25f)) else 0f
        vignette(W, H, vig, if (dying) 0xFF1A0006.toInt() else 0xFF000000.toInt())
        if (lowHp) vignette(W, H, 0.25f + 0.45f * beat, 0xFF8A0018.toInt())

        // --- screen shake and hit-stop get a hint of lens split and an impact flash.
        val shake = min(1f, w.shake)
        if (playing && (shake > 0.05f || impact > 0.01f)) chroma(W, H, 0.3f * shake + 0.25f * impact, (1.5f + 3f * shake) * u)
        val close = w.player.sinceCloseCall
        if (playing && close < CLOSE_TIME) {
            val k = 1f - close / CLOSE_TIME
            chroma(W, H, 0.4f * k, 3f * u)
            g.blend(Gfx.Blend.ADD)
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFF3CF4FF.toInt(), 0.05f * k * k))
            g.blend(Gfx.Blend.NORMAL)
        }
        if (impact > 0.01f) {
            g.blend(Gfx.Blend.ADD)
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFFFFF4EC.toInt(), 0.045f * impact))
            g.blend(Gfx.Blend.NORMAL)
        }

        if (w.zone == Zone.VOID && playing) {
            val tick = (f.t * 12f).toInt()
            g.blend(Gfx.Blend.ADD)
            for (i in 0 until 3) {
                if (hash(tick * 5 + i, 401) > 0.25f) continue
                val y = hash(tick * 5 + i, 402) * H
                val h = H * (0.003f + hash(tick + i, 403) * 0.016f)
                val off = (hash(tick + i, 404) - 0.5f) * 3f * u
                g.fillRect(off, y, W + off, y + h, if (i % 2 == 0) 0x40FF2BD6 else 0x402BFFE0)
            }
            g.blend(Gfx.Blend.NORMAL)
        }

        // --- dying: drain the colour toward bruised red, close in, and roll the tape.
        if (dying) {
            val k = HudType.outCubic(min(1f, w.dyingTime / 1.4f + if (w.phase == Phase.OVER) 1f else 0f))
            g.blend(Gfx.Blend.MULTIPLY)
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFF8A6070.toInt(), 0.9f * k))
            g.blend(Gfx.Blend.SCREEN)
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFF2A0610.toInt(), 0.55f * k))
            g.blend(Gfx.Blend.NORMAL)
            // Iris in on the body.
            val p = w.player
            val px = worldToX(p.x)
            val py = worldToY(Geo.groundY(p.floorF) - 0.8f)
            g.fillRectRadial(0f, 0f, W, H, px, py, W * (1.5f - 0.95f * k), 0x00000000, Col.alpha(0xFF050003.toInt(), 0.9f * k))
            vignette(W, H, k, 0xFF3A0008.toInt())
            // A slow bright band rolling down the frame, like a failing tape.
            val roll = fract(f.t * 0.35f) * (H + 40f * u) - 20f * u
            g.blend(Gfx.Blend.ADD)
            g.fillVerticalGradient(0f, roll - 6f * u, W, roll, 0x00FF3050, Col.alpha(0xFFFF3050.toInt(), 0.07f * k))
            g.blend(Gfx.Blend.NORMAL)
            val bar = H * 0.055f * k
            g.fillRect(0f, 0f, W, bar, 0xFF000000.toInt())
            g.fillRect(0f, H - bar, W, H, 0xFF000000.toInt())
            lines(W, H, Col.alpha(0xFF000000.toInt(), 0.22f * k))
            grainPass(W, H, 0.6f * k)
            chroma(W, H, 0.35f * k, 3f * u)
        }

        if (w.flashAmount > 0.01f) {
            val a = min(1f, w.flashAmount)
            when (w.flash) {
                Flash.HURT -> {
                    // Tint the whole frame blood-red without washing it out, then burn the edges.
                    g.blend(Gfx.Blend.MULTIPLY)
                    g.fillRect(0f, 0f, W, H, Col.alpha(0xFFFF7A84.toInt(), 0.45f * a))
                    g.blend(Gfx.Blend.NORMAL)
                    vignette(W, H, a, 0xFFD0001C.toInt())
                    chroma(W, H, 0.55f * a, 5f * u, 0xFFFF1A30.toInt(), 0xFFFF6A20.toInt())
                }
                Flash.WHITE -> {
                    g.blend(Gfx.Blend.ADD)
                    g.fillRect(0f, 0f, W, H, Col.alpha(0xFFFFF4E8.toInt(), 0.45f * a))
                    g.blend(Gfx.Blend.NORMAL)
                }
                Flash.GOLD -> {
                    g.blend(Gfx.Blend.ADD)
                    g.fillRect(0f, 0f, W, H, Col.alpha(0xFFFFC23A.toInt(), 0.3f * a))
                    g.blend(Gfx.Blend.NORMAL)
                }
                Flash.NONE -> Unit
            }
        }

        if (scanlines) lines(W, H, 0x16000000)
        if (grain) grainPass(W, H, 0.35f)
    }

    /** Coloured fringes on the left/right edges: a cheap stand-in for lens chromatic aberration. */
    private fun chroma(W: Float, H: Float, a: Float, width: Float, left: Int = 0xFFFF2B8A.toInt(), right: Int = 0xFF2BD8FF.toInt()) {
        if (a <= 0.005f) return
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(0f, H)
        g.rotate(-90f)
        g.fillVerticalGradient(0f, 0f, H, width, Col.alpha(left, a), left and 0xFFFFFF)
        g.restore()
        g.save()
        g.translate(W, 0f)
        g.rotate(90f)
        g.fillVerticalGradient(0f, 0f, H, width, Col.alpha(right, a), right and 0xFFFFFF)
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
    }

    private fun vignette(W: Float, H: Float, strength: Float, color: Int) {
        if (strength <= 0.005f) return
        val band = W * 0.2f
        val a = Col.alpha(color, 0.75f * min(1f, strength))
        val z = Col.alpha(color, 0f)
        g.fillVerticalGradient(0f, 0f, W, band * 0.9f, a, z)
        g.fillVerticalGradient(0f, H - band * 1.1f, W, H, z, a)
        // Sides: rotate the vertical gradient.
        g.save()
        g.translate(0f, H)
        g.rotate(-90f)
        g.fillVerticalGradient(0f, 0f, H, band * 0.6f, a, z)
        g.restore()
        g.save()
        g.translate(W, 0f)
        g.rotate(90f)
        g.fillVerticalGradient(0f, 0f, H, band * 0.6f, a, z)
        g.restore()
    }

    private fun lines(W: Float, H: Float, color: Int) {
        if (Col.a(color) == 0) return
        val step = max(3f, H / 320f)
        var y = 0f
        while (y < H) {
            g.fillRect(0f, y, W, y + step * 0.42f, color)
            y += step
        }
    }

    /** Film grain: a sparse field of light and dark specks, reseeded every frame. */
    private fun grainPass(W: Float, H: Float, strength: Float) {
        if (strength <= 0.01f) return
        val tick = (f.t * 24f).toInt()
        val sz = max(1.5f, W / 500f)
        val n = 220
        for (i in 0 until n) {
            val x = hash(tick * 977 + i, 601) * W
            val y = hash(tick * 977 + i, 602) * H
            val light = i and 1 == 0
            val a = strength * (0.1f + 0.14f * hash(i + tick, 603))
            g.fillRect(x, y, x + sz, y + sz, if (light) Col.alpha(0xFFFFFFFF.toInt(), a) else Col.alpha(0xFF000000.toInt(), a * 1.4f))
        }
    }

    private companion object {
        const val SLOTS = 32
        const val CLOSE_TIME = 0.35f
        const val KILL_TIME = 0.2f
        /** The engine's near-miss popup label. */
        const val CLOSE_LABEL = Popup.CLOSE
    }
}
