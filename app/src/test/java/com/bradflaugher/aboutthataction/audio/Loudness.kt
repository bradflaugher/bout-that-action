package com.bradflaugher.aboutthataction.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Loudness meters for tests (48 kHz stereo, interleaved): ITU-R BS.1770-4 integrated loudness
 * (K-weighted, gated), momentary loudness, true peak (4x oversampled), DC and a click detector.
 * The same measures as `tools/audio/music_qa.py`, so the two agree to a tenth of a dB.
 */
internal object Loudness {
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0
        fun next(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private fun shelf() = Biquad(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585)
    private fun rlb() = Biquad(1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621)

    /** K-weighted energy (both channels) per [hop] frames, from frame [from] on. */
    private fun hopSums(x: FloatArray, from: Int, hop: Int): DoubleArray {
        val sl = shelf(); val sr = shelf(); val hl = rlb(); val hr = rlb()
        val n = x.size / 2 - from
        val out = DoubleArray(n / hop)
        for (i in 0 until out.size * hop) {
            val j = (from + i) * 2
            val l = hl.next(sl.next(x[j].toDouble()))
            val r = hr.next(sr.next(x[j + 1].toDouble()))
            out[i / hop] += l * l + r * r
        }
        return out
    }

    /** Mean power of every [w]-hop window. */
    private fun windows(h: DoubleArray, w: Int, hop: Int): DoubleArray =
        DoubleArray(max(0, h.size - w + 1)) { k -> var s = 0.0; for (j in 0 until w) s += h[k + j]; s / (w * hop) }

    private fun lu(z: Double) = -0.691 + 10 * log10(z + 1e-20)

    /** Integrated loudness in LUFS, from frame [from] on. */
    fun integrated(x: FloatArray, from: Int = 0): Double {
        val hop = SR / 10
        val z = windows(hopSums(x, from, hop), 4, hop).filter { lu(it) > -70 }
        if (z.isEmpty()) return -99.0
        val rel = lu(z.average()) - 10
        val g = z.filter { lu(it) > rel }
        return if (g.isEmpty()) -99.0 else lu(g.average())
    }

    /** Momentary loudness (400 ms windows every 50 ms), in LUFS. */
    fun momentary(x: FloatArray, from: Int = 0): DoubleArray {
        val hop = SR / 20
        return windows(hopSums(x, from, hop), 8, hop).map { lu(it) }.toDoubleArray()
    }

    // 4x oversampling: a 48-tap windowed sinc per phase.
    private const val TAPS = 12
    private val phases: Array<DoubleArray> = Array(4) { ph ->
        DoubleArray(TAPS * 2) { k ->
            val t = (k - TAPS + 1) - ph / 4.0
            val sinc = if (abs(t) < 1e-9) 1.0 else sin(PI * t) / (PI * t)
            val w = 0.5 + 0.5 * cos(PI * t / TAPS)
            sinc * w
        }
    }

    /** True peak in dBTP (both channels, 4x oversampled). */
    fun truePeak(x: FloatArray): Double {
        var best = 0.0
        val n = x.size / 2
        for (ch in 0..1) {
            for (i in TAPS until n - TAPS) {
                for (ph in 0 until 4) {
                    val h = phases[ph]
                    var acc = 0.0
                    for (k in 0 until TAPS * 2) acc += h[k] * x[(i + k - TAPS + 1) * 2 + ch]
                    if (abs(acc) > best) best = abs(acc)
                }
            }
        }
        return 20 * log10(max(best, 1e-9))
    }

    /** The larger channel's mean, in dBFS. */
    fun dc(x: FloatArray): Double {
        var l = 0.0
        var r = 0.0
        for (i in 0 until x.size / 2) {
            l += x[i * 2]; r += x[i * 2 + 1]
        }
        return 20 * log10(max(abs(l), abs(r)) / (x.size / 2) + 1e-12)
    }

    /**
     * Spikiness: the largest |second difference| of the mono mix against its RMS over the 4 ms
     * around it (a lone click scores ~20; drums 8-14). Quiet stretches are skipped.
     */
    fun spikes(x: FloatArray): DoubleArray {
        val n = x.size / 2
        val d2 = DoubleArray(n)
        for (i in 1 until n - 1) {
            val m0 = (x[(i - 1) * 2] + x[(i - 1) * 2 + 1]).toDouble()
            val m1 = (x[i * 2] + x[i * 2 + 1]).toDouble()
            val m2 = (x[(i + 1) * 2] + x[(i + 1) * 2 + 1]).toDouble()
            d2[i] = (m2 - 2 * m1 + m0) * 0.5
        }
        val k = SR * 4 / 1000
        val c = DoubleArray(n + 1)
        for (i in 0 until n) c[i + 1] = c[i] + d2[i] * d2[i]
        return DoubleArray(n) { i ->
            val a = max(0, i - k)
            val b = minOf(n, i + k + 1)
            val loc = sqrt((c[b] - c[a]) / (b - a)) + 1e-7
            if (loc < 3e-4) 0.0 else abs(d2[i]) / loc
        }
    }

    private const val SR = 48000
}
