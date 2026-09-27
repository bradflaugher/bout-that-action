package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.DoorKind
import com.bradflaugher.aboutthataction.engine.FloorState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.HazardKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.Side
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The building cutaway: rooms, roof, slabs, stairwells, doors, shafts, lamps, hazards.
 *
 * Lighting model, kept consistent everywhere: light falls from the ceiling lamps, so top edges
 * catch a highlight ([Palette.trim]), undersides and right-hand returns fall into [Palette.deep],
 * and the ceiling band carries soft ambient occlusion. Line weights come from [EnvWalls].
 */
internal class Building(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val walls = EnvWalls(f)
    private val rooms = EnvRooms(f, walls)
    private val carOpen = HashMap<Int, FloatArray>()
    private val cutBuf = FloatArray(8)

    companion object {
        const val SLAB = 0.35f
        const val H = Geo.FLOOR_H
        const val W = Geo.FLOOR_W
        const val DOOR_H = 2.25f
        private const val HAIR = EnvWalls.HAIR
        private const val INTEL_RED = 0xFFFF1E3C.toInt()
        private const val INTEL_GOLD = 0xFFFFC65A.toInt()
        private const val STEEL = 0xFF4A4858.toInt()
        private const val STEEL_HI = 0xFF9A98AC.toInt()
        private const val STEEL_LO = 0xFF1E1C26.toInt()
        /** Warm practical and cool fluorescent lamp tints for per-floor colour temperature. */
        private const val WARM = 0xFFFFC890.toInt()
        private const val COOL = 0xFFD8F0FF.toInt()
        /** Lamp cone: bottom half-widths and alphas, from faint penumbra to bright core. */
        private val CONE_W = floatArrayOf(1.9f, 1.4f, 1.0f, 0.66f, 0.36f)
        private val CONE_A = floatArrayOf(0.016f, 0.022f, 0.028f, 0.034f, 0.045f)
    }

    /** 1 on the player's floor, fading to 0 one floor away: the stage gets the brightest trims. */
    private fun stage(fi: Int): Float = f.clamp01(1f - abs(fi - f.w.player.floorF))

    // =================================================================== rooms

    fun room(fs: FloorState, backdrop: Backdrop) {
        val plan = fs.plan
        val fi = plan.index
        val top = fi * H
        val gy = top + H
        val rt = top + SLAB
        if (!f.visibleY(top, gy + SLAB)) return
        val pal = f.palette(fs)
        val zone = plan.zone
        val sf = stage(fi)

        // Stairwell behind everything (it reaches up through the slab hole above).
        stairwell(fs, pal, top, gy)

        val free = rooms.scanFree(fs)
        val span = rooms.specialSpan(fs)
        g.save()
        g.clipRect(0f, rt, W, gy)
        if (plan.isVoid) {
            rooms.voidRoom(pal, fi, rt, gy)
        } else {
            g.fillVerticalGradient(0f, rt, W, gy, pal.wallTop, pal.wallBottom)
            walls.material(zone, pal, fi, rt, gy)
            // Free slots become windows onto the backdrop, or set dressing; a run of them may
            // become this floor's special room instead.
            val s0 = if (span >= 0) span shr 8 else -1
            val s1 = if (span >= 0) span and 0xFF else -1
            val windowChance = 0.35f + hash(fi, 709) * 0.35f
            for (i in Geo.SLOTS.indices) {
                if (!free[i] || i in s0..s1) continue
                val sx = Geo.SLOTS[i]
                val hv = hash(fi * 13 + i, 5)
                if (hv < windowChance) walls.window(zone, pal, sx, rt, gy, false, backdrop, fi * 7 + i)
                else walls.decor(zone, pal, sx, gy, rt, (hash(fi * 29 + i, 6) * 4000).toInt())
            }
            if (span >= 0) rooms.special(zone, pal, fs, span, rt, gy, backdrop)
        }

        // Lamp light on the lower wall and floor (the darkness overlay swallows it when shot out).
        val deepZone = depth(zone)
        val lc = lampColor(pal, zone, fi)
        for (i in plan.lights.indices) {
            val lx = plan.lights[i]
            if (fs.lightAlive[i]) {
                if (!lampOn(fi, i, zone)) continue
                // Additive light: a broad wash on the wall, a pool where the cone lands.
                g.blend(Gfx.Blend.ADD)
                g.glow(lx, rt + 0.6f, 2.9f, Col.alpha(lc, 0.1f + 0.06f * deepZone))
                g.save()
                g.translate(lx, gy - 0.02f)
                g.scale(1f, 0.16f)
                g.glow(0f, 0f, 1.9f, Col.alpha(lc, 0.35f + 0.15f * deepZone))
                g.restore()
                g.blend(Gfx.Blend.NORMAL)
            } else if (fs.lightFall[i] < 0f) {
                brokenLamp(pal, lx, rt, gy, i)
            }
        }

        // Deep zones: the room falls off into darkness toward its ends.
        if (deepZone > 0f) {
            g.fillRectRadial(0f, rt, W, gy, W / 2f, (rt + gy) / 2f, 5.6f, 0x00000000, Col.alpha(0xFF000000.toInt(), 0.5f * deepZone))
        }
        // Deeper floors: a dead fixture dangling from its wire between the working lamps.
        if (deepZone > 0f && hash(fi, 714) < 0.45f) {
            val dx = 2.2f + hash(fi, 715) * 5.6f
            var clear = true
            for (i in plan.lights.indices) if (abs(plan.lights[i] - dx) < 0.9f) clear = false
            if (clear) danglingLamp(dx, rt, fi)
        }
        stairSign(plan.stairsDown, pal, rt, 1f)
        ceiling(pal, rt, sf)
        baseboard(pal, gy, sf)
        if (plan.isVoid) walls.voidGlitch(pal, fi, rt, gy)
        g.restore()

        for (s in plan.shafts) shaftOnFloor(fs, pal, s.x, s.top, s.bottom, top, rt, gy)
        for (i in plan.doors.indices) door(fs, pal, zone, i, gy)
        for (h in plan.hazards) hazardBase(h.kind, pal, h.x, rt, gy, h.state(f.wt))
    }

    private fun danglingLamp(x: Float, rt: Float, fi: Int) {
        val sway = sin(f.t * 0.8f + fi) * 6f + 18f
        g.save()
        g.translate(x, rt + 0.14f)
        g.rotate(sway)
        g.line(0f, 0f, 0f, 0.75f, 0.015f, 0xFF121016.toInt())
        g.translate(0f, 0.8f)
        g.rotate(-40f)
        poly.quad(g, -0.08f, -0.08f, 0.08f, -0.08f, 0.2f, 0.07f, -0.2f, 0.07f, 0xFF201E26.toInt())
        g.fillRect(-0.19f, 0.06f, 0.19f, 0.08f, 0xFF3A3A40.toInt())
        g.restore()
        // An occasional dying spark.
        if (hash((f.t * 9f).toInt(), fi + 400) > 0.93f) f.glowDot(x + 0.3f, rt + 0.95f, 0.03f, 0xFFFFE080.toInt(), 1f)
    }

    /** 0 up in the tower, rising to 1 from the Mines down: how much the rooms rely on lamp pools. */
    private fun depth(zone: Zone): Float = when (zone) {
        Zone.ROOFTOP, Zone.TOWER, Zone.LABS -> 0f
        Zone.METRO -> 0.35f
        Zone.VOID -> 0.5f
        Zone.MINES, Zone.MAGMA, Zone.HELL -> 1f
    }

    /**
     * Colour temperature of this floor's lamps: warm practicals against the cool office up top,
     * sodium or fluorescent in the metro, flame-warm below. A small fixed set, so the cached
     * gradient shaders stay few.
     */
    private fun lampColor(pal: Palette, zone: Zone, fi: Int): Int {
        val h = hash(fi, 704)
        return when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> if (h < 0.4f) Col.lerp(pal.lamp, WARM, 0.6f) else pal.lamp
            Zone.LABS -> if (h < 0.3f) Col.lerp(pal.lamp, pal.neon, 0.3f) else pal.lamp
            Zone.METRO -> if (h < 0.45f) COOL else pal.lamp
            else -> pal.lamp
        }
    }

    /** Some lamps flicker, more of them the deeper you go. False while a flickering lamp is out. */
    private fun lampOn(fi: Int, i: Int, zone: Zone): Boolean {
        val chance = when (zone) {
            Zone.ROOFTOP, Zone.TOWER, Zone.LABS -> 0.07f
            Zone.METRO -> 0.16f
            Zone.MINES, Zone.MAGMA -> 0.24f
            Zone.HELL, Zone.VOID -> 0.3f
        }
        if (hash(fi * 7 + i, 705) >= chance) return true
        val tick = (f.t * 14f).toInt()
        val burst = sin(f.t * 0.9f + i * 2.3f + fi) > 0.35f
        return hash(tick, fi * 31 + i) > (if (burst) 0.5f else 0.03f)
    }

    /** Soffit, cove light and ambient occlusion under the slab. */
    private fun ceiling(pal: Palette, rt: Float, sf: Float) {
        val c = rt + 0.14f
        g.fillVerticalGradient(0f, c, W, c + 0.75f, Col.alpha(pal.deep, 0.5f), Col.alpha(pal.deep, 0f))
        g.fillRect(0f, rt, W, c, pal.ceil)
        g.fillRect(0f, c - HAIR, W, c, Col.alpha(pal.trim, 0.3f))
        g.fillRect(0f, c, W, c + 0.02f, Col.fade(pal.neon, 0.35f + 0.35f * sf))
        g.fillRect(0f, c + 0.02f, W, c + 0.14f, Col.fade(pal.neon, 0.05f + 0.04f * sf))
    }

    private fun baseboard(pal: Palette, gy: Float, sf: Float) {
        g.fillVerticalGradient(0f, gy - 0.45f, W, gy - 0.1f, Col.alpha(pal.deep, 0f), Col.alpha(pal.deep, 0.35f))
        g.fillRect(0f, gy - 0.1f, W, gy, Col.alpha(pal.deep, 0.75f))
        g.fillRect(0f, gy - 0.1f, W, gy - 0.1f + HAIR, Col.alpha(pal.trim, 0.35f))
        g.fillRect(0f, gy - 0.12f, W, gy - 0.1f, Col.fade(pal.neon2, 0.15f + 0.25f * sf))
    }

    private fun stairSign(side: Side, pal: Palette, rt: Float, a: Float) {
        val x = if (side == Side.LEFT) 0.72f else W - 0.72f
        val y = rt + 0.62f
        val green = 0xFF3CFF7A.toInt()
        g.fillRoundRect(x - 0.5f, y - 0.28f, x + 0.5f, y + 0.28f, 0.1f, Col.fade(0x143CFF7A, a))
        g.fillRoundRect(x - 0.4f, y - 0.19f, x + 0.4f, y + 0.19f, 0.05f, Col.fade(0xEE061208.toInt(), a))
        g.strokeRoundRect(x - 0.4f, y - 0.19f, x + 0.4f, y + 0.19f, 0.05f, 0.022f, Col.fade(green, a))
        f.worldText("EXIT", x + 0.08f, y + 0.085f, 0.22f, Col.fade(green, a))
        Glyphs.arrow(g, x - 0.26f, y, 0.09f, 0f, 1f, 0.028f, Col.fade(green, a))
    }

    // ------------------------------------------------------------- stairwell

    /** The switchback down from the floor above lands on this floor's arrival side. */
    private fun stairwell(fs: FloorState, pal: Palette, top: Float, gy: Float) {
        val side = fs.plan.arrival
        val left = side == Side.LEFT
        val xin = if (left) Geo.STAIR_W else W - Geo.STAIR_W
        val xw = if (left) 0.12f else W - 0.12f
        val x0 = min(xin, if (left) 0f else W)
        val x1 = max(xin, if (left) 0f else W)
        // A deep, quiet shaft: darker up top where it pierces the slab.
        g.fillVerticalGradient(x0, top, x1, gy, Col.mul(pal.wallBottom, 0.45f), Col.mul(pal.wallTop, 0.72f))
        // Emergency light strip on the outer wall.
        val sx = if (left) x0 + 0.05f else x1 - 0.05f
        g.fillRect(sx - 0.02f, top + 0.2f, sx + 0.02f, gy - 0.2f, Col.alpha(pal.neon2, 0.35f))
        g.fillRect(sx - 0.1f, top + 0.2f, sx + 0.1f, gy - 0.2f, Col.alpha(pal.neon2, 0.06f))
        // Newel wall on the inner edge with a lit bevel.
        val nx0 = if (left) xin - 0.05f else xin
        g.fillRect(nx0, top, nx0 + 0.05f, gy, Col.mul(pal.panel, 0.8f))
        g.fillRect(if (left) xin - HAIR else xin, top, if (left) xin else xin + HAIR, gy, Col.alpha(pal.trim, 0.3f))
        val mid = top + H * 0.52f
        // Back flight (wall → inner edge, down to this floor) then the landing, then the front flight.
        flight(pal, xw, mid, xin, gy, Col.mul(pal.panel, 0.75f), Col.fade(pal.neon2, 0.3f), false)
        val lx0 = min(xw, xw + (xin - xw) * 0.25f)
        val lx1 = max(xw, xw + (xin - xw) * 0.25f)
        g.fillRect(lx0, mid, lx1, mid + 0.2f, Col.mul(pal.panel, 1.1f))
        g.fillRect(lx0, mid, lx1, mid + HAIR, Col.alpha(pal.trim, 0.7f))
        flight(pal, xin, top, xw, mid, Col.mul(pal.panel, 1.15f), Col.fade(pal.neon2, 0.75f), true)
    }

    private fun flight(pal: Palette, xa: Float, ya: Float, xb: Float, yb: Float, color: Int, rail: Int, front: Boolean) {
        val n = 6
        val dx = (xb - xa) / n
        val dy = (yb - ya) / n
        val p = poly.begin()
        p.add(xa, ya)
        for (k in 0 until n) {
            p.add(xa + dx * (k + 1), ya + dy * k)
            p.add(xa + dx * (k + 1), ya + dy * (k + 1))
        }
        p.add(xb, yb + 0.22f)
        p.add(xa, ya + 0.22f)
        p.fill(g, color)
        // Tread nosings catch the light; the stringer's underside falls into shadow.
        val nose = Col.alpha(pal.trim, if (front) 0.75f else 0.4f)
        for (k in 0 until n) g.line(xa + dx * k, ya + dy * k, xa + dx * (k + 1), ya + dy * k, 0.022f, nose)
        g.line(xa, ya + 0.22f, xb, yb + 0.22f, 0.025f, Col.alpha(pal.deep, 0.8f))
        // Handrail on posts.
        val rh = 0.85f
        g.line(xa, ya - rh, xb, yb - rh, 0.08f, Col.fade(rail, 0.15f))
        g.line(xa, ya - rh, xb, yb - rh, 0.03f, rail)
        for (k in 0..2) {
            val t = k / 2f
            val px = xa + (xb - xa) * t
            val py = ya + (yb - ya) * t
            g.line(px, py - rh, px, py, 0.022f, Col.fade(rail, 0.45f))
        }
    }

    // ------------------------------------------------------------------ doors

    private fun door(fs: FloorState, pal: Palette, zone: Zone, i: Int, gy: Float) {
        val d = fs.plan.doors[i]
        val fi = fs.plan.index
        val x0 = d.x - Geo.DOOR_W / 2f
        val x1 = d.x + Geo.DOOR_W / 2f
        val y0 = gy - DOOR_H
        val intel = d.kind == DoorKind.INTEL
        val used = intel && fs.intelUsed[i]
        val live = intel && !used
        val playerIn = f.playerHiddenInDoor(d.x) || (f.w.player.state == PlayerState.INTEL && abs(f.w.player.anchorX - d.x) < 0.05f && f.w.player.floor == fi)
        val open = if (playerIn) 1f else fs.doorOpen[i]
        val pulse = 0.5f + 0.5f * sin(f.t * 3.2f)
        // Per-floor door family: tone, casing weight and signage vary floor to floor.
        val dv = (hash(fi, 706) * 3f).toInt()

        if (fs.plan.isVoid && !intel) {
            rooms.voidDoor(pal, x0, y0, x1, gy, open)
            statusLamp(pal, d.x, y0, open)
            return
        }

        // A live intel door radiates: halo on the wall, a pool on the floor.
        if (live) {
            g.fillRadialGradient(d.x, y0 + 1.0f, 1.35f + 0.12f * pulse, 0x50FF1E3C, 0x00FF1E3C)
            g.save()
            g.translate(d.x, gy - 0.02f)
            g.scale(1f, 0.13f)
            g.fillRadialGradient(0f, 0f, 1.3f, 0x70FF1E3C, 0x00FF1E3C)
            g.restore()
        }

        // Casing: top-lit, shadowed on the right return. Normal doors sit close to the wall value.
        val frameC = when {
            live -> 0xFF5A0A18.toInt()
            used -> 0xFF3A1C22.toInt()
            else -> Col.lerp(pal.wallTop, pal.doorFrame, 0.28f + dv * 0.1f)
        }
        val hi = if (live) INTEL_GOLD else pal.trim
        val cw = if (intel) 0.09f else 0.05f + dv * 0.02f
        g.fillRect(x0 - cw, y0 - cw - 0.01f, x1 + cw, gy, frameC)
        g.fillRect(x0 - cw, y0 - cw - 0.01f, x1 + cw, y0 - cw + 0.012f, Col.alpha(hi, if (used) 0.25f else if (intel) 0.65f else 0.35f))
        g.fillRect(x0 - cw, y0 - cw, x0 - cw + 0.02f, gy, Col.alpha(hi, if (used) 0.12f else if (intel) 0.3f else 0.14f))
        g.fillRect(x1 + cw - 0.03f, y0 - cw, x1 + cw, gy, Col.alpha(pal.deep, 0.6f))

        // Doorway interior, lit from within when open.
        g.fillRect(x0, y0, x1, gy, pal.deep)
        if (open > 0.02f) {
            val spill = if (intel) INTEL_RED else pal.lamp
            g.fillVerticalGradient(x0, y0, x1, gy, Col.alpha(spill, 0.06f), Col.alpha(spill, 0.28f * open))
        }

        // Leaf, swinging on the left hinge.
        val lw = Geo.DOOR_W * (1f - 0.82f * open)
        val leaf = when {
            live -> 0xFF6E0A1C.toInt()
            used -> 0xFF2A1418.toInt()
            else -> Col.lerp(pal.wallTop, pal.door, 0.45f + dv * 0.15f - 0.2f * depth(zone))
        }
        poly.quad(g, x0, y0, x0 + lw, y0 + 0.12f * open, x0 + lw, gy - 0.02f, x0, gy, leaf)
        if (lw > 0.3f) {
            g.save()
            g.translate(x0, y0)
            g.scale(lw / Geo.DOOR_W, 1f)
            if (intel) intelLeaf(live) else {
                leafDetail(zone, pal, leaf)
                // Mute the detail back toward the leaf: doors frame the scene, they don't lead it.
                g.fillRect(0f, 0.03f, Geo.DOOR_W, DOOR_H, Col.alpha(leaf, 0.4f + 0.2f * depth(zone)))
            }
            g.restore()
        }

        if (intel) {
            if (live) {
                // Light leaking round the leaf and a gold-cored neon outline.
                val core = 0xFFFFE0B0.toInt()
                f.glowLine(x0 - 0.045f, y0 - 0.055f, x1 + 0.045f, y0 - 0.055f, 0.035f, INTEL_RED, core)
                f.glowLine(x0 - 0.045f, y0 - 0.055f, x0 - 0.045f, gy, 0.035f, INTEL_RED, core)
                f.glowLine(x1 + 0.045f, y0 - 0.055f, x1 + 0.045f, gy, 0.035f, INTEL_RED, core)
                g.fillRect(x0 + lw - 0.02f, y0 + 0.05f, x0 + lw, gy - 0.05f, Col.alpha(INTEL_GOLD, 0.35f + 0.3f * pulse))
            }
            // Data-core icon on the leaf (or floating in the doorway when open).
            val cx = if (lw > 0.5f) x0 + lw / 2f else d.x
            dataCore(cx, y0 + 0.8f, 0.24f, if (used) 0xFF6A3A40.toInt() else 0xFFFFE4C8.toInt(), live)
            // Nameplate.
            val label = if (used) "CLEARED" else "INTEL"
            val py0 = y0 - 0.5f
            val py1 = y0 - 0.19f
            g.fillRoundRect(d.x - 0.44f, py0, d.x + 0.44f, py1, 0.05f, 0xF0120206.toInt())
            g.strokeRoundRect(d.x - 0.44f, py0, d.x + 0.44f, py1, 0.05f, 0.02f, if (used) 0xFF4A2A30.toInt() else Col.alpha(INTEL_GOLD, 0.8f))
            if (live) f.worldText(label, d.x, py1 - 0.075f, 0.23f, Col.alpha(INTEL_RED, 0.35f))
            f.worldText(label, d.x, py1 - 0.08f, 0.21f, if (used) 0xFF7A4A50.toInt() else 0xFFFF5A70.toInt())
        } else {
            // Someone's room beyond: a line of light under some doors.
            if (open < 0.02f && hash(fi * 11 + i, 708) < 0.3f) {
                val c = if (hash(fi * 11 + i, 710) < 0.5f) WARM else pal.lamp
                g.fillRect(x0 + 0.04f, gy - 0.025f, x1 - 0.04f, gy, Col.alpha(c, 0.85f))
                g.save()
                g.translate(d.x, gy)
                g.scale(1f, 0.12f)
                g.fillCircle(0f, 0f, 0.8f, Col.alpha(c, 0.1f))
                g.fillCircle(0f, 0f, 0.45f, Col.alpha(c, 0.14f))
                g.restore()
            }
            statusLamp(pal, d.x, y0, open)
            when (if (depth(zone) > 0.5f) 0 else dv) {
                1 -> {
                    // Room-number plate beside the frame.
                    val px = x1 + cw + 0.2f
                    g.fillRect(px - 0.14f, y0 + 0.9f, px + 0.14f, y0 + 1.06f, Col.mul(frameC, 0.7f))
                    f.worldText(EnvLabels.room(fi, i), px, y0 + 1.02f, 0.1f, Col.alpha(pal.trim, 0.8f))
                }
                2 -> g.fillRect(x0 - cw, y0 - cw - 0.03f, x1 + cw, y0 - cw - 0.01f, Col.alpha(pal.neon2, 0.35f))
                else -> Unit
            }
        }
    }

    /** Small status lamp above normal doors: red when someone's coming through (a gameplay cue). */
    private fun statusLamp(pal: Palette, x: Float, y0: Float, open: Float) {
        val lc = if (open > 0.1f) 0xFFFF3040.toInt() else pal.neon2
        g.fillRoundRect(x - 0.14f, y0 - 0.3f, x + 0.14f, y0 - 0.17f, 0.03f, pal.deep)
        g.fillRect(x - 0.1f, y0 - 0.26f, x + 0.1f, y0 - 0.21f, Col.fade(lc, if (open > 0.1f) 0.95f else 0.6f))
        if (open > 0.1f) g.fillRect(x - 0.2f, y0 - 0.33f, x + 0.2f, y0 - 0.14f, Col.fade(lc, 0.14f))
    }

    /** Zone-specific leaf detail, drawn in door-local space (0..DOOR_W, 0..DOOR_H). */
    private fun leafDetail(zone: Zone, pal: Palette, leaf: Int) {
        val w = Geo.DOOR_W
        val h = DOOR_H
        val inset = Col.mul(leaf, 0.78f)
        val hi = Col.alpha(pal.trim, 0.35f)
        // Shared: top-lit edge and a handle.
        g.fillRect(0f, 0f, w, 0.02f, hi)
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP, Zone.VOID -> {
                // Frosted glass office door with a push bar.
                g.fillRect(0.16f, 0.22f, w - 0.16f, 1.32f, Col.lerp(leaf, pal.haze, 0.09f))
                g.fillRect(0.16f, 0.22f, w - 0.16f, 0.24f, Col.alpha(pal.deep, 0.7f))
                poly.quad(g, 0.2f, 1.28f, 0.34f, 1.28f, 0.7f, 0.26f, 0.56f, 0.26f, 0x14FFFFFF)
                g.fillRect(0.12f, 1.52f, w - 0.12f, 1.58f, Col.alpha(pal.trim, 0.6f))
                g.fillRect(0.12f, h - 0.28f, w - 0.12f, h - 0.1f, inset)
            }
            Zone.LABS -> {
                // Sliding bulkhead: centre seam, porthole, hazard band.
                g.fillRect(w / 2f - 0.01f, 0f, w / 2f + 0.01f, h, Col.alpha(pal.deep, 0.8f))
                g.fillCircle(w / 2f, 0.62f, 0.2f, inset)
                g.fillCircle(w / 2f, 0.62f, 0.15f, Col.alpha(pal.neon2, 0.25f))
                g.fillCircle(w / 2f - 0.05f, 0.57f, 0.04f, 0x40FFFFFF)
                g.strokeCircle(w / 2f, 0.62f, 0.2f, 0.03f, Col.alpha(pal.trim, 0.6f))
                g.save()
                g.clipRect(0.05f, 1.36f, w - 0.05f, 1.5f)
                g.fillRect(0.05f, 1.36f, w - 0.05f, 1.5f, 0xFF15150E.toInt())
                var sx = -0.1f
                while (sx < w) {
                    poly.quad(g, sx, 1.5f, sx + 0.1f, 1.5f, sx + 0.24f, 1.36f, sx + 0.14f, 1.36f, 0xFFB08E1C.toInt())
                    sx += 0.2f
                }
                g.restore()
                g.fillRect(w - 0.2f, 1.0f, w - 0.12f, 1.08f, pal.neon)
            }
            Zone.METRO -> {
                // Riveted service door with a louvre and kick plate.
                for (row in 0..1) {
                    val y = if (row == 0) 0.12f else h - 0.12f
                    var x = 0.1f
                    while (x < w) {
                        g.fillCircle(x, y, 0.022f, Col.alpha(pal.trim, 0.5f))
                        x += 0.2f
                    }
                }
                for (k in 0 until 5) g.fillRect(0.25f, 0.4f + k * 0.1f, w - 0.25f, 0.44f + k * 0.1f, Col.alpha(pal.deep, 0.75f))
                g.fillRect(0.3f, 1.1f, w - 0.3f, 1.26f, Col.lerp(leaf, 0xFFD8D4C8.toInt(), 0.35f))
                g.fillRect(0.36f, 1.16f, w - 0.36f, 1.2f, Col.mul(leaf, 0.6f))
                g.fillRect(0.08f, h - 0.36f, w - 0.08f, h - 0.16f, Col.lerp(leaf, pal.trim, 0.12f))
            }
            Zone.MINES -> {
                // Planks, iron straps, Z-brace.
                var x = 0.2f
                while (x < w) {
                    g.fillRect(x - 0.012f, 0.02f, x, h, Col.alpha(pal.deep, 0.6f))
                    x += 0.2f
                }
                val iron = 0xFF2A2A2E.toInt()
                for (row in 0..1) {
                    val y = if (row == 0) 0.35f else h - 0.45f
                    g.fillRect(0f, y, w, y + 0.09f, iron)
                    g.fillRect(0f, y, w, y + 0.015f, 0x40FFFFFF)
                    g.fillCircle(0.12f, y + 0.045f, 0.025f, 0xFF8A8070.toInt())
                    g.fillCircle(w - 0.12f, y + 0.045f, 0.025f, 0xFF8A8070.toInt())
                }
                g.line(0.1f, h - 0.5f, w - 0.1f, 0.5f, 0.08f, inset)
            }
            Zone.MAGMA -> {
                // Blast door: heavy ribs, a heat-glow sill.
                for (k in 0 until 4) {
                    val y = 0.3f + k * 0.48f
                    g.fillRect(0.08f, y, w - 0.08f, y + 0.16f, inset)
                    g.fillRect(0.08f, y, w - 0.08f, y + 0.018f, Col.alpha(pal.trim, 0.4f))
                }
                g.fillRect(0.3f, 0.12f, w - 0.3f, 0.2f, 0xFFB8961C.toInt())
                g.fillVerticalGradient(0f, h - 0.5f, w, h, Col.alpha(pal.glow, 0f), Col.alpha(pal.glow, 0.4f))
            }
            Zone.HELL -> {
                // Iron-studded gothic door; the frame cuts a pointed arch.
                g.fillRect(w / 2f - 0.012f, 0.3f, w / 2f + 0.012f, h, Col.alpha(pal.deep, 0.6f))
                for (r in 0 until 5) for (c in 0 until 3) {
                    g.fillCircle(0.2f + c * 0.3f, 0.55f + r * 0.36f, 0.03f, 0xFF8A6A5A.toInt())
                }
                g.strokeCircle(w / 2f + 0.2f, 1.2f, 0.08f, 0.025f, 0xFFB09070.toInt())
                poly.tri(g, 0f, 0f, 0.46f, 0f, 0f, 0.48f, pal.doorFrame)
                poly.tri(g, w, 0f, w - 0.46f, 0f, w, 0.48f, pal.doorFrame)
                g.line(0f, 0.48f, 0.46f, 0f, 0.02f, Col.alpha(pal.trim, 0.3f))
                g.line(w, 0.48f, w - 0.46f, 0f, 0.02f, Col.alpha(pal.deep, 0.8f))
            }
        }
        if (zone != Zone.LABS) g.fillCircle(w - 0.14f, 1.2f, 0.045f, 0xFFD8C890.toInt())
    }

    /** The precious one: crimson lacquer, gold inlay. */
    private fun intelLeaf(live: Boolean) {
        val w = Geo.DOOR_W
        val h = DOOR_H
        val inlay = if (live) Col.alpha(INTEL_GOLD, 0.85f) else 0x40A06A50
        g.fillVerticalGradient(0.08f, 0.08f, w - 0.08f, h - 0.08f, if (live) 0xFFB01C36.toInt() else 0xFF301418.toInt(), if (live) 0xFF600A1A.toInt() else 0xFF1E0C10.toInt())
        g.strokeRect(0.12f, 0.12f, w - 0.12f, h - 0.12f, 0.025f, inlay)
        g.strokeRect(0.2f, 1.25f, w - 0.2f, h - 0.22f, 0.018f, Col.fade(inlay, 0.7f))
        g.fillRect(0f, 0f, w, 0.02f, Col.fade(inlay, 0.9f))
        g.fillCircle(w - 0.14f, 1.2f, 0.05f, if (live) INTEL_GOLD else 0xFF6A4A40.toInt())
    }

    fun dataCore(cx: Float, cy: Float, r: Float, color: Int, live: Boolean) {
        val spin = if (live) f.t * 90f else 0f
        if (live) g.fillCircle(cx, cy, r * 1.6f, Col.alpha(color, 0.18f))
        g.save()
        g.translate(cx, cy)
        g.rotate(45f)
        g.strokeRect(-r * 0.7f, -r * 0.7f, r * 0.7f, r * 0.7f, r * 0.14f, color)
        g.rotate(spin)
        g.fillRect(-r * 0.3f, -r * 0.3f, r * 0.3f, r * 0.3f, color)
        g.restore()
        for (k in 0 until 4) {
            val a = k * 1.5708f
            g.line(cx + kotlin.math.cos(a) * r * 1.05f, cy + sin(a) * r * 1.05f, cx + kotlin.math.cos(a) * r * 1.35f, cy + sin(a) * r * 1.35f, r * 0.1f, color)
        }
    }

    // ----------------------------------------------------------------- shafts

    private fun shaftOnFloor(fs: FloorState, pal: Palette, sx: Float, topFloor: Int, bottomFloor: Int, top: Float, rt: Float, gy: Float) {
        val fi = fs.plan.index
        val x0 = sx - Geo.SHAFT_W / 2f
        val x1 = sx + Geo.SHAFT_W / 2f
        val y0 = if (fi == topFloor) rt else top
        val y1 = if (fi < bottomFloor) gy + SLAB else gy
        // Shaft void, guide rails and a bolted divider beam at each floor line.
        g.fillVerticalGradient(x0, y0, x1, y1, 0xFF040308.toInt(), 0xFF0D0B14.toInt())
        for (k in 0..1) {
            val rx = if (k == 0) x0 + 0.08f else x1 - 0.12f
            g.fillRect(rx, y0, rx + 0.04f, y1, 0xFF2A2834.toInt())
            g.fillRect(rx, y0, rx + 0.012f, y1, 0x30FFFFFF)
        }
        if (fi > topFloor) {
            g.fillRect(x0, top + 0.02f, x1, top + 0.14f, 0xFF16141E.toInt())
            g.fillRect(x0, top + 0.02f, x1, top + 0.035f, 0x28FFFFFF)
        }
        if (fi == topFloor) {
            // Machine-room pulley housing.
            g.fillRect(x0, rt, x1, rt + 0.26f, 0xFF1A1822.toInt())
            g.fillRect(x0, rt + 0.24f, x1, rt + 0.26f, 0x30FFFFFF)
            g.fillCircle(sx, rt + 0.26f, 0.17f, 0xFF3A3846.toInt())
            g.strokeCircle(sx, rt + 0.26f, 0.13f, 0.02f, 0xFF5A586A.toInt())
            g.fillCircle(sx, rt + 0.26f, 0.05f, 0xFF12101A.toInt())
        }
        // Landing portal on this floor: brushed steel, lit header.
        val fy = gy - 2.6f
        g.fillRect(x0 - 0.1f, fy - 0.12f, x1 + 0.1f, fy, STEEL)
        g.fillRect(x0 - 0.1f, fy, x0, gy, STEEL)
        g.fillRect(x1, fy, x1 + 0.1f, gy, STEEL)
        g.fillRect(x0 - 0.1f, fy - 0.12f, x1 + 0.1f, fy - 0.1f, STEEL_HI)
        g.fillRect(x0 - 0.1f, fy, x0 - 0.08f, gy, 0x50FFFFFF)
        g.fillRect(x1 + 0.07f, fy, x1 + 0.1f, gy, STEEL_LO)
        g.fillRect(x0, gy - 0.04f, x1, gy, 0xFF6A6878.toInt())
        // Hall lantern: floor number and direction.
        val car = f.w.elevators[topFloor]
        val here = car != null && car.doorsOpen && car.atFloor == fi
        val iy = fy - 0.36f
        // Plate sized for four characters ("B149") plus the direction arrow.
        g.fillRoundRect(sx - 0.42f, iy - 0.15f, sx + 0.42f, iy + 0.15f, 0.05f, 0xFF08070C.toInt())
        g.strokeRoundRect(sx - 0.42f, iy - 0.15f, sx + 0.42f, iy + 0.15f, 0.05f, HAIR, 0xFF3A3846.toInt())
        if (car != null) {
            val num = car.pos.roundToInt()
            val c = if (here) pal.neon2 else Col.fade(pal.neon, 0.85f)
            f.worldText(EnvLabels.short(num), sx + 0.08f, iy + 0.07f, 0.17f, c)
            val dir = if (car.pos < fi - 0.05f) 1f else if (car.pos > fi + 0.05f) -1f else 0f
            if (dir != 0f) Glyphs.arrow(g, sx - 0.3f, iy, 0.07f, 0f, dir, 0.025f, c) else g.fillCircle(sx - 0.3f, iy, 0.04f, c)
            if (here) g.fillRoundRect(sx - 0.48f, iy - 0.21f, sx + 0.48f, iy + 0.21f, 0.08f, Col.alpha(pal.neon2, 0.15f))
        }
    }

    /** Elevator cars drawn over the floors; the player rides inside, behind glass doors. */
    fun elevators(actors: Actors) {
        val w = f.w
        // Forget door animation state for shafts the world has culled.
        if (carOpen.size > w.elevators.size) carOpen.keys.retainAll(w.elevators.keys)
        for (car in w.elevators.values) {
            val s = car.shaft
            val yb = Geo.groundY(car.pos)
            val top = yb - 2.45f
            val pulleyY = s.top * H + SLAB + 0.26f
            // Counterweight rides the other way down the shaft's right rail.
            val shaftBottom = Geo.groundY(s.bottom)
            val cwY = pulleyY + 0.3f + (shaftBottom - yb) * 0.9f
            if (f.visibleY(cwY, cwY + 0.9f) && cwY + 0.9f < shaftBottom) {
                g.line(s.x + 0.4f, pulleyY, s.x + 0.4f, cwY, 0.02f, 0xFF5A5868.toInt())
                g.fillRect(s.x + 0.3f, cwY, s.x + 0.5f, cwY + 0.9f, 0xFF24222C.toInt())
                g.fillRect(s.x + 0.3f, cwY, s.x + 0.5f, cwY + 0.02f, 0x40FFFFFF)
                for (k in 1..3) g.fillRect(s.x + 0.3f, cwY + k * 0.22f, s.x + 0.5f, cwY + k * 0.22f + 0.02f, 0xFF121018.toInt())
            }
            if (!f.visibleY(top - 4f, yb + 0.3f)) continue
            val fs = w.floors[car.pos.roundToInt()] ?: w.floors[s.top]
            val pal = f.palette(fs)
            val state = carOpen.getOrPut(s.id) { floatArrayOf(if (car.doorsOpen) 1f else 0f) }
            val target = if (car.doorsOpen) 1f else 0f
            state[0] += (target - state[0]) * min(1f, f.dt * 7f)
            if (f.dt == 0f) state[0] = target
            val open = state[0]
            val x0 = s.x - 0.52f
            val x1 = s.x + 0.52f
            // Hoist ropes to the crosshead.
            for (k in -1..1) g.line(s.x + k * 0.12f, pulleyY, s.x + k * 0.12f, top - 0.3f, 0.022f, 0xFF7A788A.toInt())
            // Crosshead and sheave.
            g.fillRect(x0 + 0.1f, top - 0.34f, x1 - 0.1f, top - 0.24f, 0xFF24222C.toInt())
            g.fillCircle(s.x, top - 0.3f, 0.08f, 0xFF4A4858.toInt())
            g.line(x0 + 0.1f, top - 0.24f, x0 - 0.02f, top - 0.14f, 0.03f, 0xFF24222C.toInt())
            g.line(x1 - 0.1f, top - 0.24f, x1 + 0.02f, top - 0.14f, 0.03f, 0xFF24222C.toInt())
            // Body and lit interior.
            g.fillRoundRect(x0 - 0.06f, top - 0.16f, x1 + 0.06f, yb + 0.14f, 0.05f, 0xFF2C2A36.toInt())
            g.fillRect(x0 - 0.06f, top - 0.16f, x1 + 0.06f, top - 0.14f, STEEL_HI)
            g.fillVerticalGradient(x0, top, x1, yb, Col.mul(pal.lamp, 0.6f), Col.mul(pal.lamp, 0.24f))
            g.fillRect(x0 + 0.34f, top + 0.12f, x0 + 0.36f, yb - 0.08f, 0x18000000)
            g.fillRect(x1 - 0.36f, top + 0.12f, x1 - 0.34f, yb - 0.08f, 0x18000000)
            g.fillRect(x0 + 0.08f, top + 0.04f, x1 - 0.08f, top + 0.09f, 0xFFFFFFFF.toInt())
            g.fillVerticalGradient(x0, top + 0.09f, x1, top + 0.7f, Col.alpha(pal.lamp, 0.3f), Col.alpha(pal.lamp, 0f))
            g.fillRect(x0, yb - 1.05f, x1, yb - 1.02f, 0x40FFFFFF)
            g.fillRect(x0, yb - 0.07f, x1, yb, 0xFF7A7888.toInt())
            g.fillRect(x0, yb - 0.07f, x1, yb - 0.06f, 0xFFB8B6C8.toInt())
            val riding = w.player.state == PlayerState.ELEVATOR && w.player.elevatorShaft == s.id
            if (riding) actors.player(force = true)
            // Glass doors.
            val half = (x1 - x0) / 2f
            val closed = half * (1f - open)
            if (closed > 0.01f) {
                val glass = Col.alpha(Col.lerp(pal.lamp, 0xFF203040.toInt(), 0.6f), 0.4f)
                g.fillRect(x0, top, x0 + closed, yb, glass)
                g.fillRect(x1 - closed, top, x1, yb, glass)
                g.fillRect(x0 + closed - 0.03f, top, x0 + closed, yb, 0xFF8A8898.toInt())
                g.fillRect(x1 - closed, top, x1 - closed + 0.03f, yb, 0xFF8A8898.toInt())
                g.line(x0 + 0.05f, yb - 0.3f, x0 + closed * 0.8f, top + 0.4f, 0.03f, 0x28FFFFFF)
            }
            // Hazard-striped toe guard under the car, status light on top.
            g.save()
            g.clipRect(x0 - 0.06f, yb + 0.02f, x1 + 0.06f, yb + 0.14f)
            g.fillRect(x0 - 0.06f, yb + 0.02f, x1 + 0.06f, yb + 0.14f, 0xFF15150E.toInt())
            var hx = x0 - 0.2f
            while (hx < x1 + 0.06f) {
                poly.quad(g, hx, yb + 0.14f, hx + 0.08f, yb + 0.14f, hx + 0.2f, yb + 0.02f, hx + 0.12f, yb + 0.02f, 0xFFB08E1C.toInt())
                hx += 0.16f
            }
            g.restore()
            g.fillRect(x0 - 0.06f, top - 0.16f, x1 + 0.06f, top - 0.12f, Col.fade(pal.neon, 0.9f))
            f.glowDot(s.x + 0.32f, top - 0.24f, 0.045f, if (car.doorsOpen) 0xFF40FF80.toInt() else 0xFFFF4040.toInt(), 0.9f)
        }
    }

    // ------------------------------------------------------------------ slabs

    fun slab(fs: FloorState) {
        val plan = fs.plan
        val fi = plan.index
        val gy = Geo.groundY(fi)
        if (!f.visibleY(gy - 1f, gy + SLAB + 0.2f)) return
        val pal = f.palette(fs)
        val below = f.w.floors[fi + 1]
        val palBelow = if (below != null) f.palette(below) else pal
        val sf = stage(fi)
        val holeL = plan.stairsDown == Side.LEFT
        val hole0 = if (holeL) 0f else W - Geo.STAIR_W
        val hole1 = if (holeL) Geo.STAIR_W else W
        // Segments: [-0.6, W+0.6] minus the stair hole and continuing shafts.
        var x = -0.6f
        val cuts = cutBuf
        var n = 0
        cuts[n++] = hole0; cuts[n++] = hole1
        for (s in plan.shafts) if (fi < s.bottom && n < 8) {
            cuts[n++] = s.x - Geo.SHAFT_W / 2f; cuts[n++] = s.x + Geo.SHAFT_W / 2f
        }
        // Sort cut pairs by start (tiny n, insertion sort).
        var i = 2
        while (i < n) {
            var j = i
            while (j >= 2 && cuts[j] < cuts[j - 2]) {
                val a = cuts[j]; val b = cuts[j + 1]
                cuts[j] = cuts[j - 2]; cuts[j + 1] = cuts[j - 1]
                cuts[j - 2] = a; cuts[j - 1] = b
                j -= 2
            }
            i += 2
        }
        var k = 0
        while (k <= n) {
            val end = if (k < n) cuts[k] else W + 0.6f
            if (end > x + 0.001f) slabSegment(pal, palBelow, x, end, gy, fi, sf)
            if (k < n) x = cuts[k + 1]
            k += 2
        }
        // Striped safety lip on the stair hole edge.
        val lip = if (holeL) hole1 else hole0
        g.fillRect(lip - 0.05f, gy, lip + 0.05f, gy + SLAB, 0xFF15150E.toInt())
        var y = gy
        while (y < gy + SLAB) {
            g.fillRect(lip - 0.05f, y, lip + 0.05f, min(gy + SLAB, y + 0.06f), 0xFFD8B01E.toInt())
            y += 0.12f
        }
    }

    private fun slabSegment(pal: Palette, palBelow: Palette, x0: Float, x1: Float, gy: Float, fi: Int, sf: Float) {
        if (fi == 0) {
            // The roof deck: wet concrete catching the neon, then the building's crown.
            g.fillVerticalGradient(x0, gy, x1, gy + SLAB, 0xFF2E2840.toInt(), 0xFF16121F.toInt())
            g.fillRect(x0, gy, x1, gy + 0.05f, 0xFF5E5478.toInt())
            g.fillRect(x0, gy, x1, gy + 0.012f, 0xFFB0A4D0.toInt())
            g.fillRect(x0, gy + SLAB - 0.05f, x1, gy + SLAB, Col.fade(palBelow.neon, 0.6f))
            return
        }
        // Slab body: floor finish on top, concrete core, ceiling lip below.
        g.fillVerticalGradient(x0, gy, x1, gy + SLAB, pal.slab, palBelow.ceil)
        g.fillRect(x0, gy + 0.03f, x1, gy + 0.075f, Col.alpha(pal.trim, 0.12f))
        g.fillRect(x0, gy + 0.075f, x1, gy + 0.09f, Col.alpha(pal.deep, 0.8f))
        g.fillRect(x0, gy + SLAB - 0.035f, x1, gy + SLAB, Col.mul(palBelow.panel, 0.9f))
        // The stage line: the floor's neon edge, brightest where the player stands.
        g.fillRect(x0, gy, x1, gy + 0.03f, Col.fade(pal.slabEdge, 0.55f + 0.45f * sf))
        g.fillRect(x0, gy - 0.05f, x1, gy, Col.fade(pal.slabEdge, 0.05f + 0.1f * sf))
        if (sf > 0.02f) g.fillRect(x0, gy + 0.03f, x1, gy + 0.12f, Col.fade(pal.slabEdge, 0.18f * sf))
    }

    // ------------------------------------------------------------ outer walls

    fun outerWalls() {
        val w = f.w
        val roofY = Geo.groundY(0)
        for (i in f.first..f.last) {
            val fs = w.floors[i] ?: continue
            val pal = f.palette(fs)
            val top = if (i == 0) roofY - 0.5f else i * H + SLAB
            val bottom = (i + 1) * H + SLAB
            if (i == 0) {
                // Parapets with a lit coping.
                for (side in 0..1) {
                    val x0 = if (side == 0) -0.6f else W
                    g.fillVerticalGradient(x0, top, x0 + 0.6f, bottom, 0xFF231E34.toInt(), 0xFF120F1C.toInt())
                    g.fillRect(x0, top, x0 + 0.6f, top + 0.09f, 0xFF4A4262.toInt())
                    g.fillRect(x0, top, x0 + 0.6f, top + 0.015f, 0xFFA898D0.toInt())
                    g.fillRect(x0, top + 0.09f, x0 + 0.6f, top + 0.12f, Col.fade(pal.neon, 0.7f))
                }
                continue
            }
            val zone = fs.plan.zone
            for (side in 0..1) {
                val x0 = if (side == 0) -0.6f else W
                facade(zone, pal, i, side, x0, top - SLAB, bottom)
            }
            // Building edge: a thin neon seam where the cutaway meets the facade.
            val edge = Col.fade(pal.neon, if (zone == Zone.TOWER || zone == Zone.ROOFTOP || zone == Zone.LABS) 0.8f else 0.45f)
            g.fillRect(-0.035f, top - SLAB, 0f, bottom, edge)
            g.fillRect(W, top - SLAB, W + 0.035f, bottom, edge)
        }
    }

    /**
     * What surrounds the cutaway: glass curtain wall up in the tower, cast concrete in the labs,
     * then raw earth, strata and rock that glows hotter the deeper you go.
     */
    private fun facade(zone: Zone, pal: Palette, i: Int, side: Int, x0: Float, top: Float, bottom: Float) {
        val x1 = x0 + 0.6f
        val inner = if (side == 0) x1 else x0
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> {
                g.fillVerticalGradient(x0, top, x1, bottom, Col.lerp(pal.outer, pal.skyBottom, 0.35f), pal.outer)
                // Two glass panes per floor; a lit office behind some.
                val lit = hash(i * 2 + side, 201) > 0.35f
                val py0 = top + SLAB + 0.5f
                val py1 = bottom - SLAB - 0.4f
                g.fillRect(x0 + 0.12f, py0, x1 - 0.12f, py1, if (lit) Col.fade(pal.outerLit, 0.4f) else 0xFF08060C.toInt())
                g.fillRect(x0 + 0.12f, py0, x1 - 0.12f, py0 + 0.35f, if (lit) Col.fade(pal.outerLit, 0.25f) else 0x10FFFFFF)
                poly.quad(g, x0 + 0.12f, py1 - 0.3f, x0 + 0.12f, py1 - 0.6f, x1 - 0.12f, py0 + 0.2f, x1 - 0.12f, py0 + 0.5f, 0x12FFFFFF)
                g.fillRect(x0 + 0.29f, py0, x0 + 0.31f, py1, pal.outer)
                // Steel fin on the building's corner, an aircraft light every few floors.
                val fx = if (side == 0) x0 else x1 - 0.05f
                g.fillRect(fx, top, fx + 0.05f, bottom, Col.mul(pal.outer, 1.8f))
                g.fillRect(fx, top, fx + 0.012f, bottom, Col.alpha(pal.trim, 0.25f))
                if (i % 4 == 1 && sin(f.t * 2.2f + side) > 0.2f) f.glowDot(fx + 0.025f, top + 0.6f, 0.03f, 0xFFFF3040.toInt(), 0.9f)
                g.fillRect(x0, bottom - SLAB, x1, bottom - SLAB + 0.07f, Col.mul(pal.outer, 2.4f))
                g.fillRect(x0, bottom - SLAB, x1, bottom - SLAB + 0.012f, Col.alpha(pal.trim, 0.5f))
            }
            Zone.LABS, Zone.VOID -> {
                g.fillVerticalGradient(x0, top, x1, bottom, Col.mul(pal.outer, 1.6f), pal.outer)
                g.fillRect(x0, top + 0.02f, x1, top + 0.04f, Col.alpha(pal.trim, 0.25f))
                val vy = top + SLAB + 1.1f
                for (k in 0 until 5) g.fillRect(x0 + 0.14f, vy + k * 0.1f, x1 - 0.14f, vy + k * 0.1f + 0.04f, pal.deep)
                // A service riser running the height of the building.
                val px = if (side == 0) x0 + 0.08f else x1 - 0.16f
                g.fillRect(px, top, px + 0.08f, bottom, Col.mul(pal.panel, 0.9f))
                g.fillRect(px, top, px + 0.02f, bottom, Col.alpha(pal.trim, 0.25f))
                g.fillRect(px - 0.02f, top + 1.6f, px + 0.1f, top + 1.7f, Col.mul(pal.panel, 1.2f))
                val on = sin(f.t * 2f + i) > -0.3f
                g.fillCircle(inner + (if (side == 0) -0.12f else 0.12f), top + SLAB + 0.5f, 0.035f, if (on) pal.outerLit else pal.deep)
            }
            else -> earth(zone, pal, i, x0, x1, top, bottom)
        }
    }

    private fun earth(zone: Zone, pal: Palette, i: Int, x0: Float, x1: Float, top: Float, bottom: Float) {
        // Rock mass with world-anchored strata so the bands run unbroken floor to floor.
        g.fillVerticalGradient(x0, top, x1, bottom, Col.mul(pal.outer, 1.5f), pal.outer)
        val band = 0.55f
        var k = (top / band).toInt()
        while (k * band < bottom) {
            val y = k * band
            val t = hash(k, 211)
            val c = if (t > 0.6f) Col.alpha(pal.trim, 0.1f) else if (t < 0.3f) Col.alpha(pal.deep, 0.5f) else 0
            if (c != 0) {
                val w0 = y + (hash(k, 212) - 0.5f) * 0.1f
                val w1 = y + (hash(k + 1, 212) - 0.5f) * 0.1f
                poly.quad(g, x0, w0, x1, w1, x1, w1 + band * 0.6f, x0, w0 + band * 0.6f, c)
            }
            if (zone == Zone.MINES && hash(k, 213) > 0.8f) {
                val gx = x0 + 0.1f + hash(k, 214) * 0.4f
                g.fillCircle(gx, y + 0.2f, 0.035f, if (k % 2 == 0) 0xFF5AD8FF.toInt() else 0xFFFFC84A.toInt())
            }
            k++
        }
        if (zone == Zone.MAGMA || zone == Zone.HELL) {
            // Glowing fissure crawling down through the rock.
            val pulse = 0.6f + 0.4f * sin(f.t * 1.7f + i)
            val cx = x0 + 0.15f + hash(i, 215) * 0.3f
            g.line(cx, top, cx + 0.12f, top + 1.2f, 0.09f, Col.fade(pal.glow, 0.15f * pulse))
            g.line(cx, top, cx + 0.12f, top + 1.2f, 0.028f, Col.fade(pal.glow, pulse))
            g.line(cx + 0.12f, top + 1.2f, cx - 0.05f, top + 2.4f, 0.028f, Col.fade(pal.glow, pulse))
            g.line(cx - 0.05f, top + 2.4f, cx + 0.06f, bottom, 0.028f, Col.fade(pal.glow, pulse * 0.7f))
        }
        when (zone) {
            Zone.METRO -> {
                // Cable conduits bracketed to the retaining wall.
                val cx = x0 + 0.38f
                for (k in 0..2) g.fillRect(cx + k * 0.05f, top, cx + k * 0.05f + 0.03f, bottom, if (k == 1) 0xFF3A2A18.toInt() else 0xFF20242A.toInt())
                g.fillRect(cx - 0.03f, top + 1.2f, cx + 0.16f, top + 1.26f, 0xFF4A5058.toInt())
            }
            Zone.MINES -> {
                // Shoring: a timber cross-brace per floor.
                g.line(x0, top + 0.4f, x1, bottom - 0.6f, 0.07f, 0xFF3E2716.toInt())
                g.line(x0, top + 0.4f, x1, bottom - 0.6f, 0.015f, 0xFF7A5230.toInt())
            }
            Zone.HELL -> {
                // Ribs of something enormous buried in the rock.
                for (k in 0 until 3) {
                    val ry = top + 0.6f + k * 0.9f
                    g.line(x0, ry, x1, ry - 0.2f, 0.06f, Col.alpha(pal.trim, 0.35f))
                }
            }
            else -> Unit
        }
        // Retaining-wall lip at each floor.
        g.fillRect(x0, bottom - SLAB, x1, bottom - SLAB + 0.06f, Col.mul(pal.outer, 2.2f))
        g.fillRect(x0, bottom - SLAB, x1, bottom - SLAB + 0.012f, Col.alpha(pal.trim, 0.4f))
    }

    // ------------------------------------------------------------ the rooftop

    fun roof(fs: FloorState) {
        val gy = Geo.groundY(0)
        val pal = Palette.of(Zone.ROOFTOP)
        val hutRight = fs.plan.stairsDown == Side.RIGHT
        // Water tower, left.
        waterTower(if (hutRight) 0.75f else W - 0.75f, gy)
        billboard(pal, gy)
        hut(pal, gy, hutRight)
        // Low AC units.
        acUnit(if (hutRight) 3.9f else W - 3.9f, gy)
        acUnit(if (hutRight) 7.0f else W - 7.0f, gy)
        // Stairwell down to floor 1 sits under the hut; the floor-1 room draws it.
        rain(gy)
    }

    /** Rain slanting across the roof, with splashes on the deck. */
    private fun rain(gy: Float) {
        val top = max(f.camY, gy - 9f)
        val h = gy - top
        if (h <= 0f) return
        for (k in 0 until 46) {
            val x = hash(k, 221) * (W + 1.2f) - 0.6f
            val sp = 6f + hash(k, 222) * 3f
            val y = top + fract(hash(k, 223) + f.t * sp / h.coerceAtLeast(1f)) * h
            val len = 0.25f + hash(k, 224) * 0.2f
            g.line(x, y, x - len * 0.18f, y - len, 0.012f, if (k % 5 == 0) 0x50FFB0E0 else 0x38C0D8FF)
        }
        for (k in 0 until 8) {
            val ph = fract(f.t * 1.7f + hash(k, 225))
            val x = hash(k + (f.t * 1.7f + hash(k, 225)).toInt() * 13, 226) * W
            g.strokeCircle(x, gy - 0.01f, 0.03f + ph * 0.08f, 0.01f, Col.alpha(0xFFC0D8FF.toInt(), 0.35f * (1f - ph)))
        }
    }

    private fun waterTower(x: Float, gy: Float) {
        val c = 0xFF1A1428.toInt()
        g.line(x - 0.45f, gy, x - 0.35f, gy - 1.3f, 0.06f, c)
        g.line(x + 0.45f, gy, x + 0.35f, gy - 1.3f, 0.06f, c)
        g.line(x - 0.4f, gy - 0.4f, x + 0.4f, gy - 1.0f, 0.03f, c)
        g.line(x + 0.4f, gy - 0.4f, x - 0.4f, gy - 1.0f, 0.03f, c)
        g.fillRect(x - 0.55f, gy - 2.6f, x + 0.55f, gy - 1.3f, 0xFF231A34.toInt())
        for (k in 0 until 5) g.fillRect(x - 0.55f + k * 0.26f, gy - 2.6f, x - 0.53f + k * 0.26f, gy - 1.3f, 0xFF191226.toInt())
        poly.tri(g, x - 0.62f, gy - 2.6f, x + 0.62f, gy - 2.6f, x, gy - 3.1f, 0xFF2C2240.toInt())
        g.fillRect(x - 0.55f, gy - 1.42f, x + 0.55f, gy - 1.3f, 0xFF3A2E52.toInt())
        // Rim light from the magenta sky.
        g.fillRect(x + 0.5f, gy - 2.6f, x + 0.55f, gy - 1.3f, 0x60FF3D9A)
        g.line(x + 0.62f, gy - 2.6f, x, gy - 3.1f, 0.02f, 0x60FF3D9A)
    }

    private fun acUnit(x: Float, gy: Float) {
        g.fillRect(x - 0.35f, gy - 0.5f, x + 0.35f, gy, 0xFF2A2440.toInt())
        g.fillRect(x - 0.35f, gy - 0.5f, x + 0.35f, gy - 0.46f, 0xFF4A4266.toInt())
        g.fillRect(x + 0.31f, gy - 0.46f, x + 0.35f, gy, 0x40FF3D9A)
        g.fillCircle(x, gy - 0.25f, 0.17f, 0xFF15101F.toInt())
        g.save()
        g.translate(x, gy - 0.25f)
        g.rotate(f.t * 400f)
        g.line(-0.14f, 0f, 0.14f, 0f, 0.05f, 0xFF3A3456.toInt())
        g.line(0f, -0.14f, 0f, 0.14f, 0.05f, 0xFF3A3456.toInt())
        g.restore()
    }

    private val rows = arrayOf(
        "DRAG ← →" to "RUN",
        "TAP" to "SHOOT",
        "SWIPE ↑" to "JUMP",
        "SWIPE ↓" to "HIDE",
    )

    /** The tutorial, as a rooftop billboard + graffiti. */
    private fun billboard(pal: Palette, gy: Float) {
        val x0 = 2.9f
        val x1 = 8.05f
        val y0 = gy - 5.75f
        val y1 = gy - 2.25f
        val legC = 0xFF151022.toInt()
        // Scaffold legs.
        for (k in 0..1) {
            val lx = if (k == 0) x0 + 0.5f else x1 - 0.5f
            g.fillRect(lx - 0.07f, y1, lx + 0.07f, gy, legC)
            g.line(lx - 0.07f, y1 + 0.3f, lx + 0.5f * (if (lx < 5f) 1 else -1), gy - 0.1f, 0.04f, legC)
        }
        g.fillRect(x0 + 0.3f, y1 + 0.8f, x1 - 0.3f, y1 + 0.9f, legC)
        // Glow the board throws onto the night.
        g.fillRectRadial(x0 - 1f, y0 - 1f, x1 + 1f, y1 + 1.2f, (x0 + x1) / 2f, (y0 + y1) / 2f, 3.6f, 0x22FF2E88, 0x00FF2E88)
        // Board.
        g.fillRoundRect(x0 - 0.12f, y0 - 0.12f, x1 + 0.12f, y1 + 0.12f, 0.12f, 0xFF0B0716.toInt())
        g.fillVerticalGradient(x0, y0, x1, y1, 0xFF140C2A.toInt(), 0xFF0A0618.toInt())
        // Neon border (flickers once in a while).
        val flick = if (hash((f.t * 5f).toInt(), 7) > 0.97f) 0.4f else 1f
        g.strokeRoundRect(x0 + 0.06f, y0 + 0.06f, x1 - 0.06f, y1 - 0.06f, 0.1f, 0.14f, Col.fade(pal.neon, 0.18f * flick))
        g.strokeRoundRect(x0 + 0.06f, y0 + 0.06f, x1 - 0.06f, y1 - 0.06f, 0.1f, 0.035f, Col.fade(pal.neon, flick))
        // Header.
        f.worldText("'BOUT THAT ACTION", (x0 + x1) / 2f, y0 + 0.5f, 0.3f, pal.neon, Gfx.Font.TITLE)
        g.fillRect(x0 + 0.4f, y0 + 0.62f, x1 - 0.4f, y0 + 0.645f, Col.fade(pal.neon, 0.5f))
        // Rows: gesture (white) → action (cyan).
        val size = 0.36f
        val pitch = 0.62f
        val colX = x0 + 0.35f
        val actX = x1 - 0.35f
        for (k in rows.indices) {
            val row = rows[k]
            val y = y0 + 1.22f + k * pitch
            worldRich(row.first, colX, y, size, 0xFFF4ECFF.toInt(), Gfx.Align.LEFT)
            g.fillRect(colX + 2.1f, y - 0.11f, actX - 1.25f, y - 0.09f, 0x40FFFFFF)
            f.worldText(row.second, actX, y, size, Col.fade(pal.neon2, 0.2f), Gfx.Font.TITLE, Gfx.Align.RIGHT)
            f.worldText(row.second, actX, y, size, pal.neon2, Gfx.Font.TITLE, Gfx.Align.RIGHT)
        }
        // LED ticker bar under the board.
        val ty0 = y1 + 0.2f
        g.fillRoundRect(x0 + 0.1f, ty0, x1 - 0.1f, ty0 + 0.42f, 0.06f, 0xFF120308.toInt())
        g.strokeRoundRect(x0 + 0.1f, ty0, x1 - 0.1f, ty0 + 0.42f, 0.06f, 0.02f, 0x80FF3048.toInt())
        f.worldText("WALK INTO THEM = TAKEDOWN", (x0 + x1) / 2f, ty0 + 0.3f, 0.27f, 0xFFFF4A5E.toInt())
    }

    private fun worldRich(text: String, x: Float, y: Float, size: Float, color: Int, align: Gfx.Align) {
        g.save()
        g.translate(x, y)
        g.scale(1f / f.s, 1f / f.s)
        Glyphs.text(g, text, 0f, 0f, size * f.s, color, Gfx.Font.HUD, align)
        g.restore()
    }

    private fun hut(pal: Palette, gy: Float, right: Boolean) {
        val x0 = if (right) W - 1.75f else -0.25f
        val x1 = if (right) W + 0.25f else 1.75f
        val top = gy - 2.5f
        g.fillVerticalGradient(x0, top, x1, gy, 0xFF261E3C.toInt(), 0xFF1A1430.toInt())
        g.fillRect(x0 - 0.12f, top - 0.14f, x1 + 0.12f, top, 0xFF3A3056.toInt())
        g.fillRect(x0 - 0.12f, top - 0.14f, x1 + 0.12f, top - 0.125f, 0xFF8A7AB8.toInt())
        g.fillRect(x0 - 0.12f, top, x1 + 0.12f, top + 0.03f, Col.fade(pal.neon, 0.8f))
        // Brick courses.
        var y = top + 0.3f
        var r = 0
        while (y < gy) {
            g.fillRect(x0, y, x1, y + 0.02f, 0xFF15102A.toInt())
            var bx = x0 + if (r % 2 == 0) 0.2f else 0.45f
            while (bx < x1) {
                g.fillRect(bx, y - 0.28f, bx + 0.02f, y, 0xFF15102A.toInt())
                bx += 0.5f
            }
            y += 0.3f
            r++
        }
        // Doorway down.
        val dx = if (right) W - 0.8f else 0.8f
        g.fillRect(dx - 0.42f, gy - 2.0f, dx + 0.42f, gy, 0xFF050308.toInt())
        g.fillVerticalGradient(dx - 0.42f, gy - 2.0f, dx + 0.42f, gy, 0x10FFD0A0, 0x40FFD0A0)
        g.strokeRect(dx - 0.45f, gy - 2.03f, dx + 0.45f, gy, 0.06f, 0xFF4A3E6A.toInt())
        // Door lamp and its cone.
        f.glowDot(dx, gy - 2.18f, 0.06f, 0xFFFFD9A0.toInt(), 1f)
        poly.quad(g, dx - 0.08f, gy - 2.15f, dx + 0.08f, gy - 2.15f, dx + 0.7f, gy, dx - 0.7f, gy, 0x14FFD9A0)
        poly.quad(g, dx - 0.06f, gy - 2.15f, dx + 0.06f, gy - 2.15f, dx + 0.4f, gy, dx - 0.4f, gy, 0x10FFD9A0)
        // Graffiti: the grenade tip, spray-painted.
        val gx = if (right) x0 + 0.5f else x1 - 0.5f
        g.save()
        g.translate(gx, gy - 1.35f)
        g.rotate(-8f)
        g.scale(1f / f.s, 1f / f.s)
        val sz = 0.25f * f.s
        g.text("2×TAP", 1.5f, 1.5f, sz, 0x80000000.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text("2×TAP", 0f, 0f, sz, 0xFFB6FF3C.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text("GRENADE", 0f, sz * 1.1f, sz * 0.82f, 0xFFFFD23C.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text("i'm just 'bout that action, boss", 0f, sz * 2.4f, sz * 0.36f, 0x70E8E0FF, Gfx.Font.HUD, Gfx.Align.CENTER)
        g.restore()
        // Antenna with a blinking beacon.
        val ax = if (right) x1 - 0.35f else x0 + 0.35f
        g.line(ax, top - 0.14f, ax, top - 1.6f, 0.04f, 0xFF2A2240.toInt())
        g.line(ax - 0.2f, top - 1.0f, ax + 0.2f, top - 1.0f, 0.03f, 0xFF2A2240.toInt())
        if (sin(f.t * 4f) > 0f) f.glowDot(ax, top - 1.62f, 0.06f, 0xFFFF3040.toInt(), 1f)
    }

    // ------------------------------------------------------ lamps & darkness

    private fun brokenLamp(pal: Palette, lx: Float, rt: Float, gy: Float, i: Int) {
        // Snapped cable, sparking now and then; wreck and glass on the floor.
        g.line(lx, rt + 0.1f, lx + 0.05f, rt + 0.38f, 0.022f, 0xFF1A1A1A.toInt())
        if (hash((f.t * 10f).toInt(), i + 300) > 0.85f) {
            f.glowDot(lx + 0.05f, rt + 0.39f, 0.035f, 0xFFFFE080.toInt(), 1f)
            g.line(lx + 0.05f, rt + 0.39f, lx + 0.15f, rt + 0.55f, 0.012f, 0xFFFFF0B0.toInt())
        }
        g.save()
        g.translate(lx + 0.15f, gy - 0.07f)
        g.rotate(160f)
        poly.quad(g, -0.25f, -0.1f, 0.25f, -0.1f, 0.14f, 0.08f, -0.14f, 0.08f, Col.mul(pal.panel, 0.7f))
        g.restore()
        for (k in 0 until 5) g.fillRect(lx - 0.45f + k * 0.22f, gy - 0.035f, lx - 0.41f + k * 0.22f, gy, 0x80A0E0FF.toInt())
    }

    /** Shot-out floors go properly dark, except for a small glow around the player. */
    fun darkness(fs: FloorState) {
        val d = fs.darkness
        if (d <= 0.01f) return
        val fi = fs.plan.index
        val top = fi * H + SLAB
        val gy = Geo.groundY(fi)
        if (!f.visibleY(top, gy)) return
        val a = 0.93f * d
        val dark = 0xFF020106.toInt()
        val p = f.w.player
        val onFloor = p.floor == fi && p.state != PlayerState.INTEL && p.state != PlayerState.DEAD
        // Quantize so the cached gradient shaders don't churn as lights die.
        val aq = (a * 16f).toInt() / 16f
        if (onFloor) {
            g.fillRectRadial(
                -0.6f, top, W + 0.6f, gy + 0.05f, p.x, gy - p.z - 0.8f, 2.6f,
                Col.alpha(dark, aq * 0.08f), Col.alpha(dark, aq),
            )
        } else {
            g.fillRect(-0.6f, top, W + 0.6f, gy + 0.05f, Col.alpha(dark, a))
        }
    }

    fun lightsAndHazards(fs: FloorState) {
        val plan = fs.plan
        val fi = plan.index
        if (fi == 0) return
        val rt = fi * H + SLAB
        val gy = Geo.groundY(fi)
        if (!f.visibleY(rt, gy)) return
        val pal = f.palette(fs)
        // Flooded metro platforms: water over the actors' feet.
        if (!plan.isVoid && plan.zone == Zone.METRO) {
            rooms.scanFree(fs)
            if (rooms.flooded(fs, rooms.specialSpan(fs))) rooms.water(pal, fs, gy)
        }
        // Emergency lighting survives a blackout: the exit sign still glows.
        val d = fs.darkness
        if (d > 0.3f) {
            stairSign(plan.stairsDown, pal, rt, 0.75f * d)
            val x = if (plan.stairsDown == Side.LEFT) 0.72f else W - 0.72f
            g.fillRect(x - 0.7f, rt + 0.62f, x + 0.7f, gy, Col.alpha(0xFF3CFF7A.toInt(), 0.025f * d))
        }
        val lc = lampColor(pal, plan.zone, fi)
        val style = if (hash(fi, 703) < 0.5f) 0 else 1
        for (i in plan.lights.indices) {
            val lx = plan.lights[i]
            val fall = fs.lightFall[i]
            if (fs.lightAlive[i]) {
                lamp(pal, lc, plan.zone, style, lx, rt, gy, 0f, lampOn(fi, i, plan.zone))
            } else if (fall >= 0f) {
                val t = (fall / World.LIGHT_FALL_TIME).coerceIn(0f, 1f)
                val y = (gy - 0.25f - rt - 0.45f) * t * t
                g.line(lx, rt + 0.05f, lx, rt + 0.2f, 0.025f, 0xFF1A1A1A.toInt())
                lamp(pal, lc, plan.zone, style, lx, rt, gy, y, false, t * 50f)
            }
        }
        for (h in plan.hazards) hazardLive(h.kind, pal, h.x, rt, gy, h.state(f.wt), plan.zone)
    }

    /**
     * A ceiling fixture in the zone's style (two variants per zone, chosen per floor) and, when
     * lit, its volumetric cone: five nested sheets from a wide faint penumbra to a bright core.
     */
    private fun lamp(pal: Palette, c: Int, zone: Zone, style: Int, lx: Float, rt: Float, gy: Float, drop: Float, alive: Boolean, spin: Float = 0f) {
        val hang = if ((zone == Zone.MINES || zone == Zone.HELL) && style == 1) 0.62f else 0.4f
        val cy = rt + hang + drop
        val wide = (zone == Zone.TOWER && style == 1) || zone == Zone.METRO && style == 0 || zone == Zone.LABS && style == 0
        val top = if (wide) 0.34f else 0.14f
        if (drop == 0f) g.line(lx, rt + 0.14f, lx, cy - 0.1f, 0.018f, 0xFF15151C.toInt())
        if (alive) {
            val y0 = cy + 0.08f
            val coneBoost = 1f + 0.7f * depth(zone)
            g.blend(Gfx.Blend.ADD)
            for (k in 0 until 5) {
                val bw = CONE_W[k]
                val tw = top * (0.4f + 0.15f * (4 - k))
                poly.quad(g, lx - tw, y0, lx + tw, y0, lx + bw, gy, lx - bw, gy, Col.alpha(c, CONE_A[k] * coneBoost))
            }
            // Dust motes drifting through the beam.
            for (k in 0 until 4) {
                val ph = fract(f.t * (0.05f + hash(k, 231) * 0.05f) + hash(k + (lx * 10f).toInt(), 232))
                val my = cy + 0.3f + ph * (gy - cy - 0.5f)
                val mx = lx + (hash(k + (lx * 7f).toInt(), 233) - 0.5f) * 1.6f * ph + sin(f.t * 0.7f + k) * 0.08f
                g.fillCircle(mx, my, 0.014f, Col.alpha(c, 0.45f * (1f - ph)))
            }
            g.blend(Gfx.Blend.NORMAL)
        }
        val lit = if (alive) c else 0xFF3A3A40.toInt()
        val dark = 0xFF1C1A24.toInt()
        g.save()
        g.translate(lx, cy)
        if (spin != 0f) g.rotate(spin)
        when {
            zone == Zone.TOWER && style == 1 || zone == Zone.ROOFTOP && style == 1 -> {
                // Linear LED bar.
                g.fillRect(-0.42f, -0.04f, 0.42f, 0.04f, dark)
                g.fillRect(-0.42f, -0.04f, 0.42f, -0.03f, 0x40FFFFFF)
                g.fillRect(-0.4f, 0.03f, 0.4f, 0.06f, lit)
            }
            zone == Zone.LABS && style == 0 -> {
                g.fillRect(-0.34f, -0.08f, 0.34f, 0.06f, 0xFF1C2A2C.toInt())
                g.fillRect(-0.34f, -0.08f, 0.34f, -0.065f, 0x40FFFFFF)
                g.fillRect(-0.3f, 0.04f, 0.3f, 0.09f, lit)
            }
            zone == Zone.LABS -> {
                // Caged industrial dome.
                poly.quad(g, -0.08f, -0.1f, 0.08f, -0.1f, 0.2f, 0.07f, -0.2f, 0.07f, 0xFF1C2A2C.toInt())
                g.fillRect(-0.18f, 0.05f, 0.18f, 0.09f, lit)
                g.line(-0.14f, 0.09f, 0.14f, 0.09f, 0.015f, 0xFF1C2A2C.toInt())
                g.line(0f, 0.07f, 0f, 0.14f, 0.015f, 0xFF1C2A2C.toInt())
            }
            zone == Zone.METRO && style == 0 -> {
                g.fillRect(-0.4f, -0.06f, 0.4f, 0.02f, 0xFF22262A.toInt())
                g.fillRoundRect(-0.38f, 0.02f, 0.38f, 0.08f, 0.03f, lit)
                for (k in 0 until 5) g.fillRect(-0.3f + k * 0.15f, 0.0f, -0.29f + k * 0.15f, 0.1f, 0xFF22262A.toInt())
            }
            zone == Zone.METRO -> {
                // Sodium globe in a wire guard.
                g.fillRect(-0.06f, -0.12f, 0.06f, -0.04f, 0xFF22262A.toInt())
                g.fillCircle(0f, 0.04f, 0.1f, lit)
                g.strokeCircle(0f, 0.04f, 0.12f, 0.015f, 0xFF22262A.toInt())
            }
            zone == Zone.MINES && style == 0 -> {
                g.fillCircle(0f, 0.02f, 0.08f, lit)
                g.strokeCircle(0f, 0.02f, 0.11f, 0.018f, 0xFF2A2018.toInt())
                g.line(-0.11f, 0.02f, 0.11f, 0.02f, 0.015f, 0xFF2A2018.toInt())
                g.fillRect(-0.05f, -0.12f, 0.05f, -0.06f, 0xFF2A2018.toInt())
            }
            zone == Zone.MINES -> {
                // Hurricane lantern on a long wire.
                g.fillRect(-0.1f, -0.14f, 0.1f, -0.1f, 0xFF2A2018.toInt())
                g.fillRoundRect(-0.08f, -0.1f, 0.08f, 0.1f, 0.04f, Col.alpha(lit, 0.85f))
                g.strokeRoundRect(-0.08f, -0.1f, 0.08f, 0.1f, 0.04f, 0.018f, 0xFF2A2018.toInt())
                g.fillRect(-0.1f, 0.1f, 0.1f, 0.13f, 0xFF2A2018.toInt())
            }
            zone == Zone.MAGMA && style == 1 -> {
                // Caged bulkhead.
                g.fillRoundRect(-0.2f, -0.08f, 0.2f, 0.08f, 0.06f, 0xFF2A1A18.toInt())
                g.fillRoundRect(-0.15f, -0.04f, 0.15f, 0.06f, 0.04f, lit)
                for (k in 0 until 3) g.fillRect(-0.1f + k * 0.1f, -0.06f, -0.09f + k * 0.1f, 0.08f, 0xFF2A1A18.toInt())
            }
            zone == Zone.HELL && style == 0 -> {
                poly.quad(g, -0.22f, -0.05f, 0.22f, -0.05f, 0.12f, 0.1f, -0.12f, 0.1f, 0xFF2A1612.toInt())
                g.fillRect(-0.22f, -0.05f, 0.22f, -0.035f, 0x50FFB080)
                g.fillRect(-0.16f, 0.07f, 0.16f, 0.1f, lit)
                if (alive) {
                    val t = f.t * 10f + lx
                    poly.tri(g, -0.14f, -0.05f, 0.14f, -0.05f, sin(t) * 0.04f, -0.3f - sin(t * 1.3f) * 0.05f, 0xD0FF5A18.toInt())
                }
            }
            zone == Zone.HELL -> {
                // Candle chandelier: an iron ring of flames.
                g.fillRect(-0.34f, 0f, 0.34f, 0.04f, 0xFF2A1612.toInt())
                g.line(0f, 0f, -0.3f, -0.2f, 0.012f, 0xFF2A1612.toInt())
                g.line(0f, 0f, 0.3f, -0.2f, 0.012f, 0xFF2A1612.toInt())
                for (k in 0 until 5) {
                    val cx = -0.28f + k * 0.14f
                    g.fillRect(cx - 0.02f, -0.1f, cx + 0.02f, 0f, 0xFFD8C8A8.toInt())
                    if (alive) poly.tri(g, cx - 0.025f, -0.1f, cx + 0.025f, -0.1f, cx + sin(f.t * 11f + k) * 0.015f, -0.2f, 0xFFFFC040.toInt())
                }
            }
            else -> {
                poly.quad(g, -0.1f, -0.12f, 0.1f, -0.12f, 0.27f, 0.08f, -0.27f, 0.08f, 0xFF2A2834.toInt())
                poly.quad(g, -0.1f, -0.12f, 0.1f, -0.12f, 0.12f, -0.1f, -0.12f, -0.1f, 0x40FFFFFF)
                g.fillRect(-0.26f, 0.06f, 0.26f, 0.09f, lit)
            }
        }
        g.restore()
        if (alive) {
            g.blend(Gfx.Blend.ADD)
            g.glow(lx, cy + 0.08f, 0.55f, Col.alpha(c, 0.45f))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    private fun hazardBase(kind: HazardKind, pal: Palette, x: Float, rt: Float, gy: Float, st: Float) {
        when (kind) {
            HazardKind.LASER -> {
                val c = 0xFF2A2A34.toInt()
                g.fillRoundRect(x - 0.18f, rt + 0.1f, x + 0.18f, rt + 0.34f, 0.04f, c)
                g.fillRoundRect(x - 0.18f, gy - 0.22f, x + 0.18f, gy, 0.04f, c)
                g.fillRect(x - 0.18f, rt + 0.1f, x + 0.18f, rt + 0.12f, 0x40FFFFFF)
                g.fillRect(x - 0.18f, gy - 0.22f, x + 0.18f, gy - 0.2f, 0x40FFFFFF)
                g.fillRect(x - 0.06f, rt + 0.34f, x + 0.06f, rt + 0.4f, Col.fade(pal.laser, 0.4f + st * 0.6f))
                g.fillRect(x - 0.06f, gy - 0.28f, x + 0.06f, gy - 0.22f, Col.fade(pal.laser, 0.4f + st * 0.6f))
            }
            HazardKind.VENT -> {
                g.fillRect(x - 0.42f, gy - 0.1f, x + 0.42f, gy + 0.04f, 0xFF15131A.toInt())
                for (k in 0 until 6) g.fillRect(x - 0.36f + k * 0.13f, gy - 0.08f, x - 0.3f + k * 0.13f, gy, 0xFF3A3640.toInt())
                g.fillRect(x - 0.46f, gy - 0.14f, x + 0.46f, gy - 0.1f, 0xFFD8B01E.toInt())
            }
        }
    }

    private fun hazardLive(kind: HazardKind, pal: Palette, x: Float, rt: Float, gy: Float, st: Float, zone: Zone) {
        when (kind) {
            HazardKind.LASER -> {
                val y0 = rt + 0.4f
                val y1 = gy - 0.28f
                when {
                    st >= 1f -> {
                        val wob = 0.85f + 0.15f * sin(f.t * 40f)
                        g.fillRect(x - 0.3f, y0, x + 0.3f, y1, Col.alpha(pal.laser, 0.08f))
                        f.glowLine(x, y0, x, y1, 0.075f * wob, pal.laser, 0xFFFFFFFF.toInt())
                        f.glowDot(x, y0, 0.08f, pal.laser)
                        f.glowDot(x, y1, 0.08f, pal.laser)
                    }
                    st > 0f -> {
                        // Warm-up telegraph: a stuttering thin beam that thickens.
                        val on = sin(f.t * (30f + st * 40f)) > -0.2f + st * 0.5f
                        if (on) g.line(x, y0, x, y1, 0.015f + st * 0.02f, Col.alpha(pal.laser, 0.3f + 0.5f * st))
                        f.glowDot(x, y0, 0.05f + st * 0.04f, pal.laser, st)
                        f.glowDot(x, y1, 0.05f + st * 0.04f, pal.laser, st)
                    }
                    else -> {
                        var y = y0
                        while (y < y1) {
                            g.fillRect(x - 0.008f, y, x + 0.008f, y + 0.08f, Col.alpha(pal.laser, 0.18f))
                            y += 0.22f
                        }
                    }
                }
            }
            HazardKind.VENT -> {
                val steam = zone == Zone.METRO || zone == Zone.MINES || zone == Zone.LABS
                when {
                    st >= 1f -> {
                        val h = 1.05f + sin(f.t * 23f) * 0.08f
                        val c1 = pal.vent
                        val c2 = if (steam) 0xFFFFFFFF.toInt() else 0xFFFFF0A0.toInt()
                        g.fillRadialGradient(x, gy - 0.4f, 1.1f, Col.alpha(c1, 0.3f), Col.alpha(c1, 0f))
                        jet(x, gy, 0.5f, h, Col.alpha(c1, if (steam) 0.55f else 0.85f), 0f)
                        jet(x, gy, 0.3f, h * 0.8f, Col.alpha(c2, if (steam) 0.7f else 0.9f), 1.7f)
                        jet(x, gy, 0.14f, h * 0.55f, 0xEEFFFFFF.toInt(), 3.1f)
                        if (steam) {
                            for (k in 0 until 5) {
                                val ph = fract(f.t * 1.8f + k * 0.2f)
                                val px = x + sin(k * 2.3f + f.t * 3f) * 0.18f
                                g.fillCircle(px, gy - 0.3f - ph * 1.1f, 0.14f + ph * 0.22f, Col.alpha(0xFFFFFFFF.toInt(), 0.45f * (1f - ph)))
                            }
                        }
                    }
                    st > 0f -> {
                        g.fillRect(x - 0.36f, gy - 0.1f, x + 0.36f, gy - 0.02f, Col.alpha(pal.vent, 0.3f + 0.6f * st))
                        for (k in 0 until 3) {
                            val ph = fract(f.t * 2.5f + k * 0.33f)
                            g.fillCircle(x - 0.2f + k * 0.2f, gy - 0.1f - ph * 0.5f * st, 0.06f + ph * 0.1f, Col.alpha(pal.vent, (1f - ph) * 0.5f * st))
                        }
                    }
                }
            }
        }
    }

    private fun jet(x: Float, gy: Float, w: Float, h: Float, color: Int, seed: Float) {
        val p = poly.begin()
        p.add(x - w / 2f, gy - 0.05f)
        val n = 5
        for (k in 0..n) {
            val t = k / n.toFloat()
            val xx = x - w / 2f + w * t
            val tip = if (k % 2 == 0) h * (0.75f + 0.25f * sin(f.t * 30f + k * 1.3f + seed)) else h * 0.55f
            p.add(xx, gy - tip * (1f - abs(t - 0.5f) * 0.6f))
        }
        p.add(x + w / 2f, gy - 0.05f)
        p.fill(g, color)
    }
}
