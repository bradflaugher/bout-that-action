package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.PickupKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crisp vector icons for the HUD and the perk cards, drawn in a box [s] px wide centred on
 * (cx, cy) with one colour. Line weight scales with the box, so they read from a 5 mm HUD
 * chip up to a perk-card medallion.
 */
internal object HudIcons {
    private val poly = Poly()

    /** A cut-out tone (a dark hole, a white tip) at the icon colour's own alpha, so dimmed icons dim whole. */
    private fun cut(tone: Int, c: Int) = Col.alpha(tone, Col.a(c) / 255f)

    private const val HOLE = 0xFF0A0810.toInt()

    fun perk(g: Gfx, p: Perk, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        // Bold strokes: an icon has to hold up as a 20 px HUD chip glyph, not just on a card.
        val sw = s * 0.14f
        when (p) {
            Perk.RAPID_FIRE -> {
                // Three rounds in a tight stream with clear gaps, one bold speed line behind.
                for (i in 0 until 3) bullet(g, cx - k * 0.36f + i * k * 0.6f, cy, k * 0.46f, k * 0.19f, c)
                g.line(cx - k * 1.0f, cy, cx - k * 0.86f, cy, sw, c)
            }
            Perk.HOLLOW_POINT -> {
                // A round whose nose has mushroomed open on impact: more damage per hit.
                g.fillRoundRect(cx - k * 0.95f, cy - k * 0.24f, cx - k * 0.05f, cy + k * 0.24f, k * 0.06f, c)
                poly.begin()
                    .add(cx - k * 0.08f, cy - k * 0.26f).add(cx + k * 0.22f, cy - k * 0.66f).add(cx + k * 0.4f, cy - k * 0.3f)
                    .add(cx + k * 0.55f, cy - k * 0.12f).add(cx + k * 0.55f, cy + k * 0.12f).add(cx + k * 0.4f, cy + k * 0.3f)
                    .add(cx + k * 0.22f, cy + k * 0.66f).add(cx - k * 0.08f, cy + k * 0.26f)
                    .fill(g, c)
                // One bold impact streak ahead of it.
                g.line(cx + k * 0.75f, cy, cx + k * 0.98f, cy, sw, c)
            }
            Perk.PIERCE -> {
                // A bar with a round punched clean through it and out the other side.
                g.fillRoundRect(cx - k * 0.15f, cy - k * 0.85f, cx + k * 0.15f, cy + k * 0.85f, k * 0.08f, c)
                g.line(cx - k * 0.95f, cy, cx + k * 0.3f, cy, sw * 1.15f, c)
                poly.tri(g, cx + k * 0.98f, cy, cx + k * 0.3f, cy - k * 0.42f, cx + k * 0.3f, cy + k * 0.42f, c)
            }
            Perk.RICOCHET -> {
                // One thick V: in to the wall, out again, with a solid head.
                g.fillRoundRect(cx + k * 0.66f, cy - k * 0.92f, cx + k * 0.94f, cy + k * 0.92f, k * 0.08f, c)
                g.line(cx - k * 0.9f, cy - k * 0.7f, cx + k * 0.5f, cy, sw * 1.1f, c)
                g.line(cx + k * 0.5f, cy, cx - k * 0.3f, cy + k * 0.4f, sw * 1.1f, c)
                poly.tri(g, cx - k * 0.85f, cy + k * 0.68f, cx - k * 0.1f, cy + k * 0.12f, cx - k * 0.04f, cy + k * 0.72f, c)
            }
            Perk.SPLIT_SHOT -> {
                g.line(cx - k * 0.9f, cy, cx - k * 0.2f, cy, sw, c)
                g.line(cx - k * 0.2f, cy, cx + k * 0.55f, cy - k * 0.55f, sw, c)
                g.line(cx - k * 0.2f, cy, cx + k * 0.55f, cy + k * 0.55f, sw, c)
                g.fillCircle(cx + k * 0.7f, cy - k * 0.66f, k * 0.24f, c)
                g.fillCircle(cx + k * 0.7f, cy + k * 0.66f, k * 0.24f, c)
            }
            Perk.VITALITY -> {
                Glyphs.heart(g, cx - k * 0.1f, cy + k * 0.05f, k * 1.55f, c)
                g.fillCircle(cx + k * 0.62f, cy - k * 0.55f, k * 0.38f, cut(HOLE, c))
                g.line(cx + k * 0.62f, cy - k * 0.8f, cx + k * 0.62f, cy - k * 0.3f, sw * 0.8f, c)
                g.line(cx + k * 0.37f, cy - k * 0.55f, cx + k * 0.87f, cy - k * 0.55f, sw * 0.8f, c)
            }
            Perk.CQC -> {
                // A clenched fist (close quarters), and a small heart: its takedowns heal.
                g.fillRoundRect(cx - k * 0.78f, cy - k * 0.3f, cx + k * 0.3f, cy + k * 0.62f, k * 0.2f, c)
                for (i in 0 until 4) {
                    val x = cx - k * 0.65f + i * k * 0.27f
                    g.fillRoundRect(x - k * 0.13f, cy - k * 0.58f, x + k * 0.13f, cy - k * 0.12f, k * 0.12f, c)
                    if (i > 0) g.line(x - k * 0.135f, cy - k * 0.5f, x - k * 0.135f, cy - k * 0.18f, s * 0.035f, cut(HOLE, c))
                }
                // The thumb folded across the fingers.
                g.fillRoundRect(cx - k * 0.7f, cy - k * 0.02f, cx + k * 0.05f, cy + k * 0.22f, k * 0.12f, cut(HOLE, c))
                g.fillRoundRect(cx - k * 0.66f, cy + k * 0.01f, cx + k * 0.02f, cy + k * 0.19f, k * 0.09f, c)
                Glyphs.heart(g, cx + k * 0.66f, cy - k * 0.5f, k * 0.62f, c)
            }
            Perk.GHOST_BOX -> {
                // A cardboard box with small open flaps, its bottom edge trailing off like a ghost's,
                // and the peek slit.
                val top = cy - k * 0.42f
                val pb = poly.begin()
                    .add(cx - k * 0.75f, top).add(cx + k * 0.75f, top).add(cx + k * 0.75f, cy + k * 0.6f)
                for (i in 0..6) {
                    val x = cx + k * 0.75f - i * k * 0.25f
                    pb.add(x, cy + if (i % 2 == 0) k * 0.6f else k * 0.86f)
                }
                pb.fill(g, c)
                poly.quad(g, cx - k * 0.75f, top, cx - k * 0.92f, top - k * 0.3f, cx - k * 0.6f, top - k * 0.34f, cx - k * 0.45f, top, c)
                poly.quad(g, cx + k * 0.75f, top, cx + k * 0.92f, top - k * 0.3f, cx + k * 0.6f, top - k * 0.34f, cx + k * 0.45f, top, c)
                g.fillRoundRect(cx - k * 0.48f, cy - k * 0.16f, cx + k * 0.48f, cy + k * 0.1f, k * 0.13f, cut(HOLE, c))
            }
            Perk.DOUBLE_JUMP -> {
                chevron(g, cx, cy - k * 0.32f, k * 0.7f, sw * 1.15f, c)
                chevron(g, cx, cy + k * 0.4f, k * 0.7f, sw * 1.15f, c)
            }
            Perk.DEMOLITION -> {
                // A blast: an eight-point starburst with a hot core. Not the grenade you throw.
                val pb = poly.begin()
                for (i in 0 until 16) {
                    val a = -PI.toFloat() / 2f + i * PI.toFloat() / 8f
                    val rr = if (i % 2 == 0) k * 0.98f else k * 0.5f
                    pb.add(cx + cos(a) * rr, cy + sin(a) * rr)
                }
                pb.fill(g, c)
                g.fillCircle(cx, cy, k * 0.3f, cut(HOLE, c))
                g.fillCircle(cx, cy, k * 0.16f, c)
            }
            Perk.MAGNET -> {
                g.strokeArc(cx, cy - k * 0.05f, k * 0.56f, 180f, -180f, sw * 1.9f, c)
                g.line(cx - k * 0.56f, cy - k * 0.05f, cx - k * 0.56f, cy - k * 0.55f, sw * 1.9f, c)
                g.line(cx + k * 0.56f, cy - k * 0.05f, cx + k * 0.56f, cy - k * 0.55f, sw * 1.9f, c)
                // Bold white poles.
                g.fillRect(cx - k * 0.83f, cy - k * 0.95f, cx - k * 0.29f, cy - k * 0.58f, cut(0xFFFFFFFF.toInt(), c))
                g.fillRect(cx + k * 0.29f, cy - k * 0.95f, cx + k * 0.83f, cy - k * 0.58f, cut(0xFFFFFFFF.toInt(), c))
            }
            Perk.REFLEX -> eye(g, cx, cy, s, c)
            Perk.ARMOR -> vest(g, cx, cy, s * 0.95f, c)
            Perk.LUCKY -> clover(g, cx, cy, s, c)
            Perk.SHOCKWAVE -> {
                // A stomp driving into the floor line, a bold blast dome rippling out from it.
                g.fillRoundRect(cx - k * 0.98f, cy + k * 0.5f, cx + k * 0.98f, cy + k * 0.72f, k * 0.1f, c)
                g.strokeArc(cx, cy + k * 0.5f, k * 0.46f, 200f, 140f, sw, c)
                g.strokeArc(cx, cy + k * 0.5f, k * 0.86f, 205f, 130f, sw, c)
                Glyphs.arrow(g, cx, cy - k * 0.55f, k * 0.38f, 0f, 1f, sw * 1.1f, c)
            }
            // TODO(hero perks): real icons.
            else -> g.fillCircle(cx, cy, k * 0.6f, c)
        }
    }

    fun pickup(g: Gfx, p: PickupKind, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = s * 0.11f
        when (p) {
            PickupKind.SHOTGUN -> {
                // Long barrel over a pump, a receiver and a sloped stock: a shotgun, not a pistol.
                g.fillRoundRect(cx - k * 0.3f, cy - k * 0.3f, cx + k * 0.98f, cy - k * 0.12f, k * 0.06f, c)
                g.fillRoundRect(cx + k * 0.15f, cy - k * 0.06f, cx + k * 0.62f, cy + k * 0.1f, k * 0.06f, c)
                poly.begin()
                    .add(cx - k * 0.3f, cy - k * 0.3f).add(cx - k * 0.05f, cy - k * 0.3f).add(cx - k * 0.05f, cy + k * 0.08f)
                    .add(cx - k * 0.4f, cy + k * 0.1f).add(cx - k * 0.98f, cy + k * 0.42f).add(cx - k * 0.98f, cy + k * 0.06f)
                    .add(cx - k * 0.55f, cy - k * 0.14f)
                    .fill(g, c)
            }
            PickupKind.MINIGUN -> {
                // A rotary: a fat motor housing and a clamped cluster of barrels.
                g.fillRoundRect(cx - k * 0.95f, cy - k * 0.36f, cx - k * 0.2f, cy + k * 0.36f, k * 0.14f, c)
                for (i in 0 until 3) {
                    val y = cy - k * 0.24f + i * k * 0.24f
                    g.fillRoundRect(cx - k * 0.25f, y - k * 0.075f, cx + k * 0.98f, y + k * 0.075f, k * 0.075f, c)
                }
                g.fillRect(cx + k * 0.55f, cy - k * 0.38f, cx + k * 0.68f, cy + k * 0.38f, c)
            }
            PickupKind.SLOWMO -> hourglass(g, cx, cy, s, c)
            PickupKind.SHIELD -> shield(g, cx, cy, s, c, 0xFF0A1420.toInt())
            PickupKind.GRENADE -> grenade(g, cx, cy, s * 0.8f, c)
            PickupKind.MEDKIT -> {
                g.fillRoundRect(cx - k * 0.8f, cy - k * 0.65f, cx + k * 0.8f, cy + k * 0.65f, k * 0.2f, c)
                g.fillRect(cx - k * 0.14f, cy - k * 0.45f, cx + k * 0.14f, cy + k * 0.45f, cut(HOLE, c))
                g.fillRect(cx - k * 0.45f, cy - k * 0.14f, cx + k * 0.45f, cy + k * 0.14f, cut(HOLE, c))
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
        g.fillCircle(cx - r * 0.35f, cy - r * 0.1f, r * 0.24f, Col.alpha(0xFFFFFFFF.toInt(), 0.4f * Col.a(c) / 255f))
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

    /** A plate carrier: shoulder straps round a scooped neck, armholes cut in, webbing across the front. */
    fun vest(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        poly.begin()
            .add(cx - s * 0.3f, cy - s * 0.48f).add(cx - s * 0.13f, cy - s * 0.48f).add(cx - s * 0.1f, cy - s * 0.32f)
            .add(cx, cy - s * 0.25f).add(cx + s * 0.1f, cy - s * 0.32f).add(cx + s * 0.13f, cy - s * 0.48f)
            .add(cx + s * 0.3f, cy - s * 0.48f).add(cx + s * 0.3f, cy - s * 0.26f).add(cx + s * 0.42f, cy - s * 0.12f)
            .add(cx + s * 0.42f, cy + s * 0.4f).add(cx + s * 0.34f, cy + s * 0.48f).add(cx - s * 0.34f, cy + s * 0.48f)
            .add(cx - s * 0.42f, cy + s * 0.4f).add(cx - s * 0.42f, cy - s * 0.12f).add(cx - s * 0.3f, cy - s * 0.26f)
            .fill(g, c)
        // Webbing straps across the front plate.
        for (i in 0 until 2) {
            val y = cy + s * (0.08f + i * 0.17f)
            g.line(cx - s * 0.28f, y, cx + s * 0.28f, y, s * 0.05f, cut(0x99000000.toInt(), c))
        }
    }

    /** An eye, wide open: REFLEX sees the bullet coming. */
    fun eye(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val pb = poly.begin()
        for (i in 0..16) {
            val t = -1f + i / 8f
            pb.add(cx + t * k * 0.95f, cy - (1f - t * t) * k * 0.55f)
        }
        for (i in 1 until 16) {
            val t = 1f - i / 8f
            pb.add(cx + t * k * 0.95f, cy + (1f - t * t) * k * 0.55f)
        }
        pb.fill(g, c)
        g.fillCircle(cx, cy, k * 0.42f, cut(HOLE, c))
        g.fillCircle(cx, cy, k * 0.26f, c)
        g.fillCircle(cx + k * 0.1f, cy - k * 0.1f, k * 0.08f, cut(HOLE, c))
        // Alert lashes: it's the instant before the hit.
        for (i in -1..1) {
            val x = cx + i * k * 0.42f
            g.line(x, cy - k * 0.66f, x + i * k * 0.12f, cy - k * 0.9f, s * 0.07f, c)
        }
    }

    /** A four-leaf clover of heart-shaped leaves round a small gap, and a curling stem. */
    fun clover(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val ccx = cx - k * 0.08f
        val ccy = cy - k * 0.08f
        for (i in 0 until 4) {
            g.save()
            g.translate(ccx, ccy)
            g.rotate(45f + i * 90f)
            // Point to the centre: the heart's tip sits at the origin, lobes outward.
            g.rotate(180f)
            Glyphs.heart(g, 0f, -k * 0.44f, k * 0.8f, c)
            g.restore()
        }
        g.line(ccx, ccy, ccx + k * 0.78f, ccy + k * 0.86f, s * 0.08f, c)
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

    /** A five-point star: the STASH (a bonus, not a threat). */
    fun stash(g: Gfx, cx: Float, cy: Float, s: Float, c: Int) {
        val r = s * 0.5f
        val pb = poly.begin()
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val rr = if (i % 2 == 0) r else r * 0.44f
            pb.add(cx + cos(a) * rr, cy + sin(a) * rr + r * 0.06f)
        }
        pb.fill(g, c)
    }
}
