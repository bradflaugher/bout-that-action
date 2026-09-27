package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.HallState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.sin

/**
 * Per-floor variety: special rooms that take over a run of free wall slots (boardroom,
 * containment bay, flooded platform, collapsed shaft, lava-fall chamber, hell cathedral),
 * and the Void's own negative-space rooms. Everything is seeded from the floor index, so a
 * floor looks the same every time you see it and different from the ones around it.
 */
internal class EnvRooms(private val f: Frame, private val walls: EnvWalls) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val free = BooleanArray(Geo.SLOTS.size)

    companion object {
        const val W = Geo.FLOOR_W
        private const val HAIR = EnvWalls.HAIR
        /** Roughly two floors in five try for a special room (when they have the wall space). */
        private const val SPECIAL_CHANCE = 0.34f
    }

    /** True if slot [i] carries no door, shaft or hazard. Fills [free] as a side effect. */
    fun scanFree(fs: HallState): BooleanArray {
        val plan = fs.plan
        for (i in Geo.SLOTS.indices) {
            val sx = Geo.SLOTS[i]
            var used = false
            for (k in plan.doors.indices) if (plan.doors[k].x == sx) used = true
            for (k in plan.shafts.indices) if (plan.shafts[k].x == sx) used = true
            for (k in plan.hazards.indices) if (plan.hazards[k].x == sx) used = true
            free[i] = !used
        }
        return free
    }

    /**
     * The special-room span on this floor as (first slot shl 8) or last slot, or -1.
     * Call [scanFree] first.
     */
    fun specialSpan(fs: HallState): Int {
        val plan = fs.plan
        if (plan.isVoid || plan.index == 0) return -1
        val chance = when (plan.zone) {
            Zone.MAGMA -> 0.2f
            Zone.LABS -> 0.26f
            Zone.MINES -> 0.4f
            else -> SPECIAL_CHANCE
        }
        if (hash(plan.look, 702) > chance) return -1
        var bestS = -1
        var bestE = -1
        var s = -1
        for (i in 0..free.size) {
            val ok = i < free.size && free[i]
            if (ok && s < 0) s = i
            if (!ok && s >= 0) {
                if (bestS < 0 || i - 1 - s > bestE - bestS) {
                    bestS = s
                    bestE = i - 1
                }
                s = -1
            }
        }
        if (bestS < 0) return -1
        return (bestS shl 8) or bestE
    }

    /** Some metro floors are flooded ankle-deep across the whole platform (all of the special ones). */
    fun flooded(fs: HallState, span: Int) =
        fs.plan.zone == Zone.METRO && !fs.plan.isVoid && (span >= 0 || hash(fs.plan.look, 711) < 0.2f)

    fun special(zone: Zone, pal: Palette, fs: HallState, span: Int, rt: Float, gy: Float, backdrop: Backdrop) {
        val fi = fs.plan.look
        val x0 = Geo.SLOTS[span shr 8] - 0.55f
        val x1 = Geo.SLOTS[span and 0xFF] + 0.55f
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP, Zone.VOID -> boardroom(pal, fi, x0, x1, rt, gy, backdrop)
            Zone.LABS -> {
                g.save()
                g.clipRect(x0, rt, x1, gy)
                containment(pal, fi, x0, x1, rt, gy)
                g.restore()
            }
            Zone.METRO -> platform(pal, fi, x0, x1, rt, gy, backdrop)
            Zone.MINES -> collapse(pal, fi, x0, x1, rt, gy)
            Zone.MAGMA -> lavaFall(pal, fi, x0, x1, rt, gy)
            Zone.HELL -> cathedral(pal, fi, x0, x1, rt, gy, backdrop)
        }
    }

    // ------------------------------------------------------------------ tower

    /** Corner boardroom: a panoramic window on the city, a long table, a pendant row. */
    private fun boardroom(pal: Palette, fi: Int, x0: Float, x1: Float, rt: Float, gy: Float, backdrop: Backdrop) {
        walls.windowSpan(Zone.TOWER, pal, x0 + 0.05f, x1 - 0.05f, rt + 0.42f, gy - 0.55f, false, backdrop, fi, mullion = 1.2f)
        // Warm wash from the pendants over the table.
        val cx = (x0 + x1) / 2f
        g.fillRectRadial(x0, rt, x1, gy, cx, gy - 0.9f, (x1 - x0) * 0.6f, 0x30FFC890, 0x00FFC890)
        // Table: long walnut top, a lit edge, pedestal legs; chairs silhouetted behind.
        val ty = gy - 0.72f
        var chair = x0 + 0.35f
        while (chair < x1 - 0.3f) {
            g.fillRoundRect(chair - 0.13f, ty - 0.42f, chair + 0.13f, ty + 0.05f, 0.06f, 0xFF120C1C.toInt())
            g.fillRect(chair - 0.13f, ty - 0.42f, chair + 0.13f, ty - 0.4f, Col.alpha(pal.trim, 0.3f))
            chair += 0.55f
        }
        g.fillRect(x0 + 0.2f, ty, x1 - 0.2f, ty + 0.08f, 0xFF3A2418.toInt())
        g.fillRect(x0 + 0.2f, ty, x1 - 0.2f, ty + 0.018f, 0xFFB08050.toInt())
        g.fillRect(x0 + 0.2f, ty + 0.08f, x1 - 0.2f, ty + 0.11f, 0x60000000)
        for (k in 0..1) {
            val lx = if (k == 0) x0 + 0.6f else x1 - 0.6f
            g.fillRect(lx - 0.05f, ty + 0.08f, lx + 0.05f, gy, 0xFF1A1014.toInt())
            g.fillRect(lx - 0.2f, gy - 0.04f, lx + 0.2f, gy, 0xFF1A1014.toInt())
        }
        // Laptops glowing on the table.
        var lx = x0 + 0.6f
        while (lx < x1 - 0.5f) {
            g.fillRect(lx - 0.1f, ty - 0.14f, lx + 0.1f, ty, Col.alpha(pal.neon2, 0.55f))
            g.fillRect(lx - 0.1f, ty - 0.14f, lx + 0.1f, ty - 0.125f, 0x80FFFFFF.toInt())
            lx += 0.9f
        }
        // Brass pendant row.
        var px = x0 + 0.5f
        while (px < x1 - 0.3f) {
            g.line(px, rt + 0.14f, px, rt + 0.62f, 0.012f, 0xFF15101C.toInt())
            poly.quad(g, px - 0.06f, rt + 0.62f, px + 0.06f, rt + 0.62f, px + 0.1f, rt + 0.72f, px - 0.1f, rt + 0.72f, 0xFFB08850.toInt())
            g.fillCircle(px, rt + 0.74f, 0.05f, 0xFFFFE0A8.toInt())
            g.fillCircle(px, rt + 0.74f, 0.14f, 0x30FFD090)
            px += 0.8f
        }
    }

    // ------------------------------------------------------------------- labs

    /** Containment bay: a floor-to-ceiling tank holding something big, under warning strobes. */
    private fun containment(pal: Palette, fi: Int, x0: Float, x1: Float, rt: Float, gy: Float) {
        val cx = (x0 + x1) / 2f
        val t0 = rt + 0.35f
        val t1 = gy - 0.28f
        g.fillRect(x0, t0 - 0.12f, x1, t0, Col.mul(pal.panel, 0.8f))
        g.fillRect(x0, t1, x1, gy, Col.mul(pal.panel, 0.8f))
        g.fillRect(x0, t1, x1, t1 + 0.02f, Col.alpha(pal.trim, 0.6f))
        g.fillRectRadial(x0, t0, x1, t1, cx, (t0 + t1) / 2f, (x1 - x0) * 0.55f, Col.alpha(pal.neon, 0.4f), Col.alpha(pal.neon, 0.1f))
        // The specimen: a huge curled silhouette with a faint heartbeat of bioluminescence.
        val bob = sin(f.t * 0.6f + fi) * 0.05f
        val sc = 0xB0031410.toInt()
        val sy = (t0 + t1) / 2f + bob
        val k = ((x1 - x0) / 2.3f).coerceIn(0.5f, 1f)
        g.save()
        g.translate(cx, sy)
        g.scale(k, k)
        g.translate(-cx, -sy)
        g.fillCircle(cx - 0.25f, sy - 0.35f, 0.36f, sc)
        g.fillRoundRect(cx - 0.55f, sy - 0.2f, cx + 0.45f, sy + 0.55f, 0.35f, sc)
        g.line(cx + 0.35f, sy + 0.3f, cx + 0.9f, sy + 0.7f, 0.12f, sc)
        g.line(cx - 0.5f, sy + 0.2f, cx - 0.95f, sy + 0.75f, 0.1f, sc)
        g.line(cx + 0.1f, sy - 0.5f, cx + 0.6f, sy - 0.85f, 0.08f, sc)
        val beat = if (fract(f.t * 0.9f) < 0.12f) 0.9f else 0.35f
        g.fillCircle(cx - 0.32f, sy - 0.4f, 0.04f, Col.alpha(pal.neon2, beat))
        g.fillCircle(cx - 0.18f, sy - 0.38f, 0.04f, Col.alpha(pal.neon2, beat))
        g.restore()
        for (b in 0 until 7) {
            val by = t1 - 0.1f - fract(f.t * 0.35f + b * 0.143f) * (t1 - t0 - 0.2f)
            g.fillCircle(x0 + 0.2f + hash(b + fi, 91) * (x1 - x0 - 0.4f), by, 0.025f, Col.alpha(0xFFFFFFFF.toInt(), 0.5f))
        }
        // Glass: vertical reflections and ribs.
        var rx = x0 + 0.6f
        while (rx < x1 - 0.2f) {
            g.fillRect(rx - 0.02f, t0, rx + 0.02f, t1, Col.mul(pal.panel, 0.9f))
            rx += 1.2f
        }
        g.fillRect(x0 + 0.12f, t0, x0 + 0.18f, t1, 0x28FFFFFF)
        // Rotating warning strobes at both ends.
        val on = sin(f.t * 5f) > 0.3f
        for (k in 0..1) {
            val sx = if (k == 0) x0 + 0.15f else x1 - 0.15f
            g.fillCircle(sx, t0 - 0.2f, 0.06f, if (on) 0xFFFF4A1C.toInt() else 0xFF4A1C10.toInt())
            if (on) g.fillCircle(sx, t0 - 0.2f, 0.2f, 0x30FF4A1C)
        }
    }

    // ------------------------------------------------------------------ metro

    /** A flooded platform: a wide window onto the tunnel; water drawn over the actors later. */
    private fun platform(pal: Palette, fi: Int, x0: Float, x1: Float, rt: Float, gy: Float, backdrop: Backdrop) {
        walls.windowSpan(Zone.METRO, pal, x0 + 0.05f, x1 - 0.05f, rt + 0.55f, gy - 0.7f, false, backdrop, fi, mullion = 0f)
        // Leaks: drips from the ceiling seams.
        for (k in 0 until 5) {
            val dx = hash(fi * 5 + k, 93) * W
            val ph = fract(f.t * (0.6f + hash(k, 94) * 0.5f) + hash(fi + k, 95))
            g.fillRect(dx - 0.008f, rt + 0.14f, dx + 0.008f, rt + 0.3f, 0x40A0C8E0)
            g.fillCircle(dx, rt + 0.3f + ph * (gy - rt - 0.3f), 0.018f, 0x70C8E8FF)
        }
        // Water staining on the tiles.
        g.fillVerticalGradient(0f, gy - 0.9f, W, gy, 0x00203848, 0x40203848)
    }

    /** Ankle-deep water over the floor, with reflections of the lamps. Drawn after actors. */
    fun water(pal: Palette, fs: HallState, gy: Float) {
        val top = gy - 0.16f
        g.fillVerticalGradient(0f, top, W, gy, 0x70204A5A, 0xA00A1C28.toInt())
        g.fillRect(0f, top, W, top + 0.014f, 0xA0B8E8FF.toInt())
        // Lamps reflected as broken, rippling streaks.
        g.blend(Gfx.Blend.ADD)
        val lights = fs.plan.lights
        for (i in lights.indices) {
            if (!fs.lightAlive[i]) continue
            val lx = lights[i]
            for (k in 0 until 4) {
                val w = 0.7f - k * 0.15f + sin(f.t * 3f + k * 1.3f + lx) * 0.06f
                val ox = sin(f.t * 2.1f + k + lx) * 0.04f
                g.fillRect(lx - w + ox, top + 0.025f + k * 0.032f, lx + w + ox, top + 0.04f + k * 0.032f, Col.alpha(pal.lamp, 0.4f - k * 0.08f))
            }
        }
        g.blend(Gfx.Blend.NORMAL)
        for (k in 0 until 6) {
            val ph = fract(f.t * 0.8f + hash(k + fs.plan.look, 96))
            val x = hash(k * 3 + fs.plan.look + (f.t * 0.8f + hash(k + fs.plan.look, 96)).toInt() * 7, 97) * W
            g.line(x - 0.05f - ph * 0.3f, top + 0.007f, x + 0.05f + ph * 0.3f, top + 0.007f, 0.012f, Col.alpha(0xFFE0F4FF.toInt(), 0.6f * (1f - ph)))
        }
    }

    // ------------------------------------------------------------------ mines

    /** A collapsed shaft: the ceiling has given way, daylight-less dust pours through a hole. */
    private fun collapse(pal: Palette, fi: Int, x0: Float, x1: Float, rt: Float, gy: Float) {
        val cx = (x0 + x1) / 2f + (hash(fi, 98) - 0.5f) * 0.5f
        // The hole: black above a ragged lip, with a cold shaft of light falling through.
        poly.begin()
        poly.add(cx - 0.7f, rt)
        for (k in 0..6) poly.add(cx - 0.7f + k * 0.233f, rt + 0.25f + hash(k + fi, 99) * 0.25f)
        poly.add(cx + 0.7f, rt)
        poly.fill(g, 0xFF030202.toInt())
        poly.quad(g, cx - 0.55f, rt + 0.3f, cx + 0.55f, rt + 0.3f, cx + 1.1f, gy, cx - 1.1f, gy, 0x14B8D0E0)
        poly.quad(g, cx - 0.3f, rt + 0.3f, cx + 0.3f, rt + 0.3f, cx + 0.6f, gy, cx - 0.6f, gy, 0x14B8D0E0)
        // Falling grit in the beam.
        for (k in 0 until 10) {
            val ph = fract(f.t * (0.25f + hash(k, 100) * 0.3f) + hash(k + fi, 101))
            val px = cx + (hash(k * 7 + fi, 102) - 0.5f) * (0.8f + ph * 1.2f)
            g.fillCircle(px, rt + 0.35f + ph * (gy - rt - 0.5f), 0.015f + hash(k, 103) * 0.015f, Col.alpha(0xFFD8E4F0.toInt(), 0.55f * (1f - ph * 0.6f)))
        }
        // Broken timbers hanging at angles.
        val wood = 0xFF3E2716.toInt()
        g.save()
        g.translate(cx - 0.8f, rt + 0.3f)
        g.rotate(38f)
        g.fillRect(0f, -0.08f, 1.4f, 0.08f, wood)
        g.fillRect(0f, -0.08f, 1.4f, -0.05f, 0xFF7A5230.toInt())
        g.restore()
        g.save()
        g.translate(cx + 0.9f, rt + 0.2f)
        g.rotate(118f)
        g.fillRect(0f, -0.07f, 1.1f, 0.07f, wood)
        g.restore()
        // Rubble pile.
        poly.begin()
        poly.add(cx - 1.3f, gy)
        for (k in 0..8) {
            val t = k / 8f
            val hgt = sin(t * 3.1416f) * 0.65f + hash(k + fi, 104) * 0.18f
            poly.add(cx - 1.3f + t * 2.6f, gy - hgt)
        }
        poly.add(cx + 1.3f, gy)
        poly.fill(g, 0xFF2A2018.toInt())
        for (k in 0 until 9) {
            val rx = cx - 1.1f + hash(k + fi * 3, 105) * 2.2f
            val ry = gy - 0.05f - hash(k + fi * 3, 106) * 0.45f * sin((rx - cx + 1.3f) / 2.6f * 3.1416f)
            val r = 0.06f + hash(k, 107) * 0.1f
            g.fillRect(rx - r, ry - r * 0.7f, rx + r, ry + r * 0.7f, if (k % 3 == 0) 0xFF4A3A2C.toInt() else 0xFF1E1610.toInt())
            g.fillRect(rx - r, ry - r * 0.7f, rx + r, ry - r * 0.55f, 0x30FFFFFF)
        }
        // A glinting ore chunk in the spill.
        g.fillCircle(cx + 0.3f, gy - 0.35f, 0.06f, 0xFF5AD8FF.toInt())
        g.fillCircle(cx + 0.3f, gy - 0.35f, 0.18f, 0x305AD8FF)
    }

    // ------------------------------------------------------------------ magma

    /** Lava-fall chamber: molten rock pours from a ceiling fissure into a channel. */
    private fun lavaFall(pal: Palette, fi: Int, x0: Float, x1: Float, rt: Float, gy: Float) {
        val cx = (x0 + x1) / 2f
        g.fillRectRadial(x0 - 1f, rt, x1 + 1f, gy, cx, gy - 0.6f, (x1 - x0) * 0.75f + 1f, 0x55FF6A10, 0x00FF6A10)
        // Channel.
        g.fillRect(x0 + 0.1f, gy - 0.18f, x1 - 0.1f, gy, 0xFFFF6A10.toInt())
        g.fillRect(x0 + 0.1f, gy - 0.18f, x1 - 0.1f, gy - 0.15f, 0xFFFFE070.toInt())
        for (k in 0 until 6) {
            val px = x0 + 0.2f + fract(hash(k, 108) + f.t * 0.12f) * (x1 - x0 - 0.4f)
            g.fillRect(px - 0.15f, gy - 0.11f, px + 0.15f, gy - 0.08f, 0xFFFFC040.toInt())
        }
        g.fillRect(x0, gy - 0.22f, x0 + 0.1f, gy, 0xFF2A1A18.toInt())
        g.fillRect(x1 - 0.1f, gy - 0.22f, x1, gy, 0xFF2A1A18.toInt())
        // A rock alcove holds the fall, so it reads as scenery behind the action, not a hazard.
        val fw = 0.24f
        val top = rt + 0.2f
        val bot = gy - 0.16f
        g.fillRoundRect(cx - 0.55f, rt + 0.14f, cx + 0.55f, gy - 0.18f, 0.3f, 0xFF0A0506.toInt())
        g.strokeRoundRect(cx - 0.55f, rt + 0.14f, cx + 0.55f, gy - 0.18f, 0.3f, 0.04f, Col.alpha(pal.trim, 0.25f))
        // The fall itself: layered ribbons with thin bright streaks sliding down.
        g.fillRect(cx - fw * 1.8f, top, cx + fw * 1.8f, bot, 0x30FF5A00)
        g.fillRect(cx - fw, top, cx + fw, bot, 0xFFD84A00.toInt())
        g.fillRect(cx - fw * 0.55f, top, cx + fw * 0.55f, bot, 0xFFFF8A18.toInt())
        g.fillRect(cx - fw * 0.18f, top, cx + fw * 0.18f, bot, 0xFFFFC050.toInt())
        for (s in 0 until 12) {
            val sy = top + fract(f.t * 0.9f + s / 12f) * (bot - top)
            val sx = cx + (hash(s, 109) - 0.5f) * fw * 1.6f
            g.fillRect(sx - 0.012f, sy, sx + 0.012f, sy + 0.35f, 0xB0FFE8A0.toInt())
        }
        // Fissure it pours from, and the splash.
        poly.quad(g, cx - 0.6f, rt + 0.14f, cx + 0.6f, rt + 0.14f, cx + fw, top + 0.1f, cx - fw, top + 0.1f, 0xFF140807.toInt())
        g.line(cx - 0.6f, rt + 0.15f, cx + 0.6f, rt + 0.15f, 0.03f, 0xFFFF8A20.toInt())
        for (k in 0 until 5) {
            val ph = fract(f.t * 1.4f + k * 0.2f)
            val dx = (hash(k + fi, 110) - 0.5f) * 1.2f * ph
            g.fillCircle(cx + dx, bot - sin(ph * 3.1416f) * 0.35f, 0.04f * (1f - ph) + 0.01f, 0xFFFFD060.toInt())
        }
        g.fillCircle(cx, bot, 0.5f, 0x40FFB040)
    }

    // ------------------------------------------------------------------- hell

    /** A cathedral nave: a great pointed window onto the sea of fire, candles below. */
    private fun cathedral(pal: Palette, fi: Int, x0: Float, x1: Float, rt: Float, gy: Float, backdrop: Backdrop) {
        val cx = (x0 + x1) / 2f
        val wy0 = rt + 0.3f
        val wy1 = gy - 0.8f
        g.save()
        g.clipRect(x0 + 0.1f, wy0, x1 - 0.1f, wy1)
        backdrop.zone(Zone.HELL, pal, x0 + 0.1f, wy0, x1 - 0.1f, wy1, false)
        g.restore()
        // Stone tracery: mask the corners into a pointed arch, add mullions.
        val stone = Col.mul(pal.wallTop, 0.9f)
        poly.tri(g, x0 + 0.1f, wy0, cx, wy0, x0 + 0.1f, wy0 + (wy1 - wy0) * 0.45f, stone)
        poly.tri(g, x1 - 0.1f, wy0, cx, wy0, x1 - 0.1f, wy0 + (wy1 - wy0) * 0.45f, stone)
        g.line(x0 + 0.1f, wy0 + (wy1 - wy0) * 0.45f, cx, wy0, 0.05f, Col.alpha(pal.trim, 0.35f))
        g.line(x1 - 0.1f, wy0 + (wy1 - wy0) * 0.45f, cx, wy0, 0.05f, Col.alpha(pal.trim, 0.35f))
        var mx = cx - 0.6f
        while (mx <= cx + 0.61f) {
            g.fillRect(mx - 0.025f, wy0 + 0.35f, mx + 0.025f, wy1, stone)
            mx += 0.6f
        }
        g.strokeCircle(cx, wy0 + 0.55f, 0.25f, 0.04f, stone)
        g.fillRect(x0, wy1, x1, wy1 + 0.12f, stone)
        g.fillRect(x0, wy1, x1, wy1 + 0.02f, Col.alpha(pal.trim, 0.4f))
        // Firelight pouring in.
        g.fillRectRadial(x0 - 0.5f, wy0, x1 + 0.5f, gy, cx, wy1, (x1 - x0) * 0.7f, 0x40FF4A18, 0x00FF4A18)
        // Candle rack.
        var k = 0
        var cxk = x0 + 0.3f
        while (cxk < x1 - 0.2f && k < 20) {
            val h = 0.12f + hash(k + fi, 111) * 0.22f
            g.fillRect(cxk - 0.025f, gy - 0.2f - h, cxk + 0.025f, gy - 0.2f, 0xFFD8C8A8.toInt())
            val fl = sin(f.t * 11f + k * 1.7f) * 0.015f
            poly.tri(g, cxk - 0.025f, gy - 0.2f - h, cxk + 0.025f, gy - 0.2f - h, cxk + fl, gy - 0.3f - h, 0xFFFFC040.toInt())
            cxk += 0.16f
            k++
        }
        g.fillRect(x0 + 0.2f, gy - 0.2f, x1 - 0.2f, gy - 0.14f, 0xFF2A1612.toInt())
    }

    // ------------------------------------------------------------------- void

    /**
     * A Void room is no room: black space, a wireframe box drawn in perspective, the floor grid
     * running to a vanishing point, debris of other floors adrift, stairs hanging upside down.
     */
    fun voidRoom(pal: Palette, fi: Int, rt: Float, gy: Float) {
        val h = gy - rt
        g.fillVerticalGradient(0f, rt, W, gy, pal.deep, Col.mul(pal.wallBottom, 0.8f))
        // Back wall of the box.
        val inset = 1.6f + hash(fi, 120) * 0.6f
        val bx0 = inset
        val bx1 = W - inset
        val by0 = rt + h * 0.24f
        val by1 = gy - h * 0.22f
        val vpx = (bx0 + bx1) / 2f
        val vpy = (by0 + by1) / 2f
        val line = Col.alpha(pal.neon, 0.45f)
        val faint = Col.alpha(pal.neon2, 0.18f)
        // Floor grid to the vanishing point, and depth rings.
        var x = -0.4f
        while (x <= W + 0.4f) {
            val t = 0.62f
            g.line(x, gy, x + (vpx - x) * t, gy + (vpy - gy) * t, HAIR, faint)
            g.line(x, rt, x + (vpx - x) * t, rt + (vpy - rt) * t, HAIR, faint)
            x += 0.8f
        }
        val scroll = fract(f.t * 0.25f)
        for (k in 0 until 4) {
            val d = (k + scroll) / 4f
            val t = d * d * 0.9f
            val l = t * (bx0 - 0f)
            val r = W - t * (W - bx1)
            val top = rt + t * (by0 - rt)
            val bot = gy + t * (by1 - gy)
            g.strokeRect(l, top, r, bot, HAIR, Col.alpha(pal.neon2, 0.08f + 0.14f * (1f - d)))
        }
        g.strokeRect(bx0, by0, bx1, by1, 0.025f, line)
        g.line(0f, rt, bx0, by0, 0.025f, line)
        g.line(W, rt, bx1, by0, 0.025f, line)
        g.line(0f, gy, bx0, by1, 0.025f, line)
        g.line(W, gy, bx1, by1, 0.025f, line)
        // A doorway of light on the back wall: somewhere you can never reach.
        val dw = 0.35f
        g.fillRect(vpx - dw, by1 - 0.9f, vpx + dw, by1, Col.alpha(pal.neon2, 0.1f))
        g.strokeRect(vpx - dw, by1 - 0.9f, vpx + dw, by1, HAIR, Col.alpha(pal.neon2, 0.6f))
        // Upside-down stairs hanging from the ceiling (every other block of floors).
        if ((fi / 3) % 2 == 0) {
            val sx0 = 2.4f + hash(fi, 121) * 4f
            val steps = 6
            var px = sx0
            var py = rt + 0.15f
            poly.begin()
            poly.add(sx0, rt + 0.15f)
            for (k in 0 until steps) {
                poly.add(px, py + 0.18f)
                poly.add(px + 0.24f, py + 0.18f)
                px += 0.24f
                py += 0.18f
            }
            poly.add(px, rt + 0.15f)
            poly.fill(g, Col.alpha(pal.deep, 0.9f))
            px = sx0
            py = rt + 0.15f
            for (k in 0 until steps) {
                g.line(px, py, px, py + 0.18f, HAIR, line)
                g.line(px, py + 0.18f, px + 0.24f, py + 0.18f, HAIR, line)
                px += 0.24f
                py += 0.18f
            }
        }
        // Debris drifting: shards and cubes in the room's two neons, one lost chair.
        for (i in 0 until 7) {
            val dx = fract(hash(i + fi * 7, 122) + f.t * (0.01f + hash(i, 123) * 0.02f)) * (W + 1f) - 0.5f
            val dy = rt + 0.4f + hash(i + fi * 7, 124) * (h - 0.9f) + sin(f.t * 0.7f + i) * 0.1f
            val r = 0.08f + hash(i + fi, 125) * 0.14f
            val c = if (i % 2 == 0) pal.neon else pal.neon2
            g.save()
            g.translate(dx, dy)
            g.rotate(f.t * (20f + i * 9f) + i * 40f)
            when (i % 3) {
                0 -> {
                    g.fillRect(-r, -r, r, r, Col.alpha(pal.deep, 0.9f))
                    g.strokeRect(-r, -r, r, r, HAIR, Col.alpha(c, 0.8f))
                    g.line(-r, -r, r * 0.4f, -r * 1.6f, HAIR, Col.alpha(c, 0.5f))
                    g.line(r, -r, r * 1.4f, -r * 1.6f, HAIR, Col.alpha(c, 0.5f))
                }
                1 -> poly.tri(g, -r, r, r * 1.2f, r * 0.4f, -r * 0.2f, -r * 1.3f, Col.alpha(c, 0.35f))
                else -> {
                    // A lost office chair, still spinning.
                    g.strokeRect(-r, -r, r, 0f, HAIR, Col.alpha(c, 0.7f))
                    g.line(-r, 0f, r, 0f, HAIR, Col.alpha(c, 0.7f))
                    g.line(0f, 0f, 0f, r, HAIR, Col.alpha(c, 0.7f))
                    g.line(-r * 0.8f, r, r * 0.8f, r, HAIR, Col.alpha(c, 0.7f))
                }
            }
            g.restore()
        }
        // A slow hue-split pulse of the whole room edge.
        val p = 0.5f + 0.5f * sin(f.t * 1.3f + fi)
        g.fillRect(0f, rt, W, rt + 0.02f, Col.alpha(pal.neon2, 0.3f * p))
        g.fillRect(0f, gy - 0.02f, W, gy, Col.alpha(pal.neon, 0.3f * (1f - p)))
        // A few stars in the negative space.
        for (i in 0 until 12) {
            val sx = hash(i + fi * 13, 126) * W
            val sy = rt + hash(i + fi * 13, 127) * h
            g.fillCircle(sx, sy, 0.012f, Col.alpha(0xFFFFFFFF.toInt(), 0.2f + 0.4f * hash(i, 128)))
        }
    }

    /** Void doors are drawn as wireframes; this is their outline. */
    fun voidDoor(pal: Palette, x0: Float, y0: Float, x1: Float, gy: Float, open: Float) {
        g.fillRect(x0, y0, x1, gy, Col.alpha(pal.deep, 0.92f))
        g.strokeRect(x0, y0, x1, gy, 0.025f, Col.alpha(pal.neon2, 0.75f))
        val lw = (x1 - x0) * (1f - 0.82f * open)
        g.line(x0 + lw, y0 + 0.1f * open, x0 + lw, gy, 0.02f, Col.alpha(pal.neon, 0.8f))
        g.line(x0, y0, x0 + lw, gy, HAIR, Col.alpha(pal.neon, 0.25f))
        g.line(x0 + lw, y0, x0, gy, HAIR, Col.alpha(pal.neon, 0.25f))
        g.fillCircle(x0 + lw - 0.12f, gy - 1.05f, 0.035f, pal.neon2)
    }
}
