package com.bradflaugher.aboutthataction.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal enum class Wave { SINE, TRIANGLE, SAW, SQUARE, PULSE, NOISE }

/** One band-limited (PolyBLEP) oscillator sample at phase [p] (cycles, 0..1). */
internal fun osc(w: Wave, p: Float, dt: Float, pw: Float, n: Noise): Float = when (w) {
    Wave.SINE -> Dsp.sin01(p)
    Wave.TRIANGLE -> if (p < 0.5f) 4f * p - 1f else 3f - 4f * p
    Wave.SAW -> 2f * p - 1f - Dsp.polyBlep(p, dt)
    Wave.SQUARE, Wave.PULSE -> {
        val width = if (w == Wave.SQUARE) 0.5f else pw
        var v = if (p < width) 1f else -1f
        v += Dsp.polyBlep(p, dt)
        var q = p - width
        if (q < 0f) q += 1f
        v - Dsp.polyBlep(q, dt)
    }
    Wave.NOISE -> n.next()
}

/** A synth voice preset. Immutable; songs build these once up front. */
internal class Patch(
    val wave1: Wave = Wave.SAW,
    val wave2: Wave = Wave.SAW,
    /** Osc 2 offset in semitones (plus [detune]). */
    val osc2Semi: Float = 0f,
    val osc2Level: Float = 0f,
    /** Detune in semitones for osc 2 / the supersaw spread. */
    val detune: Float = 0.1f,
    /** Three copies of [wave1] spread across the stereo field. */
    val supersaw: Boolean = false,
    val sub: Float = 0f,
    val noise: Float = 0f,
    val pw: Float = 0.5f,
    val pwm: Float = 0f,
    val cutoff: Float = 3000f,
    val q: Float = 0.8f,
    /** Filter envelope depth in octaves. */
    val envAmt: Float = 0f,
    val keyTrack: Float = 0.3f,
    val a: Float = 0.004f,
    val d: Float = 0.3f,
    val s: Float = 0.8f,
    val r: Float = 0.15f,
    val fa: Float = 0.002f,
    val fd: Float = 0.25f,
    val fs: Float = 0f,
    val fr: Float = 0.2f,
    val drive: Float = 0f,
    val glide: Float = 0f,
    /** Vibrato depth in semitones (fades in after the note starts). */
    val vibrato: Float = 0f,
    val vibRate: Float = 5.5f,
    val gain: Float = 0.3f,
    /** Sample-and-hold decimation factor for a chiptune crunch (0/1 = off). */
    val crush: Int = 0,
    /** How strongly music intensity opens the filter (0 = ignores intensity). */
    val bright: Float = 1f,
) {
    val driveGain = 1f + drive * 3f
    val driveComp = if (drive > 0f) 1f / Dsp.tanh(min(driveGain, 3f)) * (1f / (1f + drive * 0.5f)) else 1f

    companion object {
        val DEFAULT = Patch()
    }
}

/** Polyphonic-capable subtractive voice: 1–3 oscillators + sub + noise → ZDF lowpass → VCA. */
internal class SynthVoice(private val sr: Int, seed: Int) {
    private val noise = Noise(seed)
    var patch = Patch.DEFAULT
    private val amp = Adsr(sr)
    private val flt = Adsr(sr)
    private val svfL = Svf()
    private val svfR = Svf()
    var note = -1; private set
    var age = 0L; private set
    private var gate = 0
    private var hz = 440f
    private var target = 440f
    private var p1 = 0f
    private var p2 = 0f
    private var p3 = 0f
    private var ps = 0f
    private var lfo = 0f
    private var sinceOn = 0
    private var vel = 1f

    val active: Boolean get() = amp.active
    val gated: Boolean get() = gate > 0
    val level: Float get() = amp.level

    fun noteOn(n: Int, velocity: Float, gateSamples: Int, legato: Boolean, age: Long) {
        note = n
        vel = velocity
        this.age = age
        target = Dsp.midiToHz(n.toFloat())
        gate = max(1, gateSamples)
        if (legato && active && patch.glide > 0f) return // glide, don't retrigger
        if (!active) {
            hz = target
            p1 = abs(noise.next()); p2 = abs(noise.next()); p3 = abs(noise.next()); ps = 0f
            svfL.reset(); svfR.reset()
        } else if (patch.glide <= 0f) {
            hz = target
        }
        val p = patch
        amp.set(p.a, p.d, p.s, p.r)
        flt.set(p.fa, p.fd, p.fs, p.fr)
        amp.gateOn(); flt.gateOn()
        sinceOn = 0
    }

    fun release() {
        gate = 0; amp.gateOff(); flt.gateOff()
    }

    fun kill() {
        gate = 0; amp.reset(); flt.reset(); svfL.reset(); svfR.reset()
    }

    /** Accumulate [n] samples into [l]/[r]. Control-rate parameters are updated once per call. */
    fun render(l: FloatArray, r: FloatArray, off: Int, n: Int, pitchMul: Float, cutMul: Float) {
        val pt = patch
        if (pt.glide > 0f && hz != target) {
            val k = 1f - Dsp.decay60(pt.glide, sr).pow(n)
            hz *= (target / hz).pow(k)
        } else {
            hz = target
        }
        sinceOn += n
        lfo += pt.vibRate * n / sr
        if (lfo > 1f) lfo -= 1f
        var f = hz * pitchMul
        if (pt.vibrato > 0f) {
            val depth = min(1f, sinceOn / (0.3f * sr))
            f *= 1f + 0.05776f * pt.vibrato * depth * Dsp.sin01(lfo)
        }
        val inv = 1f / sr
        val dt1: Float
        val dt2: Float
        val dt3: Float
        val det = 1f + 0.05776f * pt.detune
        if (pt.supersaw) {
            dt1 = f / det * inv; dt2 = f * det * inv; dt3 = f * inv
        } else {
            dt1 = f * inv; dt2 = f * Dsp.semis(pt.osc2Semi) * det * inv; dt3 = 0f
        }
        val dts = f * 0.5f * inv
        val pw = if (pt.pwm > 0f) (pt.pw + pt.pwm * Dsp.sin01(lfo * 0.37f)).coerceIn(0.05f, 0.95f) else pt.pw
        val cut = pt.cutoff * cutMul * 2f.pow(pt.envAmt * flt.level) * (f / 261.6f).pow(pt.keyTrack)
        val g = Dsp.svfG(cut, sr)
        svfL.set(g, pt.q)
        if (pt.supersaw) svfR.set(g, pt.q)
        val gain = pt.gain * vel
        val w1 = pt.wave1
        val w2 = pt.wave2
        for (i in 0 until n) {
            flt.next()
            val a = amp.next() * gain
            if (gate > 0 && --gate == 0) {
                amp.gateOff(); flt.gateOff()
            }
            p1 += dt1; if (p1 >= 1f) p1 -= 1f
            p2 += dt2; if (p2 >= 1f) p2 -= 1f
            var extra = 0f
            if (pt.sub > 0f) {
                ps += dts; if (ps >= 1f) ps -= 1f
                extra += pt.sub * Dsp.sin01(ps)
            }
            if (pt.noise > 0f) extra += pt.noise * noise.next()
            if (pt.supersaw) {
                p3 += dt3; if (p3 >= 1f) p3 -= 1f
                val c = osc(w1, p3, dt3, pw, noise) * 0.7f + extra
                var xl = svfL.lp(osc(w1, p1, dt1, pw, noise) + c)
                var xr = svfR.lp(osc(w1, p2, dt2, pw, noise) + c)
                if (pt.drive > 0f) {
                    xl = Dsp.tanh(xl * pt.driveGain) * pt.driveComp
                    xr = Dsp.tanh(xr * pt.driveGain) * pt.driveComp
                }
                l[off + i] += xl * a
                r[off + i] += xr * a
            } else {
                var x = osc(w1, p1, dt1, pw, noise)
                if (pt.osc2Level > 0f) x += pt.osc2Level * osc(w2, p2, dt2, pw, noise)
                x = svfL.lp(x + extra)
                if (pt.drive > 0f) x = Dsp.tanh(x * pt.driveGain) * pt.driveComp
                x *= a
                l[off + i] += x
                r[off + i] += x
            }
        }
        if (!active) {
            svfL.reset(); svfR.reset()
        }
    }

    fun sanitize() {
        svfL.sanitize(); svfR.sanitize()
    }
}

/** A voice pool playing one patch. One voice = mono/legato (glides when notes overlap). */
internal class Instrument(private val sr: Int, voices: Int, seed: Int) {
    private val pool = Array(voices) { SynthVoice(sr, seed * 31 + it + 1) }
    private var counter = 0L
    private val tmpL = FloatArray(MAX_CHUNK)
    private val tmpR = FloatArray(MAX_CHUNK)
    private var held = 0f
    private var heldR = 0f
    private var crushCount = 0
    var patch: Patch = Patch.DEFAULT
        set(value) {
            field = value
            for (v in pool) v.patch = value
        }

    fun noteOn(note: Int, vel: Float, gateSamples: Int) {
        counter++
        if (pool.size == 1) {
            val v = pool[0]
            v.noteOn(note, vel, gateSamples, legato = v.gated, age = counter)
            return
        }
        var pick = pool[0]
        var best = Float.MAX_VALUE
        for (v in pool) {
            if (!v.active) {
                pick = v; break
            }
            // Prefer stealing released, quiet, old voices.
            val score = v.level + (if (v.gated) 1f else 0f) + (counter - v.age) * -0.001f
            if (score < best) {
                best = score; pick = v
            }
        }
        pick.noteOn(note, vel, gateSamples, legato = false, age = counter)
    }

    fun releaseAll() = pool.forEach { it.release() }
    fun kill() = pool.forEach { it.kill() }
    fun sanitize() = pool.forEach { it.sanitize() }

    /** Render [n] samples (n <= MAX_CHUNK) into [l]/[r] (overwrites). */
    fun render(l: FloatArray, r: FloatArray, n: Int, pitchMul: Float, cutMul: Float) {
        l.fill(0f, 0, n); r.fill(0f, 0, n)
        for (v in pool) if (v.active) v.render(l, r, 0, n, pitchMul, cutMul)
        val c = patch.crush
        if (c > 1) {
            for (i in 0 until n) {
                if (crushCount-- <= 0) {
                    crushCount = c - 1; held = l[i]; heldR = r[i]
                }
                l[i] = held; r[i] = heldR
            }
        }
    }

    companion object {
        const val MAX_CHUNK = 64
    }
}

/** Stereo wind: two band-passed noise streams with slow gusting cutoff and level. */
internal class Wind(private val sr: Int) {
    private val nl = Noise(1234)
    private val nr = Noise(98765)
    private val fl = Svf()
    private val fr = Svf()
    private var t = 0.0

    fun render(l: FloatArray, r: FloatArray, n: Int, level: Float) {
        if (level <= 0f) return
        t += n.toDouble() / sr
        val tf = t.toFloat()
        val gust = 0.55f + 0.45f * Dsp.sin01(tf * 0.07f) * Dsp.sin01(tf * 0.023f + 0.3f)
        fl.setHz(380f + 900f * gust + 200f * Dsp.sin01(tf * 0.31f), 1.6f, sr)
        fr.setHz(420f + 850f * gust + 200f * Dsp.sin01(tf * 0.27f + 0.5f), 1.6f, sr)
        val g = level * (0.35f + 0.65f * gust)
        for (i in 0 until n) {
            l[i] += fl.bp(nl.next()) * g
            r[i] += fr.bp(nr.next()) * g
        }
    }
}

/** Helicopter-ish rotor thump, retriggered on every 16th by the sequencer. */
internal class Rotor(private val sr: Int) {
    private val noise = Noise(4242)
    private val lp = Svf().apply { setHz(260f, 1.2f, sr) }
    private var env = 0f
    private var coef = Dsp.decay60(0.09f, sr)
    private var phase = 0f
    private var vel = 0f

    fun trigger(v: Float) {
        vel = v; env = 1f
    }

    fun render(l: FloatArray, r: FloatArray, n: Int, level: Float, pan: Float) {
        if (env < 1e-4f || level <= 0f) return
        val gl = Dsp.panL(pan) * level * vel
        val gr = Dsp.panR(pan) * level * vel
        for (i in 0 until n) {
            phase += 48f / sr; if (phase >= 1f) phase -= 1f
            val x = (lp.lp(noise.next()) * 2.2f + Dsp.sin01(phase) * 0.6f) * env
            env *= coef
            l[i] += x * gl
            r[i] += x * gr
        }
    }
}

/** Transition riser: resonant band-passed noise and a rising tone driven by [progress] 0..1. */
internal class Riser(private val sr: Int) {
    private val noise = Noise(777)
    private val bp = Svf()
    private var phase = 0f
    private var level = 0f
    var progress = -1f

    fun render(l: FloatArray, r: FloatArray, off: Int, n: Int, gain: Float) {
        val on = progress in 0f..1f
        if (!on && level < 1e-4f) return
        val p = if (on) progress else 1f
        val targetLevel = if (on) p * p * gain else 0f
        bp.setHz(300f * 30f.pow(p), 2.5f, sr)
        val hz = 180f * 8f.pow(p)
        val step = (targetLevel - level) / n
        for (i in 0 until n) {
            level += if (on) step else -level * 0.02f
            phase += hz / sr; if (phase >= 1f) phase -= 1f
            val x = (bp.bp(noise.next()) * 1.6f + Dsp.sin01(phase) * 0.12f) * level
            l[off + i] += x
            r[off + i] += x
        }
    }
}
