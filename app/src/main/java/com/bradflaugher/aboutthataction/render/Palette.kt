package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.abs
import kotlin.math.floor

/** Color math on 0xAARRGGBB ints. */
internal object Col {
    fun a(c: Int) = c ushr 24
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Same RGB, alpha replaced by [alpha] (0..1). */
    fun alpha(c: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (c and 0xFFFFFF)

    /** Scales the existing alpha by [f]. */
    fun fade(c: Int, f: Float): Int = (((a(c) * f.coerceIn(0f, 1f)).toInt()) shl 24) or (c and 0xFFFFFF)

    fun lerp(x: Int, y: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return argb(
            (a(x) + (a(y) - a(x)) * u).toInt(),
            (r(x) + (r(y) - r(x)) * u).toInt(),
            (g(x) + (g(y) - g(x)) * u).toInt(),
            (b(x) + (b(y) - b(x)) * u).toInt(),
        )
    }

    fun mul(c: Int, f: Float) = argb(a(c), (r(c) * f).toInt(), (g(c) * f).toInt(), (b(c) * f).toInt())

    fun hsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = v * s
        val x = c * (1f - abs(hh % 2f - 1f))
        val m = v - c
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }

    /** Rotates the hue of [c] by [deg] degrees. */
    fun hueShift(c: Int, deg: Float): Int {
        val r = r(c) / 255f
        val g = g(c) / 255f
        val b = b(c) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d < 1e-4f) return c
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return hsv(h + deg, d / max, max, a(c))
    }

    fun gray(c: Int): Int {
        val l = (r(c) * 0.3f + g(c) * 0.59f + b(c) * 0.11f).toInt()
        return argb(a(c), l, l, l)
    }
}

/** Cheap deterministic hash noise in [0, 1). */
internal fun hash(i: Int, salt: Int = 0): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

internal fun fract(x: Float) = x - floor(x)

/** Everything a zone looks like. */
internal class Palette(
    val skyTop: Int,
    val skyBottom: Int,
    val wallTop: Int,
    val wallBottom: Int,
    /** Panel seams, darker wall detail. */
    val panel: Int,
    /** Main neon accent (trims, signage). */
    val neon: Int,
    /** Secondary neon. */
    val neon2: Int,
    val slab: Int,
    val slabEdge: Int,
    val lamp: Int,
    val outer: Int,
    val outerLit: Int,
    val door: Int,
    val doorFrame: Int,
    val enemyMain: Int,
    val enemyAccent: Int,
    val enemySkin: Int,
    val bgFar: Int,
    val bgMid: Int,
    val bgNear: Int,
    val laser: Int,
    val vent: Int,
) {
    fun map(f: (Int) -> Int) = Palette(
        f(skyTop), f(skyBottom), f(wallTop), f(wallBottom), f(panel), f(neon), f(neon2), f(slab), f(slabEdge), f(lamp),
        f(outer), f(outerLit), f(door), f(doorFrame), f(enemyMain), f(enemyAccent), f(enemySkin), f(bgFar), f(bgMid),
        f(bgNear), f(laser), f(vent),
    )

    companion object {
        private val base = HashMap<Zone, Palette>()
        private const val VOID_STEPS = 48
        private val voidCache = HashMap<Zone, Array<Palette?>>()

        init {
            base[Zone.ROOFTOP] = Palette(
                skyTop = 0xFF070417.toInt(), skyBottom = 0xFF4A1450.toInt(),
                wallTop = 0xFF1B1236.toInt(), wallBottom = 0xFF100A22.toInt(), panel = 0xFF2A1D52.toInt(),
                neon = 0xFFFF2E88.toInt(), neon2 = 0xFF25E8FF.toInt(),
                slab = 0xFF151024.toInt(), slabEdge = 0xFFFF2E88.toInt(), lamp = 0xFFFFD9F2.toInt(),
                outer = 0xFF0D0A1C.toInt(), outerLit = 0xFFFFB45A.toInt(),
                door = 0xFF2B2150.toInt(), doorFrame = 0xFF4E3C8A.toInt(),
                enemyMain = 0xFF14121C.toInt(), enemyAccent = 0xFFFF2E5C.toInt(), enemySkin = 0xFFE8B89A.toInt(),
                bgFar = 0xFF2A1248.toInt(), bgMid = 0xFF1A0C33.toInt(), bgNear = 0xFF0B0619.toInt(),
                laser = 0xFFFF2050.toInt(), vent = 0xFFE0F0FF.toInt(),
            )
            base[Zone.TOWER] = Palette(
                skyTop = 0xFF0A0520.toInt(), skyBottom = 0xFF3C1048.toInt(),
                wallTop = 0xFF241646.toInt(), wallBottom = 0xFF140C2C.toInt(), panel = 0xFF33215F.toInt(),
                neon = 0xFFFF3D9A.toInt(), neon2 = 0xFF2CF0FF.toInt(),
                slab = 0xFF0E0A1C.toInt(), slabEdge = 0xFFFF3D9A.toInt(), lamp = 0xFFFFE0F4.toInt(),
                outer = 0xFF0F0B22.toInt(), outerLit = 0xFF7DF3FF.toInt(),
                door = 0xFF3A2A6A.toInt(), doorFrame = 0xFF6A52B8.toInt(),
                enemyMain = 0xFF121019.toInt(), enemyAccent = 0xFFFF2E5C.toInt(), enemySkin = 0xFFE8B89A.toInt(),
                bgFar = 0xFF2C1450.toInt(), bgMid = 0xFF1C0D38.toInt(), bgNear = 0xFF0C0720.toInt(),
                laser = 0xFFFF2050.toInt(), vent = 0xFFE0F0FF.toInt(),
            )
            base[Zone.LABS] = Palette(
                skyTop = 0xFF03100F.toInt(), skyBottom = 0xFF06201C.toInt(),
                wallTop = 0xFF12272A.toInt(), wallBottom = 0xFF0A1618.toInt(), panel = 0xFF1B3A3E.toInt(),
                neon = 0xFF3CFF8E.toInt(), neon2 = 0xFF18C8FF.toInt(),
                slab = 0xFF08110F.toInt(), slabEdge = 0xFF3CFF8E.toInt(), lamp = 0xFFD8FFF0.toInt(),
                outer = 0xFF0A1414.toInt(), outerLit = 0xFF3CFF8E.toInt(),
                door = 0xFF23403F.toInt(), doorFrame = 0xFF4E7C78.toInt(),
                enemyMain = 0xFFD5E0E0.toInt(), enemyAccent = 0xFF3CFF8E.toInt(), enemySkin = 0xFFD9B090.toInt(),
                bgFar = 0xFF0C2724.toInt(), bgMid = 0xFF081A18.toInt(), bgNear = 0xFF040E0D.toInt(),
                laser = 0xFF3CFF6A.toInt(), vent = 0xFFB0FFD8.toInt(),
            )
            base[Zone.METRO] = Palette(
                skyTop = 0xFF0B0A0C.toInt(), skyBottom = 0xFF1C1712.toInt(),
                wallTop = 0xFF2B2620.toInt(), wallBottom = 0xFF1A1612.toInt(), panel = 0xFF3A332A.toInt(),
                neon = 0xFFFFB020.toInt(), neon2 = 0xFF2F8CFF.toInt(),
                slab = 0xFF100E0C.toInt(), slabEdge = 0xFFFFB020.toInt(), lamp = 0xFFFFD08A.toInt(),
                outer = 0xFF121010.toInt(), outerLit = 0xFFFFB020.toInt(),
                door = 0xFF3B3A40.toInt(), doorFrame = 0xFF6B6A72.toInt(),
                enemyMain = 0xFF1C2A40.toInt(), enemyAccent = 0xFFFF7A1A.toInt(), enemySkin = 0xFFD8A888.toInt(),
                bgFar = 0xFF221D18.toInt(), bgMid = 0xFF161310.toInt(), bgNear = 0xFF0B0A08.toInt(),
                laser = 0xFFFF3030.toInt(), vent = 0xFFE8E4DC.toInt(),
            )
            base[Zone.MINES] = Palette(
                skyTop = 0xFF120C08.toInt(), skyBottom = 0xFF26180E.toInt(),
                wallTop = 0xFF33241A.toInt(), wallBottom = 0xFF1E140E.toInt(), panel = 0xFF4A3424.toInt(),
                neon = 0xFFFF9A2A.toInt(), neon2 = 0xFFFFE066.toInt(),
                slab = 0xFF140D08.toInt(), slabEdge = 0xFFB07030.toInt(), lamp = 0xFFFFC77A.toInt(),
                outer = 0xFF160F0A.toInt(), outerLit = 0xFFFF9A2A.toInt(),
                door = 0xFF4A3322.toInt(), doorFrame = 0xFF7A5634.toInt(),
                enemyMain = 0xFF34485C.toInt(), enemyAccent = 0xFFFFD21E.toInt(), enemySkin = 0xFFD0A080.toInt(),
                bgFar = 0xFF3A2618.toInt(), bgMid = 0xFF2A1A10.toInt(), bgNear = 0xFF170E08.toInt(),
                laser = 0xFFFF4020.toInt(), vent = 0xFFE8D8C0.toInt(),
            )
            base[Zone.MAGMA] = Palette(
                skyTop = 0xFF1A0503.toInt(), skyBottom = 0xFF4A0E04.toInt(),
                wallTop = 0xFF2E120C.toInt(), wallBottom = 0xFF1A0806.toInt(), panel = 0xFF45190E.toInt(),
                neon = 0xFFFF6A10.toInt(), neon2 = 0xFFFFD02A.toInt(),
                slab = 0xFF120605.toInt(), slabEdge = 0xFFFF6A10.toInt(), lamp = 0xFFFFB060.toInt(),
                outer = 0xFF160706.toInt(), outerLit = 0xFFFF6A10.toInt(),
                door = 0xFF4A2A22.toInt(), doorFrame = 0xFF7E4A38.toInt(),
                enemyMain = 0xFFB9B2AA.toInt(), enemyAccent = 0xFFFFC21A.toInt(), enemySkin = 0xFFD8A080.toInt(),
                bgFar = 0xFF3A0E06.toInt(), bgMid = 0xFF260804.toInt(), bgNear = 0xFF120302.toInt(),
                laser = 0xFFFFE040.toInt(), vent = 0xFFFF7A18.toInt(),
            )
            base[Zone.HELL] = Palette(
                skyTop = 0xFF1C0006.toInt(), skyBottom = 0xFF8A0A10.toInt(),
                wallTop = 0xFF22060C.toInt(), wallBottom = 0xFF100206.toInt(), panel = 0xFF3A0810.toInt(),
                neon = 0xFFFF2240.toInt(), neon2 = 0xFFFF9A1A.toInt(),
                slab = 0xFF0C0104.toInt(), slabEdge = 0xFFFF2240.toInt(), lamp = 0xFFFFA070.toInt(),
                outer = 0xFF18020A.toInt(), outerLit = 0xFFFF3A1A.toInt(),
                door = 0xFF4A1018.toInt(), doorFrame = 0xFF8A2A30.toInt(),
                enemyMain = 0xFF5E0A16.toInt(), enemyAccent = 0xFFFFB020.toInt(), enemySkin = 0xFFE0B0A0.toInt(),
                bgFar = 0xFF5A0610.toInt(), bgMid = 0xFF30030A.toInt(), bgNear = 0xFF120004.toInt(),
                laser = 0xFFFF3A1A.toInt(), vent = 0xFFFF5A10.toInt(),
            )
            base[Zone.VOID] = Palette(
                skyTop = 0xFF020206.toInt(), skyBottom = 0xFF10061E.toInt(),
                wallTop = 0xFF0C0A1A.toInt(), wallBottom = 0xFF06050E.toInt(), panel = 0xFF1A1636.toInt(),
                neon = 0xFFFF2BD6.toInt(), neon2 = 0xFF2BFFE0.toInt(),
                slab = 0xFF05040A.toInt(), slabEdge = 0xFFFF2BD6.toInt(), lamp = 0xFFE8D0FF.toInt(),
                outer = 0xFF06050C.toInt(), outerLit = 0xFF2BFFE0.toInt(),
                door = 0xFF1E1A3A.toInt(), doorFrame = 0xFF4A3E8A.toInt(),
                enemyMain = 0xFF0C0C12.toInt(), enemyAccent = 0xFF2BFFE0.toInt(), enemySkin = 0xFFC0B0D0.toInt(),
                bgFar = 0xFF1A0A30.toInt(), bgMid = 0xFF0E0620.toInt(), bgNear = 0xFF050210.toInt(),
                laser = 0xFFFF2BD6.toInt(), vent = 0xFF2BFFE0.toInt(),
            )
        }

        fun of(zone: Zone): Palette = base.getValue(zone)

        /**
         * The Void wears another zone's clothes, hue-cycling over time. Quantized and cached
         * so the glitch costs no allocations after warm-up.
         */
        fun void(zone: Zone, time: Float, floor: Int): Palette {
            val arr = voidCache.getOrPut(zone) { arrayOfNulls(VOID_STEPS) }
            val step = (((time * 0.12f + floor * 0.173f) % 1f + 1f) % 1f * VOID_STEPS).toInt().coerceIn(0, VOID_STEPS - 1)
            return arr[step] ?: run {
                val deg = step * 360f / VOID_STEPS
                val src = of(zone)
                val voidP = of(Zone.VOID)
                src.map { Col.hueShift(it, deg) }.let { p ->
                    Palette(
                        p.skyTop, p.skyBottom, Col.lerp(p.wallTop, voidP.wallTop, 0.5f), Col.lerp(p.wallBottom, voidP.wallBottom, 0.5f),
                        p.panel, p.neon, Col.hueShift(voidP.neon2, deg), p.slab, p.slabEdge, p.lamp, p.outer, p.outerLit, p.door,
                        p.doorFrame, p.enemyMain, p.enemyAccent, p.enemySkin, p.bgFar, p.bgMid, p.bgNear, p.laser, p.vent,
                    )
                }.also { arr[step] = it }
            }
        }
    }
}

/**
 * Reusable exact-size polygon buffers: build a polygon, then fill it before
 * building the next one. No allocation after warm-up.
 */
internal class Poly {
    private val scratch = FloatArray(256)
    private val exact = arrayOfNulls<FloatArray>(129)
    private var n = 0

    fun begin(): Poly {
        n = 0
        return this
    }

    fun add(x: Float, y: Float): Poly {
        if (n + 1 < scratch.size) {
            scratch[n] = x
            scratch[n + 1] = y
            n += 2
        }
        return this
    }

    fun fill(g: Gfx, color: Int) {
        if (n < 6) return
        val points = n / 2
        val arr = exact[points] ?: FloatArray(n).also { exact[points] = it }
        System.arraycopy(scratch, 0, arr, 0, n)
        g.fillPolygon(arr, color)
    }

    fun tri(g: Gfx, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) =
        begin().add(x1, y1).add(x2, y2).add(x3, y3).fill(g, color)

    fun quad(g: Gfx, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, color: Int) =
        begin().add(x1, y1).add(x2, y2).add(x3, y3).add(x4, y4).fill(g, color)
}
