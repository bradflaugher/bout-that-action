package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wall materials, windows and set dressing for each zone's rooms.
 *
 * Art rules kept here: every vertical edge sits on the slot grid ([SEAMS] fall halfway between
 * door/window slots), the actor band (the lower ~2 units of a room) stays low-contrast, bright
 * detail lives up near the ceiling, and light always comes from the ceiling lamps above.
 */
internal class EnvWalls(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val verts = FloatArray(16)

    companion object {
        const val W = Geo.FLOOR_W
        /** Hairline, line and trim weights (world units) used everywhere for consistency. */
        const val HAIR = 0.018f
        const val LINE = 0.03f
        const val TRIM = 0.05f
        /** Wall seams: halfway between slots, so panels, posts and columns line up with doors. */
        val SEAMS = floatArrayOf(1.4f, 2.6f, 3.8f, 5.0f, 6.2f, 7.4f, 8.6f)
        /** Window glass extents relative to rt / gy. */
        const val WIN_TOP = 0.5f
        const val WIN_BOTTOM = 0.95f
        const val WIN_HALF = 0.5f
        private val STATION_X = floatArrayOf(3.8f, 6.2f)
        /** Seams that carry structure (timber posts, bone pilasters): both ends and the middle. */
        private val POSTS = floatArrayOf(SEAMS[0], SEAMS[3], SEAMS[6])
        /** Art colourways: dusk, teal, ember, bone. */
        private val ART_TOP = intArrayOf(0xFF9A2A6A.toInt(), 0xFF1E6A78.toInt(), 0xFFB0502A.toInt(), 0xFFD8CCB8.toInt())
        private val ART_BOT = intArrayOf(0xFF2A1A5A.toInt(), 0xFF0E2238.toInt(), 0xFF3A0E1A.toInt(), 0xFF8A7A68.toInt())
        private val BOOKS = intArrayOf(0xFF6A2A3A.toInt(), 0xFF2A4A6A.toInt(), 0xFF8A6A3A.toInt(), 0xFF3A5A3A.toInt(), 0xFF5A3A6A.toInt(), 0xFFB0A080.toInt())
        private val TAGS = arrayOf("BEAST", "SKITTLES", "RUN IT", "YEAH", "#24")
        private val TOWER_TINTS = intArrayOf(0, 0x1CFF3D9A, 0x1C2C8CFF, 0x162CF0C8, 0x18FFA040)
        private val LAB_TINTS = intArrayOf(0, 0x14FFFFFF, 0x142C8CFF, 0x10A0FF40)
        private val MURALS = arrayOf("BEAST MODE", "SKITTLES", "'BOUT THAT", "RUN IT BACK")
        private val ARRIVALS = arrayOf("NEXT  2 MIN", "DELAYED", "NO SERVICE", "EXPRESS  B-99")
    }

    // ================================================================ materials

    /** The wall surface for [zone]; drawn inside the room clip, over the base gradient. */
    fun material(zone: Zone, pal: Palette, fi: Int, rt: Float, gy: Float) {
        val mv = variant(fi)
        // Department colour: each upper floor leans a little toward its own hue.
        val tint = when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> TOWER_TINTS[(hash(fi, 713) * TOWER_TINTS.size).toInt()]
            Zone.LABS -> LAB_TINTS[(hash(fi, 713) * LAB_TINTS.size).toInt()]
            else -> 0
        }
        if (tint != 0) g.fillRect(0f, rt, W, gy, tint)
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> when (mv) {
                0 -> tower(pal, rt, gy)
                1 -> marble(pal, fi, rt, gy)
                else -> slats(pal, rt, gy)
            }
            Zone.LABS -> when (mv) {
                0 -> labs(pal, rt, gy)
                1 -> { labs(pal, rt, gy); cleanRoom(pal, rt, gy) }
                else -> { labs(pal, rt, gy); quarantine(pal, rt, gy) }
            }
            Zone.METRO -> when (mv) {
                0 -> metro(pal, fi, rt, gy)
                1 -> brick(pal, fi, rt, gy)
                else -> mural(pal, fi, rt, gy)
            }
            Zone.MINES -> {
                if (mv == 2) coal(pal, fi, rt, gy)
                mines(pal, fi, rt, gy)
                if (mv == 1) crystals(pal, fi, rt, gy)
            }
            Zone.MAGMA -> when (mv) {
                0 -> magma(pal, fi, rt, gy)
                1 -> { foundry(pal, rt, gy); heatShimmer(pal, fi, rt, gy) }
                else -> { obsidian(pal, fi, rt, gy); heatShimmer(pal, fi, rt, gy) }
            }
            Zone.HELL -> when (mv) {
                0 -> hell(pal, fi, rt, gy)
                1 -> flesh(pal, fi, rt, gy)
                else -> { hell(pal, fi, rt, gy); ossuary(pal, rt, gy) }
            }
            Zone.VOID -> voidWall(pal, rt, gy)
        }
    }

    private fun seams(pal: Palette, rt: Float, gy: Float, dark: Int, light: Int) {
        for (x in SEAMS) {
            g.fillRect(x - HAIR, rt, x, gy, dark)
            g.fillRect(x, rt, x + HAIR * 0.6f, gy, light)
        }
    }

    private fun tower(pal: Palette, rt: Float, gy: Float) {
        // Lacquered wall panels on the slot grid, a darker wainscot and a chair rail.
        seams(pal, rt, gy, Col.alpha(pal.deep, 0.55f), Col.alpha(pal.trim, 0.14f))
        val wy = gy - 0.9f
        g.fillRect(0f, wy, W, gy, Col.alpha(pal.deep, 0.28f))
        g.fillRect(0f, wy - 0.035f, W, wy, Col.alpha(pal.trim, 0.32f))
        g.fillRect(0f, wy, W, wy + 0.02f, Col.alpha(pal.deep, 0.6f))
        // Wainscot panel mouldings between seams.
        var i = 0
        while (i < SEAMS.size - 1) {
            val x0 = SEAMS[i] + 0.12f
            val x1 = SEAMS[i + 1] - 0.12f
            g.strokeRect(x0, wy + 0.16f, x1, gy - 0.26f, HAIR, Col.alpha(pal.trim, 0.08f))
            i++
        }
    }

    private fun labs(pal: Palette, rt: Float, gy: Float) {
        // Clean-room cladding: a panel grid with lit top bevels.
        val h1 = rt + 1.02f
        val h2 = gy - 0.95f
        seams(pal, rt + 0.55f, gy - 0.3f, Col.alpha(pal.deep, 0.7f), Col.alpha(pal.trim, 0.12f))
        for (k in 0..1) {
            val y = if (k == 0) h1 else h2
            g.fillRect(0f, y - HAIR, W, y, Col.alpha(pal.deep, 0.7f))
            g.fillRect(0f, y, W, y + HAIR * 0.7f, Col.alpha(pal.trim, 0.16f))
        }
        // Service pipes under the ceiling, with brackets on the seams.
        val p1 = rt + 0.3f
        val p2 = rt + 0.44f
        g.fillRect(0f, p1 - 0.05f, W, p1 + 0.05f, Col.mul(pal.panel, 0.8f))
        g.fillRect(0f, p1 - 0.05f, W, p1 - 0.03f, Col.alpha(pal.trim, 0.35f))
        g.fillRect(0f, p2 - 0.035f, W, p2 + 0.035f, Col.mul(pal.panel, 0.65f))
        g.fillRect(0f, p2 - 0.035f, W, p2 - 0.02f, Col.alpha(pal.neon, 0.25f))
        for (x in SEAMS) g.fillRect(x - 0.04f, p1 - 0.09f, x + 0.04f, p2 + 0.06f, Col.mul(pal.panel, 0.55f))
        // Hazard band along the baseboard: muted so it frames rather than shouts.
        val b0 = gy - 0.3f
        val b1 = gy - 0.14f
        g.save()
        g.clipRect(0f, b0, W, b1)
        g.fillRect(0f, b0, W, b1, 0xFF15150E.toInt())
        var sx = -0.4f
        while (sx < W) {
            poly.quad(g, sx, b1, sx + 0.16f, b1, sx + 0.32f, b0, sx + 0.16f, b0, 0xFFB08E1C.toInt())
            sx += 0.32f
        }
        g.restore()
        g.fillRect(0f, b0 - HAIR, W, b0, Col.alpha(pal.trim, 0.3f))
    }

    private fun metro(pal: Palette, fi: Int, rt: Float, gy: Float) {
        // Subway tile field above, grimy dado below, the line band running above the doors.
        val dado = gy - 1.0f
        g.fillRect(0f, rt, W, dado, 0x0CFFFFFF)
        val grout = Col.alpha(pal.deep, 0.35f)
        var y = rt + 0.2f
        while (y < dado) {
            g.fillRect(0f, y, W, y + 0.012f, grout)
            y += 0.18f
        }
        var x = 0.2f
        while (x < W) {
            g.fillRect(x, rt, x + 0.012f, dado, grout)
            x += 0.4f
        }
        // Dado: big dark tiles, a lit cap, grime toward the floor.
        g.fillRect(0f, dado, W, gy, Col.alpha(pal.deep, 0.45f))
        g.fillRect(0f, dado - 0.05f, W, dado, Col.alpha(pal.trim, 0.35f))
        x = 0.6f
        while (x < W) {
            g.fillRect(x, dado, x + 0.014f, gy, Col.alpha(pal.deep, 0.5f))
            x += 1.2f
        }
        g.fillVerticalGradient(0f, gy - 0.5f, W, gy, 0x00000000, 0x40000000)
        // The line band with the station name.
        val by = rt + 0.22f
        g.fillRect(0f, by, W, by + 0.2f, Col.mul(pal.neon2, 0.72f))
        g.fillRect(0f, by, W, by + 0.025f, Col.lerp(pal.neon2, 0xFFFFFFFF.toInt(), 0.35f))
        g.fillRect(0f, by + 0.2f, W, by + 0.235f, Col.mul(pal.neon2, 0.3f))
        for (sx in STATION_X) {
            g.fillRect(sx - 0.44f, by - 0.03f, sx + 0.44f, by + 0.23f, 0xFF101214.toInt())
            f.worldText(EnvLabels.short(fi), sx, by + 0.17f, 0.18f, 0xFFF2EEE4.toInt())
        }
    }

    private fun mines(pal: Palette, fi: Int, rt: Float, gy: Float) {
        // Negative space first: a drift tunnel receding into the dark, a lamp far down it.
        val tx = 2.2f + hash(fi, 29) * 5.6f
        val tw = 0.8f + hash(fi, 30) * 0.45f
        poly.begin()
        poly.add(tx - tw, gy)
        for (k in 0..8) {
            val a = 3.1416f + k / 8f * 3.1416f
            val rr = 1f + (hash(k + fi * 5, 38) - 0.5f) * 0.18f
            poly.add(tx + cos(a) * tw * rr, gy - 0.4f + sin(a) * 1.55f * rr)
        }
        poly.add(tx + tw, gy)
        poly.fill(g, Col.alpha(pal.deep, 0.8f))
        g.fillRect(tx - tw * 0.55f, gy - 1.35f, tx + tw * 0.55f, gy, Col.alpha(0xFF000000.toInt(), 0.45f))
        g.fillRect(tx - tw * 0.3f, gy - 0.95f, tx + tw * 0.3f, gy, Col.alpha(0xFF000000.toInt(), 0.5f))
        val far = 0.6f + 0.4f * sin(f.t * 2.3f + fi)
        g.fillCircle(tx + 0.05f, gy - 0.72f, 0.14f, Col.alpha(pal.lamp, 0.12f * far))
        g.fillCircle(tx + 0.05f, gy - 0.72f, 0.03f, Col.alpha(pal.lamp, 0.8f * far))
        // Rock face: faceted boulders, top-lit, with shadowed undersides.
        for (k in 0 until 9) {
            val cx = hash(fi * 17 + k, 21) * W
            val cy = rt + 0.3f + hash(fi * 17 + k, 22) * (gy - rt - 0.6f)
            val r = 0.35f + hash(k + fi, 23) * 0.55f
            val c = if (k % 3 == 0) Col.alpha(pal.trim, 0.1f) else Col.alpha(pal.deep, 0.3f)
            poly.begin()
            // Vertices from angle pi (left) round through the top to the right and back.
            for (j in 0 until 7) {
                val a = 3.1416f + j / 7f * 6.283f
                val rr = r * (0.7f + 0.3f * hash(j + k * 7 + fi, 24))
                val vx = cx + cos(a) * rr * 1.5f
                val vy = cy + sin(a) * rr
                poly.add(vx, vy)
                verts[j * 2] = vx
                verts[j * 2 + 1] = vy
            }
            poly.fill(g, c)
            for (j in 0 until 6) {
                val upper = j < 3
                g.line(verts[j * 2], verts[j * 2 + 1], verts[j * 2 + 2], verts[j * 2 + 3], HAIR, if (upper) Col.alpha(pal.trim, 0.3f) else Col.alpha(pal.deep, 0.55f))
            }
        }
        // Two ore veins, cyan and gold, glowing with crystal clusters at the nodes.
        for (v in 0..1) {
            val pulse = 0.7f + 0.3f * sin(f.t * 1.6f + fi + v * 2f)
            var vx = hash(fi + v * 31, 25) * 4f + v * 4.5f
            var vy = rt + 0.4f + hash(fi + v * 31, 26) * 1.2f
            val vc = if ((fi + v) % 2 == 0) 0xFF5AD8FF.toInt() else 0xFFFFC84A.toInt()
            for (s in 0 until 7) {
                val nx = vx + 0.35f + hash(s + fi * 11 + v * 5, 27) * 0.5f
                val ny = vy + (hash(s + fi * 11 + v * 5, 28) - 0.5f) * 0.45f
                g.line(vx, vy, nx, ny, 0.16f, Col.alpha(vc, 0.07f * pulse))
                g.line(vx, vy, nx, ny, 0.028f, Col.alpha(vc, 0.7f * pulse))
                if (s % 3 == 1) {
                    poly.tri(g, nx - 0.06f, ny + 0.03f, nx + 0.02f, ny - 0.14f, nx + 0.07f, ny + 0.03f, Col.alpha(vc, 0.85f))
                    poly.tri(g, nx + 0.03f, ny + 0.03f, nx + 0.1f, ny - 0.08f, nx + 0.13f, ny + 0.03f, Col.alpha(vc, 0.6f))
                    g.fillCircle(nx, ny, 0.3f, Col.alpha(vc, 0.07f * pulse))
                    val tw = hash((f.t * 3f).toInt() + s + v * 7, fi)
                    if (tw > 0.7f) g.fillCircle(nx + 0.02f, ny - 0.1f, 0.025f, 0xFFFFFFFF.toInt())
                }
                vx = nx
                vy = ny
            }
        }
        // Timber sets: posts on the structural seams, a cap beam, knee braces.
        val wood = 0xFF3E2716.toInt()
        val woodHi = 0xFF7A5230.toInt()
        val capY = rt + 0.16f
        for (x in POSTS) {
            g.fillRect(x - 0.1f, capY, x + 0.1f, gy, wood)
            g.fillRect(x - 0.1f, capY, x - 0.065f, gy, woodHi)
            g.fillRect(x + 0.07f, capY, x + 0.1f, gy, 0xFF24160C.toInt())
            poly.quad(g, x - 0.1f, capY + 0.55f, x - 0.1f, capY + 0.7f, x - 0.62f, capY + 0.16f, x - 0.47f, capY + 0.16f, wood)
            poly.quad(g, x + 0.1f, capY + 0.55f, x + 0.1f, capY + 0.7f, x + 0.62f, capY + 0.16f, x + 0.47f, capY + 0.16f, wood)
        }
        g.fillRect(0f, rt, W, capY + 0.16f, wood)
        g.fillRect(0f, capY + 0.13f, W, capY + 0.16f, 0xFF1C1109.toInt())
        g.fillRect(0f, rt, W, rt + 0.03f, woodHi)
        // Power cable swagging between the posts.
        var px = 0f
        for (x in POSTS) {
            val mid = (px + x) / 2f
            g.line(px, capY + 0.34f, mid, capY + 0.5f, 0.016f, 0xFF120C08.toInt())
            g.line(mid, capY + 0.5f, x, capY + 0.34f, 0.016f, 0xFF120C08.toInt())
            px = x
        }
        // Dust hanging in the air.
        for (k in 0 until 10) {
            val dx = fract(hash(k + fi * 3, 39) + f.t * 0.01f * (1f + hash(k, 40))) * W
            val dy = rt + 0.3f + fract(hash(k + fi * 3, 41) + sin(f.t * 0.3f + k) * 0.02f) * (gy - rt - 0.5f)
            g.fillCircle(dx, dy, 0.012f, Col.alpha(pal.haze, 0.35f))
        }
        // Mine rails set into the floor.
        g.fillRect(0f, gy - 0.07f, W, gy - 0.045f, 0xFF6A6058.toInt())
        g.fillRect(0f, gy - 0.07f, W, gy - 0.062f, 0xFFB0A090.toInt())
    }

    private fun magma(pal: Palette, fi: Int, rt: Float, gy: Float) {
        // Columnar basalt: narrow facets, cool-lit on the left edge, staggered joints.
        val stone = 0xFFC8B8D0.toInt()
        var x = 0f
        var i = 0
        while (x < W && i < 40) {
            val w = 0.26f + hash(i + fi * 31, 33) * 0.22f
            val tone = hash(i + fi * 31, 34)
            if (tone > 0.5f) g.fillRect(x, rt, x + w, gy, Col.alpha(stone, 0.03f + (tone - 0.5f) * 0.06f))
            else if (tone < 0.25f) g.fillRect(x, rt, x + w, gy, Col.alpha(pal.deep, 0.35f))
            g.fillRect(x, rt, x + HAIR, gy, Col.alpha(pal.deep, 0.9f))
            g.fillRect(x + HAIR, rt, x + HAIR * 1.6f, gy, Col.alpha(stone, 0.08f))
            for (j in 0 until 2) {
                val fy = rt + 0.3f + hash(i * 3 + j + fi * 31, 35) * (gy - rt - 0.6f)
                g.line(x, fy, x + w, fy - 0.06f + j * 0.12f, HAIR, Col.alpha(pal.deep, 0.9f))
                g.line(x, fy + 0.02f, x + w, fy - 0.04f + j * 0.12f, HAIR * 0.6f, Col.alpha(stone, 0.07f))
            }
            x += w
            i++
        }
        // Glowing fissures climbing from the floor, pulsing with the heat.
        for (k in 0 until 2) {
            var cx = 1.2f + hash(fi * 5 + k, 31) * 7.6f
            var cy = gy
            val pulse = 0.7f + 0.3f * sin(f.t * 2f + k * 2.1f + fi)
            val c = Col.fade(pal.glow, pulse)
            for (s in 0 until 7) {
                val nx = cx + (hash(s + k * 9 + fi, 32) - 0.5f) * 0.45f
                val ny = cy - 0.36f
                g.line(cx, cy, nx, ny, 0.22f, Col.fade(pal.glow, 0.08f * pulse))
                g.line(cx, cy, nx, ny, 0.055f, c)
                g.line(cx, cy, nx, ny, 0.018f, Col.fade(pal.neon2, pulse))
                if (s == 2) g.line(nx, ny, nx + 0.3f, ny - 0.2f, 0.025f, c)
                cx = nx
                cy = ny
            }
        }
        // Heat from the floor.
        g.fillVerticalGradient(0f, gy - 1.1f, W, gy, Col.alpha(pal.glow, 0f), Col.alpha(pal.glow, 0.2f))
        heatShimmer(pal, fi, rt, gy)
    }

    /** Faint wavy lines drifting up the wall: the air itself cooking. */
    private fun heatShimmer(pal: Palette, fi: Int, rt: Float, gy: Float) {
        val c = Col.alpha(pal.haze, 0.07f)
        for (k in 0 until 4) {
            val ph = fract(f.t * 0.35f + hash(k + fi * 3, 36))
            val yy = gy - 0.2f - ph * (gy - rt - 0.4f)
            val x0 = hash(k + fi * 3, 37) * 7f
            var px = x0
            var py = yy
            for (s in 1..8) {
                val nx = x0 + s * 0.35f
                val ny = yy + sin(s * 1.3f + f.t * 4f + k) * 0.05f
                g.line(px, py, nx, ny, 0.025f, Col.fade(c, 1f - ph))
                px = nx
                py = ny
            }
        }
    }

    private fun hell(pal: Palette, fi: Int, rt: Float, gy: Float) {
        // Ashlar blocks, bevelled; vertebra pilasters on three seams; blood from the ceiling.
        val mortar = Col.alpha(pal.deep, 0.55f)
        val hi = Col.alpha(pal.neon, 0.07f)
        var y = rt + 0.6f
        var row = 0
        while (y < gy + 0.6f) {
            g.fillRect(0f, y - 0.04f, W, y, mortar)
            g.fillRect(0f, y, W, y + 0.015f, hi)
            var x = if (row % 2 == 0) 0f else 0.6f
            while (x < W) {
                g.fillRect(x, y - 0.6f, x + 0.04f, y - 0.04f, mortar)
                x += 1.2f
            }
            y += 0.6f
            row++
        }
        val bone = pal.trim
        for (x in POSTS) {
            var vy = rt + 0.2f
            while (vy < gy - 0.1f) {
                g.fillRoundRect(x - 0.09f, vy, x + 0.09f, vy + 0.15f, 0.05f, Col.alpha(bone, 0.3f))
                g.fillRect(x - 0.13f, vy + 0.06f, x + 0.13f, vy + 0.09f, Col.alpha(bone, 0.18f))
                vy += 0.21f
            }
        }
        for (k in 0 until 4) {
            val dx = hash(fi * 3 + k, 41) * W
            val len = 0.15f + hash(fi * 3 + k, 42) * 0.55f
            g.fillRoundRect(dx - 0.025f, rt, dx + 0.025f, rt + 0.15f + len, 0.025f, 0xFF5A000C.toInt())
            g.fillCircle(dx, rt + 0.15f + len, 0.04f, 0xFF6A0010.toInt())
        }
        // Hellfire underglow.
        g.fillVerticalGradient(0f, gy - 1.2f, W, gy, Col.alpha(pal.glow, 0f), Col.alpha(pal.glow, 0.22f))
    }

    private fun voidWall(pal: Palette, rt: Float, gy: Float) {
        seams(pal, rt, gy, Col.alpha(pal.neon, 0.12f), 0)
    }

    /** Datamosh over a Void room: stuttering slices, colour-split edges and dead blocks. */
    fun voidGlitch(pal: Palette, fi: Int, rt: Float, gy: Float) {
        // Scanlines: the room is a broadcast that's losing signal.
        var y = rt
        val scan = Col.alpha(pal.deep, 0.14f)
        while (y < gy) {
            g.fillRect(0f, y, W, y + 0.025f, scan)
            y += 0.1f
        }
        val tick = (f.t * 7f).toInt()
        for (i in 0 until 4) {
            val hv = hash(tick * 5 + i + fi * 17, 151)
            if (hv > 0.55f) continue
            val yy = rt + hash(tick * 5 + i + fi * 17, 152) * (gy - rt)
            val h = 0.04f + hash(tick + i + fi, 153) * 0.26f
            val off = (hash(tick * 3 + i, 154) - 0.5f) * 0.8f
            g.fillRect(0f, yy, W, yy + h, Col.alpha(pal.deep, 0.55f))
            g.fillRect(off, yy, W + off, yy + h * 0.35f, Col.alpha(pal.neon, 0.35f))
            g.fillRect(-off, yy + h * 0.65f, W - off, yy + h, Col.alpha(pal.neon2, 0.35f))
        }
        // Datamosh: a smear of dead macroblocks.
        if (hash(tick + fi * 7, 155) < 0.45f) {
            val bx = hash(tick + fi, 156) * (W - 1.6f)
            val by = rt + hash(tick + fi, 157) * (gy - rt - 0.8f)
            for (k in 0 until 8) {
                val cx = bx + (k % 4) * 0.4f
                val cy = by + (k / 4) * 0.4f
                val hv = hash(k + tick * 11, 158)
                if (hv < 0.25f) continue
                val c = if (hv > 0.8f) pal.neon2 else if (hv > 0.55f) pal.neon else pal.deep
                g.fillRect(cx, cy, cx + 0.4f, cy + 0.4f, Col.alpha(c, if (c == pal.deep) 0.6f else 0.2f))
            }
        }
        // A vertical tear that walks across the room.
        val tx = fract(f.t * 0.13f + fi * 0.37f) * W
        g.fillRect(tx, rt, tx + 0.02f, gy, Col.alpha(pal.neon2, 0.25f))
        g.fillRect(tx + 0.05f, rt, tx + 0.06f, gy, Col.alpha(pal.neon, 0.2f))
    }

    // ================================================================ windows

    /** A window in slot [sx] onto the zone's backdrop, glass tinted down so actors read in front. */
    fun window(zone: Zone, pal: Palette, sx: Float, rt: Float, gy: Float, isVoid: Boolean, backdrop: Backdrop, salt: Int) {
        // Per-floor proportions: some floors have tall glazing, some a lower ribbon.
        val tall = hash(salt / 7, 52) < 0.35f
        val y0 = rt + if (tall) 0.38f else WIN_TOP
        val mull = if (zone == Zone.TOWER || zone == Zone.ROOFTOP || zone == Zone.LABS) 0.5f else 0f
        windowSpan(zone, pal, sx - WIN_HALF, sx + WIN_HALF, y0, gy - WIN_BOTTOM, isVoid, backdrop, salt, mull)
    }

    /** Glazing from [x0] to [x1]: backdrop, depth tint, reflections, frame; mullions every [mullion] units (0 = none). */
    fun windowSpan(zone: Zone, pal: Palette, x0: Float, x1: Float, y0: Float, y1: Float, isVoid: Boolean, backdrop: Backdrop, salt: Int, mullion: Float) {
        // Reveal: the wall's thickness, lit from above.
        g.fillRect(x0 - 0.06f, y0 - 0.06f, x1 + 0.06f, y1 + 0.02f, pal.deep)
        g.save()
        g.clipRect(x0, y0, x1, y1)
        backdrop.zone(zone, pal, x0, y0, x1, y1, isVoid)
        // Glass: depth tint that pushes the view back, a top-down reflection and streaks.
        g.fillVerticalGradient(x0, y0, x1, y1, Col.alpha(pal.deep, 0.1f), Col.alpha(pal.deep, 0.45f))
        var rx = x0
        while (rx < x1) {
            poly.quad(g, rx + 0.05f, y1, rx + 0.3f, y1, rx + 1f, y0 + 0.2f, rx + 1f, y0 - 0.05f, 0x0FFFFFFF)
            poly.quad(g, rx + 0.38f, y1, rx + 0.45f, y1, rx + 1f, y0 + 0.62f, rx + 1f, y0 + 0.5f, 0x0AFFFFFF)
            rx += 1.6f
        }
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> {
                // Blinds drawn to a per-floor height, rain beading on the glass.
                val drop = if (hash(salt, 53) < 0.3f) 0f else 0.15f + hash(salt, 51) * 0.55f
                var y = y0
                val slat = Col.mul(pal.panel, 1.15f)
                while (y < y0 + drop) {
                    g.fillRect(x0, y, x1, y + 0.055f, slat)
                    g.fillRect(x0, y + 0.055f, x1, y + 0.07f, Col.alpha(pal.deep, 0.6f))
                    y += 0.085f
                }
                rainOnGlass(x0, y0 + drop, x1, y1, salt)
            }
            Zone.LABS -> {
                // Observation glass: a faint reticle etched on the pane.
                g.fillRect(x0, (y0 + y1) / 2f, x1, (y0 + y1) / 2f + HAIR, Col.alpha(pal.neon, 0.18f))
            }
            else -> Unit
        }
        g.restore()
        // Frame: mullions, sill, lit top edge.
        val frame = Col.mul(pal.panel, 1.3f)
        g.strokeRect(x0, y0, x1, y1, 0.05f, frame)
        g.fillRect(x0 - 0.025f, y0 - 0.025f, x1 + 0.025f, y0 - 0.01f, Col.alpha(pal.trim, 0.45f))
        if (mullion > 0f) {
            var mx = x0 + mullion
            while (mx < x1 - 0.1f) {
                g.fillRect(mx - 0.015f, y0, mx + 0.015f, y1, frame)
                mx += mullion
            }
        }
        g.fillRect(x0 - 0.1f, y1, x1 + 0.1f, y1 + 0.07f, Col.mul(pal.panel, 1.5f))
        g.fillRect(x0 - 0.1f, y1, x1 + 0.1f, y1 + 0.018f, Col.alpha(pal.trim, 0.6f))
        g.fillRect(x0 - 0.1f, y1 + 0.07f, x1 + 0.1f, y1 + 0.1f, Col.alpha(pal.deep, 0.5f))
    }

    private fun rainOnGlass(x0: Float, y0: Float, x1: Float, y1: Float, salt: Int) {
        if (y1 <= y0) return
        for (k in 0 until 6) {
            val dx = x0 + 0.06f + hash(k + salt * 7, 57) * (x1 - x0 - 0.12f)
            val sp = 0.25f + hash(k + salt * 7, 58) * 0.35f
            val dy = y0 + fract(hash(k + salt * 7, 59) + f.t * sp) * (y1 - y0)
            g.line(dx, dy - 0.14f, dx, dy, 0.012f, 0x30C8E8FF)
            g.fillCircle(dx, dy, 0.018f, 0x55D8F0FF)
        }
    }

    // ================================================================= decor

    /**
     * Set dressing for a free slot. [v] is a per-floor, per-slot hash, so each floor draws its
     * own mix from an eight-way pool, nudged off-centre so nothing sits on the same spot twice.
     */
    fun decor(zone: Zone, pal: Palette, sx0: Float, gy: Float, rt: Float, v: Int) {
        val kind = v % 8
        val sx = sx0 + ((v / 8) % 5 - 2) * 0.05f
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> when (kind) {
                0 -> { plant(pal, sx - 0.2f, gy); art(pal, sx + 0.1f, rt + 0.72f, v) }
                1 -> { cooler(pal, sx, gy); neonSign(pal, sx, rt + 0.62f, "24/7") }
                2 -> { desk(pal, sx, gy); neonSign(pal, sx, rt + 0.62f, if (v % 3 == 0) "NEXUS" else "SYNC") }
                3 -> { art(pal, sx, rt + 0.72f, v); plant(pal, sx + 0.3f, gy) }
                4 -> { bookshelf(pal, sx, gy, v) }
                5 -> { floorLamp(pal, sx + 0.25f, gy); lounge(pal, sx - 0.1f, gy) }
                6 -> { whiteboard(pal, sx, rt + 0.65f, v); cabinet(pal, sx + 0.25f, gy) }
                else -> { art(pal, sx - 0.2f, rt + 0.72f, v + 1); art(pal, sx + 0.25f, rt + 0.9f, v + 2); desk(pal, sx, gy) }
            }
            Zone.LABS -> when (kind) {
                0 -> { serverRack(pal, sx, gy, v); warnSign(sx, rt + 0.62f) }
                1 -> { labBench(pal, sx, gy); monitor(pal, sx, rt + 0.55f) }
                2 -> tank(pal, sx, gy, v)
                3 -> { serverRack(pal, sx, gy, v); monitor(pal, sx, rt + 0.55f) }
                4 -> { fumeHood(pal, sx, gy) }
                5 -> { cryo(pal, sx, gy, v) }
                6 -> { robotArm(pal, sx, gy); warnSign(sx + 0.25f, rt + 0.62f) }
                else -> { bioBin(pal, sx + 0.25f, gy); monitor(pal, sx, rt + 0.55f); labBench(pal, sx - 0.1f, gy) }
            }
            Zone.METRO -> when (kind) {
                0 -> vending(pal, sx, gy)
                1 -> { bench(pal, sx, gy); metroMap(pal, sx, rt + 1.35f) }
                2 -> { adBox(pal, sx, rt + 1.1f, v); bin(pal, sx + 0.32f, gy) }
                3 -> { adBox(pal, sx, rt + 1.1f, v + 1); bench(pal, sx, gy) }
                4 -> { turnstile(pal, sx, gy) }
                5 -> { graffiti(pal, sx, gy, v); bin(pal, sx - 0.3f, gy) }
                6 -> { arrivals(pal, sx, rt + 0.62f); bench(pal, sx, gy) }
                else -> { payphone(pal, sx - 0.15f, gy); graffiti(pal, sx + 0.1f, gy, v + 3) }
            }
            Zone.MINES -> when (kind) {
                0 -> { crates(pal, sx, gy); lantern(pal, sx + 0.25f, rt + 0.9f) }
                1 -> { drill(pal, sx, gy); lantern(pal, sx - 0.2f, rt + 0.9f) }
                2 -> cart(pal, sx, gy)
                3 -> { tnt(pal, sx, gy); lantern(pal, sx, rt + 0.9f) }
                4 -> { orePile(pal, sx, gy, v) }
                5 -> { tools(pal, sx, gy); lantern(pal, sx + 0.3f, rt + 0.9f) }
                6 -> { ladder(pal, sx, rt, gy) }
                else -> { plunger(pal, sx, gy); crates(pal, sx + 0.1f, gy) }
            }
            Zone.MAGMA -> when (kind) {
                0 -> { coolant(pal, sx - 0.18f, gy); gauge(pal, sx + 0.25f, rt + 0.95f) }
                1 -> { crucible(pal, sx, gy); warnSign(sx, rt + 0.62f) }
                2 -> coolant(pal, sx, gy)
                3 -> { gauge(pal, sx, rt + 0.95f); crucible(pal, sx, gy) }
                4 -> { heatPipes(pal, sx, rt, gy) }
                5 -> { obsidian(pal, sx, gy, v) }
                6 -> { fan(pal, sx, rt + 1.0f); coolant(pal, sx + 0.2f, gy) }
                else -> { canisters(pal, sx, gy); gauge(pal, sx - 0.2f, rt + 0.95f) }
            }
            Zone.HELL -> when (kind) {
                0 -> { candles(sx, gy); rune(pal, sx, rt + 1.1f) }
                1 -> { sconce(pal, sx, rt + 1.25f); skulls(pal, sx, gy) }
                2 -> { chains(pal, sx, rt); skulls(pal, sx, gy) }
                3 -> { sconce(pal, sx, rt + 1.25f); candles(sx, gy) }
                4 -> { cage(pal, sx, rt) }
                5 -> { altar(pal, sx, gy); rune(pal, sx, rt + 1.0f) }
                6 -> { bones(pal, sx, gy, v); sconce(pal, sx + 0.3f, rt + 1.25f) }
                else -> { maiden(pal, sx, gy) }
            }
            Zone.VOID -> neonSign(pal, sx, rt + 1.1f, "NULL")
        }
    }

    // ------------------------------------------------------------------ tower

    private fun plant(pal: Palette, x: Float, gy: Float) {
        poly.quad(g, x - 0.18f, gy - 0.4f, x + 0.18f, gy - 0.4f, x + 0.13f, gy, x - 0.13f, gy, 0xFF1C1428.toInt())
        g.fillRect(x - 0.2f, gy - 0.44f, x + 0.2f, gy - 0.39f, Col.alpha(pal.trim, 0.5f))
        for (k in 0 until 7) {
            g.save()
            g.translate(x, gy - 0.42f)
            g.rotate(-60f + k * 20f + sin(f.t * 1.3f + k) * 2.5f)
            poly.quad(g, 0f, 0f, 0.07f, -0.3f, 0f, -0.66f - (k % 2) * 0.14f, -0.07f, -0.3f, if (k % 2 == 0) 0xFF1B6E4C.toInt() else 0xFF11523A.toInt())
            g.restore()
        }
    }

    private fun cooler(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.18f, gy - 0.95f, x + 0.18f, gy, 0.04f, 0xFFB8B8CC.toInt())
        g.fillRect(x - 0.18f, gy - 0.95f, x - 0.12f, gy, 0x30FFFFFF)
        g.fillRect(x - 0.18f, gy - 0.58f, x + 0.18f, gy - 0.55f, 0xFF7C7C94.toInt())
        g.fillRoundRect(x - 0.14f, gy - 1.42f, x + 0.14f, gy - 0.95f, 0.1f, 0x996AC8FF.toInt())
        g.fillRect(x - 0.09f, gy - 1.34f, x - 0.06f, gy - 1.0f, 0x55FFFFFF)
        g.fillRect(x - 0.04f, gy - 0.78f, x + 0.04f, gy - 0.72f, 0xFF3060FF.toInt())
    }

    private fun desk(pal: Palette, x: Float, gy: Float) {
        // Monitor glow first so the desk silhouettes against it.
        g.fillRadialGradient(x, gy - 1.05f, 0.7f, Col.alpha(pal.neon2, 0.18f), Col.alpha(pal.neon2, 0f))
        g.fillRoundRect(x - 0.24f, gy - 1.28f, x + 0.24f, gy - 0.94f, 0.03f, 0xFF0A0814.toInt())
        g.fillRect(x - 0.21f, gy - 1.25f, x + 0.21f, gy - 0.97f, Col.mul(pal.neon2, 0.45f))
        for (k in 0 until 3) g.fillRect(x - 0.17f, gy - 1.2f + k * 0.07f, x - 0.17f + 0.1f + hash(k, 3) * 0.22f, gy - 1.18f + k * 0.07f, Col.alpha(0xFFFFFFFF.toInt(), 0.5f))
        g.fillRect(x - 0.03f, gy - 0.94f, x + 0.03f, gy - 0.78f, 0xFF15101F.toInt())
        g.fillRect(x - 0.5f, gy - 0.78f, x + 0.5f, gy - 0.72f, Col.mul(pal.trim, 0.6f))
        g.fillRect(x - 0.5f, gy - 0.78f, x + 0.5f, gy - 0.765f, Col.alpha(pal.trim, 0.8f))
        g.fillRect(x - 0.46f, gy - 0.72f, x - 0.42f, gy, 0xFF15101F.toInt())
        g.fillRect(x + 0.42f, gy - 0.72f, x + 0.46f, gy, 0xFF15101F.toInt())
        g.fillRect(x + 0.14f, gy - 0.72f, x + 0.42f, gy - 0.3f, 0xFF1C1530.toInt())
    }

    private fun neonSign(pal: Palette, x: Float, y: Float, text: String) {
        val flick = if (hash((f.t * 6f).toInt(), text.length) > 0.96f) 0.35f else 1f
        g.fillRoundRect(x - 0.46f, y - 0.24f, x + 0.46f, y + 0.24f, 0.07f, 0x66000000)
        g.strokeRoundRect(x - 0.44f, y - 0.22f, x + 0.44f, y + 0.22f, 0.07f, 0.09f, Col.fade(pal.neon, 0.18f * flick))
        g.strokeRoundRect(x - 0.44f, y - 0.22f, x + 0.44f, y + 0.22f, 0.07f, 0.025f, Col.fade(pal.neon, flick))
        f.worldText(text, x, y + 0.085f, 0.22f, Col.fade(pal.neon2, 0.25f * flick), Gfx.Font.TITLE)
        f.worldText(text, x, y + 0.085f, 0.21f, Col.fade(pal.neon2, flick), Gfx.Font.TITLE)
    }

    /** Framed print: one of six compositions in one of four colourways, varying per floor. */
    private fun art(pal: Palette, x: Float, y: Float, v: Int) {
        val wide = v % 5 == 0
        val hw = if (wide) 0.42f else 0.3f
        val h = if (wide) 0.56f else 0.8f
        val cw = (v / 6) % 4
        val top = ART_TOP[cw]
        val bot = ART_BOT[cw]
        g.fillRect(x - hw, y - 0.02f, x + hw, y + h, 0xFF0C0916.toInt())
        g.fillRect(x - hw, y - 0.02f, x + hw, y, Col.alpha(pal.trim, 0.6f))
        val l = x - hw + 0.05f
        val r = x + hw - 0.05f
        val t = y + 0.04f
        val b = y + h - 0.06f
        g.fillVerticalGradient(l, t, r, b, top, bot)
        val ink = 0xFF0C0916.toInt()
        val paper = 0xFFF4ECFF.toInt()
        val mx = (l + r) / 2f
        val my = (t + b) / 2f
        when (v % 6) {
            0 -> {
                g.fillCircle(mx, my - 0.02f, 0.13f, 0xFFFFD860.toInt())
                for (k in 0 until 3) g.fillRect(l, my + 0.02f + k * 0.05f, r, my + 0.035f + k * 0.05f, ink)
                poly.tri(g, l, b, mx, my + 0.12f, r, b, 0xFF140A24.toInt())
            }
            1 -> {
                g.fillRect(l, my + 0.1f, r, b, 0xFF120A20.toInt())
                g.strokeCircle(mx, my - 0.04f, 0.11f, 0.028f, paper)
            }
            2 -> {
                poly.tri(g, mx - 0.18f, b - 0.1f, mx + 0.02f, t + 0.12f, mx + 0.2f, b - 0.1f, 0xFF120A20.toInt())
                g.fillRect(l, b - 0.1f, r, b - 0.08f, paper)
            }
            3 -> {
                // Mondrian-ish grid.
                g.fillRect(l, my - 0.02f, r, my + 0.01f, ink)
                g.fillRect(mx - 0.06f, t, mx - 0.03f, b, ink)
                g.fillRect(mx - 0.03f, my + 0.01f, r, b, 0xFFE8D8C0.toInt())
                g.fillRect(l, t, mx - 0.06f, my - 0.02f, 0xFFD83040.toInt())
            }
            4 -> {
                // A single brush stroke.
                g.line(l + 0.05f, b - 0.12f, mx, t + 0.15f, 0.06f, ink)
                g.line(mx, t + 0.15f, r - 0.04f, my, 0.045f, ink)
                g.fillCircle(r - 0.1f, t + 0.1f, 0.04f, 0xFFD83040.toInt())
            }
            else -> {
                // Portrait silhouette.
                g.fillCircle(mx, my - 0.1f, 0.09f, ink)
                g.fillRoundRect(mx - 0.15f, my, mx + 0.15f, b, 0.1f, ink)
                g.fillRect(mx - 0.1f, my - 0.12f, mx + 0.1f, my - 0.09f, 0xFFFF3D6A.toInt())
            }
        }
        g.fillRect(x - hw, y + h, x + hw, y + h + 0.03f, Col.alpha(pal.deep, 0.6f))
    }

    // ------------------------------------------------------------------- labs

    private fun serverRack(pal: Palette, x: Float, gy: Float, v: Int) {
        val x0 = x - 0.3f
        val x1 = x + 0.3f
        val top = gy - 1.95f
        g.fillRect(x0, top, x1, gy, 0xFF050D0E.toInt())
        g.fillRect(x0, top, x1, top + 0.03f, Col.alpha(pal.trim, 0.5f))
        g.strokeRect(x0, top, x1, gy, HAIR, Col.mul(pal.panel, 1.2f))
        for (row in 0 until 11) {
            val ry = top + 0.12f + row * 0.16f
            g.fillRect(x0 + 0.05f, ry, x1 - 0.05f, ry + 0.11f, 0xFF0B1818.toInt())
            g.fillRect(x0 + 0.05f, ry, x1 - 0.05f, ry + 0.012f, 0x18FFFFFF)
            for (c in 0 until 3) {
                val hv = hash(row * 7 + c + v * 97, 74)
                if (hv > 0.6f) continue
                val on = sin(f.t * (2f + hv * 7f) + hv * 40f) > 0f
                if (!on) continue
                val lc = if (hv < 0.08f) 0xFFFF4060.toInt() else if (hv < 0.35f) pal.neon else pal.neon2
                g.fillRect(x1 - 0.1f - c * 0.07f, ry + 0.04f, x1 - 0.07f - c * 0.07f, ry + 0.07f, lc)
            }
        }
        g.fillRect(x0 - 0.08f, top, x1 + 0.08f, gy, Col.alpha(pal.neon2, 0.035f))
    }

    private fun warnSign(x: Float, y: Float) {
        poly.tri(g, x, y - 0.26f, x + 0.3f, y + 0.24f, x - 0.3f, y + 0.24f, 0xFFD8B01E.toInt())
        poly.tri(g, x, y - 0.16f, x + 0.21f, y + 0.18f, x - 0.21f, y + 0.18f, 0xFF15150E.toInt())
        f.worldText("!", x, y + 0.16f, 0.26f, 0xFFD8B01E.toInt(), Gfx.Font.TITLE)
    }

    private fun labBench(pal: Palette, x: Float, gy: Float) {
        g.fillRect(x - 0.48f, gy - 0.74f, x + 0.48f, gy - 0.68f, 0xFFA8B8B8.toInt())
        g.fillRect(x - 0.48f, gy - 0.74f, x + 0.48f, gy - 0.73f, 0xFFE8F4F4.toInt())
        g.fillRect(x - 0.44f, gy - 0.68f, x + 0.44f, gy - 0.1f, 0xFF142626.toInt())
        g.fillRect(x - 0.02f, gy - 0.64f, x + 0.02f, gy - 0.14f, 0xFF0A1616.toInt())
        for (k in 0 until 3) {
            val fx = x - 0.28f + k * 0.28f
            val c = if (k == 1) pal.neon2 else pal.neon
            g.fillRect(fx - 0.025f, gy - 1.0f, fx + 0.025f, gy - 0.9f, 0x70FFFFFF)
            poly.quad(g, fx - 0.025f, gy - 0.9f, fx + 0.025f, gy - 0.9f, fx + 0.09f, gy - 0.75f, fx - 0.09f, gy - 0.75f, Col.fade(c, 0.85f))
            g.fillCircle(fx, gy - 0.82f, 0.16f, Col.fade(c, 0.12f))
        }
    }

    private fun tank(pal: Palette, x: Float, gy: Float, v: Int) {
        val top = gy - 2.15f
        g.fillRadialGradient(x, gy - 1.1f, 0.95f, Col.alpha(pal.neon, 0.16f), Col.alpha(pal.neon, 0f))
        g.fillRoundRect(x - 0.3f, top + 0.15f, x + 0.3f, gy - 0.2f, 0.12f, Col.alpha(pal.neon, 0.22f))
        g.fillVerticalGradient(x - 0.26f, top + 0.5f, x + 0.26f, gy - 0.22f, Col.alpha(pal.neon, 0.1f), Col.alpha(pal.neon, 0.35f))
        // Specimen: a curled silhouette floating in the fluid.
        val bob = sin(f.t * 0.9f + v) * 0.04f
        val sc = 0x90061A14.toInt()
        g.fillCircle(x, top + 0.8f + bob, 0.13f, sc)
        g.fillRoundRect(x - 0.12f, top + 0.92f + bob, x + 0.12f, top + 1.45f + bob, 0.1f, sc)
        g.line(x - 0.1f, top + 1.0f + bob, x - 0.2f, top + 1.3f + bob, 0.05f, sc)
        g.line(x + 0.08f, top + 1.42f + bob, x + 0.12f, top + 1.7f + bob, 0.06f, sc)
        for (b in 0 until 4) {
            val by = gy - 0.3f - fract(f.t * 0.4f + b * 0.25f) * 1.4f
            g.fillCircle(x - 0.12f + (b % 3) * 0.12f, by, 0.022f, Col.fade(pal.neon, 0.85f))
        }
        g.fillRect(x - 0.22f, top + 0.2f, x - 0.18f, gy - 0.3f, 0x30FFFFFF)
        g.strokeRoundRect(x - 0.3f, top + 0.15f, x + 0.3f, gy - 0.2f, 0.12f, LINE, Col.alpha(pal.trim, 0.7f))
        g.fillRect(x - 0.38f, gy - 0.22f, x + 0.38f, gy, Col.mul(pal.panel, 0.9f))
        g.fillRect(x - 0.38f, gy - 0.22f, x + 0.38f, gy - 0.2f, Col.alpha(pal.trim, 0.6f))
        g.fillRect(x - 0.38f, top, x + 0.38f, top + 0.2f, Col.mul(pal.panel, 0.9f))
        g.fillRect(x - 0.38f, top, x + 0.38f, top + 0.02f, Col.alpha(pal.trim, 0.6f))
        g.fillRect(x - 0.3f, gy - 0.14f, x - 0.1f, gy - 0.08f, pal.neon)
    }

    private fun monitor(pal: Palette, x: Float, y: Float) {
        g.fillRoundRect(x - 0.4f, y, x + 0.4f, y + 0.5f, 0.03f, 0xFF071010.toInt())
        g.fillRect(x - 0.36f, y + 0.04f, x + 0.36f, y + 0.46f, Col.mul(pal.neon2, 0.14f))
        var px = x - 0.36f
        var py = y + 0.25f
        for (k in 1..12) {
            val nx = x - 0.36f + k * (0.72f / 12f)
            val ny = y + 0.25f + sin(k * 1.9f + f.t * 4f) * 0.12f * (if (k % 4 == 0) 1.4f else 0.5f)
            g.line(px, py, nx, ny, 0.022f, pal.neon)
            px = nx
            py = ny
        }
        g.fillRect(x - 0.4f, y, x + 0.4f, y + 0.015f, Col.alpha(pal.trim, 0.5f))
    }

    // ------------------------------------------------------------------ metro

    private fun vending(pal: Palette, x: Float, gy: Float) {
        g.fillRadialGradient(x, gy - 1.1f, 1.0f, Col.alpha(pal.neon2, 0.16f), Col.alpha(pal.neon2, 0f))
        g.fillRoundRect(x - 0.38f, gy - 1.85f, x + 0.38f, gy, 0.04f, 0xFF1A1C22.toInt())
        g.fillRect(x - 0.38f, gy - 1.85f, x + 0.38f, gy - 1.83f, 0x40FFFFFF)
        g.fillRect(x - 0.32f, gy - 1.77f, x + 0.14f, gy - 0.5f, Col.mul(pal.neon2, 0.5f))
        for (r in 0 until 5) for (c in 0 until 3) {
            val cc = when ((r + c) % 3) {
                0 -> 0xFFFF5060.toInt()
                1 -> 0xFFFFD040.toInt()
                else -> 0xFF60FF90.toInt()
            }
            g.fillRect(x - 0.28f + c * 0.14f, gy - 1.7f + r * 0.23f, x - 0.19f + c * 0.14f, gy - 1.57f + r * 0.23f, cc)
            g.fillRect(x - 0.3f, gy - 1.55f + r * 0.23f, x + 0.12f, gy - 1.54f + r * 0.23f, 0x40FFFFFF)
        }
        g.fillRect(x + 0.19f, gy - 1.45f, x + 0.32f, gy - 1.05f, 0xFF30323A.toInt())
        g.fillRect(x + 0.22f, gy - 1.4f, x + 0.29f, gy - 1.36f, pal.neon)
        g.fillRect(x - 0.32f, gy - 0.4f, x + 0.14f, gy - 0.2f, 0xFF050508.toInt())
    }

    private fun bench(pal: Palette, x: Float, gy: Float) {
        val wood = 0xFF6A5A44.toInt()
        g.fillRect(x - 0.46f, gy - 0.48f, x + 0.46f, gy - 0.42f, wood)
        g.fillRect(x - 0.46f, gy - 0.48f, x + 0.46f, gy - 0.47f, 0xFFA89070.toInt())
        g.fillRect(x - 0.46f, gy - 0.86f, x + 0.46f, gy - 0.8f, wood)
        g.fillRect(x - 0.46f, gy - 0.86f, x + 0.46f, gy - 0.85f, 0xFFA89070.toInt())
        g.fillRect(x - 0.38f, gy - 0.86f, x - 0.34f, gy, 0xFF2A2C2E.toInt())
        g.fillRect(x + 0.34f, gy - 0.86f, x + 0.38f, gy, 0xFF2A2C2E.toInt())
    }

    private fun metroMap(pal: Palette, x: Float, y: Float) {
        g.fillRect(x - 0.44f, y - 0.3f, x + 0.44f, y + 0.3f, 0xFFD8D4C8.toInt())
        g.strokeRect(x - 0.44f, y - 0.3f, x + 0.44f, y + 0.3f, LINE, 0xFF2A2C2E.toInt())
        g.line(x - 0.38f, y - 0.08f, x + 0.38f, y - 0.08f, 0.045f, pal.neon2)
        g.line(x - 0.38f, y + 0.18f, x - 0.1f, y - 0.08f, 0.045f, 0xFFE02030.toInt())
        g.line(x - 0.1f, y - 0.08f, x + 0.3f, y + 0.22f, 0.045f, 0xFFE02030.toInt())
        g.line(x - 0.2f, y - 0.26f, x + 0.1f, y + 0.26f, 0.045f, pal.neon)
        for (k in 0 until 4) g.fillCircle(x - 0.3f + k * 0.2f, y - 0.08f, 0.035f, 0xFFFFFFFF.toInt())
    }

    /** Backlit advertising lightbox. */
    private fun adBox(pal: Palette, x: Float, y: Float, v: Int) {
        g.fillRadialGradient(x, y + 0.4f, 0.9f, Col.alpha(pal.lamp, 0.14f), Col.alpha(pal.lamp, 0f))
        g.fillRect(x - 0.46f, y - 0.05f, x + 0.46f, y + 0.85f, 0xFF16181A.toInt())
        val a = if (v % 2 == 0) 0xFFFF6A3C.toInt() else 0xFF3CD8FF.toInt()
        val b = if (v % 2 == 0) 0xFFFFD080.toInt() else 0xFF1C2C6A.toInt()
        g.fillVerticalGradient(x - 0.4f, y, x + 0.4f, y + 0.8f, b, a)
        if (v % 2 == 0) {
            g.fillCircle(x + 0.12f, y + 0.36f, 0.2f, 0xFFFFF4D8.toInt())
            g.fillRect(x - 0.34f, y + 0.08f, x + 0.05f, y + 0.14f, 0xFF201010.toInt())
            g.fillRect(x - 0.34f, y + 0.18f, x - 0.08f, y + 0.22f, 0xFF201010.toInt())
        } else {
            poly.quad(g, x - 0.3f, y + 0.72f, x - 0.05f, y + 0.22f, x + 0.08f, y + 0.22f, x + 0.32f, y + 0.72f, 0xFFE8F4FF.toInt())
            g.fillRect(x - 0.34f, y + 0.08f, x + 0.34f, y + 0.13f, 0xFFE8F4FF.toInt())
        }
        g.fillRect(x - 0.4f, y, x + 0.4f, y + 0.8f, 0x14FFFFFF)
        g.fillRect(x - 0.46f, y - 0.05f, x + 0.46f, y - 0.03f, Col.alpha(pal.trim, 0.6f))
    }

    private fun bin(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.13f, gy - 0.5f, x + 0.13f, gy, 0.03f, 0xFF2A2E30.toInt())
        g.fillRect(x - 0.15f, gy - 0.54f, x + 0.15f, gy - 0.48f, 0xFF40464A.toInt())
        g.fillRect(x - 0.15f, gy - 0.54f, x + 0.15f, gy - 0.53f, Col.alpha(pal.trim, 0.6f))
    }

    // ------------------------------------------------------------------ mines

    private fun crates(pal: Palette, x: Float, gy: Float) {
        crate(x - 0.22f, gy, 0.42f)
        crate(x + 0.22f, gy, 0.42f)
        crate(x, gy - 0.42f, 0.4f)
    }

    private fun crate(x: Float, bottom: Float, s: Float) {
        g.fillRect(x - s / 2, bottom - s, x + s / 2, bottom, 0xFF5A3E22.toInt())
        g.fillRect(x - s / 2, bottom - s, x + s / 2, bottom - s + 0.03f, 0xFF9A7040.toInt())
        g.strokeRect(x - s / 2 + 0.02f, bottom - s + 0.02f, x + s / 2 - 0.02f, bottom - 0.02f, 0.035f, 0xFF2E1E10.toInt())
        g.line(x - s / 2 + 0.04f, bottom - 0.04f, x + s / 2 - 0.04f, bottom - s + 0.04f, 0.035f, 0xFF2E1E10.toInt())
    }

    private fun lantern(pal: Palette, x: Float, y: Float) {
        val flick = 0.85f + 0.15f * sin(f.t * 9f + x * 3f) * sin(f.t * 5.3f)
        g.line(x, y - 0.55f, x, y - 0.18f, 0.02f, 0xFF1A120A.toInt())
        g.fillRadialGradient(x, y, 1.1f, Col.alpha(pal.lamp, 0.2f), Col.alpha(pal.lamp, 0f))
        f.glowDot(x, y, 0.07f, pal.lamp, flick)
        g.strokeRoundRect(x - 0.1f, y - 0.18f, x + 0.1f, y + 0.14f, 0.03f, 0.022f, 0xFF1A120A.toInt())
        g.fillRect(x - 0.12f, y - 0.2f, x + 0.12f, y - 0.16f, 0xFF1A120A.toInt())
    }

    private fun cart(pal: Palette, x: Float, gy: Float) {
        poly.quad(g, x - 0.46f, gy - 0.68f, x + 0.46f, gy - 0.68f, x + 0.36f, gy - 0.15f, x - 0.36f, gy - 0.15f, 0xFF3C4048.toInt())
        g.fillRect(x - 0.46f, gy - 0.7f, x + 0.46f, gy - 0.66f, 0xFF7A808A.toInt())
        for (k in 0 until 5) {
            val ox = x - 0.32f + k * 0.16f
            g.fillCircle(ox, gy - 0.74f, 0.09f, if (k == 2) 0xFFB08A3A.toInt() else 0xFF2E2620.toInt())
        }
        g.fillCircle(x - 0.1f, gy - 0.78f, 0.03f, 0xFFFFD84A.toInt())
        g.fillCircle(x - 0.23f, gy - 0.1f, 0.09f, 0xFF18181A.toInt())
        g.fillCircle(x + 0.23f, gy - 0.1f, 0.09f, 0xFF18181A.toInt())
        g.fillCircle(x - 0.23f, gy - 0.1f, 0.03f, 0xFF6A6A70.toInt())
        g.fillCircle(x + 0.23f, gy - 0.1f, 0.03f, 0xFF6A6A70.toInt())
    }

    /** A pneumatic rock drill on a stand, its bit chattering. */
    private fun drill(pal: Palette, x: Float, gy: Float) {
        val jit = if (sin(f.t * 40f) > 0f) 0.01f else -0.01f
        g.fillRect(x - 0.05f, gy - 1.4f, x + 0.05f, gy, 0xFF2E2A26.toInt())
        g.line(x, gy - 0.4f, x - 0.35f, gy, 0.04f, 0xFF2E2A26.toInt())
        g.line(x, gy - 0.4f, x + 0.35f, gy, 0.04f, 0xFF2E2A26.toInt())
        g.fillRoundRect(x - 0.14f, gy - 1.3f + jit, x + 0.14f, gy - 0.8f + jit, 0.05f, 0xFFB8922A.toInt())
        g.fillRect(x - 0.14f, gy - 1.3f + jit, x - 0.08f, gy - 0.8f + jit, 0x40FFFFFF)
        g.fillRect(x - 0.1f, gy - 1.1f + jit, x + 0.1f, gy - 1.05f + jit, 0xFF1A1610.toInt())
        poly.tri(g, x - 0.04f, gy - 0.8f + jit, x + 0.04f, gy - 0.8f + jit, x, gy - 0.5f + jit, 0xFF9A9AA4.toInt())
        g.line(x + 0.14f, gy - 1.2f, x + 0.4f, gy - 1.35f, 0.03f, 0xFF1A1610.toInt())
    }

    private fun tnt(pal: Palette, x: Float, gy: Float) {
        g.fillRect(x - 0.3f, gy - 0.34f, x + 0.3f, gy, 0xFF5A3E22.toInt())
        g.fillRect(x - 0.3f, gy - 0.34f, x + 0.3f, gy - 0.31f, 0xFF9A7040.toInt())
        f.worldText("TNT", x, gy - 0.1f, 0.16f, 0xFFD03020.toInt(), Gfx.Font.TITLE)
        for (k in 0 until 3) {
            val sx = x - 0.12f + k * 0.12f
            g.fillRoundRect(sx - 0.045f, gy - 0.62f, sx + 0.045f, gy - 0.34f, 0.03f, 0xFFB02818.toInt())
            g.line(sx, gy - 0.62f, sx + 0.03f, gy - 0.72f, 0.012f, 0xFFE8D8B0.toInt())
        }
    }

    // ------------------------------------------------------------------ magma

    private fun gauge(pal: Palette, x: Float, y: Float) {
        g.fillRect(x - 0.02f, y + 0.25f, x + 0.02f, y + 0.6f, 0xFF2A1A16.toInt())
        g.fillCircle(x, y, 0.25f, 0xFF2A1A16.toInt())
        g.fillCircle(x, y, 0.2f, 0xFFE0D0B8.toInt())
        g.fillCircle(x, y, 0.2f, Col.alpha(pal.glow, 0.15f))
        val a = -2.2f + (0.7f + 0.3f * sin(f.t * 3f)) * 3.6f
        g.line(x, y, x + cos(a) * 0.16f, y + sin(a) * 0.16f, 0.025f, 0xFFD02010.toInt())
        g.strokeCircle(x, y, 0.25f, HAIR, Col.alpha(pal.trim, 0.6f))
    }

    private fun coolant(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.26f, gy - 1.4f, x + 0.26f, gy, 0.16f, 0xFF3A302E.toInt())
        g.fillRect(x - 0.26f, gy - 1.4f, x - 0.18f, gy, 0x18FFFFFF)
        g.fillRect(x - 0.26f, gy - 1.05f, x + 0.26f, gy - 0.9f, 0xFFB8961C.toInt())
        g.fillRect(x - 0.06f, gy - 1.25f, x + 0.06f, gy - 0.25f, 0x5530D0FF)
        g.fillRect(x - 0.06f, gy - 0.68f, x + 0.06f, gy - 0.25f, 0xCC30D0FF.toInt())
    }

    /** A crucible of molten metal glowing on its stand. */
    private fun crucible(pal: Palette, x: Float, gy: Float) {
        g.fillRadialGradient(x, gy - 0.7f, 0.85f, Col.alpha(pal.glow, 0.3f), Col.alpha(pal.glow, 0f))
        poly.quad(g, x - 0.34f, gy - 0.72f, x + 0.34f, gy - 0.72f, x + 0.24f, gy - 0.22f, x - 0.24f, gy - 0.22f, 0xFF2A1C18.toInt())
        g.fillRect(x - 0.34f, gy - 0.74f, x + 0.34f, gy - 0.68f, 0xFF4A3028.toInt())
        g.fillRect(x - 0.3f, gy - 0.72f, x + 0.3f, gy - 0.69f, pal.neon2)
        g.fillRect(x - 0.28f, gy - 0.22f, x - 0.22f, gy, 0xFF1A100E.toInt())
        g.fillRect(x + 0.22f, gy - 0.22f, x + 0.28f, gy, 0xFF1A100E.toInt())
        for (k in 0 until 2) {
            val ph = fract(f.t * 0.6f + k * 0.5f)
            g.fillCircle(x - 0.1f + k * 0.2f, gy - 0.8f - ph * 0.5f, 0.05f + ph * 0.08f, Col.alpha(0xFF3A2A28.toInt(), 0.5f * (1f - ph)))
        }
    }

    // ------------------------------------------------------------------- hell

    private fun candles(x: Float, gy: Float) {
        for (k in 0 until 5) {
            val cx = x - 0.36f + k * 0.18f
            val h = 0.2f + hash(k, 61) * 0.3f
            g.fillRect(cx - 0.035f, gy - h, cx + 0.035f, gy, 0xFFD8C8A8.toInt())
            val fl = sin(f.t * 12f + k * 2f) * 0.02f
            g.fillCircle(cx, gy - h - 0.05f, 0.12f, 0x28FF8020)
            poly.tri(g, cx - 0.035f, gy - h, cx + 0.035f, gy - h, cx + fl, gy - h - 0.13f, 0xFFFFC040.toInt())
        }
    }

    private fun rune(pal: Palette, x: Float, y: Float) {
        val pulse = 0.55f + 0.45f * sin(f.t * 2.5f)
        g.strokeCircle(x, y, 0.4f, 0.09f, Col.fade(pal.neon, 0.2f * pulse))
        g.strokeCircle(x, y, 0.4f, 0.025f, Col.fade(pal.neon, pulse))
        for (k in 0 until 5) {
            val a1 = -1.5708f + k * 2.513f
            val a2 = -1.5708f + (k + 2) * 2.513f
            g.line(x + cos(a1) * 0.38f, y + sin(a1) * 0.38f, x + cos(a2) * 0.38f, y + sin(a2) * 0.38f, 0.025f, Col.fade(pal.neon, pulse))
        }
    }

    private fun skulls(pal: Palette, x: Float, gy: Float) {
        for (k in 0 until 4) {
            val sx = x - 0.3f + (k % 3) * 0.3f
            val sy = gy - 0.13f - (k / 3) * 0.23f
            g.fillCircle(sx, sy, 0.12f, pal.trim)
            g.fillRect(sx - 0.06f, sy + 0.04f, sx + 0.06f, sy + 0.12f, pal.trim)
            g.fillCircle(sx - 0.045f, sy, 0.032f, 0xFF200808.toInt())
            g.fillCircle(sx + 0.045f, sy, 0.032f, 0xFF200808.toInt())
            g.fillCircle(sx - 0.03f, sy - 0.06f, 0.03f, 0x40FFFFFF)
        }
    }

    private fun chains(pal: Palette, x: Float, rt: Float) {
        for (c in 0 until 2) {
            val cx = x - 0.2f + c * 0.4f
            val len = 1.1f + c * 0.5f
            var y = rt + 0.1f
            var k = 0
            while (y < rt + len) {
                if (k % 2 == 0) g.strokeRoundRect(cx - 0.05f, y, cx + 0.05f, y + 0.16f, 0.05f, 0.025f, 0xFF5A4A4A.toInt())
                else g.line(cx, y, cx, y + 0.16f, 0.035f, 0xFF3A2E2E.toInt())
                y += 0.13f
                k++
            }
            // Meat hook.
            g.line(cx, y, cx, y + 0.1f, 0.03f, 0xFF7A6A6A.toInt())
            g.strokeCircle(cx + 0.05f, y + 0.14f, 0.05f, 0.025f, 0xFF7A6A6A.toInt())
        }
    }

    /** Iron wall sconce holding a live flame. */
    private fun sconce(pal: Palette, x: Float, y: Float) {
        g.fillRadialGradient(x, y - 0.2f, 1.0f, Col.alpha(pal.glow, 0.24f), Col.alpha(pal.glow, 0f))
        poly.quad(g, x - 0.16f, y, x + 0.16f, y, x + 0.08f, y + 0.14f, x - 0.08f, y + 0.14f, 0xFF2A1A18.toInt())
        g.fillRect(x - 0.02f, y + 0.14f, x + 0.02f, y + 0.4f, 0xFF2A1A18.toInt())
        val t = f.t * 9f + x
        poly.tri(g, x - 0.13f, y, x + 0.13f, y, x + sin(t) * 0.05f, y - 0.42f - sin(t * 1.7f) * 0.06f, 0xE0FF4A18.toInt())
        poly.tri(g, x - 0.07f, y, x + 0.07f, y, x + sin(t + 1f) * 0.03f, y - 0.24f, 0xFFFFD050.toInt())
    }

    // ============================================================ more props

    private fun bookshelf(pal: Palette, x: Float, gy: Float, v: Int) {
        val x0 = x - 0.42f
        val x1 = x + 0.42f
        val top = gy - 1.75f
        g.fillRect(x0, top, x1, gy, 0xFF1A1224.toInt())
        g.fillRect(x0, top, x1, top + 0.03f, Col.alpha(pal.trim, 0.5f))
        for (row in 0 until 4) {
            val sy = top + 0.4f + row * 0.42f
            g.fillRect(x0 + 0.04f, sy, x1 - 0.04f, sy + 0.04f, 0xFF2E2240.toInt())
            var bx = x0 + 0.06f
            var k = 0
            while (bx < x1 - 0.1f && k < 12) {
                val bw = 0.04f + hash(k + row * 13 + v, 60) * 0.05f
                val bh = 0.22f + hash(k + row * 7 + v, 61) * 0.12f
                val c = BOOKS[(k + row + v) % BOOKS.size]
                if (hash(k + row * 5 + v, 62) > 0.12f) g.fillRect(bx, sy - bh, bx + bw, sy, c)
                bx += bw + 0.01f
                k++
            }
        }
    }

    private fun floorLamp(pal: Palette, x: Float, gy: Float) {
        // A warm practical: the one warm light in a cool room.
        val warm = 0xFFFFC27A.toInt()
        g.fillRadialGradient(x, gy - 1.35f, 1.0f, 0x38FFC27A, 0x00FFC27A)
        g.fillRect(x - 0.015f, gy - 1.3f, x + 0.015f, gy, 0xFF15101C.toInt())
        g.fillRect(x - 0.12f, gy - 0.03f, x + 0.12f, gy, 0xFF15101C.toInt())
        poly.quad(g, x - 0.1f, gy - 1.55f, x + 0.1f, gy - 1.55f, x + 0.16f, gy - 1.3f, x - 0.16f, gy - 1.3f, 0xFFE8B070.toInt())
        g.fillRect(x - 0.16f, gy - 1.31f, x + 0.16f, gy - 1.29f, warm)
        poly.quad(g, x - 0.16f, gy - 1.3f, x + 0.16f, gy - 1.3f, x + 0.5f, gy, x - 0.5f, gy, 0x10FFC27A)
    }

    private fun lounge(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.34f, gy - 0.48f, x + 0.26f, gy - 0.08f, 0.1f, 0xFF3A1E3E.toInt())
        g.fillRoundRect(x - 0.4f, gy - 0.7f, x - 0.22f, gy - 0.08f, 0.08f, 0xFF3A1E3E.toInt())
        g.fillRect(x - 0.34f, gy - 0.48f, x + 0.26f, gy - 0.46f, 0x30FFFFFF)
        g.fillRect(x - 0.3f, gy - 0.08f, x - 0.26f, gy, 0xFF15101C.toInt())
        g.fillRect(x + 0.18f, gy - 0.08f, x + 0.22f, gy, 0xFF15101C.toInt())
    }

    private fun whiteboard(pal: Palette, x: Float, y: Float, v: Int) {
        g.fillRect(x - 0.46f, y, x + 0.46f, y + 0.62f, 0xFFB8B4C8.toInt())
        g.strokeRect(x - 0.46f, y, x + 0.46f, y + 0.62f, 0.03f, 0xFF4A4658.toInt())
        // Scribbled org chart / heist plan.
        val ink = 0xFF2A3A8A.toInt()
        g.strokeRect(x - 0.1f, y + 0.08f, x + 0.1f, y + 0.18f, 0.012f, ink)
        g.line(x, y + 0.18f, x, y + 0.26f, 0.012f, ink)
        g.line(x - 0.3f, y + 0.26f, x + 0.3f, y + 0.26f, 0.012f, ink)
        for (k in 0 until 3) {
            val bx = x - 0.3f + k * 0.3f
            g.line(bx, y + 0.26f, bx, y + 0.32f, 0.012f, ink)
            g.strokeRect(bx - 0.08f, y + 0.32f, bx + 0.08f, y + 0.4f, 0.012f, if (k == v % 3) 0xFFC02030.toInt() else ink)
        }
        g.line(x - 0.35f, y + 0.5f, x - 0.1f + hash(v, 63) * 0.3f, y + 0.5f, 0.01f, ink)
        g.fillRect(x - 0.3f, y + 0.62f, x + 0.3f, y + 0.65f, 0xFF4A4658.toInt())
    }

    private fun cabinet(pal: Palette, x: Float, gy: Float) {
        g.fillRect(x - 0.18f, gy - 1.05f, x + 0.18f, gy, 0xFF2A2440.toInt())
        g.fillRect(x - 0.18f, gy - 1.05f, x + 0.18f, gy - 1.03f, Col.alpha(pal.trim, 0.5f))
        for (k in 0 until 3) {
            val dy = gy - 1.0f + k * 0.33f
            g.fillRect(x - 0.15f, dy, x + 0.15f, dy + 0.3f, 0xFF231E36.toInt())
            g.fillRect(x - 0.05f, dy + 0.08f, x + 0.05f, dy + 0.1f, Col.alpha(pal.trim, 0.6f))
        }
    }

    private fun fumeHood(pal: Palette, x: Float, gy: Float) {
        g.fillRect(x - 0.45f, gy - 2.0f, x + 0.45f, gy, 0xFF142626.toInt())
        g.fillRect(x - 0.45f, gy - 2.0f, x + 0.45f, gy - 1.98f, Col.alpha(pal.trim, 0.6f))
        g.fillRect(x - 0.38f, gy - 1.6f, x + 0.38f, gy - 0.8f, Col.alpha(pal.neon, 0.16f))
        g.fillRect(x - 0.38f, gy - 1.6f, x + 0.38f, gy - 1.56f, Col.alpha(pal.lamp, 0.7f))
        g.fillRect(x - 0.38f, gy - 1.15f, x + 0.38f, gy - 1.13f, Col.alpha(pal.trim, 0.5f))
        for (k in 0 until 3) {
            val fx = x - 0.2f + k * 0.2f
            g.fillRect(fx - 0.03f, gy - 1.02f, fx + 0.03f, gy - 0.82f, Col.alpha(if (k == 1) pal.neon2 else pal.neon, 0.8f))
        }
        g.fillRect(x - 0.45f, gy - 0.8f, x + 0.45f, gy - 0.74f, 0xFFA8B8B8.toInt())
    }

    private fun cryo(pal: Palette, x: Float, gy: Float, v: Int) {
        val frost = 0xFFB8E8FF.toInt()
        g.fillRoundRect(x - 0.28f, gy - 1.95f, x + 0.28f, gy - 0.05f, 0.25f, 0xFF1A3438.toInt())
        g.fillRoundRect(x - 0.2f, gy - 1.8f, x + 0.2f, gy - 0.25f, 0.18f, Col.alpha(frost, 0.25f))
        // Someone asleep inside.
        g.fillCircle(x, gy - 1.55f, 0.09f, 0x60102028)
        g.fillRoundRect(x - 0.1f, gy - 1.45f, x + 0.1f, gy - 0.4f, 0.08f, 0x60102028)
        g.fillRoundRect(x - 0.2f, gy - 1.8f, x - 0.14f, gy - 0.25f, 0.03f, 0x40FFFFFF)
        g.strokeRoundRect(x - 0.28f, gy - 1.95f, x + 0.28f, gy - 0.05f, 0.25f, LINE, Col.alpha(pal.trim, 0.7f))
        val c = if (v % 2 == 0) pal.neon2 else pal.neon
        g.fillRect(x - 0.08f, gy - 0.18f, x + 0.08f, gy - 0.12f, c)
        for (k in 0 until 3) {
            val ph = fract(f.t * 0.5f + k * 0.33f)
            g.fillCircle(x - 0.15f + k * 0.15f, gy - 0.05f - ph * 0.2f, 0.06f + ph * 0.08f, Col.alpha(frost, 0.2f * (1f - ph)))
        }
    }

    private fun robotArm(pal: Palette, x: Float, gy: Float) {
        val a = sin(f.t * 0.8f) * 18f
        g.fillRect(x - 0.22f, gy - 0.18f, x + 0.22f, gy, 0xFF2A3A3A.toInt())
        g.save()
        g.translate(x, gy - 0.18f)
        g.rotate(-20f + a)
        g.fillRoundRect(-0.06f, -0.8f, 0.06f, 0f, 0.05f, 0xFFD8A020.toInt())
        g.translate(0f, -0.8f)
        g.fillCircle(0f, 0f, 0.08f, 0xFF3A4A4A.toInt())
        g.rotate(70f - a * 1.5f)
        g.fillRoundRect(-0.05f, -0.6f, 0.05f, 0f, 0.04f, 0xFFD8A020.toInt())
        g.translate(0f, -0.6f)
        g.line(-0.06f, 0f, -0.08f, -0.12f, 0.025f, 0xFF3A4A4A.toInt())
        g.line(0.06f, 0f, 0.08f, -0.12f, 0.025f, 0xFF3A4A4A.toInt())
        g.fillCircle(0f, 0f, 0.025f, pal.neon)
        g.restore()
    }

    private fun bioBin(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.16f, gy - 0.55f, x + 0.16f, gy, 0.04f, 0xFFB8961C.toInt())
        g.fillRect(x - 0.18f, gy - 0.6f, x + 0.18f, gy - 0.53f, 0xFF2A2A1A.toInt())
        g.strokeCircle(x, gy - 0.28f, 0.07f, 0.02f, 0xFF1A1A10.toInt())
        g.fillCircle(x, gy - 0.28f, 0.025f, 0xFF1A1A10.toInt())
    }

    private fun turnstile(pal: Palette, x: Float, gy: Float) {
        for (k in 0..1) {
            val tx = x - 0.3f + k * 0.6f
            g.fillRect(tx - 0.08f, gy - 0.9f, tx + 0.08f, gy, 0xFF3A4046.toInt())
            g.fillRect(tx - 0.08f, gy - 0.9f, tx + 0.08f, gy - 0.88f, Col.alpha(pal.trim, 0.6f))
            g.fillRect(tx - 0.05f, gy - 0.8f, tx + 0.05f, gy - 0.74f, if (k == 0) 0xFF40FF80.toInt() else 0xFFFF4040.toInt())
        }
        g.line(x - 0.22f, gy - 0.6f, x + 0.1f, gy - 0.55f, 0.03f, 0xFF8A9096.toInt())
        g.line(x - 0.22f, gy - 0.6f, x - 0.05f, gy - 0.42f, 0.03f, 0xFF8A9096.toInt())
    }

    private fun graffiti(pal: Palette, x: Float, gy: Float, v: Int) {
        val c1 = if (v % 2 == 0) 0xFFFF4A9A.toInt() else 0xFF4AF0C0.toInt()
        val c2 = if (v % 3 == 0) 0xFFFFD84A.toInt() else 0xFF8A6AFF.toInt()
        g.save()
        g.translate(x, gy - 0.6f)
        g.rotate(-6f + (v % 5) * 3f)
        g.scale(1f / f.s, 1f / f.s)
        val sz = 0.24f * f.s
        val tag = TAGS[v % TAGS.size]
        g.text(tag, 2f, 2f, sz, 0xA0000000.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(tag, 0f, 0f, sz, Col.alpha(c1, 0.75f), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.restore()
        g.line(x - 0.35f, gy - 0.5f, x + 0.3f, gy - 0.48f, 0.02f, Col.alpha(c2, 0.5f))
        g.line(x + 0.3f, gy - 0.48f, x + 0.3f, gy - 0.3f, 0.012f, Col.alpha(c2, 0.35f))
    }

    private fun arrivals(pal: Palette, x: Float, y: Float) {
        g.fillRect(x - 0.5f, y - 0.14f, x + 0.5f, y + 0.18f, 0xFF0A0A08.toInt())
        g.strokeRect(x - 0.5f, y - 0.14f, x + 0.5f, y + 0.18f, 0.02f, 0xFF3A3A34.toInt())
        val msg = ARRIVALS[((f.t / 4f).toInt() + (x * 10f).toInt()) % ARRIVALS.size]
        f.worldText(msg, x, y + 0.08f, 0.16f, 0xFFFFB020.toInt())
        g.line(x - 0.3f, y - 0.14f, x - 0.3f, y - 0.5f, 0.015f, 0xFF2A2A28.toInt())
        g.line(x + 0.3f, y - 0.14f, x + 0.3f, y - 0.5f, 0.015f, 0xFF2A2A28.toInt())
    }

    private fun payphone(pal: Palette, x: Float, gy: Float) {
        g.fillRect(x - 0.18f, gy - 1.5f, x + 0.18f, gy - 0.9f, 0xFF3A4450.toInt())
        g.fillRect(x - 0.18f, gy - 1.5f, x + 0.18f, gy - 1.48f, Col.alpha(pal.trim, 0.6f))
        g.fillRect(x - 0.12f, gy - 1.4f, x + 0.12f, gy - 1.25f, Col.alpha(pal.neon2, 0.6f))
        for (r in 0 until 3) for (c in 0 until 3) g.fillRect(x - 0.1f + c * 0.07f, gy - 1.2f + r * 0.07f, x - 0.06f + c * 0.07f, gy - 1.16f + r * 0.07f, 0xFFB0B8C0.toInt())
        g.line(x + 0.2f, gy - 1.3f, x + 0.24f, gy - 0.6f, 0.02f, 0xFF15181C.toInt())
        g.fillRoundRect(x + 0.18f, gy - 0.66f, x + 0.3f, gy - 0.5f, 0.04f, 0xFF15181C.toInt())
    }

    private fun orePile(pal: Palette, x: Float, gy: Float, v: Int) {
        val c = if (v % 2 == 0) 0xFF5AD8FF.toInt() else 0xFFFFC84A.toInt()
        g.fillRadialGradient(x, gy - 0.2f, 0.8f, Col.alpha(c, 0.25f), Col.alpha(c, 0f))
        poly.begin()
        poly.add(x - 0.5f, gy)
        for (k in 0..6) poly.add(x - 0.5f + k / 6f * 1f, gy - sin(k / 6f * 3.1416f) * 0.4f - hash(k + v, 64) * 0.08f)
        poly.add(x + 0.5f, gy)
        poly.fill(g, 0xFF2A2018.toInt())
        for (k in 0 until 7) {
            val cx = x - 0.35f + hash(k + v, 65) * 0.7f
            val cy = gy - 0.05f - hash(k + v, 66) * 0.3f * sin((cx - x + 0.5f) * 3.1416f)
            poly.tri(g, cx - 0.05f, cy + 0.03f, cx, cy - 0.1f, cx + 0.05f, cy + 0.03f, Col.alpha(c, 0.7f + 0.3f * hash(k, 67)))
        }
    }

    private fun tools(pal: Palette, x: Float, gy: Float) {
        val wood = 0xFF6A4A2A.toInt()
        g.line(x - 0.2f, gy, x - 0.05f, gy - 1.1f, 0.04f, wood)
        poly.tri(g, x - 0.2f, gy - 1.1f, x + 0.12f, gy - 1.02f, x - 0.02f, gy - 1.16f, 0xFF7A7A80.toInt())
        g.line(x + 0.1f, gy, x + 0.22f, gy - 1.0f, 0.04f, wood)
        g.fillRoundRect(x + 0.02f, gy - 0.25f, x + 0.2f, gy, 0.03f, 0xFF6A6A70.toInt())
    }

    private fun ladder(pal: Palette, x: Float, rt: Float, gy: Float) {
        // A ladder up into a dark hole in the ceiling.
        g.fillRect(x - 0.3f, rt, x + 0.3f, rt + 0.3f, 0xFF050303.toInt())
        val wood = 0xFF5A3E22.toInt()
        g.fillRect(x - 0.2f, rt + 0.1f, x - 0.16f, gy, wood)
        g.fillRect(x + 0.16f, rt + 0.1f, x + 0.2f, gy, wood)
        var y = rt + 0.3f
        while (y < gy) {
            g.fillRect(x - 0.16f, y, x + 0.16f, y + 0.03f, wood)
            y += 0.28f
        }
        g.line(x + 0.25f, rt + 0.1f, x + 0.3f, gy - 0.4f, 0.012f, 0xFF8A7A5A.toInt())
    }

    private fun plunger(pal: Palette, x: Float, gy: Float) {
        g.fillRect(x - 0.35f, gy - 0.28f, x - 0.05f, gy, 0xFF5A3E22.toInt())
        g.fillRect(x - 0.22f, gy - 0.55f, x - 0.18f, gy - 0.28f, 0xFF3A3A40.toInt())
        g.fillRect(x - 0.32f, gy - 0.58f, x - 0.08f, gy - 0.54f, 0xFF3A3A40.toInt())
        g.line(x - 0.05f, gy - 0.15f, x + 0.4f, gy - 0.05f, 0.015f, 0xFFB02818.toInt())
    }

    private fun heatPipes(pal: Palette, x: Float, rt: Float, gy: Float) {
        for (k in 0 until 3) {
            val px = x - 0.3f + k * 0.3f
            g.fillRect(px - 0.07f, rt + 0.14f, px + 0.07f, gy, 0xFF3A2A28.toInt())
            g.fillRect(px - 0.07f, rt + 0.14f, px - 0.04f, gy, 0x20FFFFFF)
            for (r in 0 until 4) {
                val ry = rt + 0.6f + r * 0.65f
                g.fillRect(px - 0.09f, ry, px + 0.09f, ry + 0.06f, 0xFF4A3A36.toInt())
            }
            val hot = 0.5f + 0.5f * sin(f.t * 1.5f + k)
            g.fillRect(px - 0.07f, gy - 0.6f, px + 0.07f, gy, Col.alpha(pal.glow, 0.2f + 0.2f * hot))
        }
    }

    private fun obsidian(pal: Palette, x: Float, gy: Float, v: Int) {
        for (k in 0 until 4) {
            val cx = x - 0.3f + k * 0.2f
            val h = 0.4f + hash(k + v, 68) * 0.6f
            poly.tri(g, cx - 0.1f, gy, cx + 0.1f, gy, cx + (hash(k, 69) - 0.5f) * 0.1f, gy - h, 0xFF0C0810.toInt())
            g.line(cx - 0.02f, gy - 0.05f, cx, gy - h + 0.05f, 0.015f, Col.alpha(pal.glow, 0.7f))
        }
        g.fillRect(x - 0.45f, gy - 0.05f, x + 0.45f, gy, Col.alpha(pal.glow, 0.6f))
    }

    private fun fan(pal: Palette, x: Float, y: Float) {
        g.fillRect(x - 0.35f, y - 0.35f, x + 0.35f, y + 0.35f, 0xFF201414.toInt())
        g.strokeRect(x - 0.35f, y - 0.35f, x + 0.35f, y + 0.35f, 0.03f, Col.alpha(pal.trim, 0.5f))
        g.fillCircle(x, y, 0.3f, 0xFF0C0808.toInt())
        g.save()
        g.translate(x, y)
        g.rotate(f.t * 300f)
        for (k in 0 until 4) {
            g.rotate(90f)
            poly.tri(g, 0f, 0f, 0.26f, -0.06f, 0.24f, 0.08f, 0xFF4A3A36.toInt())
        }
        g.restore()
        g.fillCircle(x, y, 0.05f, 0xFF6A5A56.toInt())
    }

    private fun canisters(pal: Palette, x: Float, gy: Float) {
        for (k in 0 until 3) {
            val cx = x - 0.28f + k * 0.28f
            g.fillRoundRect(cx - 0.1f, gy - 0.6f, cx + 0.1f, gy, 0.05f, 0xFF4A4448.toInt())
            g.fillRect(cx - 0.1f, gy - 0.45f, cx + 0.1f, gy - 0.4f, if (k == 1) 0xFFB8961C.toInt() else 0xFFB02818.toInt())
            g.fillRect(cx - 0.1f, gy - 0.6f, cx - 0.06f, gy, 0x20FFFFFF)
        }
    }

    private fun cage(pal: Palette, x: Float, rt: Float) {
        val sway = sin(f.t * 0.7f + x) * 4f
        g.save()
        g.translate(x, rt + 0.14f)
        g.rotate(sway)
        g.line(0f, 0f, 0f, 0.5f, 0.025f, 0xFF3A2E2E.toInt())
        val iron = 0xFF4A3A3A.toInt()
        g.strokeRect(-0.28f, 0.5f, 0.28f, 1.5f, 0.03f, iron)
        var bx = -0.2f
        while (bx < 0.25f) {
            g.line(bx, 0.5f, bx, 1.5f, 0.018f, iron)
            bx += 0.1f
        }
        // Its occupant, long gone.
        g.fillCircle(0.02f, 1.25f, 0.07f, pal.trim)
        g.line(-0.15f, 1.45f, 0.15f, 1.4f, 0.03f, pal.trim)
        g.restore()
    }

    private fun altar(pal: Palette, x: Float, gy: Float) {
        g.fillRadialGradient(x, gy - 0.6f, 0.9f, 0x40FF2240, 0x00FF2240)
        g.fillRect(x - 0.42f, gy - 0.55f, x + 0.42f, gy, 0xFF2A0C10.toInt())
        g.fillRect(x - 0.48f, gy - 0.62f, x + 0.48f, gy - 0.55f, 0xFF3A1418.toInt())
        g.fillRect(x - 0.48f, gy - 0.62f, x + 0.48f, gy - 0.605f, Col.alpha(pal.trim, 0.4f))
        g.fillRect(x - 0.3f, gy - 0.55f, x + 0.3f, gy - 0.4f, 0xFF5A000C.toInt())
        g.fillCircle(x, gy - 0.72f, 0.1f, pal.trim)
    }

    private fun bones(pal: Palette, x: Float, gy: Float, v: Int) {
        for (k in 0 until 8) {
            val bx = x - 0.4f + hash(k + v, 70) * 0.8f
            val by = gy - 0.04f - hash(k + v, 71) * 0.2f
            val a = hash(k + v, 72) * 3.1416f
            val l = 0.12f + hash(k, 73) * 0.1f
            g.line(bx - kotlin.math.cos(a) * l, by - sin(a) * l * 0.4f, bx + kotlin.math.cos(a) * l, by + sin(a) * l * 0.4f, 0.035f, pal.trim)
        }
        g.fillCircle(x + 0.1f, gy - 0.28f, 0.1f, pal.trim)
        g.fillCircle(x + 0.07f, gy - 0.29f, 0.025f, 0xFF200808.toInt())
        g.fillCircle(x + 0.13f, gy - 0.29f, 0.025f, 0xFF200808.toInt())
    }

    private fun maiden(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.28f, gy - 2.0f, x + 0.28f, gy, 0.25f, 0xFF3A2A28.toInt())
        g.fillRect(x - 0.28f, gy - 1.4f, x + 0.28f, gy - 1.35f, 0xFF2A1A18.toInt())
        g.fillCircle(x, gy - 1.7f, 0.14f, 0xFF4A3634.toInt())
        g.fillCircle(x - 0.05f, gy - 1.72f, 0.02f, 0xFF100404.toInt())
        g.fillCircle(x + 0.05f, gy - 1.72f, 0.02f, 0xFF100404.toInt())
        g.line(x, gy - 1.3f, x, gy - 0.05f, 0.015f, 0xFF140606.toInt())
        for (k in 0 until 5) g.fillCircle(x - 0.18f, gy - 1.2f + k * 0.22f, 0.025f, 0xFF8A6A5A.toInt())
        g.fillRect(x - 0.3f, gy - 0.3f, x + 0.3f, gy, Col.alpha(0xFF6A000C.toInt(), 0.5f))
    }

    // ======================================================= material variants

    /**
     * Wall-material variants give each floor its own identity even when doors fill its slots:
     * half the floors wear the zone's standard finish, the rest one of two alternates.
     */
    private fun variant(fi: Int): Int {
        val h = hash(fi, 712)
        return if (h < 0.5f) 0 else if (h < 0.75f) 1 else 2
    }

    /** Big veined stone slabs and a brass rail: the executive floors. */
    private fun marble(pal: Palette, fi: Int, rt: Float, gy: Float) {
        g.fillRect(0f, rt, W, gy, Col.alpha(pal.trim, 0.13f))
        for (k in 0..3) {
            val x = SEAMS[k * 2]
            g.fillRect(x - HAIR, rt, x + HAIR * 0.5f, gy, Col.alpha(pal.deep, 0.5f))
        }
        val vein = Col.alpha(0xFFFFFFFF.toInt(), 0.14f)
        for (v in 0 until 6) {
            var x = hash(v + fi * 7, 80) * W
            var y = rt + 0.2f
            for (s in 0 until 7) {
                val nx = x + (hash(s + v * 9 + fi, 81) - 0.35f) * 0.7f
                val ny = y + 0.45f
                g.line(x, y, nx, ny, 0.012f, vein)
                x = nx
                y = ny
            }
        }
        val ry = gy - 0.9f
        g.fillRect(0f, ry - 0.03f, W, ry, 0xB0C8A060.toInt())
        g.fillRect(0f, ry - 0.03f, W, ry - 0.022f, 0xC0FFE0A0.toInt())
    }

    /** Warm timber slats with a hidden LED cove: the design-studio floors. */
    private fun slats(pal: Palette, rt: Float, gy: Float) {
        g.fillRect(0f, rt, W, gy, 0x38A0602A)
        var x = 0.06f
        val dark = Col.alpha(pal.deep, 0.55f)
        while (x < W) {
            g.fillRect(x, rt + 0.3f, x + 0.03f, gy - 0.12f, dark)
            x += 0.14f
        }
        g.fillRect(0f, rt + 0.26f, W, rt + 0.3f, 0x60FFC890)
    }

    /** Bright clean-room cladding with rounded panels and a status strip. */
    private fun cleanRoom(pal: Palette, rt: Float, gy: Float) {
        g.fillRect(0f, rt, W, gy, 0x10FFFFFF)
        for (i in 0 until SEAMS.size - 1) {
            val x0 = SEAMS[i] + 0.05f
            val x1 = SEAMS[i + 1] - 0.05f
            g.strokeRoundRect(x0, rt + 0.3f, x1, rt + 1.35f, 0.12f, HAIR, Col.alpha(pal.trim, 0.2f))
            g.strokeRoundRect(x0, rt + 1.45f, x1, gy - 0.3f, 0.12f, HAIR, Col.alpha(pal.trim, 0.2f))
        }
        g.fillRect(0f, rt + 1.38f, W, rt + 1.42f, Col.alpha(pal.neon2, 0.35f))
    }

    /** A sealed floor: red strobe wash, hazard banding, stencilled warnings. */
    private fun quarantine(pal: Palette, rt: Float, gy: Float) {
        val pulse = 0.5f + 0.5f * sin(f.t * 4f)
        g.fillRect(0f, rt, W, gy, Col.alpha(0xFFFF2020.toInt(), 0.04f + 0.05f * pulse))
        g.save()
        g.clipRect(0f, rt + 0.2f, W, rt + 0.32f)
        g.fillRect(0f, rt + 0.2f, W, rt + 0.32f, 0xFF15150E.toInt())
        var sx = -0.4f
        while (sx < W) {
            poly.quad(g, sx, rt + 0.32f, sx + 0.12f, rt + 0.32f, sx + 0.24f, rt + 0.2f, sx + 0.12f, rt + 0.2f, 0xFFC02020.toInt())
            sx += 0.24f
        }
        g.restore()
        for (k in 0..1) f.worldText("QUARANTINE", if (k == 0) 3.2f else 6.8f, rt + 0.62f, 0.18f, Col.alpha(0xFFFF4040.toInt(), 0.75f))
    }

    /** Old brick with the ghosts of torn posters. */
    private fun brick(pal: Palette, fi: Int, rt: Float, gy: Float) {
        g.fillRect(0f, rt, W, gy, 0x18A04828)
        val mortar = Col.alpha(pal.deep, 0.45f)
        var y = rt + 0.18f
        var row = 0
        while (y < gy) {
            g.fillRect(0f, y, W, y + 0.02f, mortar)
            var x = if (row % 2 == 0) 0.2f else 0.4f
            while (x < W) {
                g.fillRect(x, y - 0.16f, x + 0.02f, y, mortar)
                x += 0.4f
            }
            y += 0.18f
            row++
        }
        for (k in 0 until 3) {
            val px = hash(fi * 3 + k, 82) * (W - 1f)
            val py = rt + 0.9f + hash(fi * 3 + k, 83) * 0.8f
            val c = if (k % 2 == 0) 0x40E8D0A0 else 0x40C04830
            poly.quad(g, px, py, px + 0.6f, py + 0.05f, px + 0.52f, py + 0.7f, px + 0.05f, py + 0.55f, c)
        }
    }

    /** Board-formed concrete under a huge mural. */
    private fun mural(pal: Palette, fi: Int, rt: Float, gy: Float) {
        var y = rt + 0.3f
        while (y < gy) {
            g.fillRect(0f, y, W, y + 0.01f, Col.alpha(pal.deep, 0.3f))
            y += 0.3f
        }
        val tag = MURALS[fi % MURALS.size]
        g.save()
        g.translate(W / 2f, rt + 0.78f)
        g.rotate(-3f)
        g.scale(1f / f.s, 1f / f.s)
        val sz = 0.5f * f.s
        g.text(tag, 3f, 3f, sz, 0x60000000, Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(tag, 0f, 0f, sz, if (fi % 2 == 0) 0x70FF4A9A else 0x704AF0C0, Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.restore()
        for (k in 0 until 6) {
            val dx = 1.5f + hash(k + fi, 84) * 7f
            g.fillRect(dx, rt + 0.8f, dx + 0.02f, rt + 0.95f + hash(k, 85) * 0.4f, if (fi % 2 == 0) 0x50FF4A9A else 0x504AF0C0)
        }
    }

    /** Crystal cave: glowing clusters bursting from the rock. */
    private fun crystals(pal: Palette, fi: Int, rt: Float, gy: Float) {
        g.blend(Gfx.Blend.ADD)
        for (k in 0 until 6) {
            val cx = hash(fi * 5 + k, 86) * W
            val cy = rt + 0.4f + hash(fi * 5 + k, 87) * (gy - rt - 0.8f)
            val c = if (k % 3 == 0) 0xFFFF5AD8.toInt() else if (k % 3 == 1) 0xFF5AD8FF.toInt() else 0xFF8AFFB0.toInt()
            g.glow(cx, cy, 1.1f, Col.alpha(c, 0.45f))
        }
        g.blend(Gfx.Blend.NORMAL)
        for (k in 0 until 6) {
            val cx = hash(fi * 5 + k, 86) * W
            val cy = rt + 0.4f + hash(fi * 5 + k, 87) * (gy - rt - 0.8f)
            val c = if (k % 3 == 0) 0xFFFF5AD8.toInt() else if (k % 3 == 1) 0xFF5AD8FF.toInt() else 0xFF8AFFB0.toInt()
            for (j in 0 until 4) {
                val a = -1.2f + j * 0.8f + hash(j + k, 88) * 0.3f
                val l = 0.3f + hash(j + k * 4 + fi, 89) * 0.3f
                val tx = cx + sin(a) * l
                val ty = cy - cos(a) * l
                poly.tri(g, cx - 0.06f, cy, cx + 0.06f, cy, tx, ty, Col.alpha(c, 0.9f))
                g.line(cx, cy, tx, ty, 0.01f, 0xC0FFFFFF.toInt())
            }
        }
    }

    /** Coal seams: glossy black bands through the rock. */
    private fun coal(pal: Palette, fi: Int, rt: Float, gy: Float) {
        for (b in 0 until 3) {
            val y = rt + 0.5f + b * 0.9f + hash(fi + b, 90) * 0.2f
            poly.begin()
            poly.add(-0.1f, y + 0.3f)
            for (k in 0..8) poly.add(k * 1.25f, y + sin(k * 1.1f + b + fi) * 0.08f)
            poly.add(W + 0.1f, y + 0.3f)
            poly.fill(g, 0xB0050404.toInt())
            g.line(0f, y + 0.02f, W, y + 0.04f, 0.012f, 0x40FFFFFF)
        }
    }

    /** Foundry plating: riveted steel, hot at the floor. */
    private fun foundry(pal: Palette, rt: Float, gy: Float) {
        g.fillRect(0f, rt, W, gy, 0x14B0A0A0)
        var y = rt + 0.8f
        while (y < gy) {
            g.fillRect(0f, y, W, y + 0.025f, Col.alpha(pal.deep, 0.7f))
            var x = 0.3f
            while (x < W) {
                g.fillCircle(x, y - 0.06f, 0.022f, Col.alpha(pal.trim, 0.45f))
                x += 0.6f
            }
            y += 0.8f
        }
        for (x in SEAMS) g.fillRect(x - HAIR, rt, x + HAIR, gy, Col.alpha(pal.deep, 0.7f))
        g.fillRect(0f, gy - 0.5f, W, gy - 0.46f, Col.alpha(pal.glow, 0.7f))
    }

    /** Obsidian: black glass in sharp facets, veined with fire. */
    private fun obsidian(pal: Palette, fi: Int, rt: Float, gy: Float) {
        for (k in 0 until 8) {
            val x = k * 1.3f + hash(k + fi, 91) * 0.4f
            poly.quad(g, x, rt, x + 1.2f, rt, x + 0.4f + hash(k, 92), gy, x - 0.6f, gy, if (k % 2 == 0) 0x40000000 else 0x0CFFFFFF)
        }
        for (v in 0 until 5) {
            var x = hash(v + fi * 5, 93) * W
            var y = gy
            for (s in 0 until 5) {
                val nx = x + (hash(s + v * 7 + fi, 94) - 0.5f) * 0.8f
                val ny = y - 0.55f
                g.line(x, y, nx, ny, 0.015f, Col.alpha(pal.glow, 0.8f))
                x = nx
                y = ny
            }
        }
    }

    /** Flesh: the walls are alive, veins pulsing. */
    private fun flesh(pal: Palette, fi: Int, rt: Float, gy: Float) {
        g.fillRect(0f, rt, W, gy, 0x20801020)
        val beat = 0.6f + 0.4f * sin(f.t * 5f) * sin(f.t * 5f)
        for (v in 0 until 7) {
            var x = hash(v + fi * 3, 95) * W
            var y = rt + 0.15f
            var w = 0.05f
            for (s in 0 until 6) {
                val nx = x + (hash(s + v * 11 + fi, 96) - 0.5f) * 0.9f
                val ny = y + 0.5f
                g.line(x, y, nx, ny, w, Col.alpha(0xFF6A0010.toInt(), 0.8f))
                g.line(x, y, nx, ny, w * 0.35f, Col.alpha(0xFFFF3050.toInt(), 0.45f * beat))
                if (s == 2) g.line(nx, ny, nx + 0.4f, ny + 0.3f, w * 0.6f, Col.alpha(0xFF6A0010.toInt(), 0.7f))
                x = nx
                y = ny
                w *= 0.85f
            }
        }
        for (k in 0 until 5) {
            val px = hash(k + fi * 9, 97) * W
            val py = rt + 0.5f + hash(k + fi * 9, 98) * (gy - rt - 1f)
            g.fillCircle(px, py, 0.07f, 0x90802030.toInt())
            g.fillCircle(px - 0.02f, py - 0.02f, 0.02f, 0x60FFFFFF)
        }
    }

    /** Ossuary: skulls packed into niches above and below. */
    private fun ossuary(pal: Palette, rt: Float, gy: Float) {
        val bone = pal.trim
        for (row in 0 until 2) {
            val y = rt + 0.32f + row * 0.26f
            var x = 0.18f + row * 0.13f
            while (x < W) {
                g.fillCircle(x, y, 0.1f, Col.alpha(bone, 0.4f))
                g.fillCircle(x - 0.035f, y, 0.025f, Col.alpha(pal.deep, 0.8f))
                g.fillCircle(x + 0.035f, y, 0.025f, Col.alpha(pal.deep, 0.8f))
                x += 0.26f
            }
        }
        var x = 0.2f
        while (x < W) {
            g.line(x - 0.1f, gy - 0.2f, x + 0.1f, gy - 0.16f, 0.04f, Col.alpha(bone, 0.35f))
            g.line(x - 0.1f, gy - 0.28f, x + 0.12f, gy - 0.3f, 0.04f, Col.alpha(bone, 0.3f))
            x += 0.28f
        }
    }
}
