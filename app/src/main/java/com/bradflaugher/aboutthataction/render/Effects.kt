package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Flash
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.ParticleKind
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.TextStyle
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Bullets, particles, floating text, and full-screen post effects. */
internal class Effects(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly

    fun world() {
        bullets()
        particles()
        texts()
    }

    // ---------------------------------------------------------------- bullets

    private fun bullets() {
        val top = f.camY - 1f
        val bottom = f.camY + f.viewH + 1f
        for (b in f.w.bullets) {
            val gy = Geo.groundY(b.floor)
            val y = gy - b.z
            if (y < top || y > bottom) continue
            if (b.gravity) {
                // Fireball.
                val fl = 0.85f + 0.15f * sin(f.t * 40f + b.x * 3f)
                for (i in 1..4) {
                    val tx = b.x - b.vx * 0.025f * i
                    val ty = y + b.vz * 0.025f * i
                    g.fillCircle(tx, ty, 0.13f * (1f - i * 0.18f), Col.alpha(0xFFFF6A10.toInt(), 0.5f - i * 0.1f))
                }
                g.fillCircle(b.x, y, 0.42f * fl, 0x30FF5A10)
                g.fillCircle(b.x, y, 0.2f * fl, 0xFFFF6A10.toInt())
                g.fillCircle(b.x, y, 0.12f * fl, 0xFFFFD060.toInt())
                g.fillCircle(b.x, y, 0.05f, 0xFFFFFFFF.toInt())
                continue
            }
            val len = min(1f, b.life * 30f) * 0.035f
            val tx = b.x - b.vx * len
            val ty = y + b.vz * len
            if (b.byPlayer) {
                f.glowLine(tx, ty, b.x, y, 0.05f, 0xFF3CF4FF.toInt(), 0xFFFFFFFF.toInt(), 0.9f)
            } else {
                // Enemy rounds: fat, hot, and impossible to miss.
                f.glowLine(tx, ty, b.x, y, 0.08f, 0xFFFF3A2A.toInt(), 0xFFFFE8B0.toInt(), 1.1f)
                g.fillCircle(b.x, y, 0.08f, 0xFFFFF0C0.toInt())
            }
        }
    }

    // -------------------------------------------------------------- particles

    private fun particles() {
        val top = f.camY - 1f
        val bottom = f.camY + f.viewH + 1f
        val list = f.w.fx.particles
        for (i in list.indices) {
            val p = list[i]
            if (p.y < top || p.y > bottom) continue
            val t = p.t
            val fade = 1f - t
            when (p.kind) {
                ParticleKind.SPARK -> {
                    g.line(p.x, p.y, p.x - p.vx * 0.035f, p.y - p.vy * 0.035f, p.size * 0.55f, Col.alpha(Col.lerp(0xFFFFFFFF.toInt(), 0xFFFFB030.toInt(), t), fade))
                }
                ParticleKind.SHARD -> spinQuad(p.x, p.y, p.size, p.size * 0.5f, p.spin * (p.maxLife - p.life), Col.alpha(if (i % 3 == 0) 0xFFFFFFFF.toInt() else 0xFFFF2A48.toInt(), fade))
                ParticleKind.SMOKE -> g.fillCircle(p.x, p.y, p.size * (0.5f + t), Col.alpha(0xFF2A2834.toInt(), 0.45f * fade))
                ParticleKind.EMBER -> {
                    val c = Col.lerp(0xFFFFE070.toInt(), 0xFFFF4010.toInt(), t)
                    g.fillCircle(p.x, p.y, p.size * 1.8f, Col.alpha(c, 0.25f * fade))
                    g.fillCircle(p.x, p.y, p.size * 0.6f, Col.alpha(c, fade))
                }
                ParticleKind.GLASS -> spinQuad(p.x, p.y, p.size, p.size * 0.35f, p.spin * (p.maxLife - p.life), Col.alpha(0xFFC8F4FF.toInt(), fade))
                ParticleKind.CASING -> spinQuad(p.x, p.y, p.size, p.size * 0.45f, p.spin * (p.maxLife - p.life), Col.alpha(0xFFE8B040.toInt(), fade))
                ParticleKind.DUST -> g.fillCircle(p.x, p.y, p.size * (0.6f + t * 1.2f), Col.alpha(0xFFB8A8C8.toInt(), 0.4f * fade))
                ParticleKind.RING -> {
                    val e = 1f - (1f - t) * (1f - t)
                    val r = p.size * (0.15f + 0.85f * e)
                    g.strokeCircle(p.x, p.y, r, 0.16f * fade + 0.02f, Col.alpha(0xFFFFFFFF.toInt(), 0.2f * fade))
                    g.strokeCircle(p.x, p.y, r, 0.05f * fade + 0.01f, Col.alpha(0xFF9FF6FF.toInt(), 0.9f * fade))
                }
                ParticleKind.CARDBOARD -> spinQuad(p.x, p.y, p.size, p.size * 0.7f, p.spin * (p.maxLife - p.life), Col.alpha(0xFFC08A52.toInt(), fade))
            }
        }
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

    private fun texts() {
        for (ft in f.w.fx.texts) {
            val t = ft.t
            val pop = 1f + (1f - min(1f, t * 7f)) * 0.7f
            val a = if (t > 0.7f) max(0f, (1f - t) / 0.3f) else 1f
            val (size, color, font) = when (ft.style) {
                TextStyle.SCORE -> Triple(0.34f, 0xFFFFF4D0.toInt(), Gfx.Font.TITLE)
                TextStyle.TAKEDOWN -> Triple(0.46f, 0xFF3CF4FF.toInt(), Gfx.Font.TITLE)
                TextStyle.COMBO -> Triple(0.44f * (1f + 0.08f * sin(f.t * 20f)), 0xFFFF3D9A.toInt(), Gfx.Font.TITLE)
                TextStyle.PICKUP -> Triple(0.38f, 0xFF7CFF9A.toInt(), Gfx.Font.TITLE)
                TextStyle.WARN -> Triple(0.36f, 0xFFFF8A3A.toInt(), Gfx.Font.TITLE)
                TextStyle.BIG -> Triple(0.62f, 0xFFFFD24A.toInt(), Gfx.Font.TITLE)
            }
            val px = ft.x.coerceIn(1.2f, Geo.FLOOR_W - 1.2f)
            g.save()
            g.translate(px, ft.y)
            val sc = 1f / f.s
            g.scale(sc, sc)
            val sz = size * pop * f.s
            Glyphs.text(g, ft.text, sz * 0.06f, sz * 0.08f, sz, Col.alpha(0xFF000000.toInt(), 0.7f * a), font, Gfx.Align.CENTER)
            Glyphs.text(g, ft.text, 0f, 0f, sz, Col.fade(color, a), font, Gfx.Align.CENTER)
            g.restore()
        }
    }

    // ------------------------------------------------------------ post effects

    fun screen() {
        val w = f.w
        val W = g.width
        val H = g.height
        // Zone-coloured vignette frame (always), heavier in slow-mo and when dying.
        val slow = w.slowMo && w.phase == Phase.PLAYING
        val dying = w.phase == Phase.DYING || w.phase == Phase.OVER
        var vig = 0.5f
        if (slow) vig = 0.75f
        if (dying) vig = 0.9f
        val lowHp = w.player.hp == 1 && w.phase == Phase.PLAYING
        val vigColor = when {
            dying -> 0xFF300008.toInt()
            lowHp -> Col.lerp(0xFF000000.toInt(), 0xFF6A0010.toInt(), 0.5f + 0.5f * sin(f.t * 6f))
            else -> 0xFF000000.toInt()
        }
        vignette(W, H, vig, vigColor)

        if (slow) {
            g.fillRect(0f, 0f, W, H, 0x1C2A7AFF)
            // Chromatic fringe on the screen edges.
            g.fillRect(0f, 0f, W * 0.012f, H, 0x30FF2BD6)
            g.fillRect(W - W * 0.012f, 0f, W, H, 0x302BFFE0)
            scanlines(W, H, 0x14000000)
        }
        if (w.zone == Zone.VOID && w.phase == Phase.PLAYING) {
            val tick = (f.t * 12f).toInt()
            for (i in 0 until 2) {
                if (hash(tick * 5 + i, 401) > 0.25f) continue
                val y = hash(tick * 5 + i, 402) * H
                val h = H * (0.004f + hash(tick + i, 403) * 0.02f)
                g.fillRect(0f, y, W, y + h, if (i == 0) 0x40FF2BD6 else 0x402BFFE0)
            }
        }
        if (dying) {
            val k = min(1f, w.dyingTime / 1.2f + if (w.phase == Phase.OVER) 1f else 0f)
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFF3A3038.toInt(), 0.35f * k))
            g.fillRect(0f, 0f, W, H, Col.alpha(0xFFB0001A.toInt(), 0.22f * k))
            scanlines(W, H, Col.alpha(0xFF000000.toInt(), 0.18f * k))
        }
        if (w.flashAmount > 0.01f) {
            val a = min(1f, w.flashAmount)
            when (w.flash) {
                Flash.HURT -> {
                    g.fillRect(0f, 0f, W, H, Col.alpha(0xFFFF1030.toInt(), 0.28f * a))
                    vignette(W, H, a, 0xFFFF0020.toInt())
                }
                Flash.WHITE -> g.fillRect(0f, 0f, W, H, Col.alpha(0xFFFFF8F0.toInt(), 0.5f * a))
                Flash.GOLD -> g.fillRect(0f, 0f, W, H, Col.alpha(0xFFFFD24A.toInt(), 0.35f * a))
                Flash.NONE -> Unit
            }
        }
    }

    private fun vignette(W: Float, H: Float, strength: Float, color: Int) {
        val band = W * 0.22f
        val a = Col.alpha(color, 0.8f * strength)
        val z = Col.alpha(color, 0f)
        g.fillVerticalGradient(0f, 0f, W, band, a, z)
        g.fillVerticalGradient(0f, H - band * 1.2f, W, H, z, a)
        // Sides: rotate the vertical gradient.
        g.save()
        g.translate(0f, H)
        g.rotate(-90f)
        g.fillVerticalGradient(0f, 0f, H, band * 0.7f, a, z)
        g.restore()
        g.save()
        g.translate(W, 0f)
        g.rotate(90f)
        g.fillVerticalGradient(0f, 0f, H, band * 0.7f, a, z)
        g.restore()
    }

    private fun scanlines(W: Float, H: Float, color: Int) {
        val step = max(3f, H / 300f)
        var y = 0f
        while (y < H) {
            g.fillRect(0f, y, W, y + step * 0.4f, color)
            y += step
        }
    }
}
