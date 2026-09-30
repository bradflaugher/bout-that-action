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
    /** Tremolo depth 0..1 (amplitude LFO: surf guitar, vibraphone, Rhodes). */
    val trem: Float = 0f,
    val tremRate: Float = 6f,
    /**
     * > 0: a plucked string (Karplus-Strong) replaces osc 1: a noise burst rings round a
     * delay line one period long. This is its brightness, 0..1 (low: a nylon or pizzicato
     * pluck; high: a steel string, a banjo, a harpsichord).
     */
    val pluck: Float = 0f,
    /** How long a plucked string rings (seconds to -60 dB) while the key is held. */
    val ring: Float = 1.5f,
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
    private var tremPh = 0f
    private var tremGain = 1f
    private var sinceOn = 0
    private var vel = 1f

    // Plucked string: the delay line, its loop filter and the excitation burst still to play.
    private val string = DelayLine(MAX_STRING)
    private var ksZ = 0f
    private var exLp = 0f
    private var dcIn = 0f
    private var dcOut = 0f
    private var exciteLeft = 0

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
        if (p.pluck > 0f) {
            if (!active) {
                string.clear(); ksZ = 0f; dcIn = 0f; dcOut = 0f
            }
            exLp = 0f
            exciteLeft = (sr / target).toInt().coerceIn(2, MAX_STRING - 4)
        }
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
        var gain = pt.gain * vel
        var gainStep = 0f
        if (pt.trem > 0f) {
            // Ramped across the chunk so the LFO never zippers.
            tremPh += pt.tremRate * n / sr
            if (tremPh > 1f) tremPh -= 1f
            val tg = 1f - pt.trem * (0.5f + 0.5f * Dsp.sin01(tremPh))
            gainStep = gain * (tg - tremGain) / n
            gain *= tremGain
            tremGain = tg
        }
        val w1 = pt.wave1
        val w2 = pt.wave2
        if (pt.pluck > 0f) {
            renderString(l, r, off, n, pt, f, dt2, pw, gain, gainStep)
            return
        }
        for (i in 0 until n) {
            flt.next()
            gain += gainStep
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

    /** [render] for a plucked string: the delay-line loop stands in for osc 1. */
    private fun renderString(l: FloatArray, r: FloatArray, off: Int, n: Int, pt: Patch, f: Float, dt2: Float, pw: Float, g0: Float, gainStep: Float) {
        val b = 0.3f + 0.65f * pt.pluck.coerceIn(0f, 1f)
        // The one-pole loop filter delays the loop by about (1 - b) / b samples: take it off.
        val period = sr / f
        val delay = (period - (1f - b) / b).coerceIn(2f, (MAX_STRING - 4).toFloat())
        val fb = if (gate > 0) Dsp.decay60(pt.ring, sr).pow(period) else Dsp.decay60(minOf(pt.ring, pt.r), sr).pow(period)
        val exB = 0.25f + 0.7f * pt.pluck
        var gain = g0
        for (i in 0 until n) {
            flt.next()
            gain += gainStep
            val a = amp.next() * gain
            if (gate > 0 && --gate == 0) {
                amp.gateOff(); flt.gateOff()
            }
            var ex = 0f
            if (exciteLeft > 0) {
                exciteLeft--
                exLp += (noise.next() - exLp) * exB
                ex = exLp
            }
            ksZ += (string.read(delay) - ksZ) * b
            val y = ex + ksZ * fb
            string.write(y)
            // Block the burst's DC (it would ring round the loop), and bring the string up to
            // a full-scale oscillator's level.
            dcOut = y - dcIn + 0.997f * dcOut
            dcIn = y
            var x = dcOut * STRING_GAIN
            if (pt.osc2Level > 0f) {
                p2 += dt2; if (p2 >= 1f) p2 -= 1f
                x += pt.osc2Level * osc(pt.wave2, p2, dt2, pw, noise)
            }
            x = svfL.lp(x)
            if (pt.drive > 0f) x = Dsp.tanh(x * pt.driveGain) * pt.driveComp
            x *= a
            l[off + i] += x
            r[off + i] += x
        }
        if (!active) {
            svfL.reset(); svfR.reset()
        }
    }

    fun sanitize() {
        svfL.sanitize(); svfR.sanitize()
        ksZ = Dsp.flush(ksZ); dcOut = Dsp.flush(dcOut)
    }

    companion object {
        /** Longest string period: 4096-sample line, so down to ~12 Hz at 48 kHz. */
        const val MAX_STRING = 4000
        private const val STRING_GAIN = 3f
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

/**
 * A stadium crowd: decorrelated noise through two vowel-ish formants per side, with a slow
 * random murmur. [render]'s level carries the swell (the sequencer lifts it into fills and
 * slams it up on the crash).
 */
internal class Crowd(private val sr: Int) {
    private val nl = Noise(2718)
    private val nr = Noise(31415)
    private val nm = Noise(1618)
    private val f1l = Svf().apply { setHz(620f, 0.9f, sr) }
    private val f2l = Svf().apply { setHz(1550f, 1.1f, sr) }
    private val f1r = Svf().apply { setHz(700f, 0.9f, sr) }
    private val f2r = Svf().apply { setHz(1400f, 1.1f, sr) }
    private val murmur = OnePole().apply { setHz(5f, sr) }
    private var level = 0f

    fun render(l: FloatArray, r: FloatArray, n: Int, target: Float) {
        if (target <= 0f && level < 1e-4f) return
        val step = (target - level) / n
        for (i in 0 until n) {
            level += step
            val m = 1f + 6f * murmur.lp(nm.next())
            val g = level * m
            val xl = nl.next()
            val xr = nr.next()
            l[i] += (f1l.bp(xl) + 0.7f * f2l.bp(xl)) * g
            r[i] += (f1r.bp(xr) + 0.7f * f2r.bp(xr)) * g
        }
    }
}

/**
 * Night jungle: a cricket on each side (chirp trains of a pure ~4.4 kHz tone) over a thin
 * cicada hiss that breathes slowly. Quiet by design; it sits under a sneak mix.
 */
internal class Jungle(private val sr: Int) {
    private val noise = Noise(8086)
    private val hiss = Svf().apply { setHz(6500f, 3f, sr) }
    private var t = 0.0
    private var pl = 0f
    private var pr = 0f

    fun render(l: FloatArray, r: FloatArray, n: Int, level: Float) {
        if (level <= 0f) return
        val inv = 1f / sr
        for (i in 0 until n) {
            t += inv
            val tf = t.toFloat()
            // Chirps: 4 pulses at 30 Hz, bursts every ~0.9 s (the right cricket answers late).
            val cl = chirp(tf)
            val cr = chirp(tf + 0.41f) * (0.6f + 0.4f * Dsp.sin01(tf * 0.05f))
            pl += 4400f * inv; if (pl >= 1f) pl -= 1f
            pr += 4650f * inv; if (pr >= 1f) pr -= 1f
            val breath = 0.5f + 0.5f * Dsp.sin01(tf * 0.13f)
            val h = hiss.bp(noise.next()) * 0.35f * breath
            l[i] += (Dsp.sin01(pl) * cl * 0.25f + h) * level
            r[i] += (Dsp.sin01(pr) * cr * 0.25f + h) * level
        }
    }

    private fun chirp(x: Float): Float {
        val burst = x / 0.9f
        val inBurst = (burst - burst.toInt()) * 0.9f // seconds into this burst
        if (inBurst > 0.13f) return 0f
        val p = inBurst * 30f
        val ph = p - p.toInt()
        return if (ph < 0.5f) Dsp.sin01(ph) else 0f
    }
}

/**
 * A dusty record under a boom-bap beat: sparse crackles (sharp, high-passed clicks, some big
 * enough to pop) over a thin surface hiss.
 */
internal class Vinyl(private val sr: Int) {
    private val noise = Noise(3303)
    private val hp = OnePole().apply { setHz(2200f, sr) }
    private val hiss = Svf().apply { setHz(6000f, 0.6f, sr) }
    private var click = 0f
    private var pan = 0f
    private val clickCoef = Dsp.decay60(0.006f, sr)
    /** Chance per sample of a crackle (about 12 a second). */
    private val threshold = 1f - 2f * 12f / sr

    fun render(l: FloatArray, r: FloatArray, n: Int, level: Float) {
        if (level <= 0f) return
        for (i in 0 until n) {
            if (noise.next() > threshold) {
                val k = noise.next()
                click = (if (k < 0f) -1f else 1f) * (0.35f + 0.65f * k * k)
                pan = noise.next() * 0.6f
            }
            val c = hp.hp(click) * 0.8f
            click *= clickCoef
            val h = hiss.bp(noise.next()) * 0.06f
            l[i] += (c * (1f - maxOf(0f, pan)) + h) * level
            r[i] += (c * (1f + minOf(0f, pan)) + h) * level
        }
    }
}
