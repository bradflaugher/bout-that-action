package com.bradflaugher.aboutthataction.audio

import kotlin.math.max

internal enum class FilterMode { NONE, LOW, BAND, HIGH }

/**
 * A one-shot sound-design voice: two oscillators (mix, ring or phase modulation) plus
 * noise → sweeping multimode filter → drive/crush → AHD envelope with tremolo/vibrato.
 * Richer effects layer several voices; [delay] staggers them in time.
 *
 * Recipes set the public fields after [SfxBank.alloc] (which resets them) and call [start].
 */
internal class SfxVoice(private val sr: Int, seed: Int) {
    private val noiseGen = Noise(seed)
    private val svf = Svf()

    // ---- Parameters (reset by [reset]) --------------------------------------------------
    var wave = Wave.SINE
    var wave2 = Wave.SINE
    var level1 = 1f
    var level2 = 0f
    var ratio2 = 1f
    /** Phase-modulation index of osc 2 onto osc 1 (sine/triangle carrier). */
    var fm = 0f
    var ring = false
    var noise = 0f
    var pw = 0.5f
    var f0 = 440f
    var f1 = 440f
    var sweep = 0.1f
    var attack = 0.002f
    var hold = 0f
    var decay = 0.2f
    var filter = FilterMode.NONE
    var cut0 = 18000f
    var cut1 = 18000f
    var cutTime = 0.1f
    var q = 0.707f
    var drive = 0f
    var crush = 0
    var tremRate = 0f
    var tremDepth = 0f
    var vibRate = 0f
    var vibDepth = 0f
    var pan = 0f
    var gain = 0.3f
    var reverb = 0.1f
    var delay = 0f
    var priority = 1f

    // ---- State ------------------------------------------------------------------------
    var active = false; private set
    private var wait = 0
    private var stage = 0
    private var env = 0f
    private var aInc = 0f
    private var holdLeft = 0
    private var dCoef = 0f
    private var f = 440f
    private var fMul = 1f
    private var sweepLeft = 0
    private var cut = 18000f
    private var cMul = 1f
    private var cutLeft = 0
    private var p1 = 0f
    private var p2 = 0f
    private var lfo = 0f
    private var lfo2 = 0f
    private var ctl = 0
    private var gl = 0.7f
    private var gr = 0.7f
    private var held = 0f
    private var crushCount = 0
    private var driveGain = 1f
    private var driveComp = 1f
    var age = 0L; private set

    /** Current loudness estimate used for voice stealing. */
    val weight: Float get() = if (!active) -1f else if (wait > 0 || stage < 2) priority + 1f else env * priority

    fun reset() {
        wave = Wave.SINE; wave2 = Wave.SINE; level1 = 1f; level2 = 0f; ratio2 = 1f; fm = 0f; ring = false
        noise = 0f; pw = 0.5f; f0 = 440f; f1 = 440f; sweep = 0.1f; attack = 0.002f; hold = 0f; decay = 0.2f
        filter = FilterMode.NONE; cut0 = 18000f; cut1 = 18000f; cutTime = 0.1f; q = 0.707f; drive = 0f; crush = 0
        tremRate = 0f; tremDepth = 0f; vibRate = 0f; vibDepth = 0f; pan = 0f; gain = 0.3f; reverb = 0.1f
        delay = 0f; priority = 1f
    }

    fun start(age: Long) {
        this.age = age
        active = true
        wait = (delay * sr).toInt()
        stage = 0
        env = 0f
        aInc = 1f / max(1f, attack * sr)
        holdLeft = (hold * sr).toInt()
        dCoef = Dsp.decay60(max(decay, 0.003f), sr)
        f = f0
        sweepLeft = (sweep * sr).toInt()
        fMul = Dsp.expStep(f0, f1, sweepLeft)
        cut = cut0
        cutLeft = (cutTime * sr).toInt()
        cMul = Dsp.expStep(cut0, cut1, cutLeft)
        p1 = 0f; p2 = 0f; lfo = 0f; lfo2 = 0f; ctl = 0; held = 0f; crushCount = 0
        gl = Dsp.panL(pan); gr = Dsp.panR(pan)
        driveGain = 1f + drive * 3f
        driveComp = if (drive > 0f) 1f / Dsp.tanh(minOf(driveGain, 3f)) else 1f
        svf.reset()
    }

    fun stop() {
        active = false
    }

    fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int) {
        if (!active) return
        var i = 0
        if (wait > 0) {
            if (wait >= n) {
                wait -= n; return
            }
            i = wait; wait = 0
        }
        val inv = 1f / sr
        var trem = 1f
        var vib = 1f
        while (i < n) {
            if (ctl <= 0) {
                ctl = 16
                if (filter != FilterMode.NONE) svf.set(Dsp.svfG(cut, sr), q)
                if (tremRate > 0f) {
                    lfo += 16f * inv * tremRate
                    if (lfo > 1f) lfo -= 1f
                    trem = 1f - tremDepth * (0.5f + 0.5f * Dsp.sin01(lfo))
                }
                if (vibRate > 0f) {
                    lfo2 += 16f * inv * vibRate
                    if (lfo2 > 1f) lfo2 -= 1f
                    vib = 1f + vibDepth * Dsp.sin01(lfo2)
                }
            }
            ctl--
            when (stage) {
                0 -> {
                    env += aInc
                    if (env >= 1f) {
                        env = 1f; stage = 1
                    }
                }
                1 -> if (holdLeft-- <= 0) stage = 2
                else -> {
                    env *= dCoef
                    if (env < 3e-5f) {
                        active = false; return
                    }
                }
            }
            if (sweepLeft > 0) {
                f *= fMul; sweepLeft--
            }
            if (cutLeft > 0) {
                cut *= cMul; cutLeft--
            }
            val fn = f * vib
            val dt = fn * inv
            p1 += dt; if (p1 >= 1f) p1 -= 1f
            var o2 = 0f
            if (level2 > 0f || fm > 0f || ring) {
                val dt2 = dt * ratio2
                p2 += dt2; if (p2 >= 1f) p2 -= 1f
                o2 = osc(wave2, p2, dt2, 0.5f, noiseGen)
            }
            var x = 0f
            if (level1 > 0f) {
                val o1 = if (fm > 0f) Dsp.sin01(p1 + fm * o2 * 0.159f) else osc(wave, p1, dt, pw, noiseGen)
                x = if (ring) o1 * o2 * level1 else o1 * level1
            }
            if (level2 > 0f && !ring) x += o2 * level2
            if (noise > 0f) x += noiseGen.next() * noise
            when (filter) {
                FilterMode.LOW -> x = svf.lp(x)
                FilterMode.BAND -> x = svf.bp(x)
                FilterMode.HIGH -> x = svf.hp(x)
                FilterMode.NONE -> {}
            }
            if (crush > 1) {
                if (crushCount-- <= 0) {
                    crushCount = crush - 1; held = x
                }
                x = held
            }
            if (drive > 0f) x = Dsp.tanh(x * driveGain) * driveComp
            x *= env * gain * trem
            l[i] += x * gl
            r[i] += x * gr
            rev[i] += x * reverb
            i++
        }
    }

    fun sanitize() = svf.sanitize()
}

/** Fixed pool of SFX voices with quietest-first stealing. */
internal class SfxBank(sr: Int, size: Int = 24) {
    val voices = Array(size) { SfxVoice(sr, 0x51f0 + it * 7919) }
    private var counter = 0L

    fun alloc(): SfxVoice {
        var pick: SfxVoice? = null
        for (v in voices) if (!v.active) {
            pick = v; break
        }
        if (pick == null) {
            var best = Float.MAX_VALUE
            for (v in voices) {
                val w = v.weight - (counter - v.age) * 1e-4f
                if (w < best) {
                    best = w; pick = v
                }
            }
        }
        val v = pick!!
        v.stop()
        v.reset()
        return v
    }

    fun start(v: SfxVoice) = v.start(++counter)

    val activeCount: Int get() = voices.count { it.active }

    fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int) {
        l.fill(0f, 0, n); r.fill(0f, 0, n); rev.fill(0f, 0, n)
        for (v in voices) v.render(l, r, rev, n)
    }

    fun silence() = voices.forEach { it.stop() }
    fun sanitize() = voices.forEach { it.sanitize() }
}
