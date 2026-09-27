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

/** The building cutaway: rooms, roof, slabs, stairwells, doors, shafts, lamps, hazards. */
internal class Building(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val carOpen = HashMap<Int, FloatArray>()
    private val cutBuf = FloatArray(8)

    companion object {
        const val SLAB = 0.35f
        const val H = Geo.FLOOR_H
        const val W = Geo.FLOOR_W
        const val DOOR_H = 2.25f
    }

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

        // Stairwell behind everything (it reaches up through the slab hole above).
        stairwell(fs, pal, top, gy)

        g.save()
        g.clipRect(0f, rt, W, gy)
        g.fillVerticalGradient(0f, rt, W, gy, pal.wallTop, pal.wallBottom)
        wallPattern(zone, pal, fi, rt, gy)

        // Free slots become windows onto the backdrop, or furniture.
        for (i in Geo.SLOTS.indices) {
            val sx = Geo.SLOTS[i]
            val used = plan.doors.any { it.x == sx } || plan.shafts.any { it.x == sx } || plan.hazards.any { it.x == sx }
            if (used) continue
            val hv = hash(fi * 13 + i, 5)
            if (hv < 0.6f) window(zone, pal, sx, rt, gy, plan.isVoid, backdrop, fi * 7 + i) else decor(zone, pal, sx, gy, rt, (hv * 1000).toInt())
        }
        // Stair-down side: sign on the wall.
        stairSign(plan.stairsDown, pal, rt)

        // Ceiling and baseboard trims.
        g.fillRect(0f, rt, W, rt + 0.1f, Col.mul(pal.wallBottom, 0.6f))
        g.fillRect(0f, rt + 0.1f, W, rt + 0.13f, Col.fade(pal.neon, 0.55f))
        g.fillRect(0f, rt + 0.13f, W, rt + 0.3f, Col.fade(pal.neon, 0.07f))
        g.fillRect(0f, gy - 0.12f, W, gy, Col.mul(pal.wallBottom, 0.55f))
        g.fillRect(0f, gy - 0.14f, W, gy - 0.12f, Col.fade(pal.neon2, 0.4f))

        // Light pools on the wall and floor from live lamps (the darkness overlay dims them otherwise).
        for (i in plan.lights.indices) {
            val lx = plan.lights[i]
            if (fs.lightAlive[i]) {
                g.save()
                g.translate(lx, gy - 0.02f)
                g.scale(1f, 0.16f)
                g.fillRadialGradient(0f, 0f, 1.6f, Col.alpha(pal.lamp, 0.5f), Col.alpha(pal.lamp, 0f))
                g.restore()
                g.save()
                g.translate(lx, gy - 1.2f)
                g.scale(1f, 1.35f)
                g.fillRadialGradient(0f, 0f, 1.5f, Col.alpha(pal.lamp, 0.13f), Col.alpha(pal.lamp, 0f))
                g.restore()
            } else if (fs.lightFall[i] < 0f) {
                brokenLamp(pal, lx, rt, gy, i)
            }
        }
        g.restore()

        for (s in plan.shafts) shaftOnFloor(fs, pal, s.x, s.top, s.bottom, top, rt, gy)
        for (i in plan.doors.indices) door(fs, pal, i, gy)
        for (h in plan.hazards) hazardBase(h.kind, pal, h.x, rt, gy, h.state(f.wt))
    }

    // ------------------------------------------------------------ wall styles

    private fun wallPattern(zone: Zone, pal: Palette, fi: Int, rt: Float, gy: Float) {
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> {
                var x = 0.6f
                while (x < W) {
                    g.fillRect(x, rt, x + 0.03f, gy, pal.panel)
                    x += 1.2f
                }
                g.fillRect(0f, gy - 1.05f, W, gy - 1.0f, Col.fade(pal.neon2, 0.18f))
            }
            Zone.LABS -> {
                var y = rt + 0.9f
                while (y < gy - 0.3f) {
                    g.fillRect(0f, y, W, y + 0.025f, pal.panel)
                    y += 0.8f
                }
                // Hazard stripes along the baseboard.
                g.save()
                g.clipRect(0f, gy - 0.4f, W, gy - 0.14f)
                g.fillRect(0f, gy - 0.4f, W, gy - 0.14f, 0xFF1A1A10.toInt())
                var sx = -0.4f
                while (sx < W) {
                    poly.quad(g, sx, gy - 0.14f, sx + 0.2f, gy - 0.14f, sx + 0.46f, gy - 0.4f, sx + 0.26f, gy - 0.4f, 0xFFE8C020.toInt())
                    sx += 0.4f
                }
                g.restore()
            }
            Zone.METRO -> {
                // Grimy tiles and a line-colour band with the station number.
                val tile = 0x22FFFFFF
                var x = 0f
                while (x < W) {
                    g.fillRect(x, rt, x + 0.012f, gy, tile)
                    x += 0.32f
                }
                var y = rt + 0.3f
                while (y < gy) {
                    g.fillRect(0f, y, W, y + 0.012f, tile)
                    y += 0.24f
                }
                val by = rt + 0.75f
                g.fillRect(0f, by, W, by + 0.24f, pal.neon2)
                g.fillRect(0f, by + 0.24f, W, by + 0.28f, Col.mul(pal.neon2, 0.5f))
                g.fillRect(0f, gy - 0.9f, W, gy - 0.14f, 0x33000000)
            }
            Zone.MINES -> {
                for (k in 0 until 7) {
                    val cx = hash(fi * 17 + k, 21) * W
                    val cy = rt + 0.4f + hash(fi * 17 + k, 22) * (gy - rt - 0.8f)
                    val r = 0.3f + hash(k + fi, 23) * 0.5f
                    poly.begin()
                    for (j in 0 until 6) {
                        val a = j / 6f * 6.283f
                        val rr = r * (0.7f + 0.3f * hash(j + k * 7 + fi, 24))
                        poly.add(cx + kotlin.math.cos(a) * rr * 1.4f, cy + sin(a) * rr)
                    }
                    poly.fill(g, Col.fade(pal.panel, 0.6f))
                }
                // Timber frames.
                var x = 1.9f
                while (x < W - 1f) {
                    g.fillRect(x - 0.1f, rt, x + 0.1f, gy, 0xFF4A301A.toInt())
                    g.fillRect(x - 0.1f, rt, x - 0.06f, gy, 0xFF6A4424.toInt())
                    x += 3.1f
                }
                g.fillRect(0f, rt + 0.1f, W, rt + 0.32f, 0xFF4A301A.toInt())
            }
            Zone.MAGMA -> {
                var y = rt + 0.55f
                var row = 0
                while (y < gy) {
                    g.fillRect(0f, y, W, y + 0.03f, pal.panel)
                    var x = if (row % 2 == 0) 0.4f else 1.0f
                    while (x < W) {
                        g.fillRect(x, y - 0.55f, x + 0.03f, y, pal.panel)
                        x += 1.2f
                    }
                    y += 0.55f
                    row++
                }
                // Glowing cracks.
                for (k in 0 until 2) {
                    var cx = 1f + hash(fi * 5 + k, 31) * 8f
                    var cy = rt + 0.4f
                    val pulse = 0.6f + 0.4f * sin(f.t * 2f + k + fi)
                    for (s in 0 until 6) {
                        val nx = cx + (hash(s + k * 9 + fi, 32) - 0.5f) * 0.6f
                        val ny = cy + 0.4f
                        f.glowLine(cx, cy, nx, ny, 0.03f, Col.fade(pal.neon, pulse), 0, 0.8f)
                        cx = nx
                        cy = ny
                    }
                }
            }
            Zone.HELL -> {
                var y = rt + 0.6f
                var row = 0
                while (y < gy) {
                    g.fillRect(0f, y, W, y + 0.06f, 0x66000000)
                    var x = if (row % 2 == 0) 0f else 0.7f
                    while (x < W) {
                        g.fillRect(x, y - 0.6f, x + 0.06f, y, 0x66000000)
                        x += 1.4f
                    }
                    y += 0.6f
                    row++
                }
                // Dripping blood from the ceiling.
                for (k in 0 until 6) {
                    val dx = hash(fi * 3 + k, 41) * W
                    val len = 0.2f + hash(fi * 3 + k, 42) * 0.7f
                    g.fillRoundRect(dx - 0.03f, rt, dx + 0.03f, rt + 0.15f + len, 0.03f, 0xFF7A0010.toInt())
                    g.fillCircle(dx, rt + 0.15f + len, 0.05f, 0xFF7A0010.toInt())
                }
            }
            Zone.VOID -> Unit
        }
    }

    private fun window(zone: Zone, pal: Palette, sx: Float, rt: Float, gy: Float, isVoid: Boolean, backdrop: Backdrop, salt: Int) {
        val x0 = sx - 0.46f
        val x1 = sx + 0.46f
        val y0 = rt + 0.5f
        val y1 = gy - 0.95f
        g.save()
        g.clipRect(x0, y0, x1, y1)
        backdrop.zone(zone, pal, x0, y0, x1, y1, isVoid)
        // Glass: tint, reflection streak.
        g.fillRect(x0, y0, x1, y1, Col.alpha(pal.neon2, 0.05f))
        poly.quad(g, x0 + 0.1f, y1, x0 + 0.35f, y1, x1 - 0.05f, y0, x1 - 0.3f, y0, 0x12FFFFFF)
        if (zone == Zone.TOWER) {
            // Half-drawn blinds.
            val drop = 0.25f + hash(salt, 51) * 0.5f
            var y = y0
            while (y < y0 + drop) {
                g.fillRect(x0, y, x1, y + 0.06f, Col.mul(pal.panel, 1.3f))
                y += 0.09f
            }
        }
        g.restore()
        val frame = Col.mul(pal.panel, 1.4f)
        g.strokeRect(x0, y0, x1, y1, 0.07f, frame)
        g.fillRect(sx - 0.02f, y0, sx + 0.02f, y1, frame)
        g.fillRect(x0 - 0.08f, y1, x1 + 0.08f, y1 + 0.08f, Col.mul(pal.panel, 1.6f))
        g.fillRect(x0 - 0.08f, y1, x1 + 0.08f, y1 + 0.02f, Col.fade(pal.neon, 0.4f))
    }

    private fun stairSign(side: Side, pal: Palette, rt: Float) {
        val x = if (side == Side.LEFT) 0.72f else W - 0.72f
        val y = rt + 0.75f
        g.fillRoundRect(x - 0.42f, y - 0.2f, x + 0.42f, y + 0.2f, 0.06f, 0xDD06120A.toInt())
        g.strokeRoundRect(x - 0.42f, y - 0.2f, x + 0.42f, y + 0.2f, 0.06f, 0.025f, 0xFF3CFF7A.toInt())
        f.worldText("EXIT", x + 0.08f, y + 0.09f, 0.24f, 0xFF3CFF7A.toInt())
        Glyphs.arrow(g, x - 0.27f, y, 0.1f, 0f, 1f, 0.03f, 0xFF3CFF7A.toInt())
        g.fillRoundRect(x - 0.5f, y - 0.28f, x + 0.5f, y + 0.28f, 0.1f, 0x0E3CFF7A)
    }

    // ----------------------------------------------------------------- decor

    private fun decor(zone: Zone, pal: Palette, sx: Float, gy: Float, rt: Float, v: Int) {
        val kind = v % 4
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP -> when (kind) {
                0 -> plant(sx, gy)
                1 -> cooler(sx, gy)
                2 -> neonSign(pal, sx, rt + 1.0f, if (v % 3 == 0) "NEXUS" else if (v % 3 == 1) "24/7" else "SYNC")
                else -> poster(pal, sx, rt + 0.7f)
            }
            Zone.LABS -> when (kind) {
                0 -> warnSign(sx, rt + 1.0f)
                1 -> labBench(pal, sx, gy)
                2 -> tank(pal, sx, gy)
                else -> monitor(pal, sx, rt + 0.8f)
            }
            Zone.METRO -> when (kind) {
                0 -> vending(pal, sx, gy)
                1 -> bench(sx, gy)
                2 -> metroMap(pal, sx, rt + 1.1f)
                else -> poster(pal, sx, rt + 1.15f)
            }
            Zone.MINES -> when (kind) {
                0, 3 -> crates(sx, gy)
                1 -> lantern(pal, sx, rt + 1.2f)
                else -> cart(sx, gy)
            }
            Zone.MAGMA -> when (kind) {
                0 -> gauge(pal, sx, rt + 1.0f)
                1, 3 -> coolant(pal, sx, gy)
                else -> warnSign(sx, rt + 1.0f)
            }
            Zone.HELL -> when (kind) {
                0 -> candles(sx, gy)
                1 -> rune(pal, sx, rt + 1.3f)
                2 -> skulls(sx, gy)
                else -> chains(sx, rt)
            }
            Zone.VOID -> neonSign(pal, sx, rt + 1.1f, "NULL")
        }
    }

    private fun plant(x: Float, gy: Float) {
        poly.quad(g, x - 0.22f, gy - 0.45f, x + 0.22f, gy - 0.45f, x + 0.16f, gy - 0.02f, x - 0.16f, gy - 0.02f, 0xFF2A1A3A.toInt())
        g.fillRect(x - 0.24f, gy - 0.5f, x + 0.24f, gy - 0.44f, 0xFF3C2A56.toInt())
        for (k in 0 until 7) {
            val a = -60f + k * 20f
            g.save()
            g.translate(x, gy - 0.48f)
            g.rotate(a + sin(f.t * 1.3f + k) * 3f)
            poly.quad(g, 0f, 0f, 0.08f, -0.35f, 0f, -0.75f - (k % 2) * 0.15f, -0.08f, -0.35f, if (k % 2 == 0) 0xFF1E8A5A.toInt() else 0xFF136A44.toInt())
            g.restore()
        }
    }

    private fun cooler(x: Float, gy: Float) {
        g.fillRoundRect(x - 0.2f, gy - 1.0f, x + 0.2f, gy - 0.02f, 0.04f, 0xFFD8D8E8.toInt())
        g.fillRect(x - 0.2f, gy - 0.62f, x + 0.2f, gy - 0.58f, 0xFF9090A8.toInt())
        g.fillRoundRect(x - 0.16f, gy - 1.5f, x + 0.16f, gy - 1.0f, 0.1f, 0xAA6AC8FF.toInt())
        g.fillRect(x - 0.1f, gy - 1.4f, x - 0.06f, gy - 1.05f, 0x60FFFFFF)
        g.fillRect(x - 0.05f, gy - 0.8f, x + 0.05f, gy - 0.72f, 0xFF3060FF.toInt())
    }

    private fun neonSign(pal: Palette, x: Float, y: Float, text: String) {
        val flick = if (hash((f.t * 6f).toInt(), text.length) > 0.95f) 0.35f else 1f
        g.fillRoundRect(x - 0.48f, y - 0.3f, x + 0.48f, y + 0.3f, 0.08f, 0x55000000)
        g.strokeRoundRect(x - 0.46f, y - 0.28f, x + 0.46f, y + 0.28f, 0.08f, 0.1f, Col.fade(pal.neon, 0.2f * flick))
        g.strokeRoundRect(x - 0.46f, y - 0.28f, x + 0.46f, y + 0.28f, 0.08f, 0.03f, Col.fade(pal.neon, flick))
        f.worldText(text, x, y + 0.1f, 0.26f, Col.fade(pal.neon2, flick), Gfx.Font.TITLE)
    }

    private fun poster(pal: Palette, x: Float, y: Float) {
        g.fillRect(x - 0.34f, y, x + 0.34f, y + 0.9f, 0xFF0A0614.toInt())
        g.fillVerticalGradient(x - 0.3f, y + 0.04f, x + 0.3f, y + 0.86f, Col.mul(pal.neon, 0.6f), Col.mul(pal.neon2, 0.35f))
        g.fillCircle(x, y + 0.4f, 0.18f, 0xFFFFD860.toInt())
        for (k in 0 until 3) g.fillRect(x - 0.3f, y + 0.44f + k * 0.06f, x + 0.3f, y + 0.46f + k * 0.06f, 0xFF0A0614.toInt())
        poly.tri(g, x - 0.3f, y + 0.86f, x, y + 0.55f, x + 0.3f, y + 0.86f, 0xFF140A24.toInt())
    }

    private fun warnSign(x: Float, y: Float) {
        poly.tri(g, x, y - 0.32f, x + 0.36f, y + 0.3f, x - 0.36f, y + 0.3f, 0xFFE8C020.toInt())
        poly.tri(g, x, y - 0.2f, x + 0.25f, y + 0.23f, x - 0.25f, y + 0.23f, 0xFF1A1A10.toInt())
        f.worldText("!", x, y + 0.2f, 0.34f, 0xFFE8C020.toInt(), Gfx.Font.TITLE)
    }

    private fun labBench(pal: Palette, x: Float, gy: Float) {
        g.fillRect(x - 0.48f, gy - 0.75f, x + 0.48f, gy - 0.68f, 0xFFB8C8C8.toInt())
        g.fillRect(x - 0.44f, gy - 0.68f, x - 0.38f, gy, 0xFF607070.toInt())
        g.fillRect(x + 0.38f, gy - 0.68f, x + 0.44f, gy, 0xFF607070.toInt())
        for (k in 0 until 3) {
            val fx = x - 0.25f + k * 0.25f
            val c = if (k == 1) pal.neon2 else pal.neon
            g.fillRect(fx - 0.03f, gy - 1.02f, fx + 0.03f, gy - 0.9f, 0x80FFFFFF.toInt())
            poly.quad(g, fx - 0.03f, gy - 0.9f, fx + 0.03f, gy - 0.9f, fx + 0.1f, gy - 0.75f, fx - 0.1f, gy - 0.75f, Col.fade(c, 0.85f))
            g.fillCircle(fx, gy - 0.82f, 0.18f, Col.fade(c, 0.12f))
        }
    }

    private fun tank(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.34f, gy - 2.1f, x + 0.34f, gy - 0.1f, 0.3f, Col.fade(pal.neon, 0.25f))
        g.fillRoundRect(x - 0.24f, gy - 1.95f, x + 0.24f, gy - 0.25f, 0.2f, Col.fade(pal.neon, 0.2f))
        g.strokeRoundRect(x - 0.34f, gy - 2.1f, x + 0.34f, gy - 0.1f, 0.3f, 0.04f, 0xFF7AA8A0.toInt())
        g.fillRect(x - 0.4f, gy - 0.2f, x + 0.4f, gy, 0xFF2A3A3A.toInt())
        g.fillRect(x - 0.4f, gy - 2.25f, x + 0.4f, gy - 2.05f, 0xFF2A3A3A.toInt())
        for (b in 0 until 4) {
            val by = gy - 0.3f - fract(f.t * 0.4f + b * 0.25f) * 1.6f
            g.fillCircle(x - 0.1f + (b % 2) * 0.2f, by, 0.03f, Col.fade(pal.neon, 0.8f))
        }
    }

    private fun monitor(pal: Palette, x: Float, y: Float) {
        g.fillRoundRect(x - 0.45f, y, x + 0.45f, y + 0.6f, 0.04f, 0xFF0A1414.toInt())
        g.fillRect(x - 0.4f, y + 0.05f, x + 0.4f, y + 0.55f, Col.mul(pal.neon2, 0.15f))
        var px = x - 0.4f
        var py = y + 0.3f
        for (k in 1..12) {
            val nx = x - 0.4f + k * (0.8f / 12f)
            val ny = y + 0.3f + sin(k * 1.9f + f.t * 4f) * 0.15f * (if (k % 4 == 0) 1.4f else 0.5f)
            g.line(px, py, nx, ny, 0.025f, pal.neon)
            px = nx
            py = ny
        }
    }

    private fun vending(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.4f, gy - 1.9f, x + 0.4f, gy - 0.02f, 0.05f, 0xFF20202A.toInt())
        g.fillRect(x - 0.34f, gy - 1.82f, x + 0.16f, gy - 0.5f, Col.mul(pal.neon2, 0.55f))
        for (r in 0 until 5) for (c in 0 until 3) {
            val cc = when ((r + c) % 3) {
                0 -> 0xFFFF5060.toInt()
                1 -> 0xFFFFD040.toInt()
                else -> 0xFF60FF90.toInt()
            }
            g.fillRect(x - 0.3f + c * 0.15f, gy - 1.74f + r * 0.24f, x - 0.2f + c * 0.15f, gy - 1.6f + r * 0.24f, cc)
        }
        g.fillRect(x + 0.2f, gy - 1.5f, x + 0.34f, gy - 1.1f, 0xFF404050.toInt())
        g.fillRect(x - 0.34f, gy - 0.42f, x + 0.16f, gy - 0.22f, 0xFF050508.toInt())
        g.fillRect(x - 0.45f, gy - 1.95f, x + 0.45f, gy, Col.fade(pal.neon2, 0.08f))
    }

    private fun bench(x: Float, gy: Float) {
        g.fillRect(x - 0.48f, gy - 0.5f, x + 0.48f, gy - 0.42f, 0xFF7A6A50.toInt())
        g.fillRect(x - 0.48f, gy - 0.9f, x + 0.48f, gy - 0.84f, 0xFF7A6A50.toInt())
        g.fillRect(x - 0.4f, gy - 0.9f, x - 0.36f, gy, 0xFF3A3530.toInt())
        g.fillRect(x + 0.36f, gy - 0.9f, x + 0.4f, gy, 0xFF3A3530.toInt())
    }

    private fun metroMap(pal: Palette, x: Float, y: Float) {
        g.fillRect(x - 0.46f, y - 0.35f, x + 0.46f, y + 0.35f, 0xFFE8E4D8.toInt())
        g.line(x - 0.4f, y - 0.1f, x + 0.4f, y - 0.1f, 0.05f, pal.neon2)
        g.line(x - 0.4f, y + 0.2f, x - 0.1f, y - 0.1f, 0.05f, 0xFFE02030.toInt())
        g.line(x - 0.1f, y - 0.1f, x + 0.3f, y + 0.25f, 0.05f, 0xFFE02030.toInt())
        g.line(x - 0.2f, y - 0.3f, x + 0.1f, y + 0.3f, 0.05f, pal.neon)
        for (k in 0 until 4) g.fillCircle(x - 0.3f + k * 0.2f, y - 0.1f, 0.04f, 0xFFFFFFFF.toInt())
    }

    private fun crates(x: Float, gy: Float) {
        crate(x - 0.22f, gy, 0.42f)
        crate(x + 0.22f, gy, 0.42f)
        crate(x, gy - 0.42f, 0.4f)
    }

    private fun crate(x: Float, bottom: Float, s: Float) {
        g.fillRect(x - s / 2, bottom - s, x + s / 2, bottom, 0xFF6A4A2A.toInt())
        g.strokeRect(x - s / 2 + 0.02f, bottom - s + 0.02f, x + s / 2 - 0.02f, bottom - 0.02f, 0.035f, 0xFF3A2614.toInt())
        g.line(x - s / 2 + 0.04f, bottom - 0.04f, x + s / 2 - 0.04f, bottom - s + 0.04f, 0.035f, 0xFF3A2614.toInt())
    }

    private fun lantern(pal: Palette, x: Float, y: Float) {
        g.line(x, y - 0.6f, x, y - 0.2f, 0.02f, 0xFF201810.toInt())
        f.glowDot(x, y, 0.08f, pal.neon, 1f)
        g.fillRadialGradient(x, y, 1.2f, Col.alpha(pal.neon, 0.18f), Col.alpha(pal.neon, 0f))
        g.strokeRoundRect(x - 0.1f, y - 0.18f, x + 0.1f, y + 0.14f, 0.03f, 0.025f, 0xFF201810.toInt())
    }

    private fun cart(x: Float, gy: Float) {
        poly.quad(g, x - 0.46f, gy - 0.7f, x + 0.46f, gy - 0.7f, x + 0.36f, gy - 0.15f, x - 0.36f, gy - 0.15f, 0xFF4A4E56.toInt())
        for (k in 0 until 4) g.fillCircle(x - 0.3f + k * 0.2f, gy - 0.75f, 0.1f, 0xFF3A3028.toInt())
        g.fillCircle(x - 0.23f, gy - 0.1f, 0.1f, 0xFF20201C.toInt())
        g.fillCircle(x + 0.23f, gy - 0.1f, 0.1f, 0xFF20201C.toInt())
    }

    private fun gauge(pal: Palette, x: Float, y: Float) {
        g.fillRect(x - 0.5f, y + 0.25f, x + 0.5f, y + 0.35f, 0xFF3A2A24.toInt())
        g.fillCircle(x, y, 0.28f, 0xFF2A1A16.toInt())
        g.fillCircle(x, y, 0.23f, 0xFFE8D8C0.toInt())
        val a = -2.2f + (0.6f + 0.4f * sin(f.t * 3f)) * 3.6f
        g.line(x, y, x + kotlin.math.cos(a) * 0.18f, y + sin(a) * 0.18f, 0.03f, 0xFFD02010.toInt())
        g.fillCircle(x, y, 0.28f * 1.4f, Col.fade(pal.neon, 0.06f))
    }

    private fun coolant(pal: Palette, x: Float, gy: Float) {
        g.fillRoundRect(x - 0.3f, gy - 1.5f, x + 0.3f, gy - 0.02f, 0.2f, 0xFF4A3E3A.toInt())
        g.fillRect(x - 0.3f, gy - 1.1f, x + 0.3f, gy - 0.9f, 0xFFE8C020.toInt())
        g.fillRect(x - 0.08f, gy - 1.3f, x + 0.08f, gy - 0.25f, 0x5530D0FF)
        g.fillRect(x - 0.08f, gy - 0.7f, x + 0.08f, gy - 0.25f, 0xCC30D0FF.toInt())
        g.fillRadialGradient(x, gy - 0.5f, 0.6f, Col.alpha(pal.neon, 0.15f), Col.alpha(pal.neon, 0f))
    }

    private fun candles(x: Float, gy: Float) {
        for (k in 0 until 5) {
            val cx = x - 0.36f + k * 0.18f
            val h = 0.2f + hash(k, 61) * 0.3f
            g.fillRect(cx - 0.04f, gy - h, cx + 0.04f, gy, 0xFFE8D8B8.toInt())
            val fl = sin(f.t * 12f + k * 2f) * 0.02f
            poly.tri(g, cx - 0.04f, gy - h, cx + 0.04f, gy - h, cx + fl, gy - h - 0.14f, 0xFFFFC040.toInt())
            g.fillCircle(cx, gy - h - 0.05f, 0.12f, 0x30FF8020)
        }
    }

    private fun rune(pal: Palette, x: Float, y: Float) {
        val pulse = 0.55f + 0.45f * sin(f.t * 2.5f)
        g.strokeCircle(x, y, 0.42f, 0.1f, Col.fade(pal.neon, 0.2f * pulse))
        g.strokeCircle(x, y, 0.42f, 0.03f, Col.fade(pal.neon, pulse))
        for (k in 0 until 5) {
            val a1 = -1.5708f + k * 2.513f
            val a2 = -1.5708f + (k + 2) * 2.513f
            g.line(x + kotlin.math.cos(a1) * 0.4f, y + sin(a1) * 0.4f, x + kotlin.math.cos(a2) * 0.4f, y + sin(a2) * 0.4f, 0.03f, Col.fade(pal.neon, pulse))
        }
    }

    private fun skulls(x: Float, gy: Float) {
        for (k in 0 until 4) {
            val sx = x - 0.3f + (k % 3) * 0.3f
            val sy = gy - 0.14f - (k / 3) * 0.24f
            g.fillCircle(sx, sy, 0.13f, 0xFFD8C8B0.toInt())
            g.fillCircle(sx - 0.05f, sy, 0.035f, 0xFF200808.toInt())
            g.fillCircle(sx + 0.05f, sy, 0.035f, 0xFF200808.toInt())
        }
    }

    private fun chains(x: Float, rt: Float) {
        for (c in 0 until 2) {
            val cx = x - 0.2f + c * 0.4f
            val len = 1.2f + c * 0.5f
            var y = rt + 0.1f
            var k = 0
            while (y < rt + len) {
                if (k % 2 == 0) g.strokeRoundRect(cx - 0.05f, y, cx + 0.05f, y + 0.16f, 0.05f, 0.025f, 0xFF5A4A4A.toInt())
                else g.line(cx, y, cx, y + 0.16f, 0.035f, 0xFF4A3A3A.toInt())
                y += 0.13f
                k++
            }
        }
    }

    // ------------------------------------------------------------- stairwell

    /** The switchback down from the floor above lands on this floor's arrival side. */
    private fun stairwell(fs: FloorState, pal: Palette, top: Float, gy: Float) {
        val side = fs.plan.arrival
        val xin = if (side == Side.LEFT) Geo.STAIR_W else W - Geo.STAIR_W
        val xw = if (side == Side.LEFT) 0.12f else W - 0.12f
        val x0 = min(xin, if (side == Side.LEFT) 0f else W)
        val x1 = max(xin, if (side == Side.LEFT) 0f else W)
        g.fillVerticalGradient(x0, top, x1, gy, Col.mul(pal.wallBottom, 0.55f), Col.mul(pal.wallBottom, 0.8f))
        g.fillRect(if (side == Side.LEFT) xin - 0.04f else xin, top, if (side == Side.LEFT) xin else xin + 0.04f, gy, Col.mul(pal.panel, 0.8f))
        val mid = top + H * 0.52f
        // Back flight (wall → inner edge, down to this floor) then the landing, then the front flight.
        flight(xw, mid, xin, gy, Col.mul(pal.panel, 0.9f), Col.fade(pal.neon2, 0.35f))
        g.fillRect(min(xw, xw + (xin - xw) * 0.25f), mid, max(xw, xw + (xin - xw) * 0.25f), mid + 0.18f, Col.mul(pal.panel, 1.2f))
        flight(xin, top, xw, mid, Col.mul(pal.panel, 1.25f), Col.fade(pal.neon2, 0.7f))
    }

    private fun flight(xa: Float, ya: Float, xb: Float, yb: Float, color: Int, rail: Int) {
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
        for (k in 0 until n) g.line(xa + dx * k, ya + dy * k, xa + dx * (k + 1), ya + dy * k, 0.02f, Col.mul(color, 1.4f))
        g.line(xa, ya - 0.85f, xb, yb - 0.85f, 0.04f, rail)
        g.line(xa, ya - 0.85f, xa, ya, 0.025f, Col.fade(rail, 0.6f))
        g.line((xa + xb) / 2f, (ya + yb) / 2f - 0.85f, (xa + xb) / 2f, (ya + yb) / 2f, 0.025f, Col.fade(rail, 0.6f))
    }

    // ------------------------------------------------------------------ doors

    private fun door(fs: FloorState, pal: Palette, i: Int, gy: Float) {
        val d = fs.plan.doors[i]
        val x0 = d.x - Geo.DOOR_W / 2f
        val x1 = d.x + Geo.DOOR_W / 2f
        val y0 = gy - DOOR_H
        val intel = d.kind == DoorKind.INTEL
        val used = intel && fs.intelUsed[i]
        val playerIn = f.playerHiddenInDoor(d.x) || (f.w.player.state == PlayerState.INTEL && abs(f.w.player.anchorX - d.x) < 0.05f && f.w.player.floor == fs.plan.index)
        val open = if (playerIn) 1f else fs.doorOpen[i]
        val red = 0xFFFF1E3C.toInt()
        val frameC = if (intel) (if (used) 0xFF5A2A30.toInt() else red) else pal.doorFrame

        // Glow halo for live intel doors.
        if (intel && !used) {
            val pulse = 0.6f + 0.4f * sin(f.t * 3.2f)
            g.fillRoundRect(x0 - 0.3f, y0 - 0.3f, x1 + 0.3f, gy, 0.3f, Col.alpha(red, 0.08f * pulse))
            g.fillRoundRect(x0 - 0.16f, y0 - 0.16f, x1 + 0.16f, gy, 0.2f, Col.alpha(red, 0.14f * pulse))
        }
        // Doorway interior.
        g.fillRect(x0, y0, x1, gy, 0xFF050308.toInt())
        if (open > 0.02f) {
            val spill = if (intel) red else pal.lamp
            g.fillVerticalGradient(x0, y0, x1, gy, Col.alpha(spill, 0.05f), Col.alpha(spill, 0.25f * open))
        }
        // Leaf, swinging on the left hinge.
        val lw = Geo.DOOR_W * (1f - 0.82f * open)
        val leaf = if (intel) (if (used) 0xFF3A1A20.toInt() else 0xFF8A0E22.toInt()) else pal.door
        poly.quad(g, x0, y0, x0 + lw, y0 + 0.12f * open, x0 + lw, gy - 0.02f, x0, gy, leaf)
        if (lw > 0.3f) {
            // Panel inset and handle.
            val inset = Col.mul(leaf, 0.75f)
            g.fillRect(x0 + lw * 0.15f, y0 + 0.25f, x0 + lw * 0.85f, y0 + 0.95f, inset)
            g.fillRect(x0 + lw * 0.15f, y0 + 1.15f, x0 + lw * 0.85f, gy - 0.25f, inset)
            g.fillCircle(x0 + lw * 0.84f, gy - 1.05f, 0.05f, 0xFFD8C890.toInt())
        }
        // Frame.
        g.fillRect(x0 - 0.08f, y0 - 0.1f, x1 + 0.08f, y0, frameC)
        g.fillRect(x0 - 0.08f, y0, x0, gy, frameC)
        g.fillRect(x1, y0, x1 + 0.08f, gy, frameC)
        if (intel) {
            if (!used) {
                f.glowLine(x0 - 0.04f, y0 - 0.05f, x1 + 0.04f, y0 - 0.05f, 0.04f, red, 0xFFFFC0C8.toInt())
                f.glowLine(x0 - 0.04f, y0 - 0.05f, x0 - 0.04f, gy, 0.04f, red, 0xFFFFC0C8.toInt())
                f.glowLine(x1 + 0.04f, y0 - 0.05f, x1 + 0.04f, gy, 0.04f, red, 0xFFFFC0C8.toInt())
            }
            // Data-core icon on the leaf (or floating in the doorway when open).
            val cx = if (lw > 0.5f) x0 + lw / 2f else d.x
            dataCore(cx, y0 + 0.75f, 0.24f, if (used) 0xFF6A3A40.toInt() else 0xFFFFD0D8.toInt(), !used)
            val label = if (used) "CLEARED" else "INTEL"
            val lc = if (used) 0xFF7A4A50.toInt() else 0xFFFF4A64.toInt()
            g.fillRoundRect(d.x - 0.42f, y0 - 0.5f, d.x + 0.42f, y0 - 0.18f, 0.05f, 0xCC120206.toInt())
            f.worldText(label, d.x, y0 - 0.26f, 0.22f, lc)
        } else {
            // Small lit sign above normal doors.
            g.fillRect(d.x - 0.18f, y0 - 0.34f, d.x + 0.18f, y0 - 0.16f, Col.mul(pal.doorFrame, 0.6f))
            g.fillRect(d.x - 0.14f, y0 - 0.3f, d.x + 0.14f, y0 - 0.2f, Col.fade(if (open > 0.1f) 0xFFFF3040.toInt() else pal.neon2, 0.8f))
        }
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
        g.fillVerticalGradient(x0, y0, x1, y1, 0xFF040308.toInt(), 0xFF0C0A12.toInt())
        g.fillRect(x0 + 0.06f, y0, x0 + 0.1f, y1, 0xFF2A2834.toInt())
        g.fillRect(x1 - 0.1f, y0, x1 - 0.06f, y1, 0xFF2A2834.toInt())
        if (fi == topFloor) {
            // Pulley housing.
            g.fillRect(x0, rt, x1, rt + 0.25f, 0xFF1A1822.toInt())
            g.fillCircle(sx, rt + 0.25f, 0.16f, 0xFF3A3846.toInt())
            g.fillCircle(sx, rt + 0.25f, 0.05f, 0xFF12101A.toInt())
        }
        // Landing frame on this floor.
        val fy = gy - 2.6f
        val metal = 0xFF4A4858.toInt()
        g.fillRect(x0 - 0.1f, fy - 0.12f, x1 + 0.1f, fy, metal)
        g.fillRect(x0 - 0.1f, fy, x0, gy, metal)
        g.fillRect(x1, fy, x1 + 0.1f, gy, metal)
        g.fillRect(x0 - 0.1f, fy - 0.12f, x1 + 0.1f, fy - 0.1f, 0x60FFFFFF)
        // Floor indicator.
        val car = f.w.elevators[topFloor]
        val here = car != null && car.doorsOpen && car.atFloor == fi
        val iy = fy - 0.36f
        g.fillRoundRect(sx - 0.3f, iy - 0.14f, sx + 0.3f, iy + 0.14f, 0.05f, 0xFF08070C.toInt())
        if (car != null) {
            val num = car.pos.roundToInt()
            val c = if (here) pal.neon2 else Col.fade(pal.neon, 0.8f)
            f.worldText(if (num == 0) "R" else num.toString(), sx + 0.07f, iy + 0.08f, 0.2f, c)
            val dir = if (car.pos < fi - 0.05f) 1f else if (car.pos > fi + 0.05f) -1f else 0f
            if (dir != 0f) Glyphs.arrow(g, sx - 0.18f, iy, 0.07f, 0f, dir, 0.025f, c) else g.fillCircle(sx - 0.18f, iy, 0.04f, c)
            if (here) g.fillRoundRect(sx - 0.36f, iy - 0.2f, sx + 0.36f, iy + 0.2f, 0.08f, Col.alpha(pal.neon2, 0.15f))
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
            if (!f.visibleY(top - 4f, yb + 0.2f)) continue
            val fs = w.floors[car.pos.roundToInt()] ?: w.floors[s.top]
            val pal = f.palette(fs)
            val state = carOpen.getOrPut(s.id) { floatArrayOf(if (car.doorsOpen) 1f else 0f) }
            val target = if (car.doorsOpen) 1f else 0f
            state[0] += (target - state[0]) * min(1f, f.dt * 7f)
            if (f.dt == 0f) state[0] = target
            val open = state[0]
            val x0 = s.x - 0.52f
            val x1 = s.x + 0.52f
            // Cables from the pulley.
            val pulleyY = s.top * H + SLAB + 0.25f
            g.line(s.x - 0.12f, pulleyY, s.x - 0.12f, top - 0.12f, 0.03f, 0xFF6A6878.toInt())
            g.line(s.x + 0.12f, pulleyY, s.x + 0.12f, top - 0.12f, 0.03f, 0xFF6A6878.toInt())
            // Body and lit interior.
            g.fillRoundRect(x0 - 0.06f, top - 0.16f, x1 + 0.06f, yb + 0.1f, 0.06f, 0xFF2C2A36.toInt())
            g.fillVerticalGradient(x0, top, x1, yb, Col.mul(pal.lamp, 0.55f), Col.mul(pal.lamp, 0.25f))
            g.fillRect(x0 + 0.1f, top + 0.05f, x1 - 0.1f, top + 0.1f, 0xFFFFFFFF.toInt())
            g.fillRect(x0, top + 0.1f, x1, top + 0.5f, Col.alpha(pal.lamp, 0.25f))
            g.fillRect(x0, yb - 0.06f, x1, yb, 0xFF7A7888.toInt())
            val riding = w.player.state == PlayerState.ELEVATOR && w.player.elevatorShaft == s.id
            if (riding) actors.player(force = true)
            // Glass doors.
            val half = (x1 - x0) / 2f
            val closed = half * (1f - open)
            if (closed > 0.01f) {
                val glass = Col.alpha(Col.lerp(pal.lamp, 0xFF203040.toInt(), 0.6f), 0.42f)
                g.fillRect(x0, top, x0 + closed, yb, glass)
                g.fillRect(x1 - closed, top, x1, yb, glass)
                g.fillRect(x0 + closed - 0.03f, top, x0 + closed, yb, 0xFF8A8898.toInt())
                g.fillRect(x1 - closed, top, x1 - closed + 0.03f, yb, 0xFF8A8898.toInt())
                g.line(x0 + 0.05f, yb - 0.3f, x0 + closed * 0.8f, top + 0.4f, 0.03f, 0x30FFFFFF)
            }
            // Hazard stripes on the roof of the car and a status light.
            g.fillRect(x0 - 0.06f, top - 0.16f, x1 + 0.06f, top - 0.1f, pal.neon)
            f.glowDot(s.x, top - 0.24f, 0.05f, if (car.doorsOpen) 0xFF40FF80.toInt() else 0xFFFF4040.toInt(), 0.9f)
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
            if (end > x + 0.001f) slabSegment(pal, palBelow, x, end, gy, fi)
            if (k < n) x = cuts[k + 1]
            k += 2
        }
        // Hazard lip on the stair hole.
        val lip = if (holeL) hole1 else hole0
        g.fillRect(lip - 0.05f, gy, lip + 0.05f, gy + SLAB, 0xFFE8C020.toInt())
    }

    private fun slabSegment(pal: Palette, palBelow: Palette, x0: Float, x1: Float, gy: Float, fi: Int) {
        if (fi == 0) {
            // The roof: thick concrete with a lit edge.
            g.fillRect(x0, gy, x1, gy + SLAB, 0xFF2A2438.toInt())
            g.fillRect(x0, gy, x1, gy + 0.06f, 0xFF5A5070.toInt())
            g.fillRect(x0, gy + SLAB - 0.05f, x1, gy + SLAB, Col.fade(palBelow.neon, 0.6f))
            return
        }
        g.fillRect(x0, gy, x1, gy + SLAB, pal.slab)
        g.fillRect(x0, gy, x1, gy + 0.035f, pal.slabEdge)
        g.fillRect(x0, gy + 0.035f, x1, gy + 0.1f, Col.fade(pal.slabEdge, 0.25f))
        g.fillRect(x0, gy + SLAB - 0.04f, x1, gy + SLAB, Col.mul(palBelow.panel, 1.1f))
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
                // Parapets.
                for (side in 0..1) {
                    val x0 = if (side == 0) -0.6f else W
                    g.fillRect(x0, top, x0 + 0.6f, bottom, 0xFF1E1A2C.toInt())
                    g.fillRect(x0, top, x0 + 0.6f, top + 0.08f, 0xFF4A4262.toInt())
                    g.fillRect(x0, top + 0.08f, x0 + 0.6f, top + 0.11f, Col.fade(pal.neon, 0.7f))
                }
                continue
            }
            for (side in 0..1) {
                val x0 = if (side == 0) -0.6f else W
                g.fillRect(x0, top - SLAB, x0 + 0.6f, bottom, pal.outer)
                // Window slit with interior light.
                val lit = hash(i * 2 + side, 201) > 0.35f
                val wx = x0 + 0.18f
                g.fillRect(wx, top + 0.7f, wx + 0.24f, top + 2.3f, if (lit) Col.fade(pal.outerLit, 0.55f) else 0xFF08060C.toInt())
                g.fillRect(wx, top + 0.7f, wx + 0.24f, top + 0.8f, 0x30FFFFFF)
                // Floor ledge.
                g.fillRect(x0, bottom - SLAB, x0 + 0.6f, bottom - SLAB + 0.08f, Col.mul(pal.outer, 2.2f))
            }
            // Building edge neon.
            g.fillRect(-0.03f, top - SLAB, 0f, bottom, Col.fade(pal.neon, 0.8f))
            g.fillRect(W, top - SLAB, W + 0.03f, bottom, Col.fade(pal.neon, 0.8f))
        }
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
    }

    private fun acUnit(x: Float, gy: Float) {
        g.fillRect(x - 0.35f, gy - 0.5f, x + 0.35f, gy, 0xFF2A2440.toInt())
        g.fillRect(x - 0.35f, gy - 0.5f, x + 0.35f, gy - 0.46f, 0xFF4A4266.toInt())
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
        for (lx in floatArrayOf(x0 + 0.5f, x1 - 0.5f)) {
            g.fillRect(lx - 0.07f, y1, lx + 0.07f, gy, legC)
            g.line(lx - 0.07f, y1 + 0.3f, lx + 0.5f * (if (lx < 5f) 1 else -1), gy - 0.1f, 0.04f, legC)
        }
        g.fillRect(x0 + 0.3f, y1 + 0.8f, x1 - 0.3f, y1 + 0.9f, legC)
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
        for ((k, row) in rows.withIndex()) {
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
        g.fillRect(x0, top, x1, gy, 0xFF221B36.toInt())
        g.fillRect(x0 - 0.12f, top - 0.14f, x1 + 0.12f, top, 0xFF3A3056.toInt())
        g.fillRect(x0 - 0.12f, top - 0.14f, x1 + 0.12f, top - 0.11f, Col.fade(pal.neon, 0.8f))
        // Brick hint.
        var y = top + 0.3f
        var r = 0
        while (y < gy) {
            g.fillRect(x0, y, x1, y + 0.02f, 0xFF1A1428.toInt())
            y += 0.3f
            r++
        }
        // Doorway down.
        val dx = if (right) W - 0.8f else 0.8f
        g.fillRect(dx - 0.42f, gy - 2.0f, dx + 0.42f, gy, 0xFF050308.toInt())
        g.fillVerticalGradient(dx - 0.42f, gy - 2.0f, dx + 0.42f, gy, 0x10FFD0A0, 0x40FFD0A0)
        g.strokeRect(dx - 0.45f, gy - 2.03f, dx + 0.45f, gy, 0.06f, 0xFF4A3E6A.toInt())
        // Door lamp.
        f.glowDot(dx, gy - 2.18f, 0.06f, 0xFFFFD9A0.toInt(), 1f)
        poly.quad(g, dx - 0.08f, gy - 2.15f, dx + 0.08f, gy - 2.15f, dx + 0.7f, gy, dx - 0.7f, gy, 0x14FFD9A0)
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
        // Snapped cable, sparking now and then; wreck on the floor.
        g.line(lx, rt + 0.1f, lx + 0.05f, rt + 0.35f, 0.025f, 0xFF1A1A1A.toInt())
        if (hash((f.t * 10f).toInt(), i + 300) > 0.85f) f.glowDot(lx + 0.05f, rt + 0.36f, 0.035f, 0xFFFFE080.toInt(), 1f)
        g.save()
        g.translate(lx + 0.15f, gy - 0.07f)
        g.rotate(160f)
        poly.quad(g, -0.25f, -0.1f, 0.25f, -0.1f, 0.14f, 0.08f, -0.14f, 0.08f, Col.mul(pal.panel, 0.7f))
        g.restore()
        for (k in 0 until 4) g.fillRect(lx - 0.4f + k * 0.25f, gy - 0.04f, lx - 0.36f + k * 0.25f, gy, 0x80A0E0FF.toInt())
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
        if (onFloor) {
            g.fillRectRadial(
                -0.6f, top, W + 0.6f, gy + 0.05f, p.x, gy - p.z - 0.8f, 2.6f,
                Col.alpha(dark, a * 0.08f), Col.alpha(dark, a),
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
        for (i in plan.lights.indices) {
            val lx = plan.lights[i]
            val fall = fs.lightFall[i]
            if (fs.lightAlive[i]) {
                lamp(pal, lx, rt, gy, 0f, true)
            } else if (fall >= 0f) {
                val t = (fall / World.LIGHT_FALL_TIME).coerceIn(0f, 1f)
                val y = (gy - 0.25f - rt - 0.45f) * t * t
                g.line(lx, rt + 0.05f, lx, rt + 0.2f, 0.025f, 0xFF1A1A1A.toInt())
                lamp(pal, lx, rt, gy, y, false, t * 50f)
            }
        }
        for (h in plan.hazards) hazardLive(h.kind, pal, h.x, rt, gy, h.state(f.wt), plan.zone)
    }

    private fun lamp(pal: Palette, lx: Float, rt: Float, gy: Float, drop: Float, alive: Boolean, spin: Float = 0f) {
        val cy = rt + 0.4f + drop
        if (alive) {
            g.line(lx, rt + 0.05f, lx, cy - 0.1f, 0.025f, 0xFF1A1A22.toInt())
            // Haze cone.
            poly.quad(g, lx - 0.2f, cy + 0.08f, lx + 0.2f, cy + 0.08f, lx + 1.45f, gy, lx - 1.45f, gy, Col.alpha(pal.lamp, 0.07f))
            poly.quad(g, lx - 0.16f, cy + 0.08f, lx + 0.16f, cy + 0.08f, lx + 0.8f, gy, lx - 0.8f, gy, Col.alpha(pal.lamp, 0.06f))
        }
        g.save()
        g.translate(lx, cy)
        if (spin != 0f) g.rotate(spin)
        poly.quad(g, -0.1f, -0.12f, 0.1f, -0.12f, 0.26f, 0.08f, -0.26f, 0.08f, 0xFF2A2834.toInt())
        g.fillRect(-0.26f, 0.06f, 0.26f, 0.09f, if (alive) pal.lamp else 0xFF3A3A40.toInt())
        g.restore()
        if (alive) {
            g.fillCircle(lx, cy + 0.1f, 0.3f, Col.alpha(pal.lamp, 0.22f))
            g.fillCircle(lx, cy + 0.1f, 0.14f, Col.alpha(pal.lamp, 0.5f))
        }
    }

    private fun hazardBase(kind: HazardKind, pal: Palette, x: Float, rt: Float, gy: Float, st: Float) {
        when (kind) {
            HazardKind.LASER -> {
                val c = 0xFF2A2A34.toInt()
                g.fillRoundRect(x - 0.18f, rt + 0.1f, x + 0.18f, rt + 0.34f, 0.04f, c)
                g.fillRoundRect(x - 0.18f, gy - 0.22f, x + 0.18f, gy, 0.04f, c)
                g.fillRect(x - 0.06f, rt + 0.34f, x + 0.06f, rt + 0.4f, Col.fade(pal.laser, 0.4f + st * 0.6f))
                g.fillRect(x - 0.06f, gy - 0.28f, x + 0.06f, gy - 0.22f, Col.fade(pal.laser, 0.4f + st * 0.6f))
            }
            HazardKind.VENT -> {
                g.fillRect(x - 0.42f, gy - 0.1f, x + 0.42f, gy + 0.04f, 0xFF15131A.toInt())
                for (k in 0 until 6) g.fillRect(x - 0.36f + k * 0.13f, gy - 0.08f, x - 0.3f + k * 0.13f, gy, 0xFF3A3640.toInt())
                g.fillRect(x - 0.46f, gy - 0.14f, x + 0.46f, gy - 0.1f, 0xFFE8C020.toInt())
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
