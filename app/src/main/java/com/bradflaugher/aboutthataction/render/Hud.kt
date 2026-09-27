package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.ContextAction
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Heads-up display, zone title cards, the perk picker and the swipe-down hint. */
internal class Hud(private val f: Frame, private val building: Building) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val rect = FloatArray(4)
    private val pauseBuf = FloatArray(3)

    private var perkSeenAt = -1f
    private var lastScore = -1L
    private var scoreText = "0"
    private var lastFloor = Int.MIN_VALUE
    private var floorText = ""
    private var lastDeepest = -1
    private var deepestText = ""

    companion object {
        const val MAX_HEAT = 4.5f
        private const val PAUSE_R = 5f

        fun unit(width: Float) = width / 100f

        fun pauseCenter(width: Float, topInset: Float, out: FloatArray) {
            val u = unit(width)
            out[0] = width - 4f * u - PAUSE_R * u
            out[1] = topInset + 3f * u + PAUSE_R * u
            out[2] = PAUSE_R * u
        }

        fun isPauseButton(x: Float, y: Float, width: Float, height: Float, topInset: Float): Boolean {
            val c = FloatArray(3)
            pauseCenter(width, topInset, c)
            val r = c[2] * 1.7f
            val dx = x - c[0]
            val dy = y - c[1]
            return dx * dx + dy * dy <= r * r
        }

        /** Card i's rect (left, top, right, bottom) in pixels. */
        fun cardRect(i: Int, width: Float, height: Float, topInset: Float, bottomInset: Float, out: FloatArray) {
            val u = unit(width)
            val areaTop = topInset + height * 0.25f
            val areaBottom = height - bottomInset - height * 0.07f
            val gap = 4f * u
            val ch = min((areaBottom - areaTop - 2f * gap) / 3f, 34f * u)
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

        fun floorLabel(floor: Int) = if (floor <= 0) "ROOF" else "B-" + floor.toString().padStart(3, '0')
    }

    private fun zoneNeon(): Int {
        val w = f.w
        val fs = w.floors[w.player.floor]
        return f.palette(fs).neon
    }

    // ==================================================================== HUD

    fun draw() {
        val w = f.w
        val p = w.player
        val W = g.width
        val u = unit(W)
        val top = f.topInset + 3f * u
        val neon = zoneNeon()

        // Soft scrim so the HUD reads on any background.
        g.fillVerticalGradient(0f, 0f, W, f.topInset + 24f * u, 0xB0000000.toInt(), 0x00000000)
        g.fillVerticalGradient(0f, g.height - f.bottomInset - 14f * u, W, g.height, 0x00000000, 0x90000000.toInt())

        // ----- hearts
        val hs = 5.6f * u
        var hx = 4f * u + hs / 2f
        val hy = top + hs / 2f
        for (i in 0 until p.maxHp) {
            val full = i < p.hp
            val beat = if (full && p.hp == 1) 1f + 0.12f * abs(sin(f.t * 6f)) else 1f
            Glyphs.heart(g, hx + 0.25f * u, hy + 0.35f * u, hs * beat, 0x90000000.toInt())
            if (full) {
                Glyphs.heart(g, hx, hy, hs * 1.25f * beat, 0x30FF3B5C)
                Glyphs.heart(g, hx, hy, hs * beat, 0xFFFF3B5C.toInt())
                g.fillCircle(hx - hs * 0.2f, hy - hs * 0.18f, hs * 0.09f, 0xC0FFFFFF.toInt())
            } else {
                Glyphs.heart(g, hx, hy, hs, 0xFF3A1A24.toInt())
                Glyphs.heart(g, hx, hy, hs * 0.62f, 0xFF140A10.toInt())
            }
            hx += hs * 1.12f
        }
        if (p.shield) {
            shieldIcon(hx + 0.4f * u, hy, hs * 0.95f, 0xFF3CC8FF.toInt())
            hx += hs * 1.15f
        }
        if (p.armorReady) {
            vestIcon(hx + 0.4f * u, hy, hs * 0.9f, 0xFFFFD24A.toInt())
            hx += hs * 1.1f
        }

        // ----- grenades
        val gy = top + hs + 3.2f * u
        var gx = 4.6f * u
        val gr = 1.55f * u
        for (i in 0 until w.maxGrenades) {
            val have = i < p.grenades
            val c = if (have) 0xFF9AE040.toInt() else 0x40FFFFFF
            g.fillCircle(gx + gr, gy, gr, c)
            g.fillRect(gx + gr * 0.6f, gy - gr * 1.55f, gx + gr * 1.4f, gy - gr * 0.8f, c)
            if (have) g.fillCircle(gx + gr * 0.65f, gy - gr * 0.3f, gr * 0.3f, 0x70FFFFFF)
            gx += gr * 2.7f
        }

        // ----- perks row
        if (w.perks.isNotEmpty()) {
            var px = 4f * u
            val py = gy + 4.2f * u
            val ps = 5.4f * u
            for ((perk, n) in w.perks) {
                if (px + ps > W * 0.46f) break
                val pc = perkColor(perk)
                g.fillRoundRect(px, py, px + ps, py + ps, 1.1f * u, 0xC0101018.toInt())
                g.strokeRoundRect(px, py, px + ps, py + ps, 1.1f * u, 0.3f * u, pc)
                g.text(perkCode(perk), px + ps / 2f, py + ps * 0.64f, ps * 0.44f, pc, Gfx.Font.TITLE, Gfx.Align.CENTER)
                for (s in 0 until n) g.fillCircle(px + ps * 0.25f + s * ps * 0.25f, py + ps + 0.9f * u, 0.45f * u, pc)
                px += ps + 1.3f * u
            }
        }

        // ----- depth readout (centre)
        val cx = W / 2f
        val floor = p.floor
        if (floor != lastFloor) {
            lastFloor = floor
            floorText = floorLabel(floor)
        }
        val dSize = 7.6f * u
        g.text(floorText, cx + 0.35f * u, top + dSize * 0.9f + 0.35f * u, dSize, 0xA0000000.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(floorText, cx, top + dSize * 0.9f, dSize, Col.alpha(neon, 0.25f), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(floorText, cx, top + dSize * 0.88f, dSize, 0xFFFFFFFF.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        val zoneTitle = if (floor == 0) Zone.ROOFTOP.subtitle.uppercase() else w.zone.title
        g.text(zoneTitle, cx, top + dSize + 3.4f * u, 3f * u, neon, Gfx.Font.HUD, Gfx.Align.CENTER)
        // Heat meter.
        val bw = 22f * u
        val by = top + dSize + 5.4f * u
        val heat = (w.heat / MAX_HEAT).coerceIn(0f, 1f)
        g.fillRoundRect(cx - bw / 2f, by, cx + bw / 2f, by + 1.1f * u, 0.55f * u, 0x60000000)
        val segs = 12
        val sw = bw / segs
        for (i in 0 until segs) {
            val t = (i + 0.5f) / segs
            if (t > heat && !(i == 0 && heat > 0f)) break
            val c = when {
                t < 0.35f -> Col.lerp(0xFF3CFF8E.toInt(), 0xFFFFE040.toInt(), t / 0.35f)
                t < 0.7f -> Col.lerp(0xFFFFE040.toInt(), 0xFFFF6A1A.toInt(), (t - 0.35f) / 0.35f)
                else -> Col.lerp(0xFFFF6A1A.toInt(), 0xFFFF1E3C.toInt(), (t - 0.7f) / 0.3f)
            }
            g.fillRect(cx - bw / 2f + i * sw + 0.15f * u, by + 0.15f * u, cx - bw / 2f + (i + 1) * sw - 0.15f * u, by + 0.95f * u, c)
        }
        g.text("HEAT", cx - bw / 2f - 1.2f * u, by + 1.1f * u, 2.2f * u, 0x90FFFFFF.toInt(), Gfx.Font.HUD, Gfx.Align.RIGHT)

        // ----- pause button
        val pc = pauseBuf
        pauseCenter(W, f.topInset, pc)
        g.fillCircle(pc[0], pc[1], pc[2], 0x90101018.toInt())
        g.strokeCircle(pc[0], pc[1], pc[2], 0.35f * u, 0x70FFFFFF)
        g.fillRoundRect(pc[0] - 1.6f * u, pc[1] - 1.9f * u, pc[0] - 0.6f * u, pc[1] + 1.9f * u, 0.3f * u, 0xFFFFFFFF.toInt())
        g.fillRoundRect(pc[0] + 0.6f * u, pc[1] - 1.9f * u, pc[0] + 1.6f * u, pc[1] + 1.9f * u, 0.3f * u, 0xFFFFFFFF.toInt())

        // ----- score + deepest
        if (w.score != lastScore) {
            lastScore = w.score
            scoreText = formatScore(w.score)
        }
        if (w.deepest != lastDeepest) {
            lastDeepest = w.deepest
            deepestText = "DEEPEST " + floorLabel(w.deepest)
        }
        // Score sits under the pause button, right-aligned, clear of the depth readout.
        val sx = W - 4f * u
        val sSize = 5f * u
        val sy = pc[1] + pc[2] + sSize + 1.6f * u
        g.text(scoreText, sx + 0.3f * u, sy + 0.3f * u, sSize, 0xA0000000.toInt(), Gfx.Font.TITLE, Gfx.Align.RIGHT)
        g.text(scoreText, sx, sy, sSize, 0xFFFFF4D0.toInt(), Gfx.Font.TITLE, Gfx.Align.RIGHT)
        g.text(deepestText, sx, sy + 3.2f * u, 2.5f * u, 0xA0FFFFFF.toInt(), Gfx.Font.HUD, Gfx.Align.RIGHT)

        // ----- combo meter
        if (w.combo >= 2) {
            val cy = sy + 11.5f * u
            val right = W - 4f * u
            val pulse = 1f + 0.06f * sin(f.t * 16f)
            val cs = (6.5f + min(4f, w.combo * 0.4f)) * u * pulse
            val txt = "×" + w.combo
            g.text(txt, right + 0.4f * u, cy + 0.4f * u, cs, 0xA0000000.toInt(), Gfx.Font.TITLE, Gfx.Align.RIGHT)
            g.text(txt, right, cy, cs, 0xFFFF3D9A.toInt(), Gfx.Font.TITLE, Gfx.Align.RIGHT)
            g.text("COMBO", right, cy + 3.4f * u, 2.8f * u, 0xFFFFB0D8.toInt(), Gfx.Font.HUD, Gfx.Align.RIGHT)
            val bwc = 18f * u
            val t = (w.comboTimer / 2.6f).coerceIn(0f, 1f)
            g.fillRect(right - bwc, cy + 4.4f * u, right, cy + 5.2f * u, 0x50000000)
            g.fillRect(right - bwc * t, cy + 4.4f * u, right, cy + 5.2f * u, 0xFFFF3D9A.toInt())
        }

        // ----- bottom: ammo, weapon timer, bullet time
        val bottom = g.height - f.bottomInset - 4f * u
        ammo(4.5f * u, bottom, u)
        var pillY = bottom - 1.5f * u
        val weapon = p.weapon
        if (weapon != null) {
            timerPill(W / 2f, pillY, u, weapon.title, p.weaponTime / weapon.seconds, 0xFFFFA020.toInt(), weapon)
            pillY -= 7.5f * u
        }
        if (p.slowMoTime > 0f) {
            timerPill(W / 2f, pillY, u, "BULLET TIME", p.slowMoTime / PickupKind.SLOWMO.seconds, 0xFFB080FF.toInt(), PickupKind.SLOWMO)
        }
    }

    private fun formatScore(s: Long): String {
        val raw = s.toString()
        val sb = StringBuilder(raw.length + raw.length / 3)
        for (i in raw.indices) {
            if (i > 0 && (raw.length - i) % 3 == 0) sb.append(',')
            sb.append(raw[i])
        }
        return sb.toString()
    }

    private fun ammo(x: Float, bottom: Float, u: Float) {
        val p = f.w.player
        val infinite = p.weapon == PickupKind.SHOTGUN || p.weapon == PickupKind.MINIGUN
        val bw = 1.5f * u
        val bh = 4.2f * u
        var bx = x
        if (infinite) {
            val r = 1.6f * u
            g.strokeCircle(x + r, bottom - r * 1.3f, r, 0.6f * u, 0xFFFFA020.toInt())
            g.strokeCircle(x + r * 3f, bottom - r * 1.3f, r, 0.6f * u, 0xFFFFA020.toInt())
            return
        }
        for (i in 0 until p.magSize) {
            val have = i < p.ammo && !p.reloading
            val c = if (have) 0xFFFFD24A.toInt() else 0x40FFFFFF
            g.fillRoundRect(bx, bottom - bh, bx + bw, bottom, bw / 2f, c)
            if (have) g.fillRect(bx, bottom - bh * 0.3f, bx + bw, bottom - bh * 0.22f, 0x60000000)
            bx += bw + 0.9f * u
        }
        if (p.reloading) {
            val t = 1f - p.reloadTime / max(0.01f, p.reloadTotal)
            val w = bx - x - 0.9f * u
            g.fillRect(x, bottom + 0.8f * u, x + w * t, bottom + 1.6f * u, 0xFFFFD24A.toInt())
            val blink = (f.t * 8f).toInt() % 2 == 0
            if (blink) g.text("RELOAD", x, bottom - bh - 1.2f * u, 3f * u, 0xFFFFD24A.toInt(), Gfx.Font.HUD, Gfx.Align.LEFT)
        }
    }

    private fun timerPill(cx: Float, y: Float, u: Float, label: String, t: Float, color: Int, kind: PickupKind) {
        val w = 40f * u
        val h = 6f * u
        g.fillRoundRect(cx - w / 2f, y - h, cx + w / 2f, y, h / 2f, 0xD0101018.toInt())
        g.fillRoundRect(cx - w / 2f, y - h, cx - w / 2f + w * t.coerceIn(0f, 1f), y, h / 2f, Col.alpha(color, 0.35f))
        g.strokeRoundRect(cx - w / 2f, y - h, cx + w / 2f, y, h / 2f, 0.35f * u, color)
        g.text(label, cx + 2f * u, y - h * 0.3f, 3.3f * u, 0xFFFFFFFF.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
    }

    private fun shieldIcon(cx: Float, cy: Float, s: Float, c: Int) {
        poly.begin()
            .add(cx - s * 0.42f, cy - s * 0.42f).add(cx + s * 0.42f, cy - s * 0.42f)
            .add(cx + s * 0.38f, cy + s * 0.08f).add(cx, cy + s * 0.5f).add(cx - s * 0.38f, cy + s * 0.08f)
            .fill(g, c)
        poly.begin()
            .add(cx - s * 0.26f, cy - s * 0.28f).add(cx + s * 0.26f, cy - s * 0.28f)
            .add(cx + s * 0.23f, cy + s * 0.04f).add(cx, cy + s * 0.32f).add(cx - s * 0.23f, cy + s * 0.04f)
            .fill(g, 0xFF0A2A3A.toInt())
    }

    private fun vestIcon(cx: Float, cy: Float, s: Float, c: Int) {
        poly.begin()
            .add(cx - s * 0.2f, cy - s * 0.45f).add(cx - s * 0.08f, cy - s * 0.3f).add(cx + s * 0.08f, cy - s * 0.3f).add(cx + s * 0.2f, cy - s * 0.45f)
            .add(cx + s * 0.42f, cy - s * 0.3f).add(cx + s * 0.38f, cy + s * 0.45f).add(cx - s * 0.38f, cy + s * 0.45f).add(cx - s * 0.42f, cy - s * 0.3f)
            .fill(g, c)
        g.line(cx, cy - s * 0.25f, cx, cy + s * 0.4f, s * 0.06f, 0xFF3A2A08.toInt())
    }

    // ============================================================ zone banner

    fun banner() {
        val w = f.w
        val zone = w.bannerZone ?: return
        val bt = w.bannerTime
        if (bt <= 0f) return
        val age = 3.2f - bt
        val a = min(1f, age / 0.25f) * min(1f, bt / 0.5f)
        val W = g.width
        val u = unit(W)
        val cy = g.height * 0.3f
        val pal = if (zone == Zone.VOID) Palette.void(Zone.VOID, f.t, 0) else Palette.of(zone)
        val neon = pal.neon
        val bandH = 26f * u
        val open = min(1f, age / 0.3f)
        g.fillRect(0f, cy - bandH / 2f * open, W, cy + bandH / 2f * open, Col.alpha(0xFF05030A.toInt(), 0.78f * a))
        val lw = W * min(1f, age / 0.45f)
        g.fillRect(W / 2f - lw / 2f, cy - bandH / 2f * open, W / 2f + lw / 2f, cy - bandH / 2f * open + 0.5f * u, Col.alpha(neon, a))
        g.fillRect(W / 2f - lw / 2f, cy + bandH / 2f * open - 0.5f * u, W / 2f + lw / 2f, cy + bandH / 2f * open, Col.alpha(pal.neon2, a))
        if (open < 0.6f) return
        // Title sized to fit.
        var size = 12f * u
        val tw = g.textWidth(zone.title, size, Gfx.Font.TITLE)
        if (tw > W * 0.86f) size *= W * 0.86f / tw
        val slide = (1f - min(1f, age / 0.35f)) * 20f * u
        val ty = cy + size * 0.18f
        val jitter = if (zone == Zone.VOID) (hash((f.t * 20f).toInt(), 9) - 0.5f) * 1.5f * u else 0f
        g.text(zone.title, W / 2f + slide - 0.8f * u + jitter, ty, size, Col.alpha(0xFFFF2BD6.toInt(), 0.5f * a), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(zone.title, W / 2f + slide + 0.8f * u - jitter, ty, size, Col.alpha(0xFF2BFFE0.toInt(), 0.5f * a), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(zone.title, W / 2f + slide, ty, size * 1.02f, Col.alpha(neon, 0.35f * a), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(zone.title, W / 2f + slide, ty, size, Col.alpha(0xFFFFFFFF.toInt(), a), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text(zone.subtitle.uppercase(), W / 2f - slide, cy + bandH * 0.36f, 3.4f * u, Col.alpha(neon, a), Gfx.Font.HUD, Gfx.Align.CENTER)
        g.text(floorLabel(w.player.floor), W / 2f - slide, cy - bandH * 0.28f, 3.2f * u, Col.alpha(0xFFFFFFFF.toInt(), 0.7f * a), Gfx.Font.HUD, Gfx.Align.CENTER)
    }

    // ============================================================ perk picker

    fun perkOverlay() {
        val w = f.w
        val W = g.width
        val H = g.height
        val u = unit(W)
        if (perkSeenAt < 0f || f.t < perkSeenAt) perkSeenAt = if (f.dt == 0f) f.t - 10f else f.t
        val age = f.t - perkSeenAt
        g.fillRect(0f, 0f, W, H, Col.alpha(0xFF05020A.toInt(), 0.9f * min(1f, age / 0.2f)))
        // Red data-rain accents.
        for (i in 0 until 14) {
            val x = hash(i, 501) * W
            val y = fract(hash(i, 502) + f.t * (0.1f + hash(i, 503) * 0.2f)) * H
            g.fillRect(x, y, x + 0.4f * u, y + 8f * u, 0x30FF1E3C)
        }
        val red = 0xFFFF1E3C.toInt()
        val titleY = f.topInset + H * 0.12f
        building.dataCore(W / 2f, titleY - 7f * u, 3.4f * u, red, true)
        g.text("INTEL ACQUIRED", W / 2f, titleY + 5.5f * u, 8f * u, Col.alpha(red, 0.35f), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text("INTEL ACQUIRED", W / 2f, titleY + 5.3f * u, 7.6f * u, 0xFFFFFFFF.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
        g.text("CHOOSE ONE UPGRADE", W / 2f, titleY + 11f * u, 3.6f * u, 0xFFFF6A7E.toInt(), Gfx.Font.HUD, Gfx.Align.CENTER)

        for (i in w.perkOffer.indices) {
            val perk = w.perkOffer[i]
            cardRect(i, W, H, f.topInset, f.bottomInset, rect)
            val appear = ((age - i * 0.08f) / 0.25f).coerceIn(0f, 1f)
            val slide = (1f - appear) * (1f - appear) * W * 0.6f
            val l = rect[0] + slide
            val t = rect[1]
            val r = rect[2] + slide
            val b = rect[3]
            val h = b - t
            val pc = perkColor(perk)
            val stacks = w.stacks(perk)
            g.fillRoundRect(l - 1f * u, t - 1f * u, r + 1f * u, b + 1f * u, 4f * u, Col.alpha(pc, 0.12f))
            g.fillRoundRect(l, t, r, b, 3.2f * u, 0xF0100C18.toInt())
            g.fillVerticalGradient(l + 1f * u, t + 1f * u, r - 1f * u, t + h * 0.45f, Col.alpha(pc, 0.16f), Col.alpha(pc, 0f))
            g.strokeRoundRect(l, t, r, b, 3.2f * u, 0.5f * u, pc)
            // Badge.
            val bs = min(h * 0.62f, 22f * u)
            val bx = l + 4f * u + bs / 2f
            val bcy = t + h / 2f
            hexagon(bx, bcy, bs / 2f, Col.alpha(pc, 0.2f))
            hexagon(bx, bcy, bs / 2f * 0.86f, 0xFF0A0810.toInt())
            hexagonStroke(bx, bcy, bs / 2f * 0.86f, pc, 0.45f * u)
            g.text(perkCode(perk), bx, bcy + bs * 0.15f, bs * 0.4f, pc, Gfx.Font.TITLE, Gfx.Align.CENTER)
            // Text.
            val tx = bx + bs / 2f + 4f * u
            val maxW = r - tx - 3f * u
            var ts = 5.6f * u
            val titleW = g.textWidth(perk.title, ts, Gfx.Font.TITLE)
            if (titleW > maxW) ts *= maxW / titleW
            g.text(perk.title, tx, t + h * 0.36f, ts, 0xFFFFFFFF.toInt(), Gfx.Font.TITLE, Gfx.Align.LEFT)
            wrapText(perk.blurb, tx, t + h * 0.36f + 5.2f * u, 3.7f * u, maxW, 0xFFD8D0E8.toInt())
            // Stacks.
            val sy = b - h * 0.13f
            if (stacks == 0) {
                g.fillRoundRect(tx, sy - 3.4f * u, tx + 10f * u, sy + 0.2f * u, 1.8f * u, pc)
                g.text("NEW", tx + 5f * u, sy - 0.7f * u, 2.8f * u, 0xFF0A0810.toInt(), Gfx.Font.TITLE, Gfx.Align.CENTER)
            }
            val px0 = tx + (if (stacks == 0) 12.5f * u else 0f)
            for (s in 0 until perk.maxStacks) {
                val filled = s < stacks
                val next = s == stacks
                val x0 = px0 + s * 5.5f * u
                val c = when {
                    filled -> pc
                    next -> Col.alpha(pc, 0.55f + 0.45f * sin(f.t * 6f))
                    else -> 0x40FFFFFF
                }
                g.fillRoundRect(x0, sy - 2.6f * u, x0 + 4.4f * u, sy - 0.6f * u, 0.8f * u, c)
            }
            val lvl = "LV ${stacks + 1}/${perk.maxStacks}"
            g.text(lvl, r - 3.5f * u, sy - 0.5f * u, 3f * u, Col.alpha(pc, 0.9f), Gfx.Font.HUD, Gfx.Align.RIGHT)
        }
        g.text("TAP A CARD", W / 2f, H - f.bottomInset - 3.5f * u, 3.2f * u, Col.alpha(0xFFFFFFFF.toInt(), 0.45f + 0.35f * sin(f.t * 4f)), Gfx.Font.HUD, Gfx.Align.CENTER)
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
                yy += size * 1.2f
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

    /** World space: a little glyph over the player's head for what swipe-down does here. */
    fun contextHint() {
        val w = f.w
        val action = w.contextAction() ?: return
        val p = w.player
        if (action == ContextAction.BOX) {
            // Only suggest the box when someone is actually coming for you.
            val threat = w.enemies.any { it.floor == p.floor && it.alive && (it.state == EnemyState.ALERT || it.state == EnemyState.AIM) }
            if (!threat) return
        }
        val gy = Geo.groundY(p.floorF)
        val bob = sin(f.t * 5f) * 0.05f
        val cx = p.x
        val cy = gy - p.z - 2.2f + bob
        val color = when (action) {
            ContextAction.INTEL -> 0xFFFF1E3C.toInt()
            ContextAction.ELEVATOR -> 0xFF3CF4FF.toInt()
            ContextAction.DOOR -> 0xFFE8E0FF.toInt()
            ContextAction.BOX -> 0xFFD49A5E.toInt()
        }
        g.fillRoundRect(cx - 0.36f, cy - 0.26f, cx + 0.36f, cy + 0.26f, 0.12f, 0xD0080610.toInt())
        g.strokeRoundRect(cx - 0.36f, cy - 0.26f, cx + 0.36f, cy + 0.26f, 0.12f, 0.03f, color)
        val ix = cx - 0.12f
        when (action) {
            ContextAction.INTEL -> building.dataCore(ix, cy, 0.11f, color, true)
            ContextAction.ELEVATOR -> {
                g.strokeRect(ix - 0.1f, cy - 0.14f, ix + 0.1f, cy + 0.14f, 0.025f, color)
                g.line(ix, cy - 0.12f, ix, cy + 0.12f, 0.02f, color)
            }
            ContextAction.DOOR -> {
                g.strokeRect(ix - 0.09f, cy - 0.15f, ix + 0.09f, cy + 0.15f, 0.025f, color)
                g.fillCircle(ix + 0.04f, cy, 0.02f, color)
            }
            ContextAction.BOX -> {
                g.fillRect(ix - 0.12f, cy - 0.09f, ix + 0.12f, cy + 0.12f, color)
                g.fillRect(ix - 0.02f, cy - 0.09f, ix + 0.02f, cy + 0.12f, 0xFF7A5030.toInt())
            }
        }
        Glyphs.arrow(g, cx + 0.17f, cy, 0.12f, 0f, 1f, 0.04f, color)
        // Tail pointing at the head.
        poly.tri(g, cx - 0.07f, cy + 0.26f, cx + 0.07f, cy + 0.26f, cx, cy + 0.36f, color)
    }
}
