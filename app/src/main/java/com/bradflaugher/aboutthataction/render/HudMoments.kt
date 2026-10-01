package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.FloatingText
import com.bradflaugher.aboutthataction.engine.FloorEvent
import com.bradflaugher.aboutthataction.engine.FloorLabel
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.engine.TextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen-space cards for the fun pass: the special-floor stinger (a snappy cousin of the zone
 * title card) and the coach tip plate. Both keep off the stage, the player's own floor.
 */
internal class HudMoments(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly

    // ======================================================= special floors

    private var kickerFloor = -1
    private var kicker = ""

    /** The engine's BIG popup naming a special floor, if one is up (it's drawn as this card instead). */
    private fun eventText(): FloatingText? {
        val list = f.w.fx.texts
        for (i in list.indices) {
            val ft = list[i]
            if (ft.style == TextStyle.BIG && eventOf(ft.text) != FloorEvent.NONE) return ft
        }
        return null
    }

    /**
     * BLACKOUT / NAP TIME / PAYDAY: a skewed band that slams in from the left with the event's
     * icon in a coloured block, the name in big type, a one-line joke, and a little flourish of
     * its own (the power cutting out, a lullaby bob, gold glints), then whips off to the right.
     */
    fun floorEvent() {
        val ft = eventText() ?: return
        val ev = eventOf(ft.text)
        val w = f.w
        val W = g.width
        val H = g.height
        val u = Hud.unit(W)
        val life = ft.maxLife
        val age = ft.t * life
        val inK = HudType.outBack(HudType.clamp01(age / 0.3f), 1.4f)
        val outK = HudType.inCubic(HudType.clamp01((age - (life - 0.32f)) / 0.32f))
        val a = HudType.clamp01(age / 0.1f) * (1f - outK)
        if (a <= 0f) return
        val color = eventColor(ev)

        // Under the stage (like the zone card), or under the zone card if that's up too.
        val stageBottom = (Geo.groundY(w.player.floorF) + Building.SLAB - f.camY) * f.s
        var cy = min(H * 0.72f, max(H * 0.3f, stageBottom + 17f * u))
        if (w.bannerZone != null && w.bannerTime > 0f) cy = min(H * 0.82f, cy + 30f * u)
        val bh = 15f * u
        val bw = 86f * u
        val sk = 2.2f * u
        val cx = W / 2f - (1f - inK) * W * 0.9f + outK * W * 1.1f
        val l = cx - bw / 2f
        val r = cx + bw / 2f
        val t = cy - bh / 2f
        val b = cy + bh / 2f

        // Entry pop: a quick flash in the event colour.
        if (age < 0.18f) {
            g.blend(Gfx.Blend.ADD)
            g.fillRect(0f, 0f, W, H, Col.alpha(color, 0.12f * (1f - age / 0.18f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        // Shadow, plate, glow.
        poly.quad(g, l + sk + 0.8f * u, t + 1.2f * u, r + sk + 0.8f * u, t + 1.2f * u, r - sk + 0.8f * u, b + 1.2f * u, l - sk + 0.8f * u, b + 1.2f * u, Col.alpha(0xFF000000.toInt(), 0.5f * a))
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(cx, cy)
        g.scale(1f, 0.32f)
        g.glow(0f, 0f, bw * 0.62f, Col.alpha(color, 0.3f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        poly.quad(g, l + sk, t, r + sk, t, r - sk, b, l - sk, b, Col.alpha(0xFF08060E.toInt(), 0.94f * a))
        g.save()
        g.clipRect(l - sk, t, r + sk, b)
        g.fillVerticalGradient(l - sk, t, r + sk, b, Col.alpha(color, 0.14f * a), Col.alpha(color, 0.02f * a))
        g.restore()
        // Edges: bright top rule, event-specific bottom trim.
        g.line(l + sk, t, r + sk, t, 0.35f * u, Col.alpha(color, a))
        when (ev) {
            FloorEvent.BLACKOUT -> hazardTrim(l, r, b, sk, u, a)
            else -> g.line(l - sk, b, r - sk, b, 0.25f * u, Col.alpha(color, 0.6f * a))
        }

        // Icon block.
        val iw = 15f * u
        poly.quad(g, l + sk, t, l + iw + sk, t, l + iw - sk, b, l - sk, b, Col.alpha(color, a))
        val icx = l + iw / 2f
        eventIcon(ev, icx, cy, 9.5f * u, Col.alpha(0xFF0A0810.toInt(), a), age)

        // Kicker, title, joke.
        val tx = l + iw + 3f * u
        if (w.player.floor != kickerFloor) {
            kickerFloor = w.player.floor
            kicker = "SPECIAL FLOOR  ·  " + FloorLabel.of(kickerFloor)
        }
        HudType.tracked(g, kicker, tx, t + 3.6f * u, 2f * u, Col.alpha(color, 0.9f * a), Gfx.Font.HUD, Gfx.Align.LEFT, 0.45f * u)
        titleLetters(ev, ft.text, tx, t + 9.9f * u, 6.2f * u, u, a, age, color)
        HudType.tracked(g, jokeOf(ev), tx, t + 13.1f * u, 2.1f * u, Col.alpha(0xFFE8E4F4.toInt(), 0.8f * a * HudType.clamp01((age - 0.2f) / 0.25f)), Gfx.Font.HUD, Gfx.Align.LEFT, 0.25f * u)

        if (ev == FloorEvent.PAYDAY) glints(l, r, t, b, u, a, age)
    }

    private fun titleLetters(ev: FloorEvent, title: String, x0: Float, y: Float, size: Float, u: Float, a: Float, age: Float, color: Int) {
        val tracking = 0.6f * u
        var x = x0
        val tick = (f.t * 20f).toInt()
        for (i in title.indices) {
            val c = title[i]
            val cs = charStr(c)
            val cw = g.textWidth(cs, size, Gfx.Font.TITLE)
            val k = HudType.clamp01((age - 0.08f - i * 0.03f) / 0.2f)
            if (k > 0f && c != ' ') {
                var la = k * a
                var dy = (1f - HudType.outCubic(k)) * -2.5f * u
                when (ev) {
                    // The power stutters: letters drop out and come back, then hold.
                    FloorEvent.BLACKOUT -> if (age < 0.9f && hash(tick * 13 + i, 91) < 0.35f) la *= 0.15f
                    // A lullaby sway: the whole word rocks, gently.
                    FloorEvent.NAP_TIME -> dy += sin(f.t * 2.4f + i * 0.35f) * 0.18f * u
                    else -> Unit
                }
                g.text(cs, x + 0.35f * u, y + dy + 0.45f * u, size, Col.alpha(0xFF000000.toInt(), 0.6f * la), Gfx.Font.TITLE)
                if (k < 1f) {
                    val split = (1f - k) * 1.6f * u
                    g.blend(Gfx.Blend.ADD)
                    g.text(cs, x - split, y + dy, size, Col.alpha(color, 0.7f * la), Gfx.Font.TITLE)
                    g.text(cs, x + split, y + dy, size, Col.alpha(0xFF2BE8FF.toInt(), 0.5f * la), Gfx.Font.TITLE)
                    g.blend(Gfx.Blend.NORMAL)
                }
                g.text(cs, x, y + dy, size, Col.alpha(0xFFFFFFFF.toInt(), la), Gfx.Font.TITLE)
            }
            x += cw + tracking
        }
    }

    /** Red-and-black hazard tape along the band's bottom edge. */
    private fun hazardTrim(l: Float, r: Float, b: Float, sk: Float, u: Float, a: Float) {
        val h = 1.1f * u
        g.save()
        g.clipRect(l - sk, b - h, r - sk + h, b)
        g.fillRect(l - sk, b - h, r, b, Col.alpha(0xFF100406.toInt(), a))
        var x = l - sk - h + fract(f.t * 0.6f) * 3f * u
        while (x < r) {
            poly.quad(g, x, b, x + 1.5f * u, b, x + 1.5f * u + h, b - h, x + h, b - h, Col.alpha(0xFFFF3348.toInt(), 0.9f * a))
            x += 3f * u
        }
        g.restore()
    }

    /** Twinkling four-point glints skating across the PAYDAY band. */
    private fun glints(l: Float, r: Float, t: Float, b: Float, u: Float, a: Float, age: Float) {
        g.blend(Gfx.Blend.ADD)
        for (i in 0 until 6) {
            val ph = fract(age * 0.9f + i / 6f)
            // Along the edges and over the coin, never over the type.
            val edge = i % 3
            val x = if (edge == 0) l + 7.5f * u + (hash(i, 72) - 0.5f) * 8f * u else l + (r - l) * (0.3f + 0.65f * hash(i, 71))
            val y = if (edge == 0) t + (b - t) * hash(i, 74) else if (edge == 1) t else b
            val s = sin(ph * PI.toFloat()) * (1.2f + 0.8f * hash(i, 73)) * u
            sparkle(x, y, s, Col.alpha(0xFFFFF0B0.toInt(), a))
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    private fun sparkle(x: Float, y: Float, s: Float, c: Int) {
        if (s <= 0.05f) return
        poly.begin().add(x, y - s).add(x + s * 0.22f, y - s * 0.22f).add(x + s, y).add(x + s * 0.22f, y + s * 0.22f)
            .add(x, y + s).add(x - s * 0.22f, y + s * 0.22f).add(x - s, y).add(x - s * 0.22f, y - s * 0.22f).fill(g, c)
    }

    private fun eventIcon(ev: FloorEvent, cx: Float, cy: Float, s: Float, c: Int, age: Float) {
        val k = s / 2f
        when (ev) {
            FloorEvent.BLACKOUT -> {
                // A light bulb, struck through.
                g.fillCircle(cx, cy - k * 0.18f, k * 0.5f, c)
                g.fillRect(cx - k * 0.24f, cy + k * 0.2f, cx + k * 0.24f, cy + k * 0.52f, c)
                g.fillRect(cx - k * 0.2f, cy + k * 0.58f, cx + k * 0.2f, cy + k * 0.66f, c)
                g.line(cx - k * 0.72f, cy - k * 0.78f, cx + k * 0.72f, cy + k * 0.78f, k * 0.16f, eventColor(ev))
                g.line(cx - k * 0.72f, cy - k * 0.78f, cx + k * 0.72f, cy + k * 0.78f, k * 0.09f, c)
            }
            FloorEvent.NAP_TIME -> {
                // Crescent moon (a disc with a bite of the block colour) and a little z.
                g.fillCircle(cx - k * 0.1f, cy + k * 0.05f, k * 0.62f, c)
                g.fillCircle(cx + k * 0.18f, cy - k * 0.12f, k * 0.52f, eventColor(ev))
                val zy = cy - k * 0.55f - fract(age * 0.8f) * k * 0.2f
                val zx = cx + k * 0.5f
                val zs = k * 0.22f
                g.line(zx - zs, zy - zs, zx + zs, zy - zs, k * 0.1f, c)
                g.line(zx + zs, zy - zs, zx - zs, zy + zs, k * 0.1f, c)
                g.line(zx - zs, zy + zs, zx + zs, zy + zs, k * 0.1f, c)
            }
            else -> {
                // A coin, flipping.
                val flip = abs(cos(age * 5f))
                g.save()
                g.translate(cx, cy)
                g.scale(0.25f + 0.75f * flip, 1f)
                g.fillCircle(0f, 0f, k * 0.72f, c)
                g.strokeCircle(0f, 0f, k * 0.52f, k * 0.07f, eventColor(ev))
                g.restore()
                if (flip > 0.5f) g.text("$", cx, cy + k * 0.32f, k * 0.95f * flip, eventColor(ev), Gfx.Font.TITLE, Gfx.Align.CENTER)
            }
        }
    }

    private fun eventOf(text: String): FloorEvent = when (text) {
        FloorEvent.BLACKOUT.title -> FloorEvent.BLACKOUT
        FloorEvent.NAP_TIME.title -> FloorEvent.NAP_TIME
        FloorEvent.PAYDAY.title -> FloorEvent.PAYDAY
        else -> FloorEvent.NONE
    }

    private fun eventColor(ev: FloorEvent): Int = when (ev) {
        FloorEvent.BLACKOUT -> 0xFFFF3348.toInt()
        FloorEvent.NAP_TIME -> 0xFFB6A4FF.toInt()
        else -> 0xFFFFC83A.toInt()
    }

    private fun jokeOf(ev: FloorEvent): String = when (ev) {
        FloorEvent.BLACKOUT -> "LIGHTS OUT. THEY CAN'T SEE YOU EITHER."
        FloorEvent.NAP_TIME -> "SHHH. SOMEBODY'S ON HIS BREAK."
        else -> "SOMEBODY LEFT THE LOOT OUT."
    }

    private val charCache = Array(128) { it.toChar().toString() }
    private fun charStr(c: Char) = if (c.code < 128) charCache[c.code] else c.toString()

    // ============================================================ challenges

    private var chWorld: World? = null
    private var chId = -1
    private var chName = ""
    private var chFoot = ""
    private val rayBuf = FloatArray(6)

    /**
     * CHALLENGE CLEARED: a gold band bursts open across the screen with a sunburst behind it,
     * a tick stamp slams down, CLEARED drops in letter by letter, and confetti rains. Then it
     * folds away and the run goes on. A busted UNTOUCHED gets a smaller red BUSTED band.
     */
    fun challenge() {
        val run = f.w.challenge ?: return
        val cleared = run.cleared
        if (!cleared && !run.failed) return
        // No party on the way out: a clear just before the fatal hit keeps its card off the death.
        if (cleared && f.w.phase != Phase.PLAYING && f.w.phase != Phase.PERK_CHOICE) return
        val at = if (cleared) run.clearedAt else run.failedAt
        val life = if (cleared) CLEAR_TIME else BUST_TIME
        val age = f.wt - at
        if (at < 0f || age < 0f || age > life) return
        val ch = run.challenge
        if (ch.id != chId || f.w !== chWorld) {
            chWorld = f.w
            chId = ch.id
            chName = ch.name
            chFoot = "#" + ch.id + "  ·  " + ch.tier.title + "  ·  KEEP GOING FOR SCORE"
        }
        val w = f.w
        val W = g.width
        val H = g.height
        val u = Hud.unit(W)
        val stageBottom = (Geo.groundY(w.player.floorF) + Building.SLAB - f.camY) * f.s
        var cy = min(H * 0.7f, max(H * 0.36f, stageBottom + 26f * u))
        if (w.bannerZone != null && w.bannerTime > 0f) cy = min(H * 0.8f, cy + 30f * u)
        val out = HudType.clamp01((life - age) / 0.45f)
        val open = HudType.outCubic(HudType.clamp01(age / 0.35f)) * HudType.outCubic(out)
        val a = HudType.clamp01(age / 0.12f) * out
        if (a <= 0f) return
        if (cleared) clearedCard(cy, age, open, a, out, u) else bustedCard(cy, age, open, a, u)
    }

    private fun clearedCard(cy: Float, age: Float, open: Float, a: Float, out: Float, u: Float) {
        val W = g.width
        val H = g.height
        val gold = CH_GOLD
        // The pop: a gold wash over everything.
        if (age < 0.3f) {
            g.blend(Gfx.Blend.ADD)
            g.fillRect(0f, 0f, W, H, Col.alpha(gold, 0.2f * (1f - age / 0.3f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        // Sunburst: soft rays fanning out from the stamp, turning slowly.
        val sx = W / 2f
        val sy = cy - 9.5f * u
        val rays = 16
        val reach = W * 0.62f * (0.4f + 0.6f * open)
        val turn = age * 9f
        g.blend(Gfx.Blend.ADD)
        for (i in 0 until rays) {
            val ang = ((i * 360f / rays) + turn) * (PI.toFloat() / 180f)
            val half = (360f / rays) * 0.2f * (PI.toFloat() / 180f)
            val xy = rayBuf
            xy[0] = sx; xy[1] = sy
            xy[2] = sx + cos(ang - half) * reach; xy[3] = sy + sin(ang - half) * reach
            xy[4] = sx + cos(ang + half) * reach; xy[5] = sy + sin(ang + half) * reach
            // Bright at the medal, gone by the tip.
            g.fillPolygonGradient(
                xy, sx, sy, sx + cos(ang) * reach, sy + sin(ang) * reach,
                Col.alpha(gold, 0.26f * a), Col.alpha(gold, 0.08f * a), Col.alpha(gold, 0f), 0.35f,
            )
        }
        g.glow(sx, sy, 30f * u * open, Col.alpha(gold, 0.25f * a))
        g.blend(Gfx.Blend.NORMAL)

        // The band, opening from its centre line.
        val half = 15.5f * u * open
        val feather = 9f * u * open
        val dark = Col.alpha(0xFF07050C.toInt(), 0.93f * a)
        g.fillVerticalGradient(0f, cy - half - feather, W, cy - half, 0x00000000, dark)
        g.fillRect(0f, cy - half, W, cy + half, dark)
        g.fillVerticalGradient(0f, cy + half, W, cy + half + feather, dark, 0x00000000)
        g.fillVerticalGradient(0f, cy - half, W, cy + half, Col.alpha(gold, 0.12f * a), Col.alpha(CH_PINK, 0.05f * a))
        // Gold rules wiping outward, top and bottom.
        val lw = W * 0.86f * HudType.outCubic(HudType.clamp01((age - 0.05f) / 0.45f)) * out
        val ly1 = cy - half + 1.4f * u
        val ly2 = cy + half - 1.4f * u
        g.fillRect(W / 2f - lw / 2f, ly1, W / 2f + lw / 2f, ly1 + 0.3f * u, Col.alpha(gold, 0.9f * a))
        g.fillRect(W / 2f - lw / 2f, ly2 - 0.3f * u, W / 2f + lw / 2f, ly2, Col.alpha(gold, 0.9f * a))
        g.blend(Gfx.Blend.ADD)
        g.glow(W / 2f - lw / 2f, ly1, 2.4f * u, Col.alpha(gold, a))
        g.glow(W / 2f + lw / 2f, ly1, 2.4f * u, Col.alpha(gold, a))
        g.glow(W / 2f - lw / 2f, ly2, 2.4f * u, Col.alpha(CH_PINK, a))
        g.glow(W / 2f + lw / 2f, ly2, 2.4f * u, Col.alpha(CH_PINK, a))
        g.blend(Gfx.Blend.NORMAL)

        // The stamp: a gold medal with a tick, slammed down from big.
        val sk = HudType.clamp01((age - 0.08f) / 0.32f)
        if (sk > 0f) {
            val sc = 2.2f - 1.2f * HudType.outBack(sk, 2.2f)
            val r = 5.4f * u * sc
            val sa = HudType.clamp01(sk * 3f) * a
            val ringAge = age - 0.4f
            if (ringAge in 0f..0.6f) {
                val q = ringAge / 0.6f
                g.strokeCircle(sx, sy, 5.4f * u * (1f + q * 1.8f), 0.5f * u * (1f - q), Col.alpha(gold, 0.9f * (1f - q) * a))
            }
            g.fillCircle(sx, sy + 0.6f * u, r, Col.alpha(0xFF000000.toInt(), 0.5f * sa))
            g.fillCircle(sx, sy, r, Col.alpha(gold, sa))
            g.fillCircle(sx, sy - r * 0.08f, r * 0.84f, Col.alpha(0xFFFFE08A.toInt(), sa))
            g.strokeCircle(sx, sy, r * 0.72f, 0.28f * u * sc, Col.alpha(0xFFB87A10.toInt(), 0.7f * sa))
            // Notched medal rim.
            for (i in 0 until 16) {
                val ang = i * (PI.toFloat() / 8f) + 0.2f
                g.fillCircle(sx + cos(ang) * r, sy + sin(ang) * r, 0.55f * u * sc, Col.alpha(gold, sa))
            }
            val ink = Col.alpha(0xFF2A1A04.toInt(), sa)
            val tk = HudType.clamp01((age - 0.3f) / 0.22f)
            val ax = sx - r * 0.38f
            val ay = sy + r * 0.0f
            val mx = sx - r * 0.1f
            val my = sy + r * 0.3f
            val ex = sx + r * 0.42f
            val ey = sy - r * 0.32f
            val sw = 0.95f * u * sc
            if (tk > 0f) {
                val k1 = min(1f, tk * 2f)
                g.line(ax, ay, ax + (mx - ax) * k1, ay + (my - ay) * k1, sw, ink)
                if (tk > 0.5f) {
                    val k2 = (tk - 0.5f) * 2f
                    g.line(mx, my, mx + (ex - mx) * k2, my + (ey - my) * k2, sw, ink)
                }
            }
            g.blend(Gfx.Blend.ADD)
            g.glow(sx - r * 0.35f, sy - r * 0.4f, r * 0.5f, Col.alpha(0xFFFFFFFF.toInt(), 0.35f * sa))
            g.blend(Gfx.Blend.NORMAL)
        }

        // Kicker over the title.
        val ka = HudType.clamp01((age - 0.2f) / 0.3f) * a
        HudType.tracked(g, "CHALLENGE", W / 2f, cy - 1.6f * u, 2.4f * u, Col.alpha(gold, ka), Gfx.Font.HUD, Gfx.Align.CENTER, 0.9f * u)

        // CLEARED, letter by letter, chroma converging.
        val title = "CLEARED"
        var size = 11f * u
        val tracking = 1f * u + age * 0.15f * u
        val tw = HudType.trackedWidth(g, title, size, Gfx.Font.TITLE, tracking)
        if (tw > W * 0.84f) size *= W * 0.84f / tw
        val tw2 = HudType.trackedWidth(g, title, size, Gfx.Font.TITLE, tracking)
        val baseY = cy + 7.6f * u
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(W / 2f, baseY - size * 0.36f)
        g.scale(1f, 0.3f)
        g.glow(0f, 0f, tw2 * 0.7f, Col.alpha(gold, 0.4f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        var x = W / 2f - tw2 / 2f
        for (i in title.indices) {
            val cs = charStr(title[i])
            val cw = g.textWidth(cs, size, Gfx.Font.TITLE)
            val k = HudType.clamp01((age - 0.22f - i * 0.045f) / 0.3f)
            if (k > 0f) {
                val e = HudType.outBack(k, 2.4f)
                val dy = (1f - e) * -5f * u
                val split = (1f - HudType.outCubic(k)) * 2.6f * u
                val la = HudType.clamp01(k * 2f) * a
                if (split > 0.05f * u) {
                    g.blend(Gfx.Blend.ADD)
                    g.text(cs, x - split, baseY + dy, size, Col.alpha(CH_PINK, 0.75f * la), Gfx.Font.TITLE)
                    g.text(cs, x + split, baseY + dy, size, Col.alpha(0xFF2BE8FF.toInt(), 0.75f * la), Gfx.Font.TITLE)
                    g.blend(Gfx.Blend.NORMAL)
                }
                g.text(cs, x + 0.45f * u, baseY + dy + 0.6f * u, size, Col.alpha(0xFF000000.toInt(), 0.65f * la), Gfx.Font.TITLE)
                // Gold-to-white face: a bright top over a gold body.
                g.text(cs, x, baseY + dy, size, Col.alpha(Col.lerp(0xFFFFFFFF.toInt(), gold, 0.25f), la), Gfx.Font.TITLE)
            }
            x += cw + tracking
        }

        // The challenge's name and the foot line.
        val na = HudType.clamp01((age - 0.6f) / 0.35f) * a
        if (na > 0f) {
            var ns = 3.4f * u
            val nw = HudType.trackedWidth(g, chName, ns, Gfx.Font.TITLE, 0.4f * u)
            if (nw > W * 0.8f) ns *= W * 0.8f / nw
            HudType.tracked(g, chName, W / 2f, cy + 12.4f * u, ns, Col.alpha(0xFFFFFFFF.toInt(), na), Gfx.Font.TITLE, Gfx.Align.CENTER, 0.4f * u)
            var fs = 1.9f * u
            val fw = HudType.trackedWidth(g, chFoot, fs, Gfx.Font.HUD, 0.35f * u)
            if (fw > W * 0.86f) fs *= W * 0.86f / fw
            HudType.tracked(g, chFoot, W / 2f, ly2 + 3.4f * u, fs, Col.alpha(0xFFE8E4F4.toInt(), 0.75f * na), Gfx.Font.HUD, Gfx.Align.CENTER, 0.35f * u)
        }

        confetti(sx, sy, age, a, u)
    }

    /** Thrown up from the stamp, then fluttering down, spinning, in the challenge colours. */
    private fun confetti(ox: Float, oy: Float, age: Float, a: Float, u: Float) {
        val t = age - 0.35f
        if (t <= 0f) return
        val n = 46
        for (i in 0 until n) {
            val ang = (-PI.toFloat() / 2f) + (hash(i, 31) - 0.5f) * 2.6f
            val speed = (38f + 46f * hash(i, 32)) * u
            val drag = 1.6f
            val e = (1f - kotlin.math.exp(-drag * t)) / drag
            val fall = 26f * u * t * t
            val flutter = sin(t * (5f + 4f * hash(i, 33)) + i) * 2.2f * u
            val x = ox + cos(ang) * speed * e + flutter
            val y = oy + sin(ang) * speed * e + fall
            val life = 2.2f + 0.9f * hash(i, 34)
            val la = a * HudType.clamp01((life - t) / 0.5f)
            if (la <= 0f) continue
            val c = CONFETTI[i % CONFETTI.size]
            val spin = t * (6f + 8f * hash(i, 35)) + i
            val w = (0.8f + 0.6f * hash(i, 36)) * u
            val h = w * (0.35f + 0.65f * abs(cos(spin)))
            val ca = cos(spin * 0.7f)
            val sa = sin(spin * 0.7f)
            poly.quad(
                g,
                x - ca * w + sa * h, y - sa * w - ca * h,
                x + ca * w + sa * h, y + sa * w - ca * h,
                x + ca * w - sa * h, y + sa * w + ca * h,
                x - ca * w - sa * h, y - sa * w + ca * h,
                Col.alpha(c, la),
            )
        }
    }

    private fun bustedCard(cy: Float, age: Float, open: Float, a: Float, u: Float) {
        val W = g.width
        val H = g.height
        val red = CH_RED
        if (age < 0.2f) {
            g.blend(Gfx.Blend.ADD)
            g.fillRect(0f, 0f, W, H, Col.alpha(red, 0.12f * (1f - age / 0.2f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        val half = 9f * u * open
        val feather = 6f * u * open
        val dark = Col.alpha(0xFF0A0408.toInt(), 0.9f * a)
        g.fillVerticalGradient(0f, cy - half - feather, W, cy - half, 0x00000000, dark)
        g.fillRect(0f, cy - half, W, cy + half, dark)
        g.fillVerticalGradient(0f, cy + half, W, cy + half + feather, dark, 0x00000000)
        g.fillVerticalGradient(0f, cy - half, W, cy + half, Col.alpha(red, 0.1f * a), Col.alpha(red, 0.02f * a))
        g.fillRect(0f, cy - half, W, cy - half + 0.3f * u, Col.alpha(red, 0.8f * a))
        hazardTrim(-2f * u, W + 2f * u, cy + half, 0f, u, a)
        // BUSTED, shaking off the hit.
        val shake = if (age < 0.4f) sin(age * 70f) * 1.2f * u * (1f - age / 0.4f) else 0f
        val size = 8.4f * u
        val baseY = cy + 1.6f * u
        HudType.tracked(g, "BUSTED", W / 2f + shake + 0.4f * u, baseY + 0.5f * u, size, Col.alpha(0xFF000000.toInt(), 0.6f * a), Gfx.Font.TITLE, Gfx.Align.CENTER, 0.8f * u)
        HudType.tracked(g, "BUSTED", W / 2f + shake, baseY, size, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER, 0.8f * u)
        HudType.tracked(g, "CHALLENGE  ·  " + chName, W / 2f, cy - half + 3.4f * u, 2f * u, Col.alpha(red, 0.95f * a), Gfx.Font.HUD, Gfx.Align.CENTER, 0.45f * u)
        HudType.tracked(g, "UNTOUCHED? NOT ANY MORE.", W / 2f, cy + 6.4f * u, 2f * u, Col.alpha(0xFFE8E4F4.toInt(), 0.8f * a), Gfx.Font.HUD, Gfx.Align.CENTER, 0.35f * u)
    }

    // ============================================================ coach tips

    private var tipSrc: String? = null
    private var tipKicker = ""
    private var tipMain = ""
    private var tipGlyph = GLYPH_TAP

    /** Splits "SWIPE DOWN: HIDE" into gesture and verb once, when the tip changes. */
    private fun parseTip(tip: String) {
        if (tip === tipSrc) return
        tipSrc = tip
        val cut = tip.indexOf(": ")
        tipKicker = if (cut > 0) tip.substring(0, cut) else if (tip.startsWith("WALK")) "SNEAK UP BEHIND" else "TIP"
        tipMain = if (cut > 0) tip.substring(cut + 2) else tip
        tipGlyph = when {
            tip.contains("SWIPE DOWN") -> GLYPH_DOWN
            tip.contains("SWIPE UP") -> GLYPH_UP
            tip.contains("GRENADE") -> GLYPH_GRENADE
            tip.startsWith("WALK") -> GLYPH_WALK
            tip.contains("DOOR") -> GLYPH_DOOR
            else -> GLYPH_TAP
        }
    }

    /**
     * The coach tip: a small plate hanging just under the player's floor (or over it, when the
     * floor sits low on screen), pointing at them, with an animated gesture glyph and the move.
     */
    fun coachTip() {
        val w = f.w
        val tip = w.coachTip ?: return
        val age = f.wt - w.coachTipAt
        if (w.coachTipAt < 0f || age < 0f || age > TIP_TIME) return
        parseTip(tip)
        val W = g.width
        val H = g.height
        val u = Hud.unit(W)
        val appear = HudType.outBack(HudType.clamp01(age / 0.28f), 1.8f)
        val a = HudType.clamp01(age / 0.12f) * HudType.clamp01((TIP_TIME - age) / 0.4f)
        if (a <= 0f) return
        val p = w.player
        val px = (p.x + 0.6f) * f.s + f.shakeX
        val gy = Geo.groundY(p.floorF)
        val stageBottom = (gy + Building.SLAB - f.camY) * f.s
        val stageTop = (gy - Geo.FLOOR_H + Building.SLAB - f.camY) * f.s

        val ks = 1.7f * u
        val ms = 3f * u
        val kw = HudType.trackedWidth(g, tipKicker, ks, Gfx.Font.HUD, 0.4f * u)
        val mw = HudType.trackedWidth(g, tipMain, ms, Gfx.Font.TITLE, 0.3f * u)
        val gd = 5.8f * u
        val ch = 8.4f * u
        val cw = 1.3f * u + gd + 1.8f * u + max(kw, mw) + 2.6f * u
        val below = stageBottom + 3.2f * u + ch < H - f.bottomInset - 24f * u
        val cy = if (below) stageBottom + 3.2f * u + ch / 2f else stageTop - 3.2f * u - ch / 2f
        val cx = px.coerceIn(cw / 2f + 4f * u, W - cw / 2f - 4f * u)
        val color = TIP

        g.save()
        g.translate(cx, cy)
        g.scale(appear, appear)
        val l = -cw / 2f
        val r = cw / 2f
        val t = -ch / 2f
        val b = ch / 2f
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.scale(1f, 0.45f)
        g.glow(0f, 0f, cw * 0.7f, Col.alpha(color, 0.22f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        g.fillRoundRect(l, t + 0.8f * u, r, b + 0.8f * u, 2.2f * u, Col.alpha(0xFF000000.toInt(), 0.55f * a))
        g.fillRoundRect(l, t, r, b, 2.2f * u, Col.alpha(0xFF0B0913.toInt(), 0.97f * a))
        g.fillRoundRect(l + 0.3f * u, t + 0.25f * u, r - 0.3f * u, t + ch * 0.42f, 2f * u, Col.alpha(0xFFFFFFFF.toInt(), 0.05f * a))
        g.strokeRoundRect(l, t, r, b, 2.2f * u, 0.22f * u, Col.alpha(color, 0.85f * a))
        // Pointer at the player.
        val tx = (px - cx).coerceIn(l + 3f * u, r - 3f * u) / appear.coerceAtLeast(0.2f)
        val tipY = if (below) t else b
        val dirY = if (below) -1f else 1f
        poly.tri(g, tx - 1.3f * u, tipY, tx + 1.3f * u, tipY, tx, tipY + dirY * 1.6f * u, Col.alpha(color, 0.85f * a))

        // Gesture glyph in its own well.
        val gx = l + 1.3f * u + gd / 2f
        g.fillCircle(gx, 0f, gd / 2f, Col.alpha(color, 0.16f * a))
        g.strokeCircle(gx, 0f, gd / 2f, 0.18f * u, Col.alpha(color, 0.5f * a))
        gestureGlyph(gx, 0f, gd, color, a, age)

        val x0 = gx + gd / 2f + 1.8f * u
        HudType.tracked(g, tipKicker, x0, -0.6f * u, ks, Col.alpha(color, 0.9f * a), Gfx.Font.HUD, Gfx.Align.LEFT, 0.4f * u)
        HudType.tracked(g, tipMain, x0, 2.9f * u, ms, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.LEFT, 0.3f * u)
        g.restore()
    }

    /** A fingertip acting out the move: swipe down/up, drag across, the grenade button, or a door. */
    private fun gestureGlyph(cx: Float, cy: Float, d: Float, color: Int, a: Float, age: Float) {
        val k = d / 2f
        val white = Col.alpha(0xFFFFFFFF.toInt(), a)
        val ph = fract(age * 1.1f)
        when (tipGlyph) {
            GLYPH_DOWN, GLYPH_UP, GLYPH_WALK -> {
                val dx = if (tipGlyph == GLYPH_WALK) 1f else 0f
                val dy = if (tipGlyph == GLYPH_DOWN) 1f else if (tipGlyph == GLYPH_UP) -1f else 0f
                val e = HudType.outCubic(HudType.clamp01(ph / 0.7f))
                val fa = HudType.clamp01((1f - ph) / 0.3f) * a
                val sx = cx - dx * k * 0.55f - dy * 0f
                val sy = cy - dy * k * 0.55f
                val ex = sx + dx * k * 1.1f * e
                val ey = sy + dy * k * 1.1f * e
                g.line(sx, sy, ex, ey, k * 0.3f, Col.alpha(color, 0.45f * fa))
                Glyphs.arrow(g, cx + dx * k * 0.25f, cy + dy * k * 0.25f, k * 0.5f, dx, dy, k * 0.12f, Col.alpha(color, 0.35f * a))
                g.fillCircle(ex, ey, k * 0.26f, Col.alpha(0xFFFFFFFF.toInt(), fa))
            }
            GLYPH_GRENADE -> {
                // The button's grenade, pressed: a ring pulses out of it.
                HudIcons.grenade(g, cx, cy + k * 0.05f, d * 0.6f, Col.alpha(GRENADE_LIME, a))
                g.strokeCircle(cx, cy, k * (0.55f + 0.35f * ph), k * 0.08f, Col.alpha(GRENADE_LIME, (1f - ph) * a))
            }
            GLYPH_DOOR -> {
                HudIcons.door(g, cx - k * 0.1f, cy, d * 0.62f, Col.alpha(Building.PASSAGE, a))
                val nudge = sin(age * 6f) * k * 0.08f
                Glyphs.arrow(g, cx + k * 0.5f + nudge, cy, k * 0.22f, 1f, 0f, k * 0.09f, Col.alpha(Building.PASSAGE, a))
            }
            else -> {
                g.fillCircle(cx, cy, k * 0.26f, white)
                g.strokeCircle(cx, cy, k * (0.3f + 0.5f * ph), k * 0.1f, Col.alpha(color, (1f - ph) * a))
            }
        }
    }

    private companion object {
        /** Seconds the CHALLENGE CLEARED and BUSTED cards stay up. */
        const val CLEAR_TIME = 3.6f
        const val BUST_TIME = 2.4f
        const val CH_GOLD = 0xFFFFC23A.toInt()
        const val CH_PINK = 0xFFFF3D9A.toInt()
        const val CH_RED = 0xFFFF3348.toInt()
        val CONFETTI = intArrayOf(
            0xFFFFC23A.toInt(), 0xFFFF3D9A.toInt(), 0xFF2BE8FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF9AE040.toInt(), 0xFFFFE08A.toInt(),
        )
        /** Seconds a coach tip stays up. */
        const val TIP_TIME = 3.4f
        const val TIP = 0xFFFFC14A.toInt()
        const val GLYPH_TAP = 0
        const val GLYPH_DOWN = 1
        const val GLYPH_UP = 2
        const val GLYPH_GRENADE = 3
        const val GRENADE_LIME = 0xFF9AE040.toInt()
        const val GLYPH_WALK = 4
        const val GLYPH_DOOR = 5
    }
}
