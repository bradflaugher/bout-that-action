package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.PickupKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Crisp vector icons for the HUD and the perk cards, drawn in a box [s] px wide centred on
 * (cx, cy) with one colour. Line weight scales with the box, so they read from a 5 mm HUD
 * chip up to a perk-card medallion.
 */
internal object HudIcons {
    // Scratch polygon per thread: the game thread and the menus (the hero picker) both draw
    // these, and a shared one would have them scribbling over each other's points.
    private val polys = ThreadLocal.withInitial { Poly() }
    private val poly: Poly get() = polys.get()

    /** A cut-out tone (a dark hole, a white tip) at the icon colour's own alpha, so dimmed icons dim whole. */
    private fun cut(tone: Int, c: Int) = Col.alpha(tone, Col.a(c) / 255f)

    private const val HOLE = 0xFF0A0810.toInt()

    /**
     * Every icon is laid out on a unit grid: (cx, cy) is the centre and [k] (half the box) is
     * one unit, so the art fills about ±0.92. One stroke weight ([W] units) runs through the
     * whole family, cut-outs are at least [GAP] units so they survive a 22 px chip, and corners
     * are round (round caps, round-rect ends).
     */
    private const val W = 0.28f
    private const val GAP = 0.1f

    fun perk(g: Gfx, p: Perk, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = W * k
        val h = cut(HOLE, c)
        when (p) {
            Perk.RAPID_FIRE -> {
                // Two rounds racing side by side, speed lines streaming off their tails.
                slug(g, cx - k * 0.24f, cy - k * 0.42f, cx + k * 0.96f, k * 0.27f, c, true)
                slug(g, cx - k * 0.56f, cy + k * 0.42f, cx + k * 0.64f, k * 0.27f, c, true)
                g.line(cx - k * 0.94f, cy - k * 0.42f, cx - k * 0.52f, cy - k * 0.42f, sw * 0.8f, c)
                g.line(cx - k * 0.94f, cy + k * 0.42f, cx - k * 0.84f, cy + k * 0.42f, sw * 0.8f, c)
            }
            Perk.HOLLOW_POINT -> {
                // A cartridge standing up, its flat nose drilled out into a deep hollow.
                shape(g, HP_ROUND, cx, cy, k, c)
                shape(g, HP_HOLE, cx, cy, k, h)
                g.line(cx - k * 0.44f, cy - k * 0.3f, cx - k * 0.44f, cy + k * 0.3f, k * GAP, h)

                // And the hit it lands: impact strikes off the nose.
                for (i in 0 until 3) {
                    val a = (i - 1) * 0.9f
                    g.line(cx + k * 0.36f + cos(a) * k * 0.3f, cy + sin(a) * k * 0.3f, cx + k * 0.36f + cos(a) * k * 0.6f, cy + sin(a) * k * 0.6f, sw * 0.8f, c)
                }
            }
            Perk.PIERCE -> {
                // A plate broken open, the round already out the far side, its trail behind.
                shape(g, PIERCE_TOP, cx - k * 0.44f, cy, k, c)
                shape(g, PIERCE_BOT, cx - k * 0.44f, cy, k, c)
                g.line(cx - k * 0.98f, cy, cx - k * 0.82f, cy, sw * 0.8f, c)
                slug(g, cx - k * 0.5f, cy, cx + k * 0.96f, k * 0.25f, c)
            }
            Perk.RICOCHET -> {
                // A shot glancing off a wall: in, off the surface, out again, the equal angles marked.
                g.fillRoundRect(cx - k * 0.96f, cy + k * 0.62f, cx + k * 0.96f, cy + k * 0.9f, k * 0.08f, c)
                g.line(cx - k * 0.86f, cy - k * 0.76f, cx - k * 0.08f, cy + k * 0.44f, sw, c)
                g.line(cx - k * 0.08f, cy + k * 0.44f, cx + k * 0.56f, cy - k * 0.52f, sw, c)
                arrowHead(g, cx + k * 0.84f, cy - k * 0.94f, 0.55f, -0.835f, k * 0.52f, c)
                g.strokeArc(cx - k * 0.08f, cy + k * 0.44f, k * 0.44f, 237f, 66f, sw * 0.5f, c)
            }
            Perk.SPLIT_SHOT -> {
                // One shot forking into two rounds: one high, one low.
                g.line(cx - k * 0.94f, cy, cx - k * 0.4f, cy, sw, c)
                g.line(cx - k * 0.4f, cy, cx - k * 0.04f, cy - k * 0.54f, sw, c)
                g.line(cx - k * 0.4f, cy, cx - k * 0.04f, cy + k * 0.54f, sw, c)
                slug(g, cx - k * 0.1f, cy - k * 0.54f, cx + k * 0.96f, k * 0.24f, c)
                slug(g, cx - k * 0.1f, cy + k * 0.54f, cx + k * 0.96f, k * 0.24f, c)
            }
            Perk.VITALITY -> {
                // A heart with a plus badge: one more of them.
                Glyphs.heart(g, cx - k * 0.14f, cy + k * 0.1f, k * 1.56f, c)
                g.fillCircle(cx + k * 0.56f, cy - k * 0.54f, k * 0.46f, h)
                g.fillRoundRect(cx + k * 0.47f, cy - k * 0.88f, cx + k * 0.65f, cy - k * 0.2f, k * 0.06f, c)
                g.fillRoundRect(cx + k * 0.22f, cy - k * 0.63f, cx + k * 0.9f, cy - k * 0.45f, k * 0.06f, c)
            }
            Perk.CQC -> {
                // A clenched fist, knuckles on: four curled fingers, the thumb locked across them.
                g.fillRoundRect(cx - k * 0.44f, cy + k * 0.5f, cx + k * 0.5f, cy + k * 0.96f, k * 0.08f, c)
                g.fillRect(cx - k * 0.46f, cy + k * 0.5f, cx + k * 0.52f, cy + k * 0.6f, h)
                g.fillRoundRect(cx - k * 0.7f, cy - k * 0.4f, cx + k * 0.72f, cy + k * 0.54f, k * 0.3f, c)
                for (i in 0 until 4) {
                    val x = cx + k * (-0.52f + i * 0.35f)
                    g.fillRoundRect(x - k * 0.18f, cy - k * FIST_TOP[i], x + k * 0.18f, cy - k * 0.1f, k * 0.18f, c)
                    if (i > 0) g.line(x - k * 0.175f, cy - k * (FIST_TOP[i] - 0.14f), x - k * 0.175f, cy - k * 0.2f, k * GAP, h)
                }
                g.fillRoundRect(cx - k * 0.5f, cy - k * 0.16f, cx + k * 0.32f, cy + k * 0.3f, k * 0.23f, h)
                g.fillRoundRect(cx - k * 0.8f, cy - k * 0.06f, cx + k * 0.22f, cy + k * 0.2f, k * 0.13f, c)
            }
            Perk.GHOST_BOX -> {
                // A cardboard box haunting the hallway: flaps up, a ghost's wavy hem, two eyes.
                val pb = poly.begin()
                    .add(cx - k * 0.72f, cy - k * 0.36f).add(cx + k * 0.72f, cy - k * 0.36f).add(cx + k * 0.72f, cy + k * 0.62f)
                for (j in 0 until 3) {
                    val lx = cx + k * (0.48f - j * 0.48f)
                    for (i in 0..6) {
                        val a = PI.toFloat() * i / 6f
                        pb.add(lx + cos(a) * k * 0.24f, cy + k * 0.62f + sin(a) * k * 0.3f * (if (j == 1) 1f else 0.85f))
                    }
                }
                pb.fill(g, c)
                shape(g, BOX_FLAP, cx, cy, k, c)
                shape(g, BOX_FLAP, cx, cy, k, c, -1f)
                g.fillRoundRect(cx - k * 0.46f, cy - k * 0.14f, cx - k * 0.16f, cy + k * 0.34f, k * 0.15f, h)
                g.fillRoundRect(cx + k * 0.16f, cy - k * 0.14f, cx + k * 0.46f, cy + k * 0.34f, k * 0.15f, h)
            }
            Perk.DOUBLE_JUMP -> {
                chevron(g, cx, cy - k * 0.34f, k * 0.72f, sw * 1.05f, c)
                chevron(g, cx, cy + k * 0.4f, k * 0.72f, sw * 1.05f, c)
            }
            Perk.DEMOLITION -> {
                // A blast: an eight-point starburst with a hot core. Not the grenade you throw.
                val pb = poly.begin()
                for (i in 0 until 16) {
                    val a = -PI.toFloat() / 2f + i * PI.toFloat() / 8f
                    val rr = if (i % 2 == 0) k * 0.98f else k * 0.52f
                    pb.add(cx + cos(a) * rr, cy + sin(a) * rr)
                }
                pb.fill(g, c)
                g.fillCircle(cx, cy, k * 0.3f, h)
                g.fillCircle(cx, cy, k * 0.15f, c)
            }
            Perk.MAGNET -> {
                g.strokeArc(cx, cy - k * 0.02f, k * 0.58f, 180f, -180f, sw * 1.6f, c)
                g.line(cx - k * 0.58f, cy - k * 0.02f, cx - k * 0.58f, cy - k * 0.5f, sw * 1.6f, c)
                g.line(cx + k * 0.58f, cy - k * 0.02f, cx + k * 0.58f, cy - k * 0.5f, sw * 1.6f, c)
                // Bold white poles.
                g.fillRect(cx - k * 0.8f, cy - k * 0.92f, cx - k * 0.36f, cy - k * 0.56f, cut(0xFFFFFFFF.toInt(), c))
                g.fillRect(cx + k * 0.36f, cy - k * 0.92f, cx + k * 0.8f, cy - k * 0.56f, cut(0xFFFFFFFF.toInt(), c))
            }
            Perk.REFLEX -> eye(g, cx, cy, s, c)
            Perk.ARMOR -> vest(g, cx, cy, s * 0.95f, c)
            Perk.LUCKY -> clover(g, cx, cy, s, c)
            Perk.SHOCKWAVE -> {
                // A stomp driving into the floor, the shock rippling away down the corridor both ways.
                g.fillRoundRect(cx - k * 0.96f, cy + k * 0.64f, cx + k * 0.96f, cy + k * 0.9f, k * 0.08f, c)
                g.line(cx, cy - k * 0.94f, cx, cy - k * 0.2f, sw, c)
                poly.tri(g, cx - k * 0.36f, cy - k * 0.26f, cx + k * 0.36f, cy - k * 0.26f, cx, cy + k * 0.46f, c)
                for (sx in -1..1 step 2) for (a in SHOCK_A) {
                    val ux = sx * cos(a)
                    val uy = -sin(a)
                    g.line(cx + ux * k * 0.5f, cy + k * 0.46f + uy * k * 0.5f, cx + ux * k * 0.92f, cy + k * 0.46f + uy * k * 0.92f, sw * 0.9f, c)
                }
            }
            Perk.STIFF_ARM -> {
                // Talk to the hand: a flat palm shoved out, the impact bursting off it.
                val hx = cx - k * 0.2f
                g.fillRoundRect(hx - k * 0.26f, cy + k * 0.4f, hx + k * 0.26f, cy + k * 0.96f, k * 0.06f, c)
                g.fillRect(hx - k * 0.28f, cy + k * 0.52f, hx + k * 0.28f, cy + k * 0.6f, h)
                g.fillRoundRect(hx - k * 0.44f, cy - k * 0.2f, hx + k * 0.44f, cy + k * 0.56f, k * 0.24f, c)
                for (i in 0 until 4) {
                    val x = hx + k * (-0.33f + i * 0.22f)
                    g.fillRoundRect(x - k * 0.11f, cy - k * PALM_TOP[i], x + k * 0.11f, cy, k * 0.11f, c)
                }
                g.line(hx - k * 0.3f, cy + k * 0.36f, hx - k * 0.66f, cy - k * 0.1f, k * 0.24f, c)
                // Impact: three bold strikes off the palm.
                g.line(hx + k * 0.66f, cy - k * 0.58f, hx + k * 0.94f, cy - k * 0.82f, sw * 0.9f, c)
                g.line(hx + k * 0.7f, cy - k * 0.04f, hx + k * 1.12f, cy - k * 0.04f, sw * 0.9f, c)
                g.line(hx + k * 0.66f, cy + k * 0.5f, hx + k * 0.94f, cy + k * 0.74f, sw * 0.9f, c)
            }
            Perk.AFTERSHOCK -> {
                // The seismograph needle jumping off the chart, over the ground line.
                val xs = QUAKE_X
                val ys = QUAKE_Y
                for (i in 0 until xs.size - 1) g.line(cx + xs[i] * k, cy + ys[i] * k, cx + xs[i + 1] * k, cy + ys[i + 1] * k, sw * 0.9f, c)
                g.fillRoundRect(cx - k * 0.96f, cy + k * 0.66f, cx + k * 0.96f, cy + k * 0.94f, k * 0.08f, c)
                // The ground cracking under the big jolt.
                poly.tri(g, cx - k * 0.1f, cy + k * 0.64f, cx + k * 0.26f, cy + k * 0.64f, cx + k * 0.04f, cy + k * 0.96f, h)
            }
            Perk.CANDY_RAIN -> {
                // A big wrapped sweet, and the heart it's worth.
                g.save()
                g.translate(cx - k * 0.22f, cy - k * 0.24f)
                g.rotate(-35f)
                candy(g, 0f, 0f, k * 0.42f, c)
                g.restore()
                Glyphs.heart(g, cx + k * 0.58f, cy + k * 0.6f, k * 0.74f, c)

            }
            Perk.SHOWSTOPPER -> {
                // A face mid-wink, one eye shut and a sparkle off the grin: did she just wink?
                val fx = cx - k * 0.12f
                val fy = cy + k * 0.12f
                g.fillCircle(fx, fy, k * 0.78f, c)
                g.fillCircle(fx - k * 0.3f, fy - k * 0.18f, k * 0.13f, h)
                g.strokeArc(fx + k * 0.3f, fy - k * 0.1f, k * 0.17f, 200f, 140f, k * GAP * 1.2f, h)
                g.strokeArc(fx, fy + k * 0.02f, k * 0.46f, 25f, 130f, k * GAP * 1.3f, h)
                g.fillCircle(cx + k * 0.66f, cy - k * 0.66f, k * 0.34f, h)
                spark(g, cx + k * 0.66f, cy - k * 0.66f, k * 0.3f, c)
            }
            Perk.SPIN_KICK -> {
                // A fighter's boot with the spin whirling round it and back on itself.
                g.strokeArc(cx, cy, k * 0.84f, 200f, 250f, sw * 0.8f, c)
                arrowHead(g, cx - k * 0.4f, cy + k * 0.84f, -1f, 0f, k * 0.42f, c)
                g.save()
                g.translate(cx - k * 0.1f, cy + k * 0.02f)
                boot(g, 0f, 0f, k * 0.72f, c)
                g.restore()
            }
            Perk.FLYING_KICK -> {
                // A boot diving in, speed lines streaming behind, the impact off the sole.
                g.save()
                g.translate(cx + k * 0.16f, cy - k * 0.02f)
                g.rotate(-30f)
                boot(g, 0f, 0f, k * 0.8f, c)
                g.restore()
                g.line(cx - k * 0.96f, cy - k * 0.62f, cx - k * 0.6f, cy - k * 0.62f, sw * 0.7f, c)
                g.line(cx - k * 0.96f, cy - k * 0.1f, cx - k * 0.54f, cy - k * 0.1f, sw * 0.7f, c)
                g.line(cx - k * 0.96f, cy + k * 0.42f, cx - k * 0.44f, cy + k * 0.42f, sw * 0.7f, c)
                spark(g, cx + k * 0.74f, cy + k * 0.66f, k * 0.3f, c)
            }
            Perk.CONFETTI -> {
                // A party popper going off: the striped cone, and confetti and a streamer bursting out.
                poly.tri(g, cx - k * 0.94f, cy + k * 0.94f, cx - k * 0.66f, cy - k * 0.04f, cx + k * 0.04f, cy + k * 0.66f, c)
                g.line(cx - k * 0.72f, cy + k * 0.3f, cx - k * 0.3f, cy + k * 0.72f, k * GAP, h)
                g.line(cx - k * 0.82f, cy + k * 0.62f, cx - k * 0.62f, cy + k * 0.82f, k * GAP, h)
                for (i in CONFETTI_X.indices) {
                    g.save()
                    g.translate(cx + CONFETTI_X[i] * k, cy + CONFETTI_Y[i] * k)
                    g.rotate(i * 37f)
                    g.fillRoundRect(-k * 0.13f, -k * 0.08f, k * 0.13f, k * 0.08f, k * 0.03f, c)
                    g.restore()
                }
                g.strokeArc(cx + k * 0.02f, cy - k * 0.42f, k * 0.2f, 180f, 180f, sw * 0.6f, c)
                g.strokeArc(cx + k * 0.42f, cy - k * 0.42f, k * 0.2f, 0f, 180f, sw * 0.6f, c)
            }
            Perk.CLOWN_CAR -> {
                // A tiny bubble car, three heads crammed out of the roof: how many fit in there?
                for (i in -1..1) g.fillCircle(cx + i * k * 0.36f, cy - k * 0.6f, k * 0.25f, h)
                for (i in -1..1) g.fillCircle(cx + i * k * 0.36f, cy - k * 0.62f, k * 0.19f, c)
                g.fillArc(cx, cy + k * 0.06f, k * 0.62f, 180f, 180f, h)
                g.fillArc(cx, cy + k * 0.06f, k * 0.54f, 180f, 180f, c)
                g.fillArc(cx, cy + k * 0.04f, k * 0.34f, 180f, 180f, h)
                g.fillRoundRect(cx - k * 0.96f, cy - k * 0.02f, cx + k * 0.96f, cy + k * 0.56f, k * 0.24f, c)
                for (sx in -1..1 step 2) {
                    g.fillCircle(cx + sx * k * 0.52f, cy + k * 0.6f, k * 0.36f, h)
                    g.fillCircle(cx + sx * k * 0.52f, cy + k * 0.6f, k * 0.27f, c)
                    g.fillCircle(cx + sx * k * 0.52f, cy + k * 0.6f, k * 0.09f, h)
                }
            }
            Perk.ENCORE -> {
                // Curtain call: the drapes swept back, the pelmet, and a star taking a bow.
                g.fillRoundRect(cx - k * 0.96f, cy - k * 0.96f, cx + k * 0.96f, cy - k * 0.72f, k * 0.08f, c)
                shape(g, DRAPE, cx, cy, k, c)
                shape(g, DRAPE, cx, cy, k, c, -1f)
                stash(g, cx, cy + k * 0.22f, k * 1.1f, c)
            }
            Perk.SIGNED_FOR -> {
                // A parcel with its label, a signature scrawled across it, and the pen.
                g.fillRoundRect(cx - k * 0.92f, cy - k * 0.5f, cx + k * 0.56f, cy + k * 0.92f, k * 0.1f, c)
                g.fillRect(cx - k * 0.92f, cy - k * 0.1f, cx + k * 0.56f, cy - k * 0.1f + k * GAP * 1.4f, h)
                g.fillRoundRect(cx - k * 0.72f, cy + k * 0.18f, cx + k * 0.36f, cy + k * 0.74f, k * 0.06f, h)
                for (i in 0 until SIG_X.size - 1) {
                    g.line(cx + SIG_X[i] * k, cy + SIG_Y[i] * k, cx + SIG_X[i + 1] * k, cy + SIG_Y[i + 1] * k, k * GAP * 1.1f, c)
                }
                // The pen, nib down on the box.
                g.line(cx + k * 0.84f, cy - k * 0.94f, cx + k * 0.28f, cy - k * 0.22f, sw * 1.1f, h)
                g.line(cx + k * 0.84f, cy - k * 0.94f, cx + k * 0.34f, cy - k * 0.3f, sw * 0.8f, c)
                poly.tri(g, cx + k * 0.34f, cy - k * 0.42f, cx + k * 0.46f, cy - k * 0.32f, cx + k * 0.24f, cy - k * 0.18f, c)
            }
            Perk.PACKING_PEANUTS -> {
                // Foam packing peanuts, tumbling every which way.
                peanut(g, cx - k * 0.42f, cy - k * 0.44f, k * 0.5f, 0.5f, c, h)
                peanut(g, cx + k * 0.46f, cy - k * 0.1f, k * 0.5f, -1.0f, c, h)
                peanut(g, cx - k * 0.2f, cy + k * 0.54f, k * 0.5f, 0.15f, c, h)
            }
            Perk.FRAGILE -> {
                // The sticker's stemmed glass, cracked right across: handle with care.
                g.fillRect(cx - k * 0.56f, cy - k * 0.92f, cx + k * 0.56f, cy - k * 0.42f, c)
                g.fillArc(cx, cy - k * 0.43f, k * 0.56f, 0f, 180f, c)
                g.line(cx, cy + k * 0.08f, cx, cy + k * 0.72f, sw * 0.9f, c)
                g.fillRoundRect(cx - k * 0.46f, cy + k * 0.66f, cx + k * 0.46f, cy + k * 0.9f, k * 0.1f, c)
                for (i in 0 until CRACK_X.size - 1) {
                    g.line(cx + CRACK_X[i] * k, cy + CRACK_Y[i] * k, cx + CRACK_X[i + 1] * k, cy + CRACK_Y[i + 1] * k, k * GAP * 1.2f, h)
                }
            }
        }
    }

    /** A fighter's boot standing toe-right, [r] from the cuff to the sole, centred on (x, y). */
    private fun boot(g: Gfx, x: Float, y: Float, r: Float, c: Int) {
        val pb = poly.begin()
        var i = 0
        while (i < BOOT.size) {
            pb.add(x + BOOT[i] * r, y + BOOT[i + 1] * r)
            i += 2
        }
        pb.fill(g, c)
        val hc = cut(HOLE, c)
        g.line(x - r * 0.4f, y + r * 0.42f, x + r * 0.66f, y + r * 0.42f, r * GAP, hc)
        g.line(x - r * 0.4f, y - r * 0.62f, x + r * 0.1f, y - r * 0.62f, r * GAP, hc)
    }

    /** A foam packing peanut: a fat, pinched squiggle [r] long, turned [ang] radians, dimpled. */
    private fun peanut(g: Gfx, x: Float, y: Float, r: Float, ang: Float, c: Int, h: Int) {
        val ux = cos(ang)
        val uy = sin(ang)
        g.fillCircle(x - ux * r * 0.42f, y - uy * r * 0.42f, r * 0.44f, c)
        g.fillCircle(x + ux * r * 0.42f, y + uy * r * 0.42f, r * 0.44f, c)
        g.line(x - ux * r * 0.3f, y - uy * r * 0.3f, x + ux * r * 0.3f, y + uy * r * 0.3f, r * 0.62f, c)
        g.fillCircle(x - ux * r * 0.46f, y - uy * r * 0.46f, r * 0.12f, h)
        g.fillCircle(x + ux * r * 0.46f, y + uy * r * 0.46f, r * 0.12f, h)
    }

    /** A projectile in flight pointing right, rear at x0, tip at x1, [r] its half-thickness. */
    private fun slug(g: Gfx, x0: Float, y: Float, x1: Float, r: Float, c: Int, case: Boolean = false) {
        // A tangent ogive: the nose curves smoothly off the body to a point.
        val nose = r * 2.1f
        val sh = x1 - nose
        val rho = (r * r + nose * nose) / (2f * r)
        val pb = poly.begin().add(x0, y + r).add(x0, y - r)
        for (i in 0..6) {
            val u = nose * i / 7f
            pb.add(sh + u, y - (sqrt(rho * rho - u * u) + r - rho))
        }
        pb.add(x1, y)
        for (i in 6 downTo 0) {
            val u = nose * i / 7f
            pb.add(sh + u, y + (sqrt(rho * rho - u * u) + r - rho))
        }
        pb.fill(g, c)
        if (case) g.line(x0 + (x1 - x0) * 0.42f, y - r * 0.8f, x0 + (x1 - x0) * 0.42f, y + r * 0.8f, r * 0.36f, cut(HOLE, c))
    }

    /** A solid arrowhead with its tip at (x, y), pointing along the unit vector (dx, dy). */
    private fun arrowHead(g: Gfx, x: Float, y: Float, dx: Float, dy: Float, len: Float, c: Int) {
        val bx = x - dx * len
        val by = y - dy * len
        poly.tri(g, x, y, bx - dy * len * 0.6f, by + dx * len * 0.6f, bx + dy * len * 0.6f, by - dx * len * 0.6f, c)
    }

    /** Fills a unit-grid polygon [pts] (x0, y0, x1, y1, ...), mirrored when [fx] is -1. */
    private fun shape(g: Gfx, pts: FloatArray, cx: Float, cy: Float, k: Float, c: Int, fx: Float = 1f) {
        val pb = poly.begin()
        var i = 0
        while (i < pts.size) {
            pb.add(cx + pts[i] * k * fx, cy + pts[i + 1] * k)
            i += 2
        }
        pb.fill(g, c)
    }

    /** A wrapped sweet: a round candy, stripes across it, its wrapper twisted into fans at both ends. */
    private fun candy(g: Gfx, x: Float, y: Float, r: Float, c: Int) {
        for (sx in -1..1 step 2) {
            poly.begin()
                .add(x + sx * r * 0.7f, y - r * 0.2f).add(x + sx * r * 1.9f, y - r * 0.9f)
                .add(x + sx * r * 1.7f, y).add(x + sx * r * 1.9f, y + r * 0.9f).add(x + sx * r * 0.7f, y + r * 0.2f)
                .fill(g, c)
        }
        g.fillCircle(x, y, r, c)
        val hc = cut(HOLE, c)
        g.line(x - r * 0.25f, y - r * 0.8f, x - r * 0.25f, y + r * 0.8f, r * 0.26f, hc)
        g.line(x + r * 0.35f, y - r * 0.8f, x + r * 0.35f, y + r * 0.8f, r * 0.26f, hc)
    }

    /** A four-point spark. */
    private fun spark(g: Gfx, x: Float, y: Float, r: Float, c: Int) {
        poly.begin()
            .add(x, y - r).add(x + r * 0.28f, y - r * 0.28f).add(x + r, y).add(x + r * 0.28f, y + r * 0.28f)
            .add(x, y + r).add(x - r * 0.28f, y + r * 0.28f).add(x - r, y).add(x - r * 0.28f, y - r * 0.28f)
            .fill(g, c)
    }

    /** Knuckle heights of the fist (index to little finger) and of the open palm's fingers. */
    private val FIST_TOP = floatArrayOf(0.74f, 0.84f, 0.8f, 0.66f)
    private val PALM_TOP = floatArrayOf(0.66f, 0.84f, 0.8f, 0.6f)

    /** The hollow point, lying nose-right: a rimmed case, an ogive shoulder, a flat drilled-out nose. */
    private val HP_ROUND = floatArrayOf(
        -0.96f, -0.36f, -0.46f, -0.36f, -0.18f, -0.36f, 0.02f, -0.33f, 0.16f, -0.28f,
        0.26f, -0.23f, 0.33f, -0.21f, 0.33f, 0.21f, 0.26f, 0.23f, 0.16f, 0.28f, 0.02f, 0.33f, -0.18f, 0.36f,
        -0.46f, 0.36f, -0.96f, 0.36f,
    )
    private val HP_HOLE = floatArrayOf(0.34f, -0.14f, 0.34f, 0.14f, 0.06f, 0.05f, 0.06f, -0.05f)


    /** The plate PIERCE punches through, snapped open where the round went. */
    private val PIERCE_TOP = floatArrayOf(-0.19f, -0.96f, 0.19f, -0.96f, 0.19f, -0.46f, 0.06f, -0.36f, -0.06f, -0.46f, -0.19f, -0.34f)
    private val PIERCE_BOT = floatArrayOf(-0.19f, 0.96f, 0.19f, 0.96f, 0.19f, 0.36f, 0.06f, 0.46f, -0.06f, 0.34f, -0.19f, 0.44f)

    /** The left flap of the ghost box, folded up and out (mirrored for the right). */
    private val BOX_FLAP = floatArrayOf(-0.72f, -0.46f, -0.06f, -0.46f, -0.2f, -0.78f, -0.96f, -0.72f)

    /** Shockwave spray angles off the floor (radians up from horizontal). */
    private val SHOCK_A = floatArrayOf(0.1f, 0.56f)



    private val QUAKE_X = floatArrayOf(-0.96f, -0.62f, -0.42f, -0.16f, 0.12f, 0.4f, 0.62f, 0.96f)
    private val QUAKE_Y = floatArrayOf(0.1f, 0.1f, -0.44f, 0.46f, -0.9f, 0.3f, 0.1f, 0.1f)
    /** The fighter's boot, toe right: the shaft, the ankle, a rounded toe and a flat heel. */
    private val BOOT = floatArrayOf(
        -0.4f, -0.8f, 0.1f, -0.8f, 0.12f, -0.04f, 0.46f, 0.06f, 0.62f, 0.16f, 0.68f, 0.3f, 0.66f, 0.52f, -0.42f, 0.52f,
    )
    /** The left drape of the curtain call, swept back (mirrored for the right). */
    private val DRAPE = floatArrayOf(-0.96f, -0.74f, -0.3f, -0.74f, -0.5f, -0.44f, -0.66f, -0.02f, -0.7f, 0.94f, -0.96f, 0.94f)
    /** Confetti flying off the popper. */
    private val CONFETTI_X = floatArrayOf(-0.46f, 0.7f, 0.2f, 0.76f, -0.08f, 0.4f)
    private val CONFETTI_Y = floatArrayOf(-0.62f, -0.78f, -0.02f, 0.26f, -0.86f, 0.52f)
    /** The signature's scrawl across the parcel label. */
    private val SIG_X = floatArrayOf(-0.6f, -0.46f, -0.36f, -0.26f, -0.12f, 0f, 0.1f, 0.24f)
    private val SIG_Y = floatArrayOf(0.56f, 0.3f, 0.6f, 0.36f, 0.58f, 0.34f, 0.5f, 0.44f)
    /** The crack down the fragile glass. */
    private val CRACK_X = floatArrayOf(-0.1f, 0.1f, -0.06f, 0.14f, 0.02f)
    private val CRACK_Y = floatArrayOf(-0.92f, -0.66f, -0.46f, -0.24f, -0.02f)

    fun pickup(g: Gfx, p: PickupKind, cx: Float, cy: Float, s: Float, c: Int) {
        val k = s / 2f
        val sw = s * 0.11f
        val h = cut(HOLE, c)
        when (p) {
            PickupKind.SHOTGUN -> {
                // A pump shotgun: long barrel over the pump, a receiver and trigger, a dropped stock.
                g.fillRoundRect(cx - k * 0.3f, cy - k * 0.38f, cx + k * 0.98f, cy - k * 0.16f, k * 0.07f, c)
                g.fillRect(cx - k * 0.1f, cy - k * 0.18f, cx + k * 0.86f, cy - k * 0.04f, c)
                g.fillRoundRect(cx + k * 0.24f, cy - k * 0.16f, cx + k * 0.74f, cy + k * 0.14f, k * 0.12f, c)
                shape(g, SHOTGUN_BODY, cx, cy, k, c)
                g.strokeRoundRect(cx - k * 0.3f, cy - k * 0.02f, cx + k * 0.02f, cy + k * 0.24f, k * 0.1f, k * 0.09f, c)
            }
            PickupKind.MINIGUN -> {
                // A rotary: a fat motor housing with a carry handle and a clamped cluster of barrels.
                g.fillRoundRect(cx - k * 0.96f, cy - k * 0.4f, cx - k * 0.16f, cy + k * 0.44f, k * 0.16f, c)
                for (i in 0 until 3) {
                    val y = cy - k * 0.28f + i * k * 0.29f
                    g.fillRoundRect(cx - k * 0.2f, y - k * 0.11f, cx + k * 0.98f, y + k * 0.11f, k * 0.11f, c)
                }
                g.fillRoundRect(cx + k * 0.5f, cy - k * 0.48f, cx + k * 0.7f, cy + k * 0.5f, k * 0.05f, c)
            }
            PickupKind.SLOWMO -> hourglass(g, cx, cy, s, c)
            PickupKind.SHIELD -> shield(g, cx, cy, s, c, 0xFF0A1420.toInt())
            PickupKind.GRENADE -> grenade(g, cx, cy + s * 0.03f, s * 0.96f, c)
            PickupKind.MEDKIT -> {
                g.fillRoundRect(cx - k * 0.84f, cy - k * 0.66f, cx + k * 0.84f, cy + k * 0.66f, k * 0.2f, c)
                g.fillRect(cx - k * 0.14f, cy - k * 0.46f, cx + k * 0.14f, cy + k * 0.46f, h)
                g.fillRect(cx - k * 0.46f, cy - k * 0.14f, cx + k * 0.46f, cy + k * 0.14f, h)
            }
            PickupKind.CASH -> {
                g.fillCircle(cx, cy, k * 0.86f, c)
                g.strokeCircle(cx, cy, k * 0.66f, k * 0.08f, h)
                g.text("$", cx, cy + k * 0.4f, k * 1.1f, h, Gfx.Font.TITLE, Gfx.Align.CENTER)
            }
        }
    }

    /** The shotgun's receiver, pistol grip and stock in one piece. */
    private val SHOTGUN_BODY = floatArrayOf(
        -0.36f, -0.42f, 0.06f, -0.42f, 0.06f, 0.04f, -0.36f, 0.08f, -0.98f, 0.42f, -0.98f, 0.0f, -0.6f, -0.22f,


    )


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
        val sw = s * 0.12f
        g.line(cx - k * 0.62f, cy - k * 0.86f, cx + k * 0.62f, cy - k * 0.86f, sw, c)
        g.line(cx - k * 0.62f, cy + k * 0.86f, cx + k * 0.62f, cy + k * 0.86f, sw, c)
        g.line(cx - k * 0.46f, cy - k * 0.8f, cx + k * 0.46f, cy + k * 0.8f, sw * 0.8f, c)
        g.line(cx + k * 0.46f, cy - k * 0.8f, cx - k * 0.46f, cy + k * 0.8f, sw * 0.8f, c)
        // The sand, heaped in the bottom bulb.
        poly.tri(g, cx - k * 0.3f, cy + k * 0.76f, cx + k * 0.3f, cy + k * 0.76f, cx, cy + k * 0.36f, c)
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
        g.line(ccx, ccy, ccx + k * 0.78f, ccy + k * 0.86f, s * 0.11f, c)
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
