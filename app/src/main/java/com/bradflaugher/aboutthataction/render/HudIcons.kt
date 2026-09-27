package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.PickupKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crisp vector icons for the HUD and the perk cards, drawn in a box [s] px wide centred on
 * (cx, cy) with one colour. Line weight scales with the box, so they read from a 5 mm HUD
 * chip up to a perk-card medallion.
 */
internal object HudIcons {
    private val poly = Poly()

    fun perk(g: Gfx, p: Perk, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = s * 0.11f
        when (p) {
            Perk.RAPID_FIRE -> {
                // Three rounds in a row, speed lines behind.
                for (i in 0 until 3) {
                    val x = cx - k * 0.55f + i * k * 0.55f
                    bullet(g, x, cy, k * 0.5f, k * 0.22f, c)
                }
                g.line(cx - k * 0.95f, cy - k * 0.45f, cx - k * 0.6f, cy - k * 0.45f, sw * 0.6f, c)
                g.line(cx - k * 0.95f, cy + k * 0.45f, cx - k * 0.6f, cy + k * 0.45f, sw * 0.6f, c)
            }
            Perk.HOLLOW_POINT -> {
                g.strokeCircle(cx, cy, k * 0.72f, sw, c)
                g.fillCircle(cx, cy, k * 0.22f, c)
                for (i in 0 until 4) {
                    val a = i * 1.5708f
                    g.line(cx + cos(a) * k * 0.42f, cy + sin(a) * k * 0.42f, cx + cos(a) * k * 0.98f, cy + sin(a) * k * 0.98f, sw, c)
                }
            }
            Perk.PIERCE -> {
                // A bar with a round punched clean through it.
                g.fillRoundRect(cx - k * 0.12f, cy - k * 0.8f, cx + k * 0.12f, cy + k * 0.8f, k * 0.06f, Col.fade(c, 0.55f))
                g.line(cx - k * 0.9f, cy, cx + k * 0.45f, cy, sw, c)
                poly.tri(g, cx + k * 0.95f, cy, cx + k * 0.4f, cy - k * 0.32f, cx + k * 0.4f, cy + k * 0.32f, c)
            }
            Perk.RICOCHET -> {
                g.line(cx + k * 0.85f, cy - k * 0.9f, cx + k * 0.85f, cy + k * 0.9f, sw * 0.8f, Col.fade(c, 0.55f))
                g.line(cx - k * 0.9f, cy - k * 0.55f, cx + k * 0.7f, cy + k * 0.05f, sw, c)
                g.line(cx + k * 0.7f, cy + k * 0.05f, cx - k * 0.35f, cy + k * 0.6f, sw, c)
                poly.tri(g, cx - k * 0.75f, cy + k * 0.8f, cx - k * 0.22f, cy + k * 0.3f, cx - k * 0.1f, cy + k * 0.78f, c)
            }
            Perk.SPLIT_SHOT -> {
                g.line(cx - k * 0.9f, cy, cx - k * 0.2f, cy, sw, c)
                g.line(cx - k * 0.2f, cy, cx + k * 0.6f, cy - k * 0.55f, sw, c)
                g.line(cx - k * 0.2f, cy, cx + k * 0.6f, cy + k * 0.55f, sw, c)
                g.fillCircle(cx + k * 0.72f, cy - k * 0.62f, k * 0.18f, c)
                g.fillCircle(cx + k * 0.72f, cy + k * 0.62f, k * 0.18f, c)
            }
            Perk.VITALITY -> {
                Glyphs.heart(g, cx - k * 0.1f, cy + k * 0.05f, k * 1.55f, c)
                g.fillCircle(cx + k * 0.62f, cy - k * 0.55f, k * 0.36f, 0xFF0A0810.toInt())
                g.line(cx + k * 0.62f, cy - k * 0.8f, cx + k * 0.62f, cy - k * 0.3f, sw * 0.8f, c)
                g.line(cx + k * 0.37f, cy - k * 0.55f, cx + k * 0.87f, cy - k * 0.55f, sw * 0.8f, c)
            }
            Perk.CQC -> {
                // Knife.
                poly.begin()
                    .add(cx - k * 0.2f, cy + k * 0.2f).add(cx + k * 0.8f, cy - k * 0.8f)
                    .add(cx + k * 0.55f, cy - k * 0.1f).add(cx + k * 0.05f, cy + k * 0.42f)
                    .fill(g, c)
                g.line(cx - k * 0.42f, cy - k * 0.02f, cx + k * 0.02f, cy + k * 0.42f, sw, c)
                g.line(cx - k * 0.2f, cy + k * 0.2f, cx - k * 0.75f, cy + k * 0.75f, sw * 1.5f, c)
            }
            Perk.GHOST_BOX -> {
                g.strokeRect(cx - k * 0.75f, cy - k * 0.45f, cx + k * 0.75f, cy + k * 0.75f, sw * 0.8f, c)
                g.line(cx - k * 0.75f, cy - k * 0.45f, cx - k * 0.45f, cy - k * 0.8f, sw * 0.7f, c)
                g.line(cx + k * 0.75f, cy - k * 0.45f, cx + k * 0.45f, cy - k * 0.8f, sw * 0.7f, c)
                g.fillCircle(cx - k * 0.28f, cy + k * 0.12f, k * 0.13f, c)
                g.fillCircle(cx + k * 0.28f, cy + k * 0.12f, k * 0.13f, c)
            }
            Perk.DOUBLE_JUMP -> {
                chevron(g, cx, cy - k * 0.3f, k * 0.62f, sw * 1.1f, c)
                chevron(g, cx, cy + k * 0.35f, k * 0.62f, sw * 1.1f, Col.fade(c, 0.6f))
            }
            Perk.DEMOLITION -> grenade(g, cx, cy + k * 0.1f, k * 1.6f, c)
            Perk.MAGNET -> {
                g.strokeArc(cx, cy - k * 0.05f, k * 0.58f, 180f, -180f, sw * 2f, c)
                g.line(cx - k * 0.58f, cy - k * 0.05f, cx - k * 0.58f, cy - k * 0.7f, sw * 2f, c)
                g.line(cx + k * 0.58f, cy - k * 0.05f, cx + k * 0.58f, cy - k * 0.7f, sw * 2f, c)
                g.fillRect(cx - k * 0.8f, cy - k * 0.9f, cx - k * 0.36f, cy - k * 0.66f, 0xFFFFFFFF.toInt())
                g.fillRect(cx + k * 0.36f, cy - k * 0.9f, cx + k * 0.8f, cy - k * 0.66f, 0xFFFFFFFF.toInt())
            }
            Perk.REFLEX -> hourglass(g, cx, cy, s, c)
            Perk.ARMOR -> vest(g, cx, cy, s * 0.95f, c)
            Perk.LUCKY -> {
                val r = k * 0.3f
                g.fillCircle(cx, cy - r * 1.05f, r, c)
                g.fillCircle(cx, cy + r * 1.05f, r, c)
                g.fillCircle(cx - r * 1.05f, cy, r, c)
                g.fillCircle(cx + r * 1.05f, cy, r, c)
                g.line(cx + r * 0.6f, cy + r * 0.6f, cx + k * 0.85f, cy + k * 0.9f, sw, c)
            }
            Perk.SHOCKWAVE -> {
                g.line(cx - k * 0.9f, cy + k * 0.6f, cx + k * 0.9f, cy + k * 0.6f, sw, c)
                g.strokeArc(cx, cy + k * 0.6f, k * 0.45f, 180f, 180f, sw * 0.8f, c)
                g.strokeArc(cx, cy + k * 0.6f, k * 0.85f, 195f, 150f, sw * 0.7f, Col.fade(c, 0.6f))
                Glyphs.arrow(g, cx, cy - k * 0.35f, k * 0.45f, 0f, 1f, sw, c)
            }
        }
    }

    fun pickup(g: Gfx, p: PickupKind, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = s * 0.11f
        when (p) {
            PickupKind.SHOTGUN -> {
                g.fillRoundRect(cx - k * 0.95f, cy - k * 0.22f, cx + k * 0.95f, cy + k * 0.02f, k * 0.08f, c)
                g.fillRoundRect(cx - k * 0.95f, cy + k * 0.08f, cx + k * 0.5f, cy + k * 0.26f, k * 0.08f, c)
                poly.begin().add(cx - k * 0.95f, cy - k * 0.22f).add(cx - k * 0.45f, cy - k * 0.22f)
                    .add(cx - k * 0.6f, cy + k * 0.7f).add(cx - k * 0.95f, cy + k * 0.6f).fill(g, c)
            }
            PickupKind.MINIGUN -> {
                for (i in 0 until 3) {
                    val y = cy - k * 0.35f + i * k * 0.3f
                    g.fillRoundRect(cx - k * 0.2f, y - k * 0.08f, cx + k * 0.95f, y + k * 0.08f, k * 0.08f, c)
                }
                g.fillRoundRect(cx - k * 0.9f, cy - k * 0.5f, cx - k * 0.05f, cy + k * 0.5f, k * 0.18f, c)
            }
            PickupKind.SLOWMO -> hourglass(g, cx, cy, s, c)
            PickupKind.SHIELD -> shield(g, cx, cy, s, c, 0xFF0A1420.toInt())
            PickupKind.GRENADE -> grenade(g, cx, cy, s * 0.8f, c)
            PickupKind.MEDKIT -> {
                g.fillRoundRect(cx - k * 0.8f, cy - k * 0.65f, cx + k * 0.8f, cy + k * 0.65f, k * 0.2f, c)
                g.fillRect(cx - k * 0.14f, cy - k * 0.45f, cx + k * 0.14f, cy + k * 0.45f, 0xFF0A0810.toInt())
                g.fillRect(cx - k * 0.45f, cy - k * 0.14f, cx + k * 0.45f, cy + k * 0.14f, 0xFF0A0810.toInt())
            }
            PickupKind.CASH -> {
                g.strokeCircle(cx, cy, k * 0.75f, sw, c)
                g.text("$", cx, cy + k * 0.4f, k * 1.1f, c, Gfx.Font.TITLE, Gfx.Align.CENTER)
            }
        }
    }

    fun bullet(g: Gfx, cx: Float, cy: Float, len: Float, r: Float, c: Int) {
        g.fillRect(cx - len / 2f, cy - r, cx + len / 2f - r, cy + r, c)
        g.fillCircle(cx + len / 2f - r, cy, r, c)
    }

    fun chevron(g: Gfx, cx: Float, cy: Float, r: Float, sw: Float, c: Int) {
        g.line(cx - r, cy + r * 0.45f, cx, cy - r * 0.45f, sw, c)
        g.line(cx, cy - r * 0.45f, cx + r, cy + r * 0.45f, sw, c)
    }

    /** Pointing down: the swipe-down affordance. */
    fun chevronDown(g: Gfx, cx: Float, cy: Float, r: Float, sw: Float, c: Int) {
        g.line(cx - r, cy - r * 0.45f, cx, cy + r * 0.45f, sw, c)
        g.line(cx, cy + r * 0.45f, cx + r, cy - r * 0.45f, sw, c)
    }

    fun grenade(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val r = s * 0.34f
        g.fillCircle(cx, cy + r * 0.2f, r, c)
        g.fillRoundRect(cx - r * 0.45f, cy - r * 1.15f, cx + r * 0.45f, cy - r * 0.55f, r * 0.12f, c)
        g.strokeCircle(cx + r * 0.75f, cy - r * 1.05f, r * 0.32f, r * 0.16f, c)
        g.fillCircle(cx - r * 0.35f, cy - r * 0.1f, r * 0.24f, 0x66FFFFFF)
    }

    fun hourglass(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = s * 0.1f
        g.line(cx - k * 0.6f, cy - k * 0.85f, cx + k * 0.6f, cy - k * 0.85f, sw, c)
        g.line(cx - k * 0.6f, cy + k * 0.85f, cx + k * 0.6f, cy + k * 0.85f, sw, c)
        g.line(cx - k * 0.45f, cy - k * 0.8f, cx + k * 0.45f, cy + k * 0.8f, sw * 0.8f, c)
        g.line(cx + k * 0.45f, cy - k * 0.8f, cx - k * 0.45f, cy + k * 0.8f, sw * 0.8f, c)
        poly.tri(g, cx - k * 0.32f, cy + k * 0.75f, cx + k * 0.32f, cy + k * 0.75f, cx, cy + k * 0.3f, c)
    }

    fun shield(g: Gfx, cx: Float, cy: Float, s: Float, c: Int, inner: Int) {
        poly.begin()
            .add(cx - s * 0.42f, cy - s * 0.42f).add(cx, cy - s * 0.52f).add(cx + s * 0.42f, cy - s * 0.42f)
            .add(cx + s * 0.38f, cy + s * 0.08f).add(cx, cy + s * 0.52f).add(cx - s * 0.38f, cy + s * 0.08f)
            .fill(g, c)
        poly.begin()
            .add(cx - s * 0.27f, cy - s * 0.3f).add(cx, cy - s * 0.37f).add(cx + s * 0.27f, cy - s * 0.3f)
            .add(cx + s * 0.24f, cy + s * 0.04f).add(cx, cy + s * 0.34f).add(cx - s * 0.24f, cy + s * 0.04f)
            .fill(g, inner)
        g.fillRect(cx - s * 0.05f, cy - s * 0.3f, cx + s * 0.05f, cy + s * 0.3f, c)
    }

    fun vest(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        poly.begin()
            .add(cx - s * 0.2f, cy - s * 0.45f).add(cx - s * 0.08f, cy - s * 0.3f).add(cx + s * 0.08f, cy - s * 0.3f).add(cx + s * 0.2f, cy - s * 0.45f)
            .add(cx + s * 0.42f, cy - s * 0.3f).add(cx + s * 0.38f, cy + s * 0.45f).add(cx - s * 0.38f, cy + s * 0.45f).add(cx - s * 0.42f, cy - s * 0.3f)
            .fill(g, c)
        g.line(cx, cy - s * 0.25f, cx, cy + s * 0.4f, s * 0.06f, 0xFF1A1206.toInt())
        g.line(cx - s * 0.3f, cy + s * 0.08f, cx + s * 0.3f, cy + s * 0.08f, s * 0.05f, 0xFF1A1206.toInt())
    }

    fun elevator(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = s * 0.09f
        g.strokeRoundRect(cx - k * 0.7f, cy - k * 0.85f, cx + k * 0.7f, cy + k * 0.85f, k * 0.1f, sw, c)
        g.line(cx, cy - k * 0.55f, cx, cy + k * 0.85f, sw * 0.8f, c)
        poly.tri(g, cx - k * 0.2f, cy - k * 0.62f, cx + k * 0.2f, cy - k * 0.62f, cx, cy - k * 0.85f, c)
    }

    fun door(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = s * 0.09f
        g.strokeRect(cx - k * 0.6f, cy - k * 0.85f, cx + k * 0.6f, cy + k * 0.85f, sw, c)
        poly.quad(g, cx - k * 0.6f, cy - k * 0.85f, cx + k * 0.15f, cy - k * 0.65f, cx + k * 0.15f, cy + k * 0.95f, cx - k * 0.6f, cy + k * 0.85f, Col.fade(c, 0.45f))
        g.fillCircle(cx + k * 0.0f, cy + k * 0.08f, k * 0.09f, c)
    }

    fun box(g: Gfx, cx: Float, cy: Float, s: Float, c: Int, tape: Int) {
        val k = s / 2f
        g.fillRect(cx - k * 0.85f, cy - k * 0.5f, cx + k * 0.85f, cy + k * 0.75f, c)
        poly.quad(g, cx - k * 0.85f, cy - k * 0.5f, cx - k * 0.6f, cy - k * 0.85f, cx + k * 0.6f, cy - k * 0.85f, cx + k * 0.85f, cy - k * 0.5f, Col.mul(c, 1.2f))
        g.fillRect(cx - k * 0.12f, cy - k * 0.5f, cx + k * 0.12f, cy + k * 0.75f, tape)
    }

    fun dataCore(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        poly.begin().add(cx, cy - k * 0.9f).add(cx + k * 0.9f, cy).add(cx, cy + k * 0.9f).add(cx - k * 0.9f, cy).fill(g, c)
        poly.begin().add(cx, cy - k * 0.5f).add(cx + k * 0.5f, cy).add(cx, cy + k * 0.5f).add(cx - k * 0.5f, cy).fill(g, 0xFF0A0810.toInt())
        g.fillCircle(cx, cy, k * 0.18f, c)
    }
}
