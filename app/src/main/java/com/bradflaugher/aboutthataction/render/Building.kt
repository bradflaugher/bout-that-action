package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.DoorKind
import com.bradflaugher.aboutthataction.engine.HallState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.HazardKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.Shaft
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
    private val cutBuf = FloatArray(16)

    companion object {
        const val SLAB = 0.35f
        const val H = Geo.FLOOR_H
        const val W = Geo.FLOOR_W
        const val DOOR_H = 2.25f
        private const val HAIR = EnvWalls.HAIR
        /** The STASH: warm amber light and gold trim (a bonus, not a threat). */
        private const val STASH_GLOW = 0xFFFFA828.toInt()
        private const val STASH_GOLD = 0xFFFFD27A.toInt()
        /** Wayfinding green: passages to other hallways, and nothing else. */
        const val PASSAGE = 0xFF4CFFA8.toInt()
        /** The lift's cyan (HUD chip, map, plates). */
        const val LIFT_CYAN = 0xFF3CF4FF.toInt()
        private const val STEEL = 0xFF4A4858.toInt()
        private const val STEEL_HI = 0xFF9A98AC.toInt()
        private const val STEEL_LO = 0xFF1E1C26.toInt()
        /** Seconds for elevator doors to slide fully open or shut. */
        private const val DOOR_SLIDE = 0.3f
        /** Warm practical and cool fluorescent lamp tints for per-floor colour temperature. */
        private const val WARM = 0xFFFFC890.toInt()
        private const val COOL = 0xFFD8F0FF.toInt()
        /** Lamp cone: bottom half-widths and alphas, from faint penumbra to bright core. */
        private val CONE_W = floatArrayOf(1.9f, 1.4f, 1.0f, 0.66f, 0.36f)
        private val CONE_A = floatArrayOf(0.016f, 0.022f, 0.028f, 0.034f, 0.045f)
        /** Receding floors: a cool multiply grade, then a veil of deep blue-black. */
        private const val RECEDE_TINT = 0xFF5A6CA8.toInt()
        private const val RECEDE_INK = 0xFF03050C.toInt()
    }

    /** 1 on the player's floor, fading to 0 one floor away: the stage gets the brightest trims. */
    private fun stage(fi: Int): Float = f.clamp01(1f - abs(fi - f.w.player.floorF))

    // =================================================================== rooms

    fun room(fs: HallState, backdrop: Backdrop) {
        val plan = fs.plan
        val fi = plan.index
        val top = fi * H
        val gy = top + H
        val rt = top + SLAB
        if (!f.visibleY(top, gy + SLAB)) return
        val pal = f.palette(fs)
        val zone = plan.zone
        val sf = stage(fi)
        // Hallway A keeps the floor's own look; the other hallways get their own.
        val look = plan.look

        val free = rooms.scanFree(fs)
        val span = rooms.specialSpan(fs)
        g.save()
        g.clipRect(0f, rt, W, gy)
        if (plan.isVoid) {
            rooms.voidRoom(pal, look, rt, gy)
            walls.voidSign(pal, look, rt, gy)
        } else {
            g.fillVerticalGradient(0f, rt, W, gy, pal.wallTop, pal.wallBottom)
            walls.material(zone, pal, look, rt, gy)
            // Free slots become windows onto the backdrop, or set dressing; a run of them may
            // become this floor's special room instead.
            val s0 = if (span >= 0) span shr 8 else -1
            val s1 = if (span >= 0) span and 0xFF else -1
            val windowChance = 0.35f + hash(look, 709) * 0.35f
            for (i in Geo.SLOTS.indices) {
                if (!free[i] || i in s0..s1) continue
                val sx = Geo.SLOTS[i]
                val hv = hash(look * 13 + i, 5)
                if (hv < windowChance) walls.window(zone, pal, sx, rt, gy, false, backdrop, look * 7 + i)
                else walls.decor(zone, pal, sx, gy, rt, (hash(look * 29 + i, 6) * 4000).toInt())
            }
            if (span >= 0) rooms.special(zone, pal, fs, span, rt, gy, backdrop)
        }

        // Lamp light on the lower wall and floor (the darkness overlay swallows it when shot out).
        val deepZone = depth(zone)
        val lc = lampColor(pal, zone, look)
        for (i in plan.lights.indices) {
            val lx = plan.lights[i]
            if (fs.lightAlive[i]) {
                if (!lampOn(look, i, zone)) continue
                // Additive light: a broad wash on the wall, a pool where the cone lands.
                g.blend(Gfx.Blend.ADD)
                g.glow(lx, rt + 0.6f, 2.9f, Col.alpha(lc, 0.1f + 0.06f * deepZone))
                g.save()
                g.translate(lx, gy - 0.02f)
                g.scale(1f, 0.16f)
                g.glow(0f, 0f, 1.9f, Col.alpha(lc, 0.35f + 0.15f * deepZone))
                g.restore()
                g.blend(Gfx.Blend.NORMAL)
            } else if (fs.lightFall[i] < 0f && !blackout(fi)) {
                brokenLamp(pal, lx, rt, gy, i)
            }
        }

        // Deep zones: the room falls off into darkness toward its ends.
        if (deepZone > 0f) {
            g.fillRectRadial(0f, rt, W, gy, W / 2f, (rt + gy) / 2f, W * 0.56f, 0x00000000, Col.alpha(0xFF000000.toInt(), 0.5f * deepZone))
        }
        // Deeper floors: a dead fixture dangling from its wire between the working lamps.
        if (deepZone > 0f && hash(look, 714) < 0.45f) {
            val dx = 2.2f + hash(look, 715) * (W - 4.4f)
            var clear = true
            for (i in plan.lights.indices) if (abs(plan.lights[i] - dx) < 0.9f) clear = false
            if (clear) danglingLamp(dx, rt, look)
        }
        if (f.isStage(fi)) hallSign(fs, pal, rt, gy, 1f)
        ceiling(pal, rt, sf)
        baseboard(pal, gy, sf)
        if (plan.isVoid) walls.voidGlitch(pal, look, rt, gy)
        g.restore()

        for (s in plan.shafts) shaftOnFloor(fs, pal, s, top, rt, gy)
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

    /**
     * Wayfinding at the hallway's left end: the hallway's letter, big, over the floor number,
     * so the map on the HUD and the building agree. Emergency-lit, so it survives a blackout.
     */
    private fun hallSign(fs: HallState, pal: Palette, rt: Float, gy: Float, a: Float) {
        val plan = fs.plan
        if (plan.index == 0) return
        val x = 0.56f
        val y = rt + 0.64f
        val c = Col.lerp(pal.neon2, 0xFFFFFFFF.toInt(), 0.2f)
        g.fillRoundRect(x - 0.34f, y - 0.3f, x + 0.34f, y + 0.3f, 0.07f, Col.fade(0x22000000, a))
        g.fillRoundRect(x - 0.3f, y - 0.26f, x + 0.3f, y + 0.26f, 0.05f, Col.fade(0xF008060E.toInt(), a))
        g.strokeRoundRect(x - 0.3f, y - 0.26f, x + 0.3f, y + 0.26f, 0.05f, 0.018f, Col.fade(c, 0.55f * a))
        f.worldText(com.bradflaugher.aboutthataction.engine.Geo.hallName(plan.hall), x, y + 0.07f, 0.3f, Col.fade(c, a), Gfx.Font.TITLE)
        f.worldText(EnvLabels.short(plan.index), x, y + 0.21f, 0.09f, Col.fade(pal.trim, 0.75f * a))
    }

    // ------------------------------------------------------------------ doors

    private fun door(fs: HallState, pal: Palette, zone: Zone, i: Int, gy: Float) {
        val d = fs.plan.doors[i]
        val fi = fs.plan.index
        val x0 = d.x - Geo.DOOR_W / 2f
        val x1 = d.x + Geo.DOOR_W / 2f
        val y0 = gy - DOOR_H
        val stash = d.kind == DoorKind.STASH
        val used = stash && fs.stashUsed[i]
        val live = stash && !used
        val playerIn = f.playerHiddenInDoor(d.x) || (f.w.player.state == PlayerState.STASH && abs(f.w.player.anchorX - d.x) < 0.05f && f.w.player.floor == fi)
        val open = if (playerIn) 1f else fs.doorOpen[i]
        val pulse = 0.5f + 0.5f * sin(f.t * 3.2f)
        // Per-floor door family: tone, casing weight and signage vary floor to floor.
        val dv = (hash(fi, 706) * 3f).toInt()
        // Distant floors: silhouettes of doors, no small print.
        val far = f.lod(fi) > 0

        if (d.kind == DoorKind.PASSAGE) {
            passageDoor(fs, pal, i, gy, open)
            return
        }
        if (fs.plan.isVoid && !stash) {
            rooms.voidDoor(pal, x0, y0, x1, gy, open)
            statusLamp(pal, d.x, y0, open)
            return
        }

        // A live stash radiates warm gold: halo on the wall, a pool on the floor.
        if (live) {
            g.fillRadialGradient(d.x, y0 + 1.0f, 1.35f + 0.12f * pulse, 0x48FFA828, 0x00FFA828)
            g.save()
            g.translate(d.x, gy - 0.02f)
            g.scale(1f, 0.13f)
            g.fillRadialGradient(0f, 0f, 1.3f, 0x66FFA828, 0x00FFA828)
            g.restore()
        }

        // Casing: top-lit, shadowed on the right return. Normal doors sit close to the wall value.
        val frameC = when {
            live -> 0xFF3A2A12.toInt()
            used -> 0xFF26221E.toInt()
            else -> Col.lerp(pal.wallTop, pal.doorFrame, 0.28f + dv * 0.1f)
        }
        val hi = if (live) STASH_GOLD else pal.trim
        val cw = if (stash) 0.09f else 0.05f + dv * 0.02f
        g.fillRect(x0 - cw, y0 - cw - 0.01f, x1 + cw, gy, frameC)
        g.fillRect(x0 - cw, y0 - cw - 0.01f, x1 + cw, y0 - cw + 0.012f, Col.alpha(hi, if (used) 0.25f else if (stash) 0.65f else 0.35f))
        g.fillRect(x0 - cw, y0 - cw, x0 - cw + 0.02f, gy, Col.alpha(hi, if (used) 0.12f else if (stash) 0.3f else 0.14f))
        g.fillRect(x1 + cw - 0.03f, y0 - cw, x1 + cw, gy, Col.alpha(pal.deep, 0.6f))

        // Doorway interior, lit from within when open.
        g.fillRect(x0, y0, x1, gy, pal.deep)
        if (open > 0.02f) {
            val spill = if (stash) STASH_GLOW else pal.lamp
            g.fillVerticalGradient(x0, y0, x1, gy, Col.alpha(spill, 0.06f), Col.alpha(spill, 0.28f * open))
        }

        // Leaf, swinging on the left hinge.
        val lw = Geo.DOOR_W * (1f - 0.82f * open)
        val leaf = when {
            live -> 0xFF1C2238.toInt()
            used -> 0xFF1C1E24.toInt()
            else -> Col.lerp(pal.wallTop, pal.door, 0.45f + dv * 0.15f - 0.2f * depth(zone))
        }
        poly.quad(g, x0, y0, x0 + lw, y0 + 0.12f * open, x0 + lw, gy - 0.02f, x0, gy, leaf)
        if (lw > 0.3f && (stash || !far)) {
            g.save()
            g.translate(x0, y0)
            g.scale(lw / Geo.DOOR_W, 1f)
            if (stash) stashLeaf(live) else {
                leafDetail(zone, pal, leaf)
                // Mute the detail back toward the leaf: doors frame the scene, they don't lead it.
                g.fillRect(0f, 0.03f, Geo.DOOR_W, DOOR_H, Col.alpha(leaf, 0.4f + 0.2f * depth(zone)))
            }
            g.restore()
        }

        if (stash) {
            if (live) {
                // Light leaking round the leaf and a gold-cored neon outline.
                val core = 0xFFFFF4D8.toInt()
                f.glowLine(x0 - 0.045f, y0 - 0.055f, x1 + 0.045f, y0 - 0.055f, 0.035f, STASH_GLOW, core)
                f.glowLine(x0 - 0.045f, y0 - 0.055f, x0 - 0.045f, gy, 0.035f, STASH_GLOW, core)
                f.glowLine(x1 + 0.045f, y0 - 0.055f, x1 + 0.045f, gy, 0.035f, STASH_GLOW, core)
                g.fillRect(x0 + lw - 0.02f, y0 + 0.05f, x0 + lw, gy - 0.05f, Col.alpha(STASH_GOLD, 0.35f + 0.3f * pulse))
            }
            // A gold star on the leaf (or floating in the doorway when open).
            val cx = if (lw > 0.5f) x0 + lw / 2f else d.x
            stashStar(cx, y0 + 0.8f, 0.24f, if (used) 0xFF5A5448.toInt() else STASH_GOLD, live)
            // Nameplate.
            val label = if (used) "EMPTY" else "STASH"
            val py0 = y0 - 0.5f
            val py1 = y0 - 0.19f
            g.fillRoundRect(d.x - 0.44f, py0, d.x + 0.44f, py1, 0.05f, 0xF00E0C08.toInt())
            g.strokeRoundRect(d.x - 0.44f, py0, d.x + 0.44f, py1, 0.05f, 0.02f, if (used) 0xFF3E3A34.toInt() else Col.alpha(STASH_GOLD, 0.8f))
            if (live) f.worldText(label, d.x, py1 - 0.075f, 0.23f, Col.alpha(STASH_GLOW, 0.35f))
            f.worldText(label, d.x, py1 - 0.08f, 0.21f, if (used) 0xFF6E685C.toInt() else STASH_GLOW)
        } else {
            // Someone's room beyond: a line of light under some doors.
            if (far) return
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

    /**
     * A passage to another hallway on this floor. It must never read as a hiding doorway:
     * a heavy steel portal with a wayfinding-green neon outline, split sliding doors with a
     * lit corridor behind them, a plate naming the hallway it leads to (with a lift glyph if
     * that hallway has a ride down) and chevrons on the threshold.
     */
    private fun passageDoor(fs: HallState, pal: Palette, i: Int, gy: Float, open: Float) {
        val d = fs.plan.doors[i]
        val fi = fs.plan.index
        val x0 = d.x - 0.56f
        val x1 = d.x + 0.56f
        val y0 = gy - DOOR_H - 0.05f
        val c = PASSAGE
        val pulse = 0.5f + 0.5f * sin(f.t * 2.6f + d.x)
        if (!f.isStage(fi)) {
            quietPassage(pal, d.x, gy, f.lod(fi))
            return
        }
        val toLift = f.w.floors[fi]?.plan?.halls?.getOrNull(d.to)?.downLandings?.isNotEmpty() == true
        val visited = f.w.floors[fi]?.halls?.getOrNull(d.to)?.visited == true

        // Wayfinding light: a soft wash on the wall and a pool on the floor.
        g.blend(Gfx.Blend.ADD)
        g.glow(d.x, y0 + 0.9f, 1.5f, Col.alpha(c, 0.07f + 0.03f * pulse))
        g.save()
        g.translate(d.x, gy - 0.02f)
        g.scale(1f, 0.14f)
        g.glow(0f, 0f, 1.25f, Col.alpha(c, 0.22f + 0.1f * open))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)

        // Portal: a deep steel jamb, top-lit, shadowed on the right return.
        g.fillRect(x0 - 0.16f, y0 - 0.16f, x1 + 0.16f, gy, STEEL_LO)
        g.fillRect(x0 - 0.12f, y0 - 0.12f, x1 + 0.12f, gy, Col.lerp(STEEL, pal.doorFrame, 0.3f))
        g.fillRect(x0 - 0.12f, y0 - 0.12f, x1 + 0.12f, y0 - 0.1f, STEEL_HI)
        g.fillRect(x0 - 0.12f, y0 - 0.1f, x0 - 0.1f, gy, 0x50FFFFFF)
        g.fillRect(x1 + 0.09f, y0 - 0.1f, x1 + 0.12f, gy, STEEL_LO)

        // Beyond: a corridor running away from you, lit at the far end.
        g.fillRect(x0, y0, x1, gy, 0xFF040509.toInt())
        val vx0 = d.x - 0.2f
        val vx1 = d.x + 0.2f
        val vy0 = y0 + 0.62f
        val vy1 = gy - 0.62f
        g.fillVerticalGradient(vx0, vy0, vx1, vy1, Col.mul(pal.lamp, 0.55f), Col.mul(pal.lamp, 0.25f))
        val edge = Col.alpha(c, 0.35f)
        g.line(x0, y0, vx0, vy0, 0.018f, edge)
        g.line(x1, y0, vx1, vy0, 0.018f, edge)
        g.line(x0, gy, vx0, vy1, 0.018f, edge)
        g.line(x1, gy, vx1, vy1, 0.018f, edge)
        for (k in 1..3) {
            val t = k / 4f
            val lx0 = x0 + (vx0 - x0) * t
            val lx1 = x1 + (vx1 - x1) * t
            g.fillRect(lx0, y0 + (vy0 - y0) * t, lx1, y0 + (vy0 - y0) * t + 0.015f, Col.alpha(pal.lamp, 0.25f * t))
        }
        g.fillRect(vx0 + 0.05f, vy1 - 0.02f, vx1 - 0.05f, vy1, Col.alpha(c, 0.6f))

        // Split sliding doors: they part down the middle (a hiding door swings on a hinge).
        val half = (x1 - x0) / 2f
        val slide = half * 0.94f * open
        val panel = Col.lerp(0xFF1C2228.toInt(), pal.door, 0.25f)
        for (k in 0..1) {
            val a = if (k == 0) x0 else d.x + slide
            val b = if (k == 0) d.x - slide else x1
            if (b - a < 0.02f) continue
            g.fillRect(a, y0, b, gy, panel)
            g.fillRect(a, y0, b, y0 + 0.02f, Col.alpha(pal.trim, 0.3f))
            // A glowing window strip near the seam.
            val sx = if (k == 0) b - 0.16f else a + 0.1f
            if (sx > a + 0.02f && sx + 0.06f < b) {
                g.fillRect(sx, y0 + 0.3f, sx + 0.06f, gy - 0.6f, Col.alpha(c, 0.18f + 0.1f * pulse))
                g.fillRect(sx + 0.02f, y0 + 0.3f, sx + 0.04f, gy - 0.6f, Col.alpha(c, 0.55f))
            }
            g.fillRect(a, gy - 0.3f, b, gy - 0.26f, Col.alpha(0xFF000000.toInt(), 0.35f))
        }
        if (slide < 0.02f) g.fillRect(d.x - 0.008f, y0, d.x + 0.008f, gy, 0xFF0A0C10.toInt())

        // Neon outline: the one frame on the floor in this colour.
        val core = 0xFFE8FFF4.toInt()
        f.glowLine(x0 - 0.06f, y0 - 0.06f, x1 + 0.06f, y0 - 0.06f, 0.03f, Col.fade(c, 0.85f + 0.15f * pulse), core, 0.8f)
        f.glowLine(x0 - 0.06f, y0 - 0.06f, x0 - 0.06f, gy, 0.03f, Col.fade(c, 0.85f), core, 0.6f)
        f.glowLine(x1 + 0.06f, y0 - 0.06f, x1 + 0.06f, gy, 0.03f, Col.fade(c, 0.85f), core, 0.6f)

        // Plate: which way, which hallway, and a lift glyph if that's where the ride down is.
        val py0 = y0 - 0.64f
        val py1 = y0 - 0.2f
        val pw = if (toLift) 0.62f else 0.46f
        val px0 = d.x - pw
        val px1 = d.x + pw
        g.fillRoundRect(px0 - 0.03f, py0 + 0.04f, px1 + 0.03f, py1 + 0.06f, 0.07f, 0x55000000)
        g.fillRoundRect(px0, py0, px1, py1, 0.06f, 0xF2050C09.toInt())
        g.strokeRoundRect(px0, py0, px1, py1, 0.06f, 0.022f, Col.alpha(c, 0.9f))
        val cy = (py0 + py1) / 2f
        val dir = if (d.to > fs.plan.hall) 1f else -1f
        val ax = d.x - pw + 0.2f
        Glyphs.arrow(g, if (dir > 0) ax else px1 - 0.2f, cy, 0.1f, dir, 0f, 0.035f, c)
        val name = com.bradflaugher.aboutthataction.engine.Geo.hallName(d.to)
        val tx = if (toLift) d.x - 0.04f else d.x + 0.02f * dir
        f.worldText(name, tx, cy + 0.12f, 0.34f, Col.alpha(c, 0.35f), Gfx.Font.TITLE)
        f.worldText(name, tx, cy + 0.11f, 0.32f, if (visited) Col.lerp(c, 0xFFFFFFFF.toInt(), 0.2f) else 0xFFF2FFF8.toInt(), Gfx.Font.TITLE)
        if (toLift) HudIcons.elevator(g, if (dir > 0) px1 - 0.2f else px0 + 0.2f, cy, 0.3f, LIFT_CYAN)

        // Threshold chevrons, pointing in.
        for (k in 0..1) {
            val cx = d.x + (k - 0.5f) * 0.32f
            val a = 0.35f + 0.3f * sin(f.t * 5f - k * 1.4f)
            HudIcons.chevronDown(g, cx, gy - 0.07f, 0.07f, 0.022f, Col.alpha(c, a.coerceIn(0.1f, 0.7f)))
        }
    }

    /**
     * A passage off the stage: the same steel portal and split doors, shut, with one fine
     * green line round the frame. It still can't be mistaken for a hiding door, but it
     * doesn't call out: plates, arrows and light belong to the floor you're on.
     */
    private fun quietPassage(pal: Palette, x: Float, gy: Float, lod: Int) {
        val x0 = x - 0.56f
        val x1 = x + 0.56f
        val y0 = gy - DOOR_H - 0.05f
        g.fillRect(x0 - 0.12f, y0 - 0.12f, x1 + 0.12f, gy, Col.lerp(STEEL_LO, pal.doorFrame, 0.3f))
        g.fillRect(x0 - 0.12f, y0 - 0.12f, x1 + 0.12f, y0 - 0.1f, Col.alpha(STEEL_HI, 0.6f))
        g.fillRect(x0, y0, x1, gy, Col.lerp(0xFF161B20.toInt(), pal.door, 0.2f))
        g.fillRect(x - 0.008f, y0, x + 0.008f, gy, 0xFF07090C.toInt())
        if (lod == 0) {
            g.fillRect(x0, y0, x1, y0 + 0.02f, Col.alpha(pal.trim, 0.25f))
            g.fillRect(x - 0.1f, y0 + 0.3f, x - 0.07f, gy - 0.6f, Col.alpha(PASSAGE, 0.3f))
            g.fillRect(x + 0.07f, y0 + 0.3f, x + 0.1f, gy - 0.6f, Col.alpha(PASSAGE, 0.3f))
        }
        val c = Col.alpha(PASSAGE, if (lod == 0) 0.6f else 0.4f)
        val o = 0.06f
        val lw = 0.022f
        g.fillRect(x0 - o - lw / 2f, y0 - o - lw / 2f, x1 + o + lw / 2f, y0 - o + lw / 2f, c)
        g.fillRect(x0 - o - lw / 2f, y0 - o, x0 - o + lw / 2f, gy, c)
        g.fillRect(x1 + o - lw / 2f, y0 - o, x1 + o + lw / 2f, gy, c)
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

    /** The precious one: midnight lacquer, gold inlay. */
    private fun stashLeaf(live: Boolean) {
        val w = Geo.DOOR_W
        val h = DOOR_H
        val inlay = if (live) Col.alpha(STASH_GOLD, 0.85f) else 0x40908470
        g.fillVerticalGradient(0.08f, 0.08f, w - 0.08f, h - 0.08f, if (live) 0xFF2E3A60.toInt() else 0xFF22242C.toInt(), if (live) 0xFF161C34.toInt() else 0xFF141519.toInt())
        g.strokeRect(0.12f, 0.12f, w - 0.12f, h - 0.12f, 0.025f, inlay)
        g.strokeRect(0.2f, 1.25f, w - 0.2f, h - 0.22f, 0.018f, Col.fade(inlay, 0.7f))
        g.fillRect(0f, 0f, w, 0.02f, Col.fade(inlay, 0.9f))
        g.fillCircle(w - 0.14f, 1.2f, 0.05f, if (live) STASH_GOLD else 0xFF5A5448.toInt())
    }

    /** The stash's emblem: a five-point star, rocking gently and glowing while it's full. */
    private fun stashStar(cx: Float, cy: Float, r: Float, color: Int, live: Boolean) {
        if (live) {
            g.blend(Gfx.Blend.ADD)
            g.glow(cx, cy, r * 2.2f, Col.alpha(STASH_GLOW, 0.35f))
            g.blend(Gfx.Blend.NORMAL)
        }
        val spin = if (live) sin(f.t * 1.4f) * 0.25f else 0f
        val pb = poly.begin()
        for (k in 0 until 10) {
            val a = -kotlin.math.PI.toFloat() / 2f + k * kotlin.math.PI.toFloat() / 5f + spin
            val rr = if (k % 2 == 0) r else r * 0.44f
            pb.add(cx + kotlin.math.cos(a) * rr, cy + sin(a) * rr)
        }
        pb.fill(g, color)
    }


    // ----------------------------------------------------------------- shafts

    /**
     * One floor of a shaft column. A column only shows in the hallway it opens into (landing
     * portal, hall lantern, call button); everywhere else it runs behind the wall, unseen,
     * except the one the player rides, glazed so the ride reads all the way down.
     */
    private fun shaftOnFloor(fs: HallState, pal: Palette, s: Shaft, top: Float, rt: Float, gy: Float) {
        val landing = fs.plan.opens(s)
        val ridden = f.riding(s)
        if (!landing && !ridden) return
        val fi = fs.plan.index
        val sx = s.x
        val topFloor = s.top
        val bottomFloor = s.bottom
        val x0 = sx - Geo.SHAFT_W / 2f
        val x1 = sx + Geo.SHAFT_W / 2f
        val y0 = if (fi == topFloor) rt else top
        val y1 = if (fi < bottomFloor) gy + SLAB else gy
        // Shaft void, guide rails and a bolted divider beam at each floor line.
        if (landing) {
            g.fillVerticalGradient(x0, y0, x1, y1, 0xFF040308.toInt(), 0xFF0D0B14.toInt())
        } else {
            // Glazed: the room shows faintly through, edged in the lift's cyan.
            g.fillVerticalGradient(x0, y0, x1, y1, 0xB0040308.toInt(), 0xC80D0B14.toInt())
            g.fillRect(x0, rt, x0 + HAIR, gy, Col.alpha(LIFT_CYAN, 0.4f))
            g.fillRect(x1 - HAIR, rt, x1, gy, Col.alpha(LIFT_CYAN, 0.4f))
        }
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
        if (!landing) return
        // Landing portal on this floor: brushed steel, lit header.
        val fy = gy - 2.6f
        g.fillRect(x0 - 0.1f, fy - 0.12f, x1 + 0.1f, fy, STEEL)
        g.fillRect(x0 - 0.1f, fy, x0, gy, STEEL)
        g.fillRect(x1, fy, x1 + 0.1f, gy, STEEL)
        g.fillRect(x0 - 0.1f, fy - 0.12f, x1 + 0.1f, fy - 0.1f, STEEL_HI)
        g.fillRect(x0 - 0.1f, fy, x0 - 0.08f, gy, 0x50FFFFFF)
        g.fillRect(x1 + 0.07f, fy, x1 + 0.1f, gy, STEEL_LO)
        g.fillRect(x0, gy - 0.04f, x1, gy, 0xFF6A6878.toInt())
        // A ride down from here: a cyan call button, lit while the car is on its way.
        val car = f.w.elevators[s.id]
        val ride = fi < bottomFloor
        if (ride) {
            val bx = x1 + 0.28f
            val by = gy - 1.25f
            val calledHere = car != null && car.called == fi
            g.fillRoundRect(bx - 0.09f, by - 0.16f, bx + 0.09f, by + 0.16f, 0.03f, STEEL_LO)
            val bc = if (calledHere) LIFT_CYAN else Col.alpha(LIFT_CYAN, 0.35f)
            if (calledHere) f.glowDot(bx, by, 0.045f, LIFT_CYAN, 0.9f) else g.fillCircle(bx, by, 0.04f, bc)
        }
    }

    /**
     * How far a car's doors (and the landing doors in front of them) stand open at floor [fi],
     * 0..1, straight from the car's timing: they slide open as it stops and shut before it
     * leaves, so a waiting car never shows an empty shaft.
     */
    private fun doorOpen(car: com.bradflaugher.aboutthataction.engine.Elevator, fi: Int): Float {
        if (!car.doorsOpen || abs(car.pos - fi) > 0.01f) return 0f
        val opening = (car.openTime / DOOR_SLIDE).coerceIn(0f, 1f)
        // Carrying the player at the bottom it holds open until they step out.
        val closing = if (car.carrying && fi >= car.shaft.bottom) 1f else (car.pause / DOOR_SLIDE).coerceIn(0f, 1f)
        val t = min(opening, closing)
        return t * t * (3f - 2f * t)
    }

    /**
     * Floor [hs]'s landings, drawn over the cars: a steel surround that hides the shaft above
     * the doors, sliding landing doors that open only when the car stands here with its doors
     * open, the hall lantern and (for expresses) the gold band. Cars in transit stay hidden
     * behind them, as in a real lobby.
     */
    fun landingDoors(hs: HallState) {
        val plan = hs.plan
        val fi = plan.index
        val top = fi * H
        val gy = top + H
        val rt = top + SLAB
        if (!f.visibleY(top, gy + SLAB)) return
        val pal = f.palette(hs)
        for (s in plan.shafts) {
            if (!plan.opens(s)) continue
            val sx = s.x
            val x0 = sx - Geo.SHAFT_W / 2f
            val x1 = sx + Geo.SHAFT_W / 2f
            val fy = gy - 2.6f
            val car = f.w.elevators[s.id]
            val open = if (car != null && !f.riding(s)) doorOpen(car, fi) else 0f
            if (fi == 0) {
                // The rooftop lift house has its own brickwork, sign and button.
                if (fi < s.bottom) g.fillRect(x0, gy, x1, gy + SLAB, pal.slab)
                steelDoors(pal, x0, fy, x1, gy, open)
                continue
            }
            // Surround: ceiling to door header, so no ropes or counterweights show.
            g.fillVerticalGradient(x0, rt, x1, fy - 0.12f, 0xFF201E28.toInt(), 0xFF16141C.toInt())
            g.fillRect(x0, rt, x1, rt + 0.03f, 0x30FFFFFF)
            var px = x0 + 0.15f
            while (px < x1 - 0.1f) {
                g.fillRect(px, rt + 0.06f, px + HAIR, fy - 0.16f, 0x14FFFFFF)
                px += 0.3f
            }
            // The slabs above and below close over the shaft too.
            if (fi > s.top) g.fillRect(x0, top, x1, rt, pal.slab)
            if (fi < s.bottom) {
                g.fillRect(x0, gy, x1, gy + SLAB, pal.slab)
                g.fillRect(x0, gy, x1, gy + 0.03f, pal.slabEdge)
            }
            steelDoors(pal, x0, fy, x1, gy, open)
            if (fi < s.bottom) rideDownMarks(x0, fy, x1, gy, open) else lastStopMarks(x0, fy, x1, gy, open)
            landingFront(pal, s, fi, gy, car)
        }
    }

    /**
     * A ride down from here: cyan-lit jambs and threshold, and down chevrons on the shut
     * doors that chase toward the floor. Nothing else in the hallway glows like it.
     */
    private fun rideDownMarks(x0: Float, y0: Float, x1: Float, y1: Float, open: Float) {
        g.blend(Gfx.Blend.ADD)
        g.fillRect(x0 - 0.16f, y0, x0 - 0.02f, y1, Col.alpha(LIFT_CYAN, 0.14f))
        g.fillRect(x1 + 0.02f, y0, x1 + 0.16f, y1, Col.alpha(LIFT_CYAN, 0.14f))
        g.blend(Gfx.Blend.NORMAL)
        g.fillRect(x0 - 0.035f, y0, x0 - 0.005f, y1, Col.alpha(LIFT_CYAN, 0.85f))
        g.fillRect(x1 + 0.005f, y0, x1 + 0.035f, y1, Col.alpha(LIFT_CYAN, 0.85f))
        g.fillRect(x0, y1 - 0.05f, x1, y1 - 0.01f, Col.alpha(LIFT_CYAN, 0.7f))
        val a = 1f - (open / 0.35f).coerceIn(0f, 1f)
        if (a <= 0f) return
        val cx = (x0 + x1) / 2f
        val cy = y0 + 0.62f
        // Plate the chevrons sit on, across the seam of the doors.
        g.fillRoundRect(cx - 0.24f, cy - 0.2f, cx + 0.24f, cy + 0.56f, 0.06f, Col.alpha(0xFF06121A.toInt(), 0.85f * a))
        g.strokeRoundRect(cx - 0.24f, cy - 0.2f, cx + 0.24f, cy + 0.56f, 0.06f, 0.02f, Col.alpha(LIFT_CYAN, 0.5f * a))
        for (k in 0..2) {
            // Each chevron lights in turn, top to bottom: down, down, down.
            val beat = ((f.t * 1.6f - k * 0.33f) % 1f + 1f) % 1f
            val lit = 0.3f + 0.7f * (1f - beat) * (1f - beat)
            HudIcons.chevronDown(g, cx, cy + k * 0.18f, 0.13f, 0.035f, Col.alpha(LIFT_CYAN, lit * a))
        }
    }

    /**
     * The bottom of a shaft (where a ride down ended): plain steel with a road-style
     * DO NOT ENTER sign (red disc, white bar) across the seam, dimmed so it never outshines
     * the cyan rides down.
     */
    private fun lastStopMarks(x0: Float, y0: Float, x1: Float, y1: Float, open: Float) {
        val a = 1f - (open / 0.35f).coerceIn(0f, 1f)
        if (a <= 0f) return
        val cx = (x0 + x1) / 2f
        val cy = y0 + 0.8f
        val r = 0.27f
        g.fillCircle(cx, cy + 0.03f, r, Col.alpha(0xFF000000.toInt(), 0.4f * a))
        g.fillCircle(cx, cy, r, Col.alpha(0xFFF2F0F4.toInt(), 0.9f * a))
        g.fillCircle(cx, cy, r * 0.88f, Col.alpha(0xFFD8283A.toInt(), 0.9f * a))
        g.fillRoundRect(cx - r * 0.62f, cy - r * 0.16f, cx + r * 0.62f, cy + r * 0.16f, r * 0.04f, Col.alpha(0xFFF2F0F4.toInt(), 0.95f * a))
    }

    /** A pair of brushed-steel landing doors in [x0, x1] × [y0, y1], [open] 0..1. */
    private fun steelDoors(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float, open: Float) {
        val half = (x1 - x0) / 2f
        val leaf = half * (1f - open)
        if (leaf < 0.01f) return
        val mid = (x0 + x1) / 2f
        for (side in 0..1) {
            // Each leaf slides out from the middle into the frame.
            val a = if (side == 0) x0 else x1 - leaf
            val b = if (side == 0) x0 + leaf else x1
            g.fillVerticalGradient(a, y0, b, y1, 0xFF5E5C6E.toInt(), 0xFF34323F.toInt())
            // Brushed grain, a soft reflection of the hall's neon, and a kick plate.
            g.fillRect(a, y0, b, y0 + 0.04f, 0x30FFFFFF)
            g.fillVerticalGradient(a, y0 + 0.2f, b, y1 - 0.3f, Col.alpha(pal.neon, 0.07f), Col.alpha(pal.neon, 0f))
            g.fillRect(a, y1 - 0.34f, b, y1 - 0.3f, 0x40000000)
            g.fillRect(a, y1 - 0.3f, b, y1, 0xFF2A2834.toInt())
            val edge = if (side == 0) b else a
            g.fillRect(if (side == 0) edge - 0.025f else edge, y0, if (side == 0) edge else edge + 0.025f, y1, 0xFF2A2834.toInt())
        }
        // A diagonal sheen across both leaves while they're shut.
        if (open < 0.05f) {
            g.line(mid - half + 0.12f, y1 - 0.5f, mid - half + 0.5f, y0 + 0.3f, 0.05f, 0x18FFFFFF)
            g.line(mid + 0.18f, y1 - 0.4f, mid + 0.42f, y0 + 0.9f, 0.03f, 0x12FFFFFF)
        }
    }

    /** Express band, hall lantern (floor number and direction) and call button light over a landing. */
    private fun landingFront(pal: Palette, s: Shaft, fi: Int, gy: Float, car: com.bradflaugher.aboutthataction.engine.Elevator?) {
        val sx = s.x
        val x0 = sx - Geo.SHAFT_W / 2f
        val x1 = sx + Geo.SHAFT_W / 2f
        val fy = gy - 2.6f
        val ride = fi < s.bottom
        if (s.express) {
            // Express shafts wear a gold band on the header: 3–5 floors in one go.
            g.fillRect(x0 - 0.1f, fy - 0.12f, x1 + 0.1f, fy - 0.06f, 0xFFFFC23C.toInt())
            f.worldText("EXPRESS", sx, fy - 0.075f, 0.07f, 0xFF201404.toInt())
        }
        if (f.lod(fi) > 0) return
        // Hall lantern: floor number and direction.
        val here = car != null && car.doorsOpen && car.atFloor == fi
        val iy = fy - 0.36f
        // Plate sized for four characters ("B149") plus the direction arrow.
        g.fillRoundRect(sx - 0.42f, iy - 0.15f, sx + 0.42f, iy + 0.15f, 0.05f, 0xFF08070C.toInt())
        g.strokeRoundRect(sx - 0.42f, iy - 0.15f, sx + 0.42f, iy + 0.15f, 0.05f, HAIR, if (ride) Col.alpha(LIFT_CYAN, 0.6f) else 0xFF3A3846.toInt())
        if (car != null) {
            val num = car.pos.roundToInt()
            val c = if (here) pal.neon2 else Col.fade(pal.neon, 0.85f)
            f.worldText(EnvLabels.short(num), sx + 0.08f, iy + 0.07f, 0.17f, c)
            val dir = if (car.pos < fi - 0.05f) 1f else if (car.pos > fi + 0.05f) -1f else 0f
            if (dir != 0f) Glyphs.arrow(g, sx - 0.3f, iy, 0.07f, 0f, dir, 0.025f, c) else g.fillCircle(sx - 0.3f, iy, 0.04f, c)
            if (here) g.fillRoundRect(sx - 0.48f, iy - 0.21f, sx + 0.48f, iy + 0.21f, 0.08f, Col.alpha(pal.neon2, 0.15f))
        }
    }

    /**
     * The cars in floor [hs]'s band (its room and the slab under it), clipped to it, and only in
     * columns that open into this hallway: elsewhere the shaft runs behind the wall. Drawn inside
     * [Frame.views], so mid-slide a car travels with its hallway.
     */
    fun cars(hs: HallState, actors: Actors) {
        val fi = hs.plan.index
        // On the roof the car docks in the lift house: nothing of it shows above the door header.
        val bandTop = if (fi == 0) Geo.groundY(0) - 2.6f else fi * H + SLAB
        val bandBottom = (fi + 1) * H + SLAB
        if (!f.visibleY(bandTop, bandBottom)) return
        for (car in f.w.elevators.values) {
            val s = car.shaft
            if (f.riding(s) || !hs.plan.opens(s)) continue
            val yb = Geo.groundY(car.pos)
            val pulleyY = s.top * H + SLAB + 0.26f
            // Anything of this car (ropes, counterweight, body) in this band?
            if (pulleyY > bandBottom || yb + 0.2f < bandTop) continue
            g.save()
            g.clipRect(-0.6f, bandTop, W + 0.6f, bandBottom)
            drawCar(car, actors)
            g.restore()
        }
    }

    /** The car the player rides, whole: its column is glazed on every floor it passes. */
    fun riddenCar(actors: Actors) {
        val p = f.w.player
        if (p.state != PlayerState.ELEVATOR) return
        val car = f.w.elevators[p.elevatorShaft] ?: return
        drawCar(car, actors)
    }

    private fun drawCar(car: com.bradflaugher.aboutthataction.engine.Elevator, actors: Actors) {
        val w = f.w
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
        if (!f.visibleY(top - 4f, yb + 0.3f)) return
        val fs = w.floors[car.pos.roundToInt()] ?: w.floors[s.top]
        val pal = f.palette(fs)
        val open = doorOpen(car, car.pos.roundToInt())
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
        if (f.riding(s)) actors.player(force = true)
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

    // ------------------------------------------------------------------ slabs

    fun slab(fs: HallState) {
        val plan = fs.plan
        val fi = plan.index
        val gy = Geo.groundY(fi)
        if (!f.visibleY(gy - 1f, gy + SLAB + 0.2f)) return
        val pal = f.palette(fs)
        val below = f.w.floors[fi + 1]
        val palBelow = if (below != null) f.palette(below) else pal
        val sf = stage(fi)
        // Segments: [-0.6, W+0.6] minus the shafts that continue down (no stairwells: floors end in walls).
        var x = -0.6f
        val cuts = cutBuf
        var n = 0
        val here = f.w.floors[fi]?.hall(f.w.viewHall(fi))
        for (s in plan.shafts) if (fi < s.bottom && n < cuts.size - 1) {
            // Only a column that shows on this floor cuts the slab; one that only shows below
            // comes out of that floor's ceiling, from behind the wall.
            if (here == null || !f.shaftShows(here, s)) continue
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

    fun roof(fs: HallState) {
        val gy = Geo.groundY(0)
        val pal = Palette.of(Zone.ROOFTOP)
        val liftX = fs.plan.shafts.firstOrNull()?.x ?: (W - 2f)
        waterTower(0.75f, gy)
        billboard(pal, gy)
        acUnit(W - 0.95f, gy)
        penthouse(pal, gy, liftX, fs.plan.shafts.firstOrNull())
        rain(gy)
    }

    /** Rain slanting across the roof, with splashes on the deck. */
    private fun rain(gy: Float) {
        val top = max(f.camY, gy - 9f)
        val h = gy - top
        if (h <= 0f) return
        for (k in 0 until 60) {
            val x = hash(k, 221) * (W + 1.2f) - 0.6f
            val sp = 6f + hash(k, 222) * 3f
            val y = top + fract(hash(k, 223) + f.t * sp / h.coerceAtLeast(1f)) * h
            val len = 0.25f + hash(k, 224) * 0.2f
            g.line(x, y, x - len * 0.18f, y - len, 0.012f, if (k % 5 == 0) 0x50FFB0E0 else 0x38C0D8FF)
        }
        for (k in 0 until 10) {
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
        "SWIPE ↑" to "JUMP",
        "SWIPE ↓" to "HIDE",
        "TAP" to "DOORS & ELEVATORS",
        "GREEN BUTTON" to "GRENADE",
    )

    /** The tutorial, as a rooftop billboard: the gestures, the takedown ticker and the mode button. */
    private fun billboard(pal: Palette, gy: Float) {
        val x0 = 2.75f
        val x1 = 9.0f
        val y0 = gy - 6.55f
        val y1 = gy - 2.3f
        val legC = 0xFF151022.toInt()
        // Scaffold legs.
        for (k in 0..1) {
            val lx = if (k == 0) x0 + 0.6f else x1 - 0.6f
            g.fillRect(lx - 0.07f, y1, lx + 0.07f, gy, legC)
            g.line(lx - 0.07f, y1 + 0.3f, lx + 0.5f * (if (k == 0) 1 else -1), gy - 0.1f, 0.04f, legC)
        }
        g.fillRect(x0 + 0.3f, y1 + 0.8f, x1 - 0.3f, y1 + 0.9f, legC)
        // Glow the board throws onto the night.
        g.fillRectRadial(x0 - 1f, y0 - 1f, x1 + 1f, y1 + 1.2f, (x0 + x1) / 2f, (y0 + y1) / 2f, 4.2f, 0x22FF2E88, 0x00FF2E88)
        // Board.
        g.fillRoundRect(x0 - 0.12f, y0 - 0.12f, x1 + 0.12f, y1 + 0.12f, 0.12f, 0xFF0B0716.toInt())
        g.fillVerticalGradient(x0, y0, x1, y1, 0xFF140C2A.toInt(), 0xFF0A0618.toInt())
        // Neon border (flickers once in a while).
        val flick = if (hash((f.t * 5f).toInt(), 7) > 0.97f) 0.4f else 1f
        g.strokeRoundRect(x0 + 0.06f, y0 + 0.06f, x1 - 0.06f, y1 - 0.06f, 0.1f, 0.14f, Col.fade(pal.neon, 0.18f * flick))
        g.strokeRoundRect(x0 + 0.06f, y0 + 0.06f, x1 - 0.06f, y1 - 0.06f, 0.1f, 0.035f, Col.fade(pal.neon, flick))
        // Header.
        f.worldText("'BOUT THAT ACTION", (x0 + x1) / 2f, y0 + 0.5f, 0.32f, pal.neon, Gfx.Font.TITLE)
        g.fillRect(x0 + 0.4f, y0 + 0.64f, x1 - 0.4f, y0 + 0.665f, Col.fade(pal.neon, 0.5f))
        // Rows: gesture (white) → action (cyan), shrunk to fit if a row runs long.
        val size = 0.32f
        val pitch = 0.54f
        val colX = x0 + 0.35f
        val actX = x1 - 0.35f
        for (k in rows.indices) {
            val row = rows[k]
            val y = y0 + 1.2f + k * pitch
            worldRich(row.first, colX, y, size, 0xFFF4ECFF.toInt(), Gfx.Align.LEFT)
            val room = actX - colX - 1.75f
            val aw = g.textWidth(row.second, size * f.s, Gfx.Font.TITLE) / f.s
            val asz = if (aw > room) size * room / aw else size
            val lead = colX + Glyphs.width(g, row.first, size * f.s, Gfx.Font.HUD) / f.s + 0.18f
            val tail = actX - g.textWidth(row.second, asz * f.s, Gfx.Font.TITLE) / f.s - 0.18f
            if (tail > lead) g.fillRect(lead, y - 0.11f, tail, y - 0.09f, 0x40FFFFFF)
            f.worldText(row.second, actX, y, asz, Col.fade(pal.neon2, 0.2f), Gfx.Font.TITLE, Gfx.Align.RIGHT)
            f.worldText(row.second, actX, y, asz, pal.neon2, Gfx.Font.TITLE, Gfx.Align.RIGHT)
        }
        // The mode button, drawn like the HUD's, with what it does.
        val my = y1 - 0.42f
        val mx = colX + 0.2f
        g.fillCircle(mx, my, 0.2f, 0xFF0C0A14.toInt())
        g.strokeCircle(mx, my, 0.2f, 0.03f, 0xFFFF6A3A.toInt())
        g.fillCircle(mx, my, 0.07f, 0xFFFF6A3A.toInt())
        worldRich("TOP BUTTON:", mx + 0.34f, my + 0.1f, 0.24f, 0xFFD8D0EC.toInt(), Gfx.Align.LEFT)
        val ms = 0.24f
        val silentW = g.textWidth("SILENT", ms * f.s, Gfx.Font.TITLE) / f.s
        val slashX = actX - silentW - 0.2f
        f.worldText("SILENT", actX, my + 0.1f, ms, 0xFF9C8CFF.toInt(), Gfx.Font.TITLE, Gfx.Align.RIGHT)
        f.worldText("/", slashX, my + 0.1f, ms, 0x80FFFFFF.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        f.worldText("GUNS HOT", slashX - 0.2f, my + 0.1f, ms, 0xFFFF6A3A.toInt(), Gfx.Font.TITLE, Gfx.Align.RIGHT)
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

    /**
     * The elevator penthouse: the only way off the roof. A brick machine room over the shaft,
     * a steel landing portal the car docks in, a neon LIFT sign and a beacon.
     */
    private fun penthouse(pal: Palette, gy: Float, sx: Float, shaft: Shaft?) {
        val x0 = sx - 1.05f
        val x1 = sx + 1.05f
        val top = gy - 3.3f
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
        // The shaft opening the car docks in, with its steel portal.
        val sx0 = sx - Geo.SHAFT_W / 2f
        val sx1 = sx + Geo.SHAFT_W / 2f
        val fy = gy - 2.6f
        g.fillVerticalGradient(sx0, fy, sx1, gy + SLAB, 0xFF040308.toInt(), 0xFF0D0B14.toInt())
        g.fillRect(sx0 - 0.1f, fy - 0.12f, sx1 + 0.1f, fy, STEEL)
        g.fillRect(sx0 - 0.1f, fy, sx0, gy, STEEL)
        g.fillRect(sx1, fy, sx1 + 0.1f, gy, STEEL)
        g.fillRect(sx0 - 0.1f, fy - 0.12f, sx1 + 0.1f, fy - 0.1f, STEEL_HI)
        g.fillRect(sx1 + 0.07f, fy, sx1 + 0.1f, gy, STEEL_LO)
        // Lit LIFT sign over the portal.
        val ly = fy - 0.38f
        g.fillRoundRect(sx - 0.56f, ly - 0.2f, sx + 0.56f, ly + 0.2f, 0.06f, 0xF0060810.toInt())
        g.strokeRoundRect(sx - 0.56f, ly - 0.2f, sx + 0.56f, ly + 0.2f, 0.06f, 0.025f, LIFT_CYAN)
        HudIcons.elevator(g, sx - 0.36f, ly, 0.26f, LIFT_CYAN)
        f.worldText("LIFT", sx + 0.12f, ly + 0.08f, 0.2f, LIFT_CYAN, Gfx.Font.TITLE)
        g.blend(Gfx.Blend.ADD)
        g.glow(sx, ly, 1.1f, Col.alpha(LIFT_CYAN, 0.12f))
        g.blend(Gfx.Blend.NORMAL)
        // Call button.
        val car = if (shaft != null) f.w.elevators[shaft.id] else null
        val bx = sx1 + 0.3f
        g.fillRoundRect(bx - 0.09f, gy - 1.41f, bx + 0.09f, gy - 1.09f, 0.03f, STEEL_LO)
        if (car?.called == 0) f.glowDot(bx, gy - 1.25f, 0.045f, LIFT_CYAN, 0.9f) else g.fillCircle(bx, gy - 1.25f, 0.04f, Col.alpha(LIFT_CYAN, 0.4f))
        // Graffiti on the brick: the man himself.
        g.save()
        g.translate(x0 + 0.3f, top + 0.62f)
        g.rotate(-6f)
        g.scale(1f / f.s, 1f / f.s)
        g.text("'bout that action, boss", 0f, 0f, 0.11f * f.s, 0x90E8E0FF.toInt(), Gfx.Font.HUD, Gfx.Align.LEFT)
        g.restore()
        // Antenna with a blinking beacon.
        val ax = x1 - 0.3f
        g.line(ax, top - 0.14f, ax, top - 1.6f, 0.04f, 0xFF2A2240.toInt())
        g.line(ax - 0.2f, top - 1.0f, ax + 0.2f, top - 1.0f, 0.03f, 0xFF2A2240.toInt())
        if (sin(f.t * 4f) > 0f) f.glowDot(ax, top - 1.62f, 0.06f, 0xFFFF3040.toInt(), 1f)
    }

    // ------------------------------------------------------------- the stage

    /**
     * The player's floor is the stage: a soft pool of the zone's light where they stand and a
     * faint back-light on the wall, so the eye lands on them first. Environment light only,
     * under the actors.
     */
    fun stageLight() {
        val p = f.w.player
        if (p.state == PlayerState.DEAD || p.state == PlayerState.STASH) return
        val fi = p.floor
        val fs = f.w.floors[fi] ?: return
        val gy = Geo.groundY(p.floorF)
        if (!f.visibleY(gy - H, gy)) return
        val pal = f.palette(fs)
        val x = p.x + f.playerSlideDx()
        val lift = f.clamp01(1f - p.z / 2f)
        val c = Col.lerp(pal.neon2, 0xFFFFFFFF.toInt(), 0.35f)
        g.blend(Gfx.Blend.ADD)
        g.glow(x, gy - 1.1f, 2.1f, Col.alpha(c, 0.07f))
        g.save()
        g.translate(x, gy - 0.02f)
        g.scale(1f, 0.15f)
        g.glow(0f, 0f, 1.5f, Col.alpha(c, 0.3f * lift))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
    }

    /**
     * Floors away from the stage recede: a cool colour grade (warm neon cools and greys) and a
     * dark veil, both scaled by [Frame.recede]. Actors, lamps and all go under it together.
     */
    fun recede(fi: Int) {
        if (fi == 0) return
        val v = f.recede(fi)
        if (v <= 0.01f) return
        val fs = f.w.floors[fi] ?: return
        val top = fi * H + SLAB
        val bottom = (fi + 1) * H + SLAB
        if (!f.visibleY(top, bottom)) return
        // Quantized, so the cached fills don't churn during a ride.
        val q = (v * 32f).toInt() / 32f
        g.blend(Gfx.Blend.MULTIPLY)
        g.fillRect(-0.6f, top, W + 0.6f, bottom, Col.alpha(RECEDE_TINT, q))
        g.blend(Gfx.Blend.NORMAL)
        g.fillRect(-0.6f, top, W + 0.6f, bottom, Col.alpha(Col.lerp(f.palette(fs).skyTop, RECEDE_INK, 0.6f), q * 0.7f))
    }

    // ------------------------------------------------------ lamps & darkness

    private fun blackout(fi: Int) = f.w.floors[fi]?.plan?.event == com.bradflaugher.aboutthataction.engine.FloorEvent.BLACKOUT

    /**
     * BLACKOUT: the emergency circuit is all that's left. A red LED strip chasing along the
     * floor toward the rides down, green EXIT boxes by every lift, and a red beacon sweeping
     * the ceiling.
     */
    private fun emergency(fs: HallState, rt: Float, gy: Float) {
        val plan = fs.plan
        val red = 0xFFFF2A36.toInt()
        // Which way is out: toward the nearest ride down, else toward the nearest passage.
        // (Indexed loops over the plan's own lists: no per-frame allocation.)
        var target = -1f
        var rides = 0
        for (i in plan.landings.indices) {
            val s = plan.landings[i]
            if (plan.index >= s.bottom) continue
            rides++
            if (target < 0f || abs(s.x - W / 2f) < abs(target - W / 2f)) target = s.x
        }
        if (target < 0f) for (i in plan.doors.indices) {
            val d = plan.doors[i]
            if (d.kind == DoorKind.PASSAGE && (target < 0f || abs(d.x - W / 2f) < abs(target - W / 2f))) target = d.x
        }
        g.blend(Gfx.Blend.ADD)
        // The beacon: a slow red sweep across the ceiling and down the walls.
        val bx = W / 2f + sin(f.t * 1.3f) * W * 0.42f
        g.glow(bx, rt + 0.5f, 2.8f, Col.alpha(red, 0.2f))
        g.save()
        g.translate(bx, gy - 0.02f)
        g.scale(1f, 0.14f)
        g.glow(0f, 0f, 1.8f, Col.alpha(red, 0.3f))
        g.restore()
        var x = 0.25f
        while (x < W) {
            val toward = if (target < 0f) 1f else if (x < target) 1f else -1f
            val wave = sin(x * 2.2f - toward * f.t * 7f)
            val k = 0.25f + 0.75f * max(0f, wave) * max(0f, wave) * max(0f, wave)
            g.glow(x, gy - 0.06f, 0.28f, Col.alpha(red, 0.5f * k))
            x += 0.36f
        }
        g.blend(Gfx.Blend.NORMAL)
        x = 0.25f
        while (x < W) {
            val toward = if (target < 0f) 1f else if (x < target) 1f else -1f
            val wave = sin(x * 2.2f - toward * f.t * 7f)
            val k = 0.3f + 0.7f * max(0f, wave)
            g.fillRoundRect(x - 0.07f, gy - 0.09f, x + 0.07f, gy - 0.035f, 0.02f, Col.lerp(0xFF701420.toInt(), 0xFFFFD0D0.toInt(), k))
            x += 0.36f
        }
        // Beacon fixture.
        g.fillRect(W / 2f - 0.14f, rt + 0.02f, W / 2f + 0.14f, rt + 0.1f, 0xFF1A1A20.toInt())
        f.glowDot(W / 2f, rt + 0.14f, 0.06f, red, 0.7f + 0.3f * sin(f.t * 8f))
        // EXIT boxes beside each ride down.
        for (i in plan.landings.indices) {
            val s = plan.landings[i]
            if (plan.index >= s.bottom) continue
            val side = if (s.x < W / 2f) 1f else -1f
            exitSign(s.x + side * 0.95f, gy - 2.5f)
        }
        // No ride down in this hallway: the way out is through a passage.
        if (rides == 0) for (i in plan.doors.indices) {
            val d = plan.doors[i]
            if (d.kind != DoorKind.PASSAGE) continue
            val side = if (d.x < W / 2f) 1f else -1f
            exitSign(d.x + side * 0.95f, gy - 2.5f)
        }
    }

    private fun exitSign(x: Float, y: Float) {
        val green = 0xFF3CFF7A.toInt()
        g.blend(Gfx.Blend.ADD)
        g.glow(x, y, 0.75f, Col.alpha(green, 0.35f))
        g.blend(Gfx.Blend.NORMAL)
        g.fillRect(x - 0.4f, y - 0.14f, x + 0.4f, y + 0.14f, 0xFF06140A.toInt())
        g.strokeRect(x - 0.4f, y - 0.14f, x + 0.4f, y + 0.14f, 0.025f, green)
        f.worldText("EXIT", x + 0.1f, y + 0.065f, 0.18f, green, Gfx.Font.TITLE)
        // A tiny running figure.
        val rx = x - 0.28f
        g.fillCircle(rx + 0.02f, y - 0.07f, 0.025f, green)
        g.line(rx, y - 0.04f, rx - 0.02f, y + 0.03f, 0.025f, green)
        g.line(rx - 0.02f, y + 0.03f, rx + 0.03f, y + 0.09f, 0.022f, green)
        g.line(rx - 0.02f, y + 0.03f, rx - 0.06f, y + 0.09f, 0.022f, green)
        g.line(rx, y - 0.02f, rx + 0.05f, y + 0.01f, 0.02f, green)
    }

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
    fun darkness(fs: HallState) {
        val d = fs.darkness
        if (d <= 0.01f) return
        val fi = fs.plan.index
        val top = fi * H + SLAB
        val gy = Geo.groundY(fi)
        if (!f.visibleY(top, gy)) return
        val a = 0.93f * d
        val dark = 0xFF020106.toInt()
        val p = f.w.player
        val onFloor = f.playerIn(fs) && p.state != PlayerState.STASH && p.state != PlayerState.DEAD
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

    fun lightsAndHazards(fs: HallState) {
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
        // Emergency lighting survives a blackout: the hallway sign still glows.
        val d = fs.darkness
        if (d > 0.3f && f.isStage(fi)) hallSign(fs, pal, rt, gy, 0.75f * d)
        val look = plan.look
        val far = f.lod(fi) > 0
        val lc = lampColor(pal, plan.zone, look)
        val style = if (hash(look, 703) < 0.5f) 0 else 1
        for (i in plan.lights.indices) {
            val lx = plan.lights[i]
            val fall = fs.lightFall[i]
            if (fs.lightAlive[i]) {
                lamp(pal, lc, plan.zone, style, lx, rt, gy, 0f, lampOn(look, i, plan.zone), far = far)
            } else if (fall >= 0f) {
                val t = (fall / World.LIGHT_FALL_TIME).coerceIn(0f, 1f)
                val y = (gy - 0.25f - rt - 0.45f) * t * t
                g.line(lx, rt + 0.05f, lx, rt + 0.2f, 0.025f, 0xFF1A1A1A.toInt())
                lamp(pal, lc, plan.zone, style, lx, rt, gy, y, false, t * 50f)
            } else if (blackout(fi)) {
                // Power cut, not shot out: the fixtures hang there, dead.
                lamp(pal, lc, plan.zone, style, lx, rt, gy, 0f, false, far = far)
            }
        }
        if (blackout(fi)) emergency(fs, rt, gy)
        for (h in plan.hazards) hazardLive(h.kind, pal, h.x, rt, gy, h.state(f.wt), plan.zone)
    }

    /**
     * A ceiling fixture in the zone's style (two variants per zone, chosen per floor) and, when
     * lit, its volumetric cone: five nested sheets from a wide faint penumbra to a bright core.
     */
    private fun lamp(pal: Palette, c: Int, zone: Zone, style: Int, lx: Float, rt: Float, gy: Float, drop: Float, alive: Boolean, spin: Float = 0f, far: Boolean = false) {
        val hang = if ((zone == Zone.MINES || zone == Zone.HELL) && style == 1) 0.62f else 0.4f
        val cy = rt + hang + drop
        val wide = (zone == Zone.TOWER && style == 1) || zone == Zone.METRO && style == 0 || zone == Zone.LABS && style == 0
        val top = if (wide) 0.34f else 0.14f
        if (drop == 0f) g.line(lx, rt + 0.14f, lx, cy - 0.1f, 0.018f, 0xFF15151C.toInt())
        if (alive) {
            val y0 = cy + 0.08f
            val coneBoost = 1f + 0.7f * depth(zone)
            g.blend(Gfx.Blend.ADD)
            // Far floors: just the penumbra and the core, no motes.
            for (k in 0 until 5) {
                if (far && k != 0 && k != 3) continue
                val bw = CONE_W[k]
                val tw = top * (0.4f + 0.15f * (4 - k))
                poly.quad(g, lx - tw, y0, lx + tw, y0, lx + bw, gy, lx - bw, gy, Col.alpha(c, CONE_A[k] * coneBoost))
            }
            // Dust motes drifting through the beam.
            for (k in 0 until if (far) 0 else 4) {
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
