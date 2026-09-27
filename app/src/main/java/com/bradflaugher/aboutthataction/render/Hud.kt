package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.ContextAction
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Heads-up display, zone title cards, the perk picker and the swipe-down hint.
 *
 * Layout runs on a grid of [unit] = 1% of the screen width (so 2u ≈ 8 dp on a phone):
 * 4u side margins, rows every 2u. Left column: zone + depth + heat, hearts, gear. Right
 * column: score beside the pause button, combo under it. Bottom: ammo dial, timers.
 */
internal class Hud(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val rect = FloatArray(4)
    private val pauseBuf = FloatArray(3)

    // Cached strings: rebuilt only when their value changes, never per frame.
    private var lastFloor = Int.MIN_VALUE
    private var floorText = ""
    private var lastDeepest = -1
    private var deepestText = ""
    private var lastZoneLabel: Zone? = null
    private var zoneLabel = ""
    private var lastCombo = -1
    private var comboText = ""
    private var comboAt = -9f
    private var shownScore = -1L
    private var scoreText = "0"
    private var dispScore = 0.0
    private var scoreTarget = -1L
    private var scoreAt = -9f
    private var lastTimerTenths = -1
    private var timerText = ""
    private var lastAmmo = -1
    private var ammoText = ""

    // Heart animation bookkeeping.
    private var lastHp = -1
    private val heartLostAt = FloatArray(MAX_HEARTS) { -9f }
    private val heartGainAt = FloatArray(MAX_HEARTS) { -9f }
    private var hurtAt = -9f
    private var lastGrenades = -1
    private var grenadeAt = -9f

    // Overlays.
    private var perkSeenAt = Float.NaN
    private var hintAction: ContextAction? = null
    private var hintAt = -9f

    companion object {
        const val MAX_HEAT = 4.5f
        private const val MAX_HEARTS = 16
        private const val PAUSE_R = 4.6f
        private const val MARGIN = 4f
        private const val TOP = 2.5f

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val INK = 0xFFF4F0FF.toInt()
        private const val DIM = 0xB3E8E4F4.toInt()
        private const val FAINT = 0x73E8E4F4
        private const val SHADOW = 0x99000000.toInt()
        private const val HEART = 0xFFFF2E58.toInt()
        private const val GOLD = 0xFFFFD24A.toInt()
        private const val PINK = 0xFFFF3D9A.toInt()

        fun unit(width: Float) = width / 100f

        fun pauseCenter(width: Float, topInset: Float, out: FloatArray) {
            val u = unit(width)
            out[0] = width - MARGIN * u - PAUSE_R * u
            out[1] = topInset + TOP * u + PAUSE_R * u
            out[2] = PAUSE_R * u
        }

        fun isPauseButton(x: Float, y: Float, width: Float, height: Float, topInset: Float): Boolean {
            val c = FloatArray(3)
            pauseCenter(width, topInset, c)
            val r = c[2] * 1.8f
            val dx = x - c[0]
            val dy = y - c[1]
            return dx * dx + dy * dy <= r * r
        }

        /** Card i's rect (left, top, right, bottom) in pixels. */
        fun cardRect(i: Int, width: Float, height: Float, topInset: Float, bottomInset: Float, out: FloatArray) {
            val u = unit(width)
            val areaTop = topInset + max(height * 0.24f, 44f * u)
            val areaBottom = height - bottomInset - 14f * u
            val gap = 4f * u
            val ch = min((areaBottom - areaTop - 2f * gap) / 3f, 32f * u)
            val total = ch * 3f + gap * 2f
            val start = areaTop + (areaBottom - areaTop - total) / 2f
            out[0] = 6f * u
            out[1] = start + i * (ch + gap)
            out[2] = width - 6f * u
            out[3] = out[1] + ch
        }

        fun perkCardAt(x: Float, y: Float, width: Float, height: Float, topInset: Float, bottomInset: Float): Int {
            val r = FloatArray(4)
            for (i in 0 until 3) {
                cardRect(i, width, height, topInset, bottomInset, r)
                // Generous: the gap between cards counts for the nearest card.
                val pad = unit(width) * 2f
                if (x >= r[0] - pad && x <= r[2] + pad && y >= r[1] - pad && y <= r[3] + pad) return i
            }
            return -1
        }

        fun perkCode(p: Perk) = when (p) {
            Perk.RAPID_FIRE -> "RF"
            Perk.HOLLOW_POINT -> "HP"
            Perk.PIERCE -> "PR"
            Perk.RICOCHET -> "RC"
            Perk.SPLIT_SHOT -> "SS"
            Perk.VITALITY -> "VT"
            Perk.CQC -> "CQ"
            Perk.GHOST_BOX -> "GB"
            Perk.DOUBLE_JUMP -> "DJ"
            Perk.DEMOLITION -> "DM"
            Perk.MAGNET -> "MG"
            Perk.REFLEX -> "RX"
            Perk.ARMOR -> "KV"
            Perk.LUCKY -> "LK"
            Perk.SHOCKWAVE -> "SW"
        }

        /** Offense red, defense cyan, stealth violet, utility gold. */
        fun perkColor(p: Perk): Int = when (p) {
            Perk.RAPID_FIRE, Perk.HOLLOW_POINT, Perk.PIERCE, Perk.RICOCHET, Perk.SPLIT_SHOT -> 0xFFFF4A5E.toInt()
            Perk.VITALITY, Perk.ARMOR, Perk.REFLEX -> 0xFF3CD8FF.toInt()
            Perk.CQC, Perk.GHOST_BOX, Perk.DOUBLE_JUMP, Perk.SHOCKWAVE -> 0xFFB77CFF.toInt()
            Perk.DEMOLITION, Perk.MAGNET, Perk.LUCKY -> 0xFFFFC23C.toInt()
        }

        fun perkClass(p: Perk): String = when (p) {
            Perk.RAPID_FIRE, Perk.HOLLOW_POINT, Perk.PIERCE, Perk.RICOCHET, Perk.SPLIT_SHOT -> "OFFENSE"
            Perk.VITALITY, Perk.ARMOR, Perk.REFLEX -> "DEFENSE"
            Perk.CQC, Perk.GHOST_BOX, Perk.DOUBLE_JUMP, Perk.SHOCKWAVE -> "STEALTH"
            Perk.DEMOLITION, Perk.MAGNET, Perk.LUCKY -> "UTILITY"
        }

        fun floorLabel(floor: Int) = if (floor <= 0) "ROOF" else "B-" + floor.toString().padStart(3, '0')

        fun formatScore(s: Long): String {
            val raw = s.toString()
            val sb = StringBuilder(raw.length + raw.length / 3)
            for (i in raw.indices) {
                if (i > 0 && (raw.length - i) % 3 == 0) sb.append(',')
                sb.append(raw[i])
            }
            return sb.toString()
        }
    }

    private fun zoneNeon(): Int {
        val w = f.w
        return f.palette(w.floors[w.player.floor]).neon
    }

    /** Seconds since [at] in real (menu-independent) time. */
    private fun since(at: Float) = f.t - at

    // ==================================================================== HUD

    fun draw() {
        val w = f.w
        val W = g.width
        val H = g.height
        val u = unit(W)
        val neon = zoneNeon()
        track()
        if (!f.inPerk) perkSeenAt = Float.NaN

        // Scrims: a soft top and bottom falloff so white type reads on any zone.
        val band = f.topInset + 29f * u
        g.fillVerticalGradient(0f, 0f, W, band, 0xE6050309.toInt(), 0x9E050309.toInt())
        g.fillVerticalGradient(0f, band, W, band + 12f * u, 0x9E050309.toInt(), 0x00050309)
        g.fillVerticalGradient(0f, H - f.bottomInset - 22f * u, W, H, 0x00000000, 0xA6000000.toInt())

        val top = f.topInset + TOP * u
        val left = MARGIN * u
        depthBlock(left, top, u, neon)
        heartsRow(left, top + 15f * u, u)
        if (f.dying) {
            // On the way out only the story of the run remains: where, and how much.
            scoreBlock(top, u)
            return
        }
        gearRow(left, top + 23f * u, u)
        pauseButton(u)
        scoreBlock(top, u)
        if (w.combo >= 2) comboBlock(W - MARGIN * u, top + 16f * u, u) else lastCombo = -1

        val bottom = H - f.bottomInset - MARGIN * u
        ammoDial(left, bottom, u)
        timers(W / 2f, bottom, u)
    }

    /** Notices value changes so they can animate (pops, lost hearts, ticking numbers). */
    private fun track() {
        val w = f.w
        val p = w.player
        val now = f.t
        if (lastHp >= 0) {
            if (p.hp < lastHp) {
                for (i in p.hp until min(lastHp, MAX_HEARTS)) heartLostAt[i] = now
                hurtAt = now
            }
            if (p.hp > lastHp) for (i in lastHp until min(p.hp, MAX_HEARTS)) heartGainAt[i] = now
        }
        lastHp = p.hp
        if (lastGrenades >= 0 && p.grenades != lastGrenades) grenadeAt = now
        lastGrenades = p.grenades
        if (w.score != scoreTarget) {
            // Jumps (a new run) snap; gains tick up.
            if (scoreTarget < 0 || w.score < scoreTarget) dispScore = w.score.toDouble() else scoreAt = now
            scoreTarget = w.score
        }
        if (f.dt == 0f) dispScore = w.score.toDouble()
        val gap = w.score - dispScore
        if (gap > 0.0) dispScore = min(w.score.toDouble(), dispScore + max(gap * min(1.0, f.dt * 9.0), f.dt * 60.0))
        val shown = dispScore.toLong()
        if (shown != shownScore) {
            shownScore = shown
            scoreText = formatScore(shown)
        }
    }

    // ------------------------------------------------------- depth + heat

    private fun depthBlock(x: Float, top: Float, u: Float, neon: Int) {
        val w = f.w
        val floor = w.player.floor
        if (floor != lastFloor) {
            lastFloor = floor
            floorText = floorLabel(floor)
        }
        val zone = if (floor == 0) Zone.ROOFTOP else w.zone
        if (zone != lastZoneLabel) {
            lastZoneLabel = zone
            zoneLabel = if (zone == Zone.ROOFTOP) "ROOFTOP" else zone.title
        }
        // Kicker: a neon tick and the zone name, tracked out.
        val ky = top + 2.4f * u
        g.fillRect(x, ky - 1.9f * u, x + 0.6f * u, ky + 0.1f * u, neon)
        // Lifted toward white so dim zone neons still read on the dark band.
        HudType.tracked(g, zoneLabel, x + 1.8f * u, ky, 2.5f * u, Col.lerp(neon, WHITE, 0.22f), Gfx.Font.HUD, Gfx.Align.LEFT, 0.45f * u)

        // Depth: the hero number, with a neon backlight.
        val ds = 7.6f * u
        val by = top + 10.2f * u
        val dw = g.textWidth(floorText, ds, Gfx.Font.TITLE)
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(x + dw * 0.5f, by - ds * 0.36f)
        g.scale(1f, 0.42f)
        g.glow(0f, 0f, dw * 0.75f, Col.alpha(neon, 0.28f))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        g.text(floorText, x + 0.35f * u, by + 0.45f * u, ds, SHADOW, Gfx.Font.TITLE)
        g.text(floorText, x, by, ds, INK, Gfx.Font.TITLE)

        // Heat: 14 segments ramping green → amber → red, hottest segment glowing.
        val hy = by + 1.8f * u
        val segs = 14
        val bw = 30f * u
        val gap = 0.35f * u
        val sw = (bw - gap * (segs - 1)) / segs
        val sh = 0.9f * u
        val heat = (w.heat / MAX_HEAT).coerceIn(0f, 1f)
        val lit = if (heat <= 0f) 0 else max(1, (heat * segs + 0.5f).toInt())
        for (i in 0 until segs) {
            val sx = x + i * (sw + gap)
            if (i < lit) {
                val c = heatColor((i + 0.5f) / segs)
                g.fillRect(sx, hy, sx + sw, hy + sh, c)
                if (i == lit - 1) {
                    g.blend(Gfx.Blend.ADD)
                    g.glow(sx + sw / 2f, hy + sh / 2f, 2.6f * u, Col.alpha(c, 0.5f + 0.2f * sin(f.t * 5f)))
                    g.blend(Gfx.Blend.NORMAL)
                }
            } else {
                g.fillRect(sx, hy, sx + sw, hy + sh, 0x2EFFFFFF)
            }
        }
        HudType.tracked(g, "HEAT", x + bw + 1.4f * u, hy + sh + 0.1f * u, 2f * u, FAINT, Gfx.Font.HUD, Gfx.Align.LEFT, 0.3f * u)
    }

    private fun heatColor(t: Float): Int = when {
        t < 0.35f -> Col.lerp(0xFF3CFF8E.toInt(), 0xFFFFE040.toInt(), t / 0.35f)
        t < 0.7f -> Col.lerp(0xFFFFE040.toInt(), 0xFFFF6A1A.toInt(), (t - 0.35f) / 0.35f)
        else -> Col.lerp(0xFFFF6A1A.toInt(), 0xFFFF1E3C.toInt(), (t - 0.7f) / 0.3f)
    }

    // --------------------------------------------------------------- hearts

    private fun heartsRow(x: Float, top: Float, u: Float) {
        val p = f.w.player
        val n = min(p.maxHp, MAX_HEARTS)
        val extras = (if (p.shield) 1 else 0) + (if (p.armorReady) 1 else 0)
        // Hearts shrink to keep the row clear of the combo column.
        var hs = 5.4f * u
        var step = 6.4f * u
        val avail = 58f * u
        val need = n * step + extras * (step + 1.2f * u)
        if (need > avail) {
            val k = avail / need
            hs *= k
            step *= k
        }
        val cy = top + hs * 0.5f
        val now = f.t
        val low = p.hp == 1 && f.w.phase == Phase.PLAYING
        val beat = if (low) HudType.heartbeat(fract(now * 1.25f)) else 0f
        val hurt = since(hurtAt).let { if (it in 0f..0.35f) 1f - it / 0.35f else 0f }
        var cx = x + hs * 0.52f + sin(now * 90f) * 0.8f * u * hurt * hurt
        for (i in 0 until n) {
            val full = i < p.hp
            val lostAge = since(heartLostAt[i])
            val gainAge = since(heartGainAt[i])
            // Socket.
            Glyphs.heart(g, cx, cy + 0.3f * u, hs, 0x66000000)
            Glyphs.heart(g, cx, cy, hs, 0x40FFFFFF)
            Glyphs.heart(g, cx, cy, hs * 0.7f, 0xE0100812.toInt())
            if (full) {
                var s = hs
                if (gainAge in 0f..0.45f) s *= HudType.outBack(gainAge / 0.45f).coerceAtLeast(0.05f)
                if (low) s *= 1f + 0.16f * beat
                if (low) {
                    g.blend(Gfx.Blend.ADD)
                    g.glow(cx, cy, hs * 1.5f, Col.alpha(HEART, 0.18f + 0.45f * beat))
                    g.blend(Gfx.Blend.NORMAL)
                }
                Glyphs.heart(g, cx, cy, s, HEART)
                Glyphs.heart(g, cx, cy + s * 0.12f, s * 0.62f, 0xFFD41446.toInt())
                g.fillCircle(cx - s * 0.21f, cy - s * 0.2f, s * 0.1f, 0xD9FFFFFF.toInt())
                if (hurt > 0f) Glyphs.heart(g, cx, cy, s, Col.alpha(WHITE, 0.85f * hurt))
            } else if (lostAge in 0f..0.5f) {
                // Heart bursts: swells, flashes white, and fades out.
                val k = lostAge / 0.5f
                val s = hs * (1f + 0.7f * HudType.outCubic(k))
                val a = 1f - k
                g.blend(Gfx.Blend.ADD)
                g.glow(cx, cy, hs * 2.2f, Col.alpha(HEART, 0.6f * a))
                g.blend(Gfx.Blend.NORMAL)
                Glyphs.heart(g, cx, cy, s, Col.alpha(Col.lerp(WHITE, HEART, k * 2f), a))
            }
            cx += step
        }
        if (p.shield) {
            cx += 1.2f * u
            badge(cx, cy, hs, 0xFF3CC8FF.toInt()) { bx, by, s, c -> HudIcons.shield(g, bx, by, s * 0.78f, c, 0xFF0A1824.toInt()) }
            cx += step
        }
        if (p.armorReady) {
            cx += if (p.shield) 0f else 1.2f * u
            badge(cx, cy, hs, GOLD) { bx, by, s, c -> HudIcons.vest(g, bx, by, s * 0.78f, c) }
        }
    }

    private inline fun badge(cx: Float, cy: Float, s: Float, c: Int, icon: (Float, Float, Float, Int) -> Unit) {
        g.blend(Gfx.Blend.ADD)
        g.glow(cx, cy, s * 1.1f, Col.alpha(c, 0.22f))
        g.blend(Gfx.Blend.NORMAL)
        icon(cx, cy, s, c)
    }

    // ------------------------------------------------------ grenades + perks

    private fun gearRow(x: Float, top: Float, u: Float) {
        val w = f.w
        val p = w.player
        val rowH = 5f * u
        val cy = top + rowH / 2f
        var cx = x
        val gs = 3.6f * u
        val pop = since(grenadeAt).let { if (it in 0f..0.35f) 1f + 0.35f * HudType.decay(it / 0.35f) else 1f }
        for (i in 0 until w.maxGrenades) {
            val have = i < p.grenades
            val s = if (have && i == p.grenades - 1) gs * pop else gs
            HudIcons.grenade(g, cx + gs / 2f, cy, s, if (have) 0xFF9AE040.toInt() else 0x33FFFFFF)
            cx += gs + 0.4f * u
        }
        if (w.perks.isEmpty()) return
        cx += 1.4f * u
        g.fillRect(cx, cy - rowH * 0.36f, cx + 0.2f * u, cy + rowH * 0.36f, 0x40FFFFFF)
        cx += 1.8f * u
        val limit = 60f * u
        var shown = 0
        for ((perk, n) in w.perks) {
            if (cx + rowH > limit && shown < w.perks.size - 1) {
                // Overflow chip.
                g.fillRoundRect(cx, top, cx + rowH, top + rowH, 1.2f * u, 0xB0100C18.toInt())
                g.strokeRoundRect(cx, top, cx + rowH, top + rowH, 1.2f * u, 0.2f * u, 0x55FFFFFF)
                g.text("+" + (w.perks.size - shown), cx + rowH / 2f, cy + 0.9f * u, 2.4f * u, DIM, Gfx.Font.HUD, Gfx.Align.CENTER)
                break
            }
            perkChip(perk, n, cx, top, rowH, u)
            cx += rowH + 1.2f * u
            shown++
        }
    }

    private fun perkChip(perk: Perk, stacks: Int, x: Float, y: Float, s: Float, u: Float) {
        val pc = perkColor(perk)
        g.fillRoundRect(x, y, x + s, y + s, 1.2f * u, 0xC00C0A14.toInt())
        g.fillVerticalGradient(x + 0.3f * u, y + 0.3f * u, x + s - 0.3f * u, y + s * 0.6f, Col.alpha(pc, 0.22f), Col.alpha(pc, 0f))
        g.strokeRoundRect(x, y, x + s, y + s, 1.2f * u, 0.22f * u, Col.alpha(pc, 0.8f))
        HudIcons.perk(g, perk, x + s / 2f, y + s / 2f - (if (perk.maxStacks > 1) 0.35f * u else 0f), s * 0.56f, pc)
        if (perk.maxStacks > 1) {
            // Stack pips along the bottom edge.
            val pw = 0.7f * u
            val total = perk.maxStacks * pw + (perk.maxStacks - 1) * 0.35f * u
            var px = x + s / 2f - total / 2f
            for (i in 0 until perk.maxStacks) {
                g.fillRect(px, y + s - 0.95f * u, px + pw, y + s - 0.55f * u, if (i < stacks) pc else 0x40FFFFFF)
                px += pw + 0.35f * u
            }
        }
    }

    // ------------------------------------------------------- score + pause

    private fun pauseButton(u: Float) {
        val pc = pauseBuf
        pauseCenter(g.width, f.topInset, pc)
        g.fillCircle(pc[0], pc[1] + 0.3f * u, pc[2], 0x55000000)
        g.fillCircle(pc[0], pc[1], pc[2], 0x8C0C0A14.toInt())
        g.strokeCircle(pc[0], pc[1], pc[2] - 0.12f * u, 0.24f * u, 0x4DFFFFFF)
        val bh = 1.7f * u
        g.fillRoundRect(pc[0] - 1.35f * u, pc[1] - bh, pc[0] - 0.45f * u, pc[1] + bh, 0.25f * u, INK)
        g.fillRoundRect(pc[0] + 0.45f * u, pc[1] - bh, pc[0] + 1.35f * u, pc[1] + bh, 0.25f * u, INK)
    }

    private fun scoreBlock(top: Float, u: Float) {
        val w = f.w
        val W = g.width
        val right = W - MARGIN * u - PAUSE_R * 2f * u - 2.4f * u
        HudType.tracked(g, "SCORE", right, top + 2.4f * u, 2.5f * u, FAINT, Gfx.Font.HUD, Gfx.Align.RIGHT, 0.45f * u)
        // Fit: never reach into the depth block, however many digits.
        var ss = 6f * u
        val maxW = right - W * 0.44f
        val tw = g.textWidth(scoreText, ss, Gfx.Font.TITLE)
        if (tw > maxW) ss *= maxW / tw
        val by = top + 10.2f * u
        val age = since(scoreAt)
        val pop = if (age in 0f..0.4f) 1f + 0.14f * HudType.decay(age / 0.4f) else 1f
        val color = if (age in 0f..0.6f) Col.lerp(GOLD, 0xFFFFF4D8.toInt(), age / 0.6f) else 0xFFFFF4D8.toInt()
        g.save()
        g.translate(right, by)
        g.scale(pop, pop)
        g.text(scoreText, 0.35f * u, 0.45f * u, ss, SHADOW, Gfx.Font.TITLE, Gfx.Align.RIGHT)
        g.text(scoreText, 0f, 0f, ss, color, Gfx.Font.TITLE, Gfx.Align.RIGHT)
        g.restore()
        if (w.deepest != lastDeepest) {
            lastDeepest = w.deepest
            deepestText = "BEST " + floorLabel(w.deepest)
        }
        if (w.deepest > w.player.floor) {
            HudType.tracked(g, deepestText, right, by + 3f * u, 2f * u, FAINT, Gfx.Font.HUD, Gfx.Align.RIGHT, 0.3f * u)
        }
    }

    // ----------------------------------------------------------------- combo

    private fun comboBlock(right: Float, top: Float, u: Float) {
        val w = f.w
        if (w.combo != lastCombo) {
            if (w.combo > lastCombo) comboAt = f.t
            lastCombo = w.combo
            comboText = "×" + w.combo
        }
        val age = since(comboAt)
        val pop = if (age in 0f..0.35f) HudType.outBack(age / 0.35f, 3f) * 0.25f + 0.75f else 1f
        val tier = ((w.combo - 2) / 8f).coerceIn(0f, 1f)
        val c = Col.lerp(PINK, 0xFFFFB02E.toInt(), tier)
        HudType.tracked(g, "COMBO", right, top + 2.4f * u, 2.5f * u, Col.alpha(c, 0.85f), Gfx.Font.HUD, Gfx.Align.RIGHT, 0.45f * u)
        val cs = (6.8f + min(2.4f, w.combo * 0.15f)) * u
        val by = top + 3.2f * u + cs * 0.74f
        val tw = Glyphs.width(g, comboText, cs, Gfx.Font.TITLE)
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(right - tw * 0.5f, by - cs * 0.36f)
        g.scale(1f, 0.5f)
        g.glow(0f, 0f, tw * 0.9f, Col.alpha(c, 0.3f + 0.25f * HudType.decay(age / 0.5f)))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        g.save()
        g.translate(right, by)
        g.scale(pop, pop)
        g.text(comboText, 0.4f * u, 0.5f * u, cs, SHADOW, Gfx.Font.TITLE, Gfx.Align.RIGHT)
        g.text(comboText, 0f, 0f, cs, c, Gfx.Font.TITLE, Gfx.Align.RIGHT)
        g.restore()
        // Timer: a thin bar draining toward the right edge.
        val bw = 16f * u
        val t = (w.comboTimer / 2.6f).coerceIn(0f, 1f)
        val ty = by + 1.6f * u
        g.fillRect(right - bw, ty, right, ty + 0.55f * u, 0x33FFFFFF)
        g.fillRect(right - bw * t, ty, right, ty + 0.55f * u, c)
        if (t < 0.3f) {
            g.blend(Gfx.Blend.ADD)
            g.glow(right - bw * t, ty + 0.27f * u, 1.8f * u, Col.alpha(c, 0.7f))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    // ------------------------------------------------------------ ammo dial

    private fun ammoDial(x: Float, bottom: Float, u: Float) {
        val p = f.w.player
        val r = 5.2f * u
        val cx = x + r
        val cy = bottom - r
        val ring = 0.9f * u
        val infinite = p.weapon == PickupKind.SHOTGUN || p.weapon == PickupKind.MINIGUN
        g.fillCircle(cx, cy + 0.3f * u, r + ring, 0x55000000)
        g.fillCircle(cx, cy, r + ring * 0.5f, 0xA00C0A14.toInt())
        if (infinite) {
            val c = 0xFFFFA020.toInt()
            g.strokeCircle(cx, cy, r, ring, Col.alpha(c, 0.9f))
            g.blend(Gfx.Blend.ADD)
            g.glow(cx, cy, r * 1.8f, Col.alpha(c, 0.18f))
            g.blend(Gfx.Blend.NORMAL)
            val lr = 1.25f * u
            g.strokeCircle(cx - lr * 0.95f, cy, lr, 0.5f * u, c)
            g.strokeCircle(cx + lr * 0.95f, cy, lr, 0.5f * u, c)
            return
        }
        val n = max(1, p.magSize)
        val gapDeg = if (n > 12) 3f else 7f
        val seg = 360f / n
        if (p.reloading) {
            val t = (1f - p.reloadTime / max(0.01f, p.reloadTotal)).coerceIn(0f, 1f)
            g.strokeCircle(cx, cy, r, ring, 0x26FFFFFF)
            g.strokeArc(cx, cy, r, -90f, 360f * t, ring, GOLD)
            val a = (-90f + 360f * t) * (Math.PI.toFloat() / 180f)
            g.blend(Gfx.Blend.ADD)
            g.glow(cx + kotlin.math.cos(a) * r, cy + sin(a) * r, 2.4f * u, Col.alpha(GOLD, 0.8f))
            g.blend(Gfx.Blend.NORMAL)
            val blink = 0.55f + 0.45f * sin(f.t * 10f)
            HudType.tracked(g, "RELOAD", cx + r + 2.4f * u, cy + 0.9f * u, 2.5f * u, Col.alpha(GOLD, blink), Gfx.Font.HUD, Gfx.Align.LEFT, 0.45f * u)
        } else {
            for (i in 0 until n) {
                val have = i < p.ammo
                val start = -90f + i * seg + gapDeg / 2f
                g.strokeArc(cx, cy, r, start, seg - gapDeg, ring, if (have) GOLD else 0x2EFFFFFF)
            }
        }
        val count = if (p.reloading) 0 else p.ammo
        if (count != lastAmmo) {
            lastAmmo = count
            ammoText = count.toString()
        }
        val empty = count == 0
        g.text(ammoText, cx, cy + 1.45f * u, 4.2f * u, if (empty) Col.alpha(0xFFFF5A5A.toInt(), 0.9f) else INK, Gfx.Font.TITLE, Gfx.Align.CENTER)
    }

    // ---------------------------------------------------------------- timers

    private fun timers(cx: Float, bottom: Float, u: Float) {
        val p = f.w.player
        var y = bottom
        val weapon = p.weapon
        if (weapon != null) {
            timerPill(cx, y, u, weapon.title, p.weaponTime, weapon.seconds, 0xFFFFA020.toInt(), weapon)
            y -= 8f * u
        }
        if (p.slowMoTime > 0f) {
            timerPill(cx, y, u, "BULLET TIME", p.slowMoTime, PickupKind.SLOWMO.seconds, 0xFFB080FF.toInt(), PickupKind.SLOWMO)
        }
    }

    private fun timerPill(cx: Float, y: Float, u: Float, label: String, left: Float, total: Float, color: Int, kind: PickupKind) {
        val w = 42f * u
        val h = 6.4f * u
        val l = cx - w / 2f
        val r = cx + w / 2f
        val t = (left / max(0.01f, total)).coerceIn(0f, 1f)
        val urgent = left < 2f
        val flash = if (urgent) 0.5f + 0.5f * sin(f.t * 14f) else 0f
        g.fillRoundRect(l, y - h + 0.3f * u, r, y + 0.3f * u, h / 2f, 0x66000000)
        g.fillRoundRect(l, y - h, r, y, h / 2f, 0xD90C0A14.toInt())
        // Drain: a bright underline that shrinks toward the centre.
        val dw = (w - h) * t
        g.fillRoundRect(cx - dw / 2f, y - 0.75f * u, cx + dw / 2f, y - 0.2f * u, 0.3f * u, color)
        g.strokeRoundRect(l, y - h, r, y, h / 2f, 0.22f * u, Col.alpha(color, 0.55f + 0.45f * flash))
        // Icon disc.
        val ix = l + h / 2f
        val iy = y - h / 2f
        g.fillCircle(ix, iy, h / 2f - 0.6f * u, Col.alpha(color, 0.18f))
        HudIcons.pickup(g, kind, ix, iy, h * 0.52f, color)
        HudType.tracked(g, label, cx + 0.8f * u, y - h / 2f + 1.05f * u, 2.9f * u, INK, Gfx.Font.TITLE, Gfx.Align.CENTER, 0.25f * u)
        val tenths = (left * 10f).toInt()
        if (tenths != lastTimerTenths) {
            lastTimerTenths = tenths
            timerText = (tenths / 10).toString() + "." + (tenths % 10)
        }
        g.text(timerText, r - 2.6f * u, y - h / 2f + 1f * u, 2.7f * u, if (urgent) Col.lerp(color, WHITE, flash) else DIM, Gfx.Font.HUD, Gfx.Align.RIGHT)
    }

    // ============================================================ zone banner

    fun banner() {
        val w = f.w
        val zone = w.bannerZone ?: return
        val bt = w.bannerTime
        if (bt <= 0f) return
        val age = 3.2f - bt
        val W = g.width
        val H = g.height
        val u = unit(W)
        val cy = H * 0.34f
        val pal = if (zone == Zone.VOID) Palette.void(Zone.VOID, f.t, 0) else Palette.of(zone)
        val neon = pal.neon
        val out = HudType.clamp01(bt / 0.5f)
        val open = HudType.outCubic(age / 0.45f) * HudType.outCubic(out)
        val a = HudType.clamp01(age / 0.2f) * out

        // Entry flash in the zone colour.
        if (age < 0.3f) {
            g.blend(Gfx.Blend.ADD)
            g.fillRect(0f, 0f, W, H, Col.alpha(neon, 0.16f * (1f - age / 0.3f)))
            g.blend(Gfx.Blend.NORMAL)
        }

        // The band: a dark stripe with feathered edges that opens from the centre line.
        val half = 15f * u * open
        val feather = 10f * u * open
        val dark = Col.alpha(0xFF030106.toInt(), 0.9f * a)
        g.fillVerticalGradient(0f, cy - half - feather, W, cy - half, 0x00000000, dark)
        g.fillRect(0f, cy - half, W, cy + half, dark)
        g.fillVerticalGradient(0f, cy + half, W, cy + half + feather, dark, 0x00000000)
        // A zone-tinted wash inside the band.
        g.fillVerticalGradient(0f, cy - half, W, cy + half, Col.alpha(neon, 0.08f * a), Col.alpha(pal.neon2, 0.05f * a))

        // A huge ghosted zone numeral behind everything, drifting slowly.
        val num = numeral(zone)
        val ns = 40f * u
        g.text(num, W / 2f + 18f * u - age * 1.5f * u, cy + ns * 0.36f, ns, Col.alpha(neon, 0.07f * a), Gfx.Font.TITLE, Gfx.Align.CENTER)

        // Hairlines wiping outward.
        val lw = W * 0.8f * HudType.outCubic((age - 0.05f) / 0.5f) * out
        val ly1 = cy - 11f * u
        val ly2 = cy + 11f * u
        g.fillRect(W / 2f - lw / 2f, ly1, W / 2f + lw / 2f, ly1 + 0.25f * u, Col.alpha(neon, 0.85f * a))
        g.fillRect(W / 2f - lw / 2f, ly2 - 0.25f * u, W / 2f + lw / 2f, ly2, Col.alpha(pal.neon2, 0.85f * a))
        g.blend(Gfx.Blend.ADD)
        g.glow(W / 2f - lw / 2f, ly1, 2.5f * u, Col.alpha(neon, a))
        g.glow(W / 2f + lw / 2f, ly2, 2.5f * u, Col.alpha(pal.neon2, a))
        g.blend(Gfx.Blend.NORMAL)

        // Kicker.
        val ka = HudType.clamp01((age - 0.15f) / 0.3f) * out
        if (zone != kickerZone || w.player.floor != kickerFloor) {
            kickerZone = zone
            kickerFloor = w.player.floor
            kicker = "ZONE " + numeral(zone) + "   ·   " + floorLabel(kickerFloor)
        }
        HudType.tracked(g, kicker, W / 2f, ly1 - 2f * u, 2.6f * u, Col.alpha(neon, ka), Gfx.Font.HUD, Gfx.Align.CENTER, 0.6f * u)

        // Title: letters drop in one by one with a chromatic split that converges.
        val title = zone.title
        var size = 11f * u
        val drift = age * 0.12f * u
        var tracking = 1.1f * u + drift
        val tw0 = HudType.trackedWidth(g, title, size, Gfx.Font.TITLE, tracking)
        if (tw0 > W * 0.86f) {
            val k = W * 0.86f / tw0
            size *= k
            tracking *= k
        }
        val tw = HudType.trackedWidth(g, title, size, Gfx.Font.TITLE, tracking)
        val baseY = cy + size * 0.36f
        // Backlight.
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(W / 2f, cy)
        g.scale(1f, 0.3f)
        g.glow(0f, 0f, tw * 0.7f, Col.alpha(neon, 0.35f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        val glitch = zone == Zone.VOID || zone == Zone.HELL
        var x = W / 2f - tw / 2f
        for (i in title.indices) {
            val c = title[i]
            val cs = charStr(c)
            val cw = g.textWidth(cs, size, Gfx.Font.TITLE)
            val k = HudType.clamp01((age - 0.2f - i * 0.04f) / 0.32f)
            if (k > 0f && c != ' ') {
                val e = HudType.outCubic(k)
                val dy = (1f - e) * -4f * u
                var split = (1f - e) * 2.4f * u
                var jx = 0f
                if (glitch) {
                    val tick = (f.t * 18f).toInt()
                    if (hash(tick * 31 + i, 77) < 0.12f) {
                        jx = (hash(tick + i, 78) - 0.5f) * 2.2f * u
                        split += 0.8f * u
                    }
                }
                val la = e * out
                if (split > 0.05f * u) {
                    g.blend(Gfx.Blend.ADD)
                    g.text(cs, x - split + jx, baseY + dy, size, Col.alpha(0xFFFF2B6A.toInt(), 0.7f * la), Gfx.Font.TITLE)
                    g.text(cs, x + split + jx, baseY + dy, size, Col.alpha(0xFF2BE8FF.toInt(), 0.7f * la), Gfx.Font.TITLE)
                    g.blend(Gfx.Blend.NORMAL)
                }
                g.text(cs, x + 0.4f * u + jx, baseY + dy + 0.5f * u, size, Col.alpha(0xFF000000.toInt(), 0.6f * la), Gfx.Font.TITLE)
                g.text(cs, x + jx, baseY + dy, size, Col.alpha(WHITE, la), Gfx.Font.TITLE)
            }
            x += cw + tracking
        }

        // Subtitle.
        val sa = HudType.clamp01((age - 0.55f) / 0.4f) * out
        if (sa > 0f) {
            val sub = subtitleUpper(zone)
            HudType.tracked(g, sub, W / 2f, ly2 + 4.4f * u, 2.6f * u, Col.alpha(0xFFE8E4F4.toInt(), 0.8f * sa), Gfx.Font.HUD, Gfx.Align.CENTER, 0.45f * u)
        }
    }

    private val numerals = Array(Zone.entries.size) { it.toString().padStart(2, '0') }
    private fun numeral(z: Zone) = numerals[z.ordinal]

    private var kickerZone: Zone? = null
    private var kickerFloor = -1
    private var kicker = ""
    private var subZone: Zone? = null
    private var subText = ""

    private fun subtitleUpper(z: Zone): String {
        if (z != subZone) {
            subZone = z
            subText = z.subtitle.uppercase()
        }
        return subText
    }

    private val charCache = Array(128) { it.toChar().toString() }
    private fun charStr(c: Char) = if (c.code < 128) charCache[c.code] else c.toString()

    // ============================================================ perk picker

    fun perkOverlay() {
        val w = f.w
        val W = g.width
        val H = g.height
        val u = unit(W)
        if (perkSeenAt.isNaN() || f.t < perkSeenAt) perkSeenAt = if (f.dt == 0f) f.t - 10f else f.t
        val age = f.t - perkSeenAt
        val fade = HudType.clamp01(age / 0.22f)
        val red = 0xFFFF1E3C.toInt()

        // Backdrop: near-black with a red core glow and slow data rain.
        g.fillRect(0f, 0f, W, H, Col.alpha(0xFF06030A.toInt(), 0.93f * fade))
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(W / 2f, H * 0.18f + f.topInset)
        g.scale(1.4f, 0.8f)
        g.glow(0f, 0f, W * 0.55f, Col.alpha(red, 0.14f * fade))
        g.restore()
        for (i in 0 until 18) {
            val x = hash(i, 501) * W
            val y = fract(hash(i, 502) + f.t * (0.05f + hash(i, 503) * 0.1f)) * H * 1.2f - H * 0.1f
            val len = (6f + hash(i, 504) * 10f) * u
            g.fillVerticalGradient(x, y - len, x + 0.25f * u, y, 0x00FF1E3C, Col.alpha(red, 0.22f * fade))
        }
        g.blend(Gfx.Blend.NORMAL)

        // Heading.
        val ha = HudType.outCubic(age / 0.35f)
        val titleY = f.topInset + max(H * 0.1f, 16f * u)
        val coreY = titleY - 1f * u
        g.blend(Gfx.Blend.ADD)
        g.glow(W / 2f, coreY, 9f * u, Col.alpha(red, 0.55f * ha))
        g.blend(Gfx.Blend.NORMAL)
        g.save()
        g.translate(W / 2f, coreY)
        g.rotate(f.t * 40f)
        HudIcons.dataCore(g, 0f, 0f, 7f * u * ha, red)
        g.restore()
        g.strokeCircle(W / 2f, coreY, 5.6f * u, 0.2f * u, Col.alpha(red, 0.45f * ha))
        HudType.tracked(g, "INTEL ACQUIRED", W / 2f, titleY + 11f * u + (1f - ha) * 2f * u, 6.4f * u, Col.alpha(WHITE, ha), Gfx.Font.TITLE, Gfx.Align.CENTER, 0.5f * u)
        HudType.tracked(g, "CHOOSE ONE UPGRADE", W / 2f, titleY + 16.5f * u, 2.6f * u, Col.alpha(0xFFFF6A7E.toInt(), ha), Gfx.Font.HUD, Gfx.Align.CENTER, 0.6f * u)

        for (i in w.perkOffer.indices) perkCard(i, w.perkOffer[i], age, u)

        // Tap affordance: a finger ripple beside the hint.
        val hy = H - f.bottomInset - 6f * u
        val ta = HudType.clamp01((age - 0.6f) / 0.4f)
        val pulse = fract(f.t * 0.9f)
        HudType.tracked(g, "TAP A CARD", W / 2f + 2.5f * u, hy + 0.9f * u, 2.6f * u, Col.alpha(WHITE, (0.55f + 0.3f * sin(f.t * 3f)) * ta), Gfx.Font.HUD, Gfx.Align.CENTER, 0.6f * u)
        val fx = W / 2f - 13.5f * u
        g.strokeCircle(fx, hy, (1f + pulse * 2.6f) * u, 0.25f * u, Col.alpha(WHITE, (1f - pulse) * 0.7f * ta))
        g.fillCircle(fx, hy, 0.9f * u, Col.alpha(WHITE, 0.85f * ta))
    }

    private fun perkCard(i: Int, perk: Perk, age: Float, u: Float) {
        val w = f.w
        val W = g.width
        cardRect(i, W, g.height, f.topInset, f.bottomInset, rect)
        val k = HudType.clamp01((age - 0.12f - i * 0.09f) / 0.42f)
        if (k <= 0f) return
        val e = HudType.outBack(k, 1.2f)
        val slide = (1f - e) * W * 0.5f
        val l = rect[0] + slide
        val t = rect[1]
        val r = rect[2] + slide
        val b = rect[3]
        val h = b - t
        val pc = perkColor(perk)
        val stacks = w.stacks(perk)
        val a = HudType.clamp01(k * 2f)
        val rad = 2.4f * u

        // Plate: shadow, body, accent wash, edge.
        g.fillRoundRect(l, t + 1f * u, r, b + 1f * u, rad, Col.alpha(0xFF000000.toInt(), 0.5f * a))
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(l + h * 0.4f, (t + b) / 2f)
        g.scale(1.3f, 1f)
        g.glow(0f, 0f, h * 0.8f, Col.alpha(pc, 0.12f * a))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        g.fillRoundRect(l, t, r, b, rad, Col.alpha(0xFF110D1A.toInt(), 0.96f * a))
        g.fillVerticalGradient(l + 0.4f * u, t + 0.4f * u, r - 0.4f * u, t + h * 0.55f, Col.alpha(pc, 0.14f * a), Col.alpha(pc, 0f))
        g.strokeRoundRect(l, t, r, b, rad, 0.25f * u, Col.alpha(pc, 0.55f * a))
        // Accent spine down the left edge.
        g.fillRoundRect(l, t + rad, l + 0.6f * u, b - rad, 0.3f * u, Col.alpha(pc, a))
        // Corner notch: a class tag in the top right.
        val tag = perkClass(perk)
        val tagS = 2.1f * u
        val tagW = HudType.trackedWidth(g, tag, tagS, Gfx.Font.HUD, 0.35f * u) + 2.4f * u
        g.fillRoundRect(r - tagW - 2.4f * u, t + 2.2f * u, r - 2.4f * u, t + 5.2f * u, 1.5f * u, Col.alpha(pc, 0.16f * a))
        HudType.tracked(g, tag, r - 2.4f * u - tagW / 2f, t + 4.35f * u, tagS, Col.alpha(pc, a), Gfx.Font.HUD, Gfx.Align.CENTER, 0.35f * u)

        // Medallion.
        val ms = min(h * 0.64f, 20f * u)
        val mx = l + 4.4f * u + ms / 2f
        val my = t + h / 2f
        hexagon(mx, my, ms / 2f, Col.alpha(pc, 0.18f * a))
        hexagon(mx, my, ms / 2f * 0.86f, Col.alpha(0xFF0A0810.toInt(), a))
        hexagonStroke(mx, my, ms / 2f * 0.86f, Col.alpha(pc, a), 0.35f * u)
        g.blend(Gfx.Blend.ADD)
        g.glow(mx, my, ms * 0.45f, Col.alpha(pc, 0.25f * a))
        g.blend(Gfx.Blend.NORMAL)
        HudIcons.perk(g, perk, mx, my, ms * 0.46f, Col.alpha(pc, a))

        // Title + blurb.
        val tx = mx + ms / 2f + 4f * u
        val maxW = r - tx - 7f * u
        var ts = 5.2f * u
        val titleW = g.textWidth(perk.title, ts, Gfx.Font.TITLE)
        val titleMax = r - tx - tagW - 5f * u
        if (titleW > titleMax) ts *= titleMax / titleW
        val titleY = t + h * 0.34f + ts * 0.3f
        g.text(perk.title, tx, titleY, ts, Col.alpha(WHITE, a), Gfx.Font.TITLE)
        wrapText(perk.blurb, tx, titleY + 4.6f * u, 3.2f * u, maxW, Col.alpha(0xFFCFC8E0.toInt(), a))

        // Level: pips, the next one breathing, and NEW / LV label.
        val sy = b - 3.4f * u
        var px = tx
        if (stacks == 0) {
            val nw = 8.4f * u
            g.fillRoundRect(px, sy - 2.2f * u, px + nw, sy + 0.6f * u, 1.4f * u, Col.alpha(pc, a))
            HudType.tracked(g, "NEW", px + nw / 2f, sy + 0.05f * u, 2.2f * u, Col.alpha(0xFF0A0810.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER, 0.25f * u)
            px += nw + 2f * u
        }
        for (s in 0 until perk.maxStacks) {
            val filled = s < stacks
            val next = s == stacks
            val c = when {
                filled -> pc
                next -> Col.alpha(pc, (0.5f + 0.5f * sin(f.t * 6f)) * a)
                else -> 0x33FFFFFF
            }
            g.fillRoundRect(px, sy - 1.3f * u, px + 3.6f * u, sy - 0.3f * u, 0.5f * u, c)
            if (next) {
                g.blend(Gfx.Blend.ADD)
                g.glow(px + 1.8f * u, sy - 0.8f * u, 2.4f * u, Col.alpha(pc, 0.35f * (0.5f + 0.5f * sin(f.t * 6f)) * a))
                g.blend(Gfx.Blend.NORMAL)
            }
            px += 4.6f * u
        }
        val lvl = if (perk.maxStacks == 1) "UNIQUE" else "LV " + (stacks + 1) + " / " + perk.maxStacks
        HudType.tracked(g, lvl, px + 1f * u, sy - 0.1f * u, 2.2f * u, Col.alpha(pc, 0.8f * a), Gfx.Font.HUD, Gfx.Align.LEFT, 0.3f * u)

        // Tap chevron on the right edge, nudging.
        val nudge = (0.5f + 0.5f * sin(f.t * 5f + i)) * 0.8f * u
        val chx = r - 3.6f * u + nudge
        g.line(chx - 0.8f * u, my - 1.6f * u, chx + 0.6f * u, my, 0.45f * u, Col.alpha(pc, 0.8f * a))
        g.line(chx + 0.6f * u, my, chx - 0.8f * u, my + 1.6f * u, 0.45f * u, Col.alpha(pc, 0.8f * a))
    }

    private fun wrapText(text: String, x: Float, y: Float, size: Float, maxW: Float, color: Int) {
        if (g.textWidth(text, size) <= maxW) {
            g.text(text, x, y, size, color)
            return
        }
        // Greedy two-line wrap.
        val words = text.split(' ')
        var line = ""
        var yy = y
        var lines = 0
        for (wd in words) {
            val cand = if (line.isEmpty()) wd else "$line $wd"
            if (g.textWidth(cand, size) > maxW && line.isNotEmpty()) {
                g.text(line, x, yy, size, color)
                yy += size * 1.3f
                line = wd
                if (++lines >= 2) return
            } else {
                line = cand
            }
        }
        if (line.isNotEmpty()) g.text(line, x, yy, size, color)
    }

    private fun hexagon(cx: Float, cy: Float, r: Float, color: Int) {
        val p = poly.begin()
        for (i in 0 until 6) {
            val a = (i * 60f + 30f) * (Math.PI.toFloat() / 180f)
            p.add(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r)
        }
        p.fill(g, color)
    }

    private fun hexagonStroke(cx: Float, cy: Float, r: Float, color: Int, sw: Float) {
        for (i in 0 until 6) {
            val a0 = (i * 60f + 30f) * (Math.PI.toFloat() / 180f)
            val a1 = ((i + 1) * 60f + 30f) * (Math.PI.toFloat() / 180f)
            g.line(cx + kotlin.math.cos(a0) * r, cy + kotlin.math.sin(a0) * r, cx + kotlin.math.cos(a1) * r, cy + kotlin.math.sin(a1) * r, sw, color)
        }
    }

    // ======================================================= swipe-down hint

    private var lastStepOut = false
    private var stepOutAt = -9f

    /**
     * World space: a small chip over the player's head naming what swipe-down does here, or,
     * while hidden with the run thumb still down, how to step back out.
     */
    fun contextHint() {
        val w = f.w
        val p = w.player
        var action = w.contextAction()
        if (action == ContextAction.BOX) {
            // Only suggest the box when someone is actually coming for you.
            val threat = w.enemies.any { it.floor == p.floor && it.alive && (it.state == EnemyState.ALERT || it.state == EnemyState.AIM) }
            if (!threat) action = null
        }
        if (action != hintAction) {
            hintAction = action
            hintAt = if (f.dt == 0f) f.t - 10f else f.t
        }
        val gy = Geo.groundY(p.floorF)
        val bob = sin(f.t * 4f) * 0.035f
        val cx = p.x
        val head = gy - p.z - (if (p.state == PlayerState.BOX) 1.55f else 2.25f)
        // Keep the chip off doors, shafts and their signs: it sits beside the nearest one.
        val avoid = nearestFixture(p.x, p.floor)

        // Hidden in a door or riding a lift with the thumb that took you in still held.
        val stepOut = p.holdAxis != 0 && (p.state == PlayerState.DOOR || p.state == PlayerState.ELEVATOR)
        if (stepOut != lastStepOut) {
            lastStepOut = stepOut
            stepOutAt = if (f.dt == 0f) f.t - 10f else f.t
        }
        if (stepOut && action == null) {
            // Wait a beat so a quick in-and-out never flashes it.
            val k = HudType.clamp01((since(stepOutAt) - 0.45f) / 0.25f)
            if (k > 0f) chip(cx, head + bob, avoid, "LIFT TO STEP OUT", 0xFFB8C0D8.toInt(), -1, k, false)
            return
        }
        if (action == null) return
        val appear = HudType.clamp01(since(hintAt) / 0.22f)
        val color = when (action) {
            ContextAction.INTEL -> 0xFFFF2E4E.toInt()
            ContextAction.ELEVATOR -> 0xFF3CF4FF.toInt()
            ContextAction.DOOR -> 0xFFE8E0FF.toInt()
            ContextAction.BOX -> 0xFFE0A866.toInt()
        }
        val label = when (action) {
            ContextAction.INTEL -> "INTEL"
            ContextAction.ELEVATOR -> "RIDE"
            ContextAction.DOOR -> "HIDE"
            ContextAction.BOX -> "BOX"
        }
        chip(cx, head + bob + (1f - appear) * 0.15f, avoid, label, color, action.ordinal, appear, true)
    }

    /** X of the door or shaft nearest [x] on [floor] within reach, or NaN. */
    private fun nearestFixture(x: Float, floor: Int): Float {
        val plan = f.w.floors[floor]?.plan ?: return Float.NaN
        var best = Float.NaN
        var bd = 1.35f
        for (d in plan.doors) {
            val dd = kotlin.math.abs(d.x - x)
            if (dd < bd) { bd = dd; best = d.x }
        }
        for (sh in plan.shafts) {
            val dd = kotlin.math.abs(sh.x - x)
            if (dd < bd) { bd = dd; best = sh.x }
        }
        return best
    }

    /**
     * The hint chip: [icon] = a [ContextAction] ordinal or -1 for none; [swipe] adds the
     * animated swipe-down chevron. Drawn in pixels around the chip centre for crisp type.
     */
    private fun chip(px0: Float, cy0: Float, avoid: Float, label: String, color: Int, icon: Int, appear: Float, swipe: Boolean) {
        val e = HudType.outBack(appear, 2.2f)
        val s = f.s
        val px = 0.13f * s // the chip's own grid unit, in px
        val ts = (if (swipe) 1.9f else 1.5f) * px
        val track = 0.25f * px
        val lw = HudType.trackedWidth(g, label, ts, Gfx.Font.HUD, track)
        val ch = (if (swipe) 3.4f else 2.8f) * px
        val iconS = 2.2f * px
        var cw = 1.2f * px + lw + 1.2f * px
        if (icon >= 0) cw += iconS + 0.9f * px
        if (swipe) cw += 2.8f * px
        // Place it: over the head, or beside the nearest door/shaft (on the player's side of it).
        val hw = cw / 2f / s
        var cx = px0
        var cy = cy0
        if (!avoid.isNaN()) {
            var side = if (px0 >= avoid) 1f else -1f
            if (kotlin.math.abs(px0 - avoid) < 0.05f) side = if (avoid < Geo.FLOOR_W / 2f) 1f else -1f
            cx = avoid + side * (0.62f + hw)
            if (cx - hw < 0.15f || cx + hw > Geo.FLOOR_W - 0.15f) cx = avoid - side * (0.62f + hw)
            cy = cy0 + 0.2f
        }
        cx = cx.coerceIn(hw + 0.15f, Geo.FLOOR_W - hw - 0.15f)
        g.save()
        g.translate(cx, cy)
        g.scale(e / s, e / s)
        val l = -cw / 2f
        val r = cw / 2f
        val t = -ch / 2f
        val b = ch / 2f
        // Tell the floating text where the chip is (screen px), so popups steer around it.
        val scx = (cx + 0.6f) * s + f.shakeX
        val scy = (cy - f.camY) * s + f.shakeY
        f.reserved[0] = scx + l * e
        f.reserved[1] = scy + t * e
        f.reserved[2] = scx + r * e
        f.reserved[3] = scy + (b + 0.8f * px) * e
        f.hasReserved = true
        val edge = Col.alpha(color, if (swipe) 0.85f else 0.5f)
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.scale(1f, 0.5f)
        g.glow(0f, 0f, cw * 0.75f, Col.alpha(color, 0.2f * appear))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        // Solid plate with a soft two-step shadow: always reads as UI, never as signage.
        g.fillRoundRect(l - 0.2f * px, t + 0.2f * px, r + 0.2f * px, b + 0.7f * px, ch / 2f + 0.2f * px, 0x40000000)
        g.fillRoundRect(l, t + 0.3f * px, r, b + 0.35f * px, ch / 2f, 0x99000000.toInt())
        g.fillRoundRect(l, t, r, b, ch / 2f, 0xFF0B0913.toInt())
        g.fillRoundRect(l + 0.2f * px, t + 0.15f * px, r - 0.2f * px, t + ch * 0.45f, ch / 2f, 0x0FFFFFFF)
        g.strokeRoundRect(l, t, r, b, ch / 2f, 0.16f * px, edge)
        // Tail, leaning toward the player.
        val tx = ((px0 - cx) * s / e).coerceIn(l + ch * 0.6f, r - ch * 0.6f)
        poly.tri(g, tx - 0.55f * px, b - 0.05f * px, tx + 0.55f * px, b - 0.05f * px, tx + (if (tx > 0.5f * px) 0.35f else if (tx < -0.5f * px) -0.35f else 0f) * px, b + 0.8f * px, edge)
        var x = l + 1.2f * px
        if (icon >= 0) {
            val ix = x + iconS / 2f
            when (ContextAction.entries[icon]) {
                ContextAction.INTEL -> HudIcons.dataCore(g, ix, 0f, iconS, color)
                ContextAction.ELEVATOR -> HudIcons.elevator(g, ix, 0f, iconS, color)
                ContextAction.DOOR -> HudIcons.door(g, ix, 0f, iconS, color)
                ContextAction.BOX -> HudIcons.box(g, ix, 0f, iconS, color, 0xFF7A5030.toInt())
            }
            x += iconS + 0.9f * px
        }
        HudType.tracked(g, label, x, ts * 0.36f, ts, if (swipe) INK else Col.alpha(INK, 0.8f), Gfx.Font.HUD, Gfx.Align.LEFT, track)
        x += lw + 1.2f * px
        if (swipe) {
            // Divider then an animated swipe-down chevron.
            g.fillRect(x - 0.55f * px, t + 0.8f * px, x - 0.45f * px, b - 0.8f * px, 0x40FFFFFF)
            val sweep = fract(f.t * 1.4f)
            val chx = x + 0.9f * px
            val cyc = -0.55f * px + sweep * 1.1f * px
            HudIcons.chevronDown(g, chx, cyc, 0.6f * px, 0.24f * px, Col.alpha(color, 1f - sweep * 0.8f))
            HudIcons.chevronDown(g, chx, cyc - 0.7f * px, 0.6f * px, 0.24f * px, Col.alpha(color, 0.35f * (1f - sweep)))
        }
        g.restore()
    }
}
