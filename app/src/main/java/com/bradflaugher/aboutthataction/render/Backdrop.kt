package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The world outside the building, seen above the roof and through every window:
 * a parallax city at night, then (as you dig) labs, tunnels, rock, magma, hell, the void.
 * All drawing is culled to the region the caller has clipped to.
 */
internal class Backdrop(private val f: Frame) {
    private val g get() = f.g

    /** World y of something at backdrop height [by] on a layer with parallax [p] (0 = fixed to screen, 1 = world). */
    private fun py(by: Float, p: Float) = by + f.camY * (1f - p)

    // ------------------------------------------------------------ rooftop sky

    fun sky(roofVisible: Boolean) {
        val pal = Palette.of(Zone.ROOFTOP)
        val top = f.camY - 1f
        val bottom = Geo.groundY(0) + 0.5f
        val l = -0.6f
        val r = Geo.FLOOR_W + 0.6f
        g.save()
        g.clipRect(l, top, r, bottom)
        // Screen-anchored gradient: deep indigo overhead to a magenta horizon.
        g.fillVerticalGradient(l, top, r, py(9f, 0.05f), pal.skyTop, pal.skyBottom)
        g.fillRect(l, py(9f, 0.05f) - 0.01f, r, bottom, pal.skyBottom)
        stars(top, bottom)
        synthSun(pal)
        clouds(pal, top)
        city(Zone.ROOFTOP, pal, l, top, r, bottom, rooftop = true)
        g.restore()
    }

    /** Long, thin cloud banks lit from below by the city, drifting slowly. */
    private fun clouds(pal: Palette, top: Float) {
        for (i in 0 until 5) {
            val y = py(1.2f + i * 0.95f, 0.05f)
            if (y < top - 1f) continue
            val w = 4f + hash(i, 16) * 5f
            val x = fract(hash(i, 17) + f.t * (0.004f + i * 0.002f)) * (Geo.FLOOR_W + w + 2f) - w - 1f
            val h = 0.16f + hash(i, 18) * 0.14f
            val c = Col.alpha(Col.lerp(pal.skyTop, pal.skyBottom, 0.35f + i * 0.1f), 0.8f)
            g.fillRoundRect(x, y, x + w, y + h, h / 2f, c)
            g.fillRoundRect(x + w * 0.2f, y - h * 0.6f, x + w * 0.7f, y + h * 0.3f, h / 2f, c)
            g.fillRect(x + w * 0.1f, y + h - 0.02f, x + w * 0.9f, y + h, Col.alpha(pal.neon, 0.12f))
        }
    }

    /** Two searchlights sweeping the clouds from somewhere down in the city. */
    private fun searchlights(pal: Palette, y1: Float) {
        val poly = f.poly
        for (i in 0 until 2) {
            val ox = if (i == 0) 1.6f else 9.2f
            val oy = py(12.5f, 0.16f)
            if (oy - 12f > y1) continue
            val a = sin(f.t * (0.23f + i * 0.07f) + i * 2.4f) * 0.55f + (if (i == 0) 0.25f else -0.25f)
            val len = 14f
            val dx = kotlin.math.sin(a)
            val dy = -kotlin.math.cos(a)
            val nx = -dy
            val ny = dx
            val w0 = 0.12f
            val w1 = 1.1f
            val ex = ox + dx * len
            val ey = oy + dy * len
            poly.quad(g, ox + nx * w0, oy + ny * w0, ox - nx * w0, oy - ny * w0, ex - nx * w1, ey - ny * w1, ex + nx * w1, ey + ny * w1, Col.alpha(pal.haze, 0.07f))
            poly.quad(g, ox + nx * w0 * 0.5f, oy + ny * w0 * 0.5f, ox - nx * w0 * 0.5f, oy - ny * w0 * 0.5f, ex - nx * w1 * 0.45f, ey - ny * w1 * 0.45f, ex + nx * w1 * 0.45f, ey + ny * w1 * 0.45f, Col.alpha(pal.haze, 0.05f))
        }
    }

    private fun stars(top: Float, bottom: Float) {
        for (i in 0 until 60) {
            val x = hash(i, 11) * 11.2f - 0.6f
            val y = py(hash(i, 12) * 9f - 3.5f, 0.03f)
            if (y < top || y > bottom) continue
            val tw = 0.55f + 0.45f * sin(f.t * (1.5f + hash(i, 13) * 3f) + i)
            val r = 0.015f + hash(i, 14) * 0.03f
            g.fillCircle(x, y, r, Col.alpha(if (i % 7 == 0) 0xFF9FF3FF.toInt() else 0xFFFFFFFF.toInt(), 0.35f + 0.6f * tw))
        }
    }

    /** A striped outrun sun sitting on the skyline. */
    private fun synthSun(pal: Palette) {
        val cx = 7.4f
        val cy = py(5.4f, 0.06f)
        val r = 2.3f
        g.fillRadialGradient(cx, cy, r * 2.8f, 0x55FF5A7A, 0x00FF3D9A)
        g.save()
        g.clipRect(cx - r, cy - r, cx + r, cy + r)
        // Vertical gradient disc: draw the gradient clipped by a stack of slices.
        val slices = 16
        for (i in 0 until slices) {
            val y0 = cy - r + i * (2 * r / slices)
            val y1 = y0 + 2 * r / slices
            // Outrun gaps grow toward the bottom.
            val gap = if (i > slices / 2) (i - slices / 2) * 0.035f else 0f
            val c = Col.lerp(0xFFFFE45A.toInt(), 0xFFFF2E88.toInt(), i / (slices - 1f))
            g.save()
            g.clipRect(cx - r, y0, cx + r, y1 - gap)
            g.fillCircle(cx, cy, r, c)
            g.restore()
        }
        g.restore()
    }

    // ---------------------------------------------------------------- per zone

    /** Draw [zone]'s backdrop into the (already clipped) world region. */
    fun zone(zone: Zone, pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float, isVoid: Boolean) {
        when (zone) {
            Zone.ROOFTOP, Zone.TOWER -> {
                g.fillVerticalGradient(x0, y0, x1, y1, pal.skyTop, pal.skyBottom)
                city(zone, pal, x0, y0, x1, y1, rooftop = false)
            }
            Zone.LABS -> labs(pal, x0, y0, x1, y1)
            Zone.METRO -> metro(pal, x0, y0, x1, y1)
            Zone.MINES -> mines(pal, x0, y0, x1, y1)
            Zone.MAGMA -> magma(pal, x0, y0, x1, y1)
            Zone.HELL -> hell(pal, x0, y0, x1, y1)
            Zone.VOID -> void(pal, x0, y0, x1, y1)
        }
        if (isVoid) glitch(pal, x0, y0, x1, y1)
    }

    // -------------------------------------------------------------------- city

    private fun city(zone: Zone, pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float, rooftop: Boolean) {
        val fog = Col.lerp(pal.skyBottom, pal.haze, 0.25f)
        if (rooftop) searchlights(pal, y1)
        cityLayer(pal.bgFar, 0.10f, 1, 2.0f, 7.5f, 0.5f, 1.2f, x0, y0, x1, y1, 0.28f, 0.34f, false)
        // Atmospheric perspective: fog settles between the layers.
        g.fillVerticalGradient(x0, max(y0, py(4f, 0.12f)), x1, y1, Col.alpha(fog, 0f), Col.alpha(fog, 0.55f))
        cityLayer(pal.bgMid, 0.22f, 2, 4.2f, 10.5f, 0.8f, 1.7f, x0, y0, x1, y1, 0.34f, 0.42f, rooftop)
        g.fillVerticalGradient(x0, max(y0, py(9.5f, 0.28f)), x1, y1, Col.alpha(fog, 0f), Col.alpha(fog, 0.3f))
        if (rooftop) traffic(pal, x0, y0, x1, y1)
        cityLayer(pal.bgNear, 0.38f, 3, 7.0f, 12.5f, 1.1f, 2.2f, x0, y0, x1, y1, 0.42f, 0.5f, rooftop)
        if (zone == Zone.TOWER) {
            // Haze band at the bottom of every window.
            g.fillVerticalGradient(x0, y1 - 1.2f, x1, y1, 0x00FF3D9A, 0x30FF3D9A)
        }
    }

    /** Flying traffic: tiny lit craft drifting between the towers. */
    private fun traffic(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        for (i in 0 until 6) {
            val lane = py(7.5f + (i % 3) * 1.1f, 0.3f)
            if (lane < y0 || lane > y1) continue
            val dir = if (i % 2 == 0) 1f else -1f
            val sp = 0.6f + hash(i, 19) * 0.8f
            val span = Geo.FLOOR_W + 4f
            val u = fract(hash(i, 20) + f.t * sp / span)
            val x = if (dir > 0f) -2f + u * span else Geo.FLOOR_W + 2f - u * span
            if (x < x0 - 0.3f || x > x1 + 0.3f) continue
            g.fillRect(x - 0.12f, lane - 0.02f, x + 0.12f, lane + 0.03f, 0xFF0C0818.toInt())
            g.fillCircle(x + dir * 0.12f, lane, 0.025f, 0xFFFFF0D0.toInt())
            g.fillCircle(x - dir * 0.12f, lane, 0.02f, 0xFFFF3050.toInt())
            g.line(x + dir * 0.12f, lane, x + dir * 0.6f, lane + 0.04f, 0.03f, 0x18FFF0D0)
        }
    }

    private fun cityLayer(
        color: Int, p: Float, salt: Int, topMin: Float, topMax: Float, wMin: Float, wMax: Float,
        x0: Float, y0: Float, x1: Float, y1: Float, colPitch: Float, rowPitch: Float, details: Boolean,
    ) {
        var x = -0.6f - hash(salt, 99) * 0.8f
        var i = 0
        while (x < Geo.FLOOR_W + 0.6f && i < 40) {
            val w = wMin + hash(i, salt * 7 + 1) * (wMax - wMin)
            val bx0 = x
            val bx1 = x + w
            x = bx1 + 0.04f + hash(i, salt * 7 + 5) * 0.12f
            i++
            if (bx1 < x0 || bx0 > x1) continue
            val top = py(topMin + hash(i, salt * 7 + 2) * (topMax - topMin), p)
            if (top > y1) continue
            g.fillRect(bx0, max(top, y0 - 0.1f), bx1, y1, color)
            // Rim light from the sun/haze side and a lit roofline.
            if (top > y0 - 0.1f) g.fillRect(bx0, top, bx1, top + 0.03f, Col.lerp(color, 0xFFFF7AB0.toInt(), 0.35f))
            val rimX = if ((bx0 + bx1) / 2f < 7.4f) bx1 - 0.03f else bx0
            g.fillRect(rimX, max(top, y0 - 0.1f), rimX + 0.03f, y1, Col.lerp(color, 0xFFFF7AB0.toInt(), 0.22f))
            // Stepped crown on some towers.
            if (hash(i, salt * 7 + 3) > 0.6f && top > y0) {
                g.fillRect(bx0 + w * 0.2f, top - 0.35f, bx1 - w * 0.2f, top, color)
                if (details) {
                    g.line(bx0 + w * 0.5f, top - 0.35f, bx0 + w * 0.5f, top - 1.1f, 0.03f, color)
                    val blink = sin(f.t * 3f + i) > 0.2f
                    if (blink) f.glowDot(bx0 + w * 0.5f, top - 1.12f, 0.04f, 0xFFFF3040.toInt())
                }
            }
            // Neon edge strip on near towers.
            if (details && hash(i, salt * 7 + 4) > 0.55f && top > y0 - 20f) {
                val nc = if (hash(i, salt + 40) > 0.5f) 0xFFFF3D9A.toInt() else 0xFF2CF0FF.toInt()
                g.fillRect(bx0, max(top, y0), bx0 + 0.04f, y1, Col.fade(nc, 0.8f))
                g.fillRect(bx0 - 0.04f, max(top, y0), bx0 + 0.08f, y1, Col.fade(nc, 0.15f))
            }
            // Lit windows.
            val cols = max(1, ((w - 0.2f) / colPitch).toInt())
            val margin = (w - cols * colPitch) / 2f + colPitch * 0.25f
            val rStart = max(0, floor((y0 - top - 0.25f) / rowPitch).toInt())
            val rEnd = ceil((y1 - top) / rowPitch).toInt()
            if (rEnd - rStart > 200) continue
            val ww = colPitch * 0.5f
            val wh = rowPitch * 0.45f
            for (r in rStart..rEnd) {
                val wy = top + 0.25f + r * rowPitch
                for (c in 0 until cols) {
                    val hv = hash(r * 131 + c * 17 + i * 1013, salt)
                    if (hv > 0.3f) continue
                    val wc = when {
                        hv < 0.12f -> 0xFFFFC870.toInt()
                        hv < 0.2f -> 0xFF7DF3FF.toInt()
                        else -> 0xFFFF7AC0.toInt()
                    }
                    val wx = bx0 + margin + c * colPitch
                    g.fillRect(wx, wy, wx + ww, wy + wh, Col.fade(wc, 0.35f + p))
                }
            }
        }
    }

    // -------------------------------------------------------------------- labs

    private fun labs(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        g.fillVerticalGradient(x0, y0, x1, y1, pal.bgFar, pal.bgNear)
        // Far: glowing specimen tanks.
        val pT = 0.15f
        val period = 6f
        val k0 = floor((y0 - py(0f, pT)) / period).toInt() - 1
        val k1 = ceil((y1 - py(0f, pT)) / period).toInt()
        for (k in k0..k1) {
            for (j in 0 until 3) {
                val tx = 0.6f + j * 3.6f + hash(k * 3 + j, 71) * 1.4f
                if (tx + 0.6f < x0 || tx - 0.6f > x1) continue
                val ty = py(k * period + 1f, pT)
                val th = 3.4f
                g.fillRoundRect(tx - 0.55f, ty, tx + 0.55f, ty + th, 0.5f, 0x55000000)
                g.fillRoundRect(tx - 0.45f, ty + 0.2f, tx + 0.45f, ty + th - 0.2f, 0.4f, Col.fade(pal.neon, 0.22f))
                g.fillRoundRect(tx - 0.3f, ty + 0.5f, tx + 0.3f, ty + th - 0.5f, 0.3f, Col.fade(pal.neon, 0.18f))
                // Specimen silhouette.
                g.fillCircle(tx, ty + 1.2f, 0.22f, 0x70051410)
                g.fillRoundRect(tx - 0.2f, ty + 1.35f, tx + 0.2f, ty + 2.5f, 0.15f, 0x70051410)
                for (b in 0 until 3) {
                    val bubbleY = ty + th - 0.4f - fract(f.t * 0.3f + hash(b + k * 7 + j, 72)) * (th - 0.8f)
                    g.fillCircle(tx - 0.2f + b * 0.2f, bubbleY, 0.035f, Col.fade(pal.neon, 0.7f))
                }
            }
        }
        // Mid: server racks with blinking LEDs, on catwalk levels.
        val p = 0.32f
        val lvl = 3.2f
        val l0 = floor((y0 - py(0f, p)) / lvl).toInt() - 1
        val l1 = ceil((y1 - py(0f, p)) / lvl).toInt()
        for (l in l0..l1) {
            val ly = py(l * lvl, p)
            g.fillRect(x0, ly + 2.75f, x1, ly + 2.85f, 0xFF0E2A2A.toInt())
            g.line(x0, ly + 2.75f, x1, ly + 2.75f, 0.02f, Col.fade(pal.neon2, 0.4f))
            var rx = -0.4f + hash(l, 73) * 0.5f
            var n = 0
            while (rx < Geo.FLOOR_W + 0.6f && n < 30) {
                val rw = 0.55f
                if (rx + rw >= x0 && rx <= x1) {
                    g.fillRect(rx, ly + 0.5f, rx + rw, ly + 2.75f, 0xFF061312.toInt())
                    g.strokeRect(rx, ly + 0.5f, rx + rw, ly + 2.75f, 0.02f, 0xFF123030.toInt())
                    for (row in 0 until 9) {
                        for (c in 0 until 3) {
                            val hv = hash(row * 7 + c + n * 97 + l * 1777, 74)
                            val on = sin(f.t * (2f + hv * 6f) + hv * 40f) > 0.1f
                            if (hv < 0.55f && on) {
                                val lc = if (hv < 0.15f) 0xFFFF4060.toInt() else if (hv < 0.35f) pal.neon else pal.neon2
                                g.fillRect(rx + 0.1f + c * 0.14f, ly + 0.65f + row * 0.22f, rx + 0.16f + c * 0.14f, ly + 0.7f + row * 0.22f, lc)
                            }
                        }
                    }
                }
                rx += 0.7f
                n++
            }
        }
        // Near: overhead pipes.
        val pp = 0.55f
        val pk0 = floor((y0 - py(0f, pp)) / 3.6f).toInt() - 1
        val pk1 = ceil((y1 - py(0f, pp)) / 3.6f).toInt()
        for (k in pk0..pk1) {
            val yy = py(k * 3.6f, pp)
            g.fillRect(x0, yy, x1, yy + 0.14f, 0xFF0A1C1C.toInt())
            g.fillRect(x0, yy + 0.02f, x1, yy + 0.04f, 0x3380FFD0)
        }
    }

    // ------------------------------------------------------------------- metro

    private fun metro(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        g.fillVerticalGradient(x0, y0, x1, y1, pal.bgFar, pal.bgNear)
        val p = 0.3f
        val lvl = 4f
        val l0 = floor((y0 - py(0f, p)) / lvl).toInt() - 1
        val l1 = ceil((y1 - py(0f, p)) / lvl).toInt()
        for (l in l0..l1) {
            val ly = py(l * lvl, p)
            // Tunnel arch: a dark mouth with a lit rim.
            g.fillRoundRect(x0 - 1f, ly + 0.6f, x1 + 1f, ly + 3.4f, 1.2f, 0xFF050404.toInt())
            g.fillRect(x0, ly + 3.4f, x1, ly + 4f, 0xFF1A1612.toInt())
            // Rails.
            g.fillRect(x0, ly + 3.25f, x1, ly + 3.32f, 0xFF6A5E50.toInt())
            g.fillRect(x0, ly + 3.38f, x1, ly + 3.43f, 0xFF4A4038.toInt())
            // Tunnel lamps.
            for (j in 0 until 6) {
                val lx = j * 2.1f + (l and 1) * 1f
                if (lx < x0 - 0.3f || lx > x1 + 0.3f) continue
                f.glowDot(lx, ly + 1.0f, 0.05f, pal.neon, 0.8f)
            }
            // A train screaming past on some tracks.
            val speed = 9f + hash(l, 81) * 5f
            val cyc = 22f + hash(l, 82) * 10f
            val tx = fract((f.t * speed + hash(l, 83) * 100f) / (cyc * 1f)) * cyc * 1.6f - 14f
            val len = 12f
            if (tx + len > x0 && tx < x1) {
                val ty = ly + 1.45f
                g.fillRoundRect(tx, ty, tx + len, ty + 1.8f, 0.25f, 0xFF20242C.toInt())
                g.fillRect(tx, ty + 1.2f, tx + len, ty + 1.3f, pal.neon2)
                var wx = tx + 0.35f
                while (wx < tx + len - 0.6f) {
                    if (wx + 0.5f > x0 && wx < x1) {
                        g.fillRect(wx, ty + 0.35f, wx + 0.55f, ty + 0.95f, 0xFFFFE6B0.toInt())
                        g.fillRect(wx, ty + 0.35f, wx + 0.55f, ty + 0.45f, 0x40FFFFFF)
                    }
                    wx += 0.8f
                }
                // Headlight bloom.
                g.fillRadialGradient(tx + len, ty + 1.1f, 1.2f, 0x60FFF0C0, 0x00FFF0C0)
            }
        }
        // Near pillars.
        for (j in 0 until 5) {
            val px = j * 2.6f + 0.4f
            if (px + 0.3f < x0 || px > x1) continue
            g.fillRect(px, y0, px + 0.3f, y1, 0xCC0C0A08.toInt())
        }
    }

    // ------------------------------------------------------------------- mines

    private fun mines(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        g.fillVerticalGradient(x0, y0, x1, y1, pal.bgFar, pal.bgNear)
        val p = 0.28f
        val band = 1.3f
        val b0 = floor((y0 - py(0f, p)) / band).toInt() - 1
        val b1 = ceil((y1 - py(0f, p)) / band).toInt()
        val poly = f.poly
        for (b in b0..b1) {
            val by = py(b * band, p)
            val c = when ((b % 4 + 4) % 4) {
                0 -> 0xFF3A2618.toInt()
                1 -> 0xFF2C1C12.toInt()
                2 -> 0xFF45301E.toInt()
                else -> 0xFF22160E.toInt()
            }
            poly.begin()
            poly.add(x1 + 0.2f, by + band + 0.4f)
            poly.add(x0 - 0.2f, by + band + 0.4f)
            val steps = 8
            for (i in 0..steps) {
                val xx = x0 - 0.2f + (x1 - x0 + 0.4f) * i / steps
                poly.add(xx, by + sin(xx * 1.3f + b * 2.1f) * 0.18f + hash(i + b * 13, 91) * 0.1f)
            }
            poly.fill(g, c)
            // Ore glints.
            for (k in 0 until 5) {
                val gx = hash(k + b * 11, 92) * 11.2f - 0.6f
                if (gx < x0 || gx > x1) continue
                val gy = by + 0.3f + hash(k + b * 11, 93) * 0.8f
                val gc = if (hash(k + b, 94) > 0.6f) 0xFF5AD8FF.toInt() else 0xFFFFD84A.toInt()
                val tw = 0.5f + 0.5f * sin(f.t * 2f + k * 3f + b)
                g.fillCircle(gx, gy, 0.05f, Col.fade(gc, 0.4f + 0.6f * tw))
                g.fillCircle(gx, gy, 0.12f, Col.fade(gc, 0.12f * tw))
            }
        }
        // Distant drill rigs.
        val pd = 0.18f
        val per = 7f
        val d0 = floor((y0 - py(0f, pd)) / per).toInt() - 1
        val d1 = ceil((y1 - py(0f, pd)) / per).toInt()
        for (d in d0..d1) {
            val dx = 1f + hash(d, 95) * 8f
            if (dx + 1f < x0 || dx - 1f > x1) continue
            val dy = py(d * per + 2f, pd)
            g.fillRect(dx - 0.5f, dy, dx + 0.5f, dy + 1.2f, 0xFF1A120C.toInt())
            poly.tri(g, dx - 0.45f, dy + 1.2f, dx + 0.45f, dy + 1.2f, dx, dy + 2.6f, 0xFF2A1E14.toInt())
            val ph = fract(f.t * 1.5f)
            for (s in 0 until 4) {
                val yy = dy + 1.3f + (s + ph) * 0.32f
                val half = 0.45f * (1f - (yy - dy - 1.2f) / 1.4f)
                if (half > 0f) g.line(dx - half, yy, dx + half, yy + 0.1f, 0.04f, 0xFF55402A.toInt())
            }
            f.glowDot(dx, dy + 0.2f, 0.06f, pal.neon, 0.9f)
        }
        // Near timber beams.
        val pb = 0.6f
        val k0 = floor((y0 - py(0f, pb)) / 3.6f).toInt() - 1
        val k1 = ceil((y1 - py(0f, pb)) / 3.6f).toInt()
        for (k in k0..k1) {
            val yy = py(k * 3.6f, pb)
            g.fillRect(x0, yy, x1, yy + 0.22f, 0xFF3A2414.toInt())
        }
    }

    // ------------------------------------------------------------------- magma

    private fun magma(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        g.fillVerticalGradient(x0, y0, x1, y1, pal.bgFar, pal.bgNear)
        val p = 0.25f
        val lvl = 5f
        val l0 = floor((y0 - py(0f, p)) / lvl).toInt() - 1
        val l1 = ceil((y1 - py(0f, p)) / lvl).toInt()
        val poly = f.poly
        for (l in l0..l1) {
            val ly = py(l * lvl, p)
            // Lava lake glow at the bottom of the cavern.
            g.fillVerticalGradient(x0, ly + 3.4f, x1, ly + 4.4f, 0x00FF5A00, 0xC0FF5A00.toInt())
            g.fillRect(x0, ly + 4.4f, x1, ly + 5f, 0xFFFF7A10.toInt())
            g.fillRect(x0, ly + 4.4f, x1, ly + 4.48f, 0xFFFFE070.toInt())
            // Rock ledges.
            poly.begin()
            poly.add(x0 - 0.2f, ly + 0.9f)
            for (i in 0..6) {
                val xx = x0 + (x1 - x0) * i / 6f
                poly.add(xx, ly + 0.4f + hash(i + l * 9, 101) * 0.6f)
            }
            poly.add(x1 + 0.2f, ly + 0.9f)
            poly.add(x1 + 0.2f, ly - 0.2f)
            poly.add(x0 - 0.2f, ly - 0.2f)
            poly.fill(g, 0xFF1C0604.toInt())
            // Lava falls.
            for (j in 0 until 3) {
                val fx = 0.5f + j * 3.8f + hash(j + l * 5, 102) * 1.8f
                val fw = 0.18f + hash(j + l * 5, 103) * 0.3f
                if (fx + fw < x0 - 0.5f || fx - fw > x1 + 0.5f) continue
                val top = ly + 0.8f
                val bot = ly + 4.45f
                g.fillRect(fx - fw * 2.5f, top, fx + fw * 2.5f, bot, 0x22FF5A00)
                g.fillRect(fx - fw, top, fx + fw, bot, 0xFFFF6A00.toInt())
                g.fillRect(fx - fw * 0.4f, top, fx + fw * 0.4f, bot, 0xFFFFB030.toInt())
                for (s in 0 until 5) {
                    val sy = top + fract(f.t * 0.8f + s * 0.2f + hash(j, 104)) * (bot - top)
                    g.fillRect(fx - fw * 0.6f, sy, fx + fw * 0.6f, sy + 0.25f, 0xFFFFE890.toInt())
                }
                // Splash glow (solid discs: no gradient per waterfall).
                g.fillCircle(fx, bot, 0.7f, 0x20FFB040)
                g.fillCircle(fx, bot, 0.35f, 0x40FFD070)
            }
        }
        embers(0xFFFF9A2A.toInt(), x0, y0, x1, y1, 24)
    }

    private fun embers(color: Int, x0: Float, y0: Float, x1: Float, y1: Float, count: Int) {
        val h = y1 - y0
        // Scale with the visible area so small windows stay calm behind the actors.
        val n = min(count, ((x1 - x0) * h * 2.5f).toInt() + 2)
        for (i in 0 until n) {
            val ex = x0 + fract(hash(i, 111) + sin(f.t * 0.7f + i) * 0.03f) * (x1 - x0)
            val ey = y1 - fract(hash(i, 112) + f.t * (0.08f + hash(i, 113) * 0.12f)) * h
            val a = 0.4f + 0.6f * hash(i, 114)
            g.fillCircle(ex, ey, 0.03f + hash(i, 115) * 0.03f, Col.fade(color, a))
        }
    }

    // -------------------------------------------------------------------- hell

    private fun hell(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        g.fillVerticalGradient(x0, y0, x1, y1, pal.skyTop, pal.skyBottom)
        val p = 0.22f
        val lvl = 6f
        val l0 = floor((y0 - py(0f, p)) / lvl).toInt() - 1
        val l1 = ceil((y1 - py(0f, p)) / lvl).toInt()
        val poly = f.poly
        for (l in l0..l1) {
            val ly = py(l * lvl, p)
            // Bone spires.
            for (j in 0 until 4) {
                val sx = hash(j + l * 7, 121) * 11f - 0.3f
                if (sx + 0.5f < x0 || sx - 0.5f > x1) continue
                val sh = 2.5f + hash(j + l * 7, 122) * 2.5f
                val base = ly + lvl
                poly.tri(g, sx - 0.35f, base, sx + 0.35f, base, sx + 0.05f, base - sh, 0xFF2A0408.toInt())
                for (r in 1..4) {
                    val ry = base - sh * r / 5f
                    val rw = 0.35f * (1f - r / 5f) + 0.25f
                    g.line(sx - rw, ry + 0.12f, sx + rw, ry - 0.05f, 0.05f, 0xFF3A0A10.toInt())
                }
            }
            // Wall of flames.
            val fy = ly + lvl
            poly.begin()
            poly.add(x1 + 0.3f, fy + 0.2f)
            poly.add(x0 - 0.3f, fy + 0.2f)
            val n = 14
            for (i in 0..n) {
                val xx = x0 - 0.3f + (x1 - x0 + 0.6f) * i / n
                val tip = if (i % 2 == 0) 0.9f + sin(f.t * 5f + i * 1.7f + l) * 0.3f + hash(i + l, 123) * 0.5f else 0.25f
                poly.add(xx, fy - tip)
            }
            poly.fill(g, 0xB0E0300C.toInt())
            poly.begin()
            poly.add(x1 + 0.3f, fy + 0.2f)
            poly.add(x0 - 0.3f, fy + 0.2f)
            for (i in 0..n) {
                val xx = x0 - 0.3f + (x1 - x0 + 0.6f) * (i + 0.5f) / n
                val tip = if (i % 2 == 0) 0.5f + sin(f.t * 7f + i * 2.3f + l) * 0.2f else 0.12f
                poly.add(xx, fy - tip)
            }
            poly.fill(g, 0xF0FFB020.toInt())
        }
        embers(0xFFFFB040.toInt(), x0, y0, x1, y1, 30)
    }

    // -------------------------------------------------------------------- void

    private fun void(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        g.fillVerticalGradient(x0, y0, x1, y1, pal.skyTop, pal.skyBottom)
        // Endless synthwave grid.
        val p = 0.4f
        val step = 0.8f
        val k0 = floor((y0 - py(0f, p)) / step).toInt()
        val k1 = ceil((y1 - py(0f, p)) / step).toInt()
        for (k in k0..k1) {
            val yy = py(k * step, p)
            g.line(x0, yy, x1, yy, 0.02f, Col.fade(pal.neon, 0.35f))
        }
        var x = -0.6f
        while (x < Geo.FLOOR_W + 0.6f) {
            if (x in x0..x1) g.line(x, y0, x, y1, 0.02f, Col.fade(pal.neon2, 0.3f))
            x += step
        }
        for (i in 0 until 6) {
            val cx = hash(i, 131) * 11f - 0.5f
            val cy = py(hash(i, 132) * 40f + (f.t * 0.3f % 40f), 0.2f)
            if (cx < x0 - 0.5f || cx > x1 + 0.5f) continue
            val r = 0.2f + hash(i, 133) * 0.3f
            g.save()
            g.translate(cx, cy)
            g.rotate(f.t * 40f + i * 50f)
            g.strokeRect(-r, -r, r, r, 0.03f, Col.fade(if (i % 2 == 0) pal.neon else pal.neon2, 0.7f))
            g.restore()
        }
    }

    /** Horizontal tears in reality: quantized in time so they stutter. */
    private fun glitch(pal: Palette, x0: Float, y0: Float, x1: Float, y1: Float) {
        val tick = (f.t * 8f).toInt()
        for (i in 0 until 3) {
            val hv = hash(tick * 3 + i, 141)
            if (hv > 0.5f) continue
            val yy = y0 + hash(tick * 3 + i, 142) * (y1 - y0)
            val h = 0.05f + hash(tick + i, 143) * 0.25f
            val c = if (i % 2 == 0) pal.neon else pal.neon2
            g.fillRect(x0, yy, x1, min(y1, yy + h), Col.fade(c, 0.35f))
        }
    }
}
