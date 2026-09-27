package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.audio.AudioTestUtil.bandShare
import com.bradflaugher.aboutthataction.audio.AudioTestUtil.spectrum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DspTest {
    private val sr = AudioTestUtil.SR

    private fun renderDrum(v: DrumVoice, trigger: () -> Unit, seconds: Float = 0.5f): FloatArray {
        v.t = DrumTuning()
        v.level = 1f
        trigger()
        val n = (seconds * sr).toInt()
        val l = FloatArray(n)
        val r = FloatArray(n)
        val rev = FloatArray(n)
        var i = 0
        val cl = FloatArray(64)
        val cr = FloatArray(64)
        val cv = FloatArray(64)
        while (i < n) {
            val k = minOf(64, n - i)
            cl.fill(0f); cr.fill(0f); cv.fill(0f)
            v.render(cl, cr, cv, k, 1f)
            System.arraycopy(cl, 0, l, i, k)
            i += k
        }
        return l
    }

    @Test
    fun kickIsLowAndHatIsHigh() {
        val kick = Kick(sr)
        val k = renderDrum(kick, { kick.trigger(1f) })
        val hat = Hat(sr)
        val h = renderDrum(hat, { hat.trigger(1f, true) })
        val snare = Snare(sr)
        val s = renderDrum(snare, { snare.trigger(1f) })
        val ks = spectrum(k, 2048)
        val hs = spectrum(h, 2048)
        val ss = spectrum(s, 2048)
        val kickLow = bandShare(ks, 20.0, 200.0)
        val hatHigh = bandShare(hs, 5000.0, 24000.0)
        val snareMid = bandShare(ss, 150.0, 8000.0)
        println("kick <200Hz: %.2f  hat >5kHz: %.2f  snare 150-8k: %.2f".format(kickLow, hatHigh, snareMid))
        assertTrue("kick lacks low end ($kickLow)", kickLow > 0.5)
        assertTrue("hat lacks highs ($hatHigh)", hatHigh > 0.6)
        assertTrue("snare lacks body/crack ($snareMid)", snareMid > 0.5)
    }

    @Test
    fun polyBlepSawIsBandLimited() {
        // A 3.5 kHz saw: naive aliasing would put lots of energy in odd places; BLEP keeps
        // the spectrum dominated by true harmonics.
        val noise = Noise(1)
        val f = 3517f
        val dt = f / sr
        var p = 0f
        val x = FloatArray(16384) {
            val v = osc(Wave.SAW, p, dt, 0.5f, noise)
            p += dt; if (p >= 1f) p -= 1f
            v
        }
        val mag = spectrum(x, 16384)
        var harmonic = 0.0
        var total = 0.0
        val binHz = sr / 16384.0
        for (b in mag.indices) {
            val hz = b * binHz
            val h = hz / f
            total += mag[b] * mag[b]
            if (abs(h - Math.round(h)) * f < 40 && Math.round(h) >= 1) harmonic += mag[b] * mag[b]
        }
        assertTrue("saw aliasing too high (harmonic share ${harmonic / total})", harmonic / total > 0.97)
    }

    @Test
    fun svfLowpassAttenuatesHighs() {
        val f = Svf()
        f.setHz(500f, 0.707f, sr)
        val noise = Noise(3)
        val x = FloatArray(32768) { f.lp(noise.next()) }
        val mag = spectrum(x, 4096)
        val low = bandShare(mag, 0.0, 700.0)
        assertTrue("lowpass leaks ($low)", low > 0.5)
    }

    @Test
    fun masterBusNeverExceedsUnity() {
        val m = MasterBus(sr)
        val rng = Rng(9)
        for (i in 0 until 200000) {
            val big = (rng.nextFloat() * 2 - 1) * (if (i % 1000 < 50) 40f else 1.5f)
            assertTrue(m.process(big, -big))
            assertTrue(abs(m.outL) <= 1f && abs(m.outR) <= 1f)
        }
        // Non-finite input is swallowed and reported.
        assertTrue(!m.process(Float.NaN, 0f))
        assertEquals(0f, m.outL, 0f)
    }

    @Test
    fun reverbIsStableAndDecays() {
        val r = Reverb(sr, 2.4f, 1.1f, 5000f)
        r.process(1f)
        var maxLate = 0f
        for (i in 0 until sr * 10) {
            r.process(0f)
            assertTrue(r.outL.isFinite())
            if (i > sr * 8) maxLate = maxOf(maxLate, abs(r.outL))
        }
        assertTrue("reverb tail should die out ($maxLate)", maxLate < 1e-4f)
    }

    @Test
    fun rngIsDeterministic() {
        val a = Rng(42)
        val b = Rng(42)
        repeat(100) { assertEquals(a.nextLong(), b.nextLong()) }
        repeat(1000) { val v = a.nextFloat(); assertTrue(v >= 0f && v < 1f) }
    }
}
