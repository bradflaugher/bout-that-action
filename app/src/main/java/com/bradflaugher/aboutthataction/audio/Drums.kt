package com.bradflaugher.aboutthataction.audio

import kotlin.math.max

/** Per-song drum kit voicing. */
internal class DrumTuning(
    val kickHi: Float = 170f,
    val kickLo: Float = 48f,
    val kickPitchDecay: Float = 0.04f,
    val kickDecay: Float = 0.42f,
    val kickClick: Float = 0.5f,
    val kickDrive: Float = 0.2f,
    val snareTone: Float = 185f,
    val snareNoiseHz: Float = 4200f,
    val snareDecay: Float = 0.2f,
    val snareToneMix: Float = 0.55f,
    val clapHz: Float = 1150f,
    val clapDecay: Float = 0.17f,
    val hatTone: Float = 1f,
    val hatDecay: Float = 0.045f,
    val openDecay: Float = 0.32f,
    val tomHz: Float = 105f,
    val percHz: Float = 520f,
    val percRatio: Float = 1.41f,
    val percDecay: Float = 0.2f,
    val percFm: Float = 2.5f,
    val percNoise: Float = 0.25f,
    val crashDecay: Float = 1.7f,
    val drive: Float = 0f,
    val crush: Int = 0,
    val kickLevel: Float = 1f,
    val snareLevel: Float = 0.8f,
    val clapLevel: Float = 0.55f,
    val hatLevel: Float = 0.36f,
    val tomLevel: Float = 0.6f,
    val percLevel: Float = 0.35f,
    val crashLevel: Float = 0.3f,
    val snareVerb: Float = 0.3f,
    /** > 0: an 80s gated-reverb snare, a dense noise tail held this long and then cut dead. */
    val snareGate: Float = 0f,
    /** Sleigh bells / tambourine: jingle pitch, ring time, how much of it is shaker noise. */
    val jingleHz: Float = 5200f,
    val jingleDecay: Float = 0.16f,
    val jingleNoise: Float = 0.5f,
    val jingleLevel: Float = 0.3f,
    /** Tom ring time and how far its pitch drops (low: a hand drum's slap; high: a big tom's boom). */
    val tomDecay: Float = 0.42f,
    val tomBend: Float = 0.7f,
    /** > 0: the whole kit through a lowpass at this cutoff (Hz), like drums sampled off a record. */
    val busCutoff: Float = 0f,
)

/** Base for one-shot drum voices rendering into stereo + reverb-send buffers. */
internal abstract class DrumVoice(protected val sr: Int) {
    var t = DrumTuning()
    protected var vel = 0f
    var active = false; protected set
    var pan = 0f
    var level = 1f
    var send = 0f

    abstract fun trigger(v: Float)

    /** Cut it dead (only ever while its output is faded to nothing). */
    fun silence() {
        active = false
    }
    abstract fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float)

    protected fun out(l: FloatArray, r: FloatArray, rev: FloatArray, i: Int, x: Float) {
        val y = x * level
        l[i] += y * (1f - max(0f, pan))
        r[i] += y * (1f + minOf(0f, pan))
        rev[i] += y * send
    }
}

/** Sine kick with exponential pitch sweep, a noisy click transient and optional drive. */
internal class Kick(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(11)
    private val hp = OnePole().apply { setHz(2500f, sr) }
    private var phase = 0f
    private var pEnv = 0f
    private var aEnv = 0f
    private var cEnv = 0f
    private var hold = 0
    private var pCoef = 0f
    private var aCoef = 0f
    private val cCoef = Dsp.decay60(0.012f, sr)

    override fun trigger(v: Float) {
        vel = v; phase = 0f; pEnv = 1f; aEnv = 1f; cEnv = 1f; active = true
        hold = (0.012f * sr).toInt()
        pCoef = Dsp.decay60(t.kickPitchDecay * 6.9f, sr) // pitchDecay is a time constant
        aCoef = Dsp.decay60(t.kickDecay, sr)
    }

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        val hi = t.kickHi * pitchMul
        val lo = t.kickLo * pitchMul
        val drive = 1f + t.kickDrive * 4f
        val comp = 1f / Dsp.tanh(drive)
        for (i in 0 until n) {
            val f = lo + (hi - lo) * pEnv
            phase += f / sr; if (phase >= 1f) phase -= 1f
            var x = Dsp.sin01(phase) * aEnv + hp.hp(noise.next()) * cEnv * t.kickClick
            x = Dsp.tanh(x * drive) * comp
            out(l, r, rev, i, x * vel)
            pEnv *= pCoef
            cEnv *= cCoef
            if (hold > 0) hold-- else aEnv *= aCoef
        }
        if (aEnv < 1e-4f) active = false
    }
}

/** Two detuned sines for the body plus high-passed, band-emphasised noise for the wires. */
internal class Snare(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(22)
    private val hp = OnePole().apply { setHz(900f, sr) }
    private val lp = OnePole().apply { setHz(8000f, sr) }
    private val bp = Svf()
    private var p1 = 0f
    private var p2 = 0f
    private var tEnv = 0f
    private var nEnv = 0f
    private var pEnv = 0f
    private var tCoef = 0f
    private var nCoef = 0f
    private val pCoef = Dsp.decay60(0.05f, sr)
    private val gHp = OnePole().apply { setHz(500f, sr) }
    private val gLp = OnePole().apply { setHz(4500f, sr) }
    private var gEnv = 0f
    private var gHold = 0
    private var gCoef = 1f
    private val gCut = Dsp.decay60(0.03f, sr)

    override fun trigger(v: Float) {
        vel = v; tEnv = 1f; nEnv = 1f; pEnv = 1f; p1 = 0f; p2 = 0f; active = true
        tCoef = Dsp.decay60(0.11f, sr)
        nCoef = Dsp.decay60(t.snareDecay, sr)
        bp.setHz(t.snareNoiseHz, 0.9f, sr)
        if (t.snareGate > 0f) {
            gEnv = 0.6f; gHold = (t.snareGate * sr).toInt(); gCoef = Dsp.decay60(t.snareGate * 4f, sr)
        } else {
            gEnv = 0f; gHold = 0
        }
    }

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        val f = t.snareTone * pitchMul
        val tm = t.snareToneMix
        for (i in 0 until n) {
            val bend = 1f + 0.5f * pEnv
            p1 += f * bend / sr; if (p1 >= 1f) p1 -= 1f
            p2 += f * 1.62f * bend / sr; if (p2 >= 1f) p2 -= 1f
            val tone = (Dsp.sin01(p1) + 0.6f * Dsp.sin01(p2)) * tEnv * tm
            val nz = noise.next()
            var wires = (lp.lp(hp.hp(nz)) * 0.8f + bp.bp(nz) * 0.9f) * nEnv
            if (gEnv > 0f) {
                wires += gLp.lp(gHp.hp(nz)) * gEnv
                if (gHold > 0) {
                    gHold--; gEnv *= gCoef
                } else {
                    gEnv *= gCut; if (gEnv < 1e-4f) gEnv = 0f
                }
            }
            out(l, r, rev, i, (tone + wires) * vel)
            tEnv *= tCoef; nEnv *= nCoef; pEnv *= pCoef
        }
        if (nEnv < 1e-4f && tEnv < 1e-4f && gEnv <= 0f) active = false
    }
}

/** 909-style clap: three quick band-passed noise bursts and a tail. */
internal class Clap(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(33)
    private val bp = Svf()
    private var time = 0
    private var burst = 0f
    private val burstCoef = Dsp.decay60(0.009f, sr)
    private var tail = 0f
    private var tailCoef = 0f
    private val gap = (0.0095f * sr).toInt()

    override fun trigger(v: Float) {
        vel = v; time = 0; burst = 1f; tail = 0f; active = true
        tailCoef = Dsp.decay60(t.clapDecay, sr)
        bp.setHz(t.clapHz, 1.4f, sr)
    }

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        for (i in 0 until n) {
            if (time == gap || time == gap * 2) burst = 1f
            if (time == gap * 3) tail = 0.8f
            time++
            val x = bp.bp(noise.next()) * (burst + tail) * 2.2f
            out(l, r, rev, i, x * vel)
            burst *= burstCoef; tail *= tailCoef
        }
        if (time > gap * 3 && tail < 1e-4f) active = false
    }
}

/** 808-style metallic hat: six inharmonic squares + noise, band- and high-passed. Open/closed share a voice (choke). */
internal class Hat(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(44)
    private val ph = FloatArray(6)
    private val freqs = floatArrayOf(205.3f, 304.4f, 369.6f, 522.7f, 540f, 800f)
    private val bp = Svf()
    private val hp = Svf()
    private var env = 0f
    private var coef = 0f
    private var open = false

    fun trigger(v: Float, isOpen: Boolean) {
        vel = v; env = 1f; open = isOpen; active = true
        coef = Dsp.decay60(if (isOpen) t.openDecay else t.hatDecay, sr)
        bp.setHz(9800f, 1.2f, sr)
        hp.setHz(7000f, 0.7f, sr)
    }

    override fun trigger(v: Float) = trigger(v, false)

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        val scale = t.hatTone * 1.7f / sr
        for (i in 0 until n) {
            var m = 0f
            for (k in 0 until 6) {
                var p = ph[k] + freqs[k] * scale
                if (p >= 1f) p -= 1f
                ph[k] = p
                m += if (p < 0.5f) 1f else -1f
            }
            val x = hp.hp(bp.bp(m * 0.17f) + noise.next() * 0.5f) * env
            out(l, r, rev, i, x * vel)
            env *= coef
        }
        if (env < 1e-4f) active = false
    }
}

/** Pitched tom: sine with a downward bend and a little noise. */
internal class Tom(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(55)
    private val lp = OnePole().apply { setHz(1800f, sr) }
    private var phase = 0f
    private var hz = 100f
    private var env = 0f
    private var bend = 0f
    private var coef = 0f
    private val bendCoef = Dsp.decay60(0.12f, sr)

    fun trigger(v: Float, pitch: Int) {
        vel = v; env = 1f; bend = 1f; phase = 0f; active = true
        coef = Dsp.decay60(t.tomDecay, sr)
        hz = t.tomHz * when (pitch) {
            0 -> 0.75f
            2 -> 1.5f
            else -> 1f
        }
        pan = (pitch - 1) * 0.45f
    }

    override fun trigger(v: Float) = trigger(v, 1)

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        for (i in 0 until n) {
            phase += hz * pitchMul * (1f + t.tomBend * bend) / sr; if (phase >= 1f) phase -= 1f
            val x = (Dsp.sin01(phase) + lp.lp(noise.next()) * 0.25f * bend) * env
            out(l, r, rev, i, x * vel)
            env *= coef; bend *= bendCoef
        }
        if (env < 1e-4f) active = false
    }
}

/** FM metal hit: anvils, pipes, rim clicks — the "clank" of the industrial kits. */
internal class Perc(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(66)
    private val bp = Svf()
    private var pc = 0f
    private var pm = 0f
    private var env = 0f
    private var idx = 0f
    private var coef = 0f
    private val idxCoef = Dsp.decay60(0.06f, sr)

    override fun trigger(v: Float) {
        vel = v; env = 1f; idx = 1f; active = true
        coef = Dsp.decay60(t.percDecay, sr)
        bp.setHz(t.percHz * 3f, 2f, sr)
    }

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        val fc = t.percHz * pitchMul / sr
        val fm = fc * t.percRatio
        for (i in 0 until n) {
            pc += fc; if (pc >= 1f) pc -= 1f
            pm += fm; if (pm >= 1f) pm -= 1f
            val mod = Dsp.sin01(pm) * t.percFm * (0.3f + 0.7f * idx)
            val x = (Dsp.sin01(pc + mod) + bp.bp(noise.next()) * t.percNoise * 3f * idx) * env
            out(l, r, rev, i, x * vel)
            env *= coef; idx *= idxCoef
        }
        if (env < 1e-4f) active = false
    }
}

/**
 * Sleigh bells (or a tambourine): four inharmonic metal partials plus shaker noise, struck
 * as a quick rattle of jingles rather than one clean hit.
 */
internal class Jingle(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(99)
    private val ph = FloatArray(4)
    private val ratios = floatArrayOf(1f, 1.37f, 1.83f, 2.47f)
    private val hp = Svf()
    private var env = 0f
    private var coef = 0f
    private var time = 0
    private var hit = 0
    private val hitAt = intArrayOf((0.019f * sr).toInt(), (0.043f * sr).toInt(), (0.071f * sr).toInt())
    private val hitAmp = floatArrayOf(0.7f, 0.5f, 0.3f)

    override fun trigger(v: Float) {
        vel = v; env = 1f; time = 0; hit = 0; active = true
        coef = Dsp.decay60(t.jingleDecay, sr)
        hp.setHz(t.jingleHz * 0.8f, 0.8f, sr)
    }

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        val base = t.jingleHz * pitchMul / sr
        val nz = t.jingleNoise
        val tone = 1f - nz
        for (i in 0 until n) {
            if (hit < hitAt.size && time == hitAt[hit]) {
                if (env < hitAmp[hit]) env = hitAmp[hit]
                hit++
            }
            time++
            var m = 0f
            for (k in 0 until 4) {
                var p = ph[k] + base * ratios[k]
                if (p >= 1f) p -= 1f
                ph[k] = p
                m += if (p < 0.5f) 1f else -1f
            }
            val x = hp.hp(m * 0.2f * tone + noise.next() * nz) * env
            out(l, r, rev, i, x * vel)
            env *= coef
        }
        if (hit >= hitAt.size && env < 1e-4f) active = false
    }
}

/** Crash cymbal: dense metallic squares + noise, high-passed, long decay. */
internal class Crash(sr: Int) : DrumVoice(sr) {
    private val noise = Noise(88)
    private val ph = FloatArray(6)
    private val freqs = floatArrayOf(263f, 400f, 421f, 474f, 587f, 845f)
    private val hp = Svf()
    private var env = 0f
    private var coef = 0f

    override fun trigger(v: Float) {
        vel = v; env = 1f; active = true
        coef = Dsp.decay60(t.crashDecay, sr)
        hp.setHz(5200f, 0.8f, sr)
    }

    override fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        if (!active) return
        val scale = 2.3f / sr
        for (i in 0 until n) {
            var m = 0f
            for (k in 0 until 6) {
                var p = ph[k] + freqs[k] * scale
                if (p >= 1f) p -= 1f
                ph[k] = p
                m += if (p < 0.5f) 1f else -1f
            }
            val x = hp.hp(m * 0.12f + noise.next() * 0.7f) * env
            out(l, r, rev, i, x * vel)
            env *= coef
        }
        if (env < 1e-4f) active = false
    }
}

/** The full kit with a shared bus (drive + optional bit-crush). */
internal class DrumKit(private val sr: Int) {
    val kick = Kick(sr)
    val snare = Snare(sr)
    val clap = Clap(sr).apply { pan = 0.08f }
    val hat = Hat(sr).apply { pan = 0.3f }
    private val toms = arrayOf(Tom(sr), Tom(sr))
    private var tomNext = 0
    private val percs = arrayOf(Perc(sr).apply { pan = -0.35f }, Perc(sr).apply { pan = 0.35f })
    private var percNext = 0
    val crash = Crash(sr).apply { pan = -0.2f }
    val jingle = Jingle(sr).apply { pan = -0.35f }
    private val all: Array<DrumVoice> = arrayOf(kick, snare, clap, hat, toms[0], toms[1], percs[0], percs[1], crash, jingle)
    private var tuning = DrumTuning()
    private var held = 0f
    private var heldR = 0f
    private var crushCount = 0
    private val busL = Svf()
    private val busR = Svf()

    fun setTuning(t: DrumTuning) {
        tuning = t
        if (t.busCutoff > 0f) {
            busL.setHz(t.busCutoff, 0.6f, sr); busR.setHz(t.busCutoff, 0.6f, sr)
        }
        for (v in all) v.t = t
        kick.level = t.kickLevel; snare.level = t.snareLevel; clap.level = t.clapLevel
        hat.level = t.hatLevel; crash.level = t.crashLevel; jingle.level = t.jingleLevel; jingle.send = 0.12f
        toms.forEach { it.level = t.tomLevel; it.send = 0.25f }
        percs.forEach { it.level = t.percLevel; it.send = 0.3f }
        snare.send = t.snareVerb; clap.send = t.snareVerb * 0.8f; crash.send = 0.2f; hat.send = 0.04f
    }

    /** Cut every drum (a restart: tails frozen under a finished fade must not come back). */
    fun kill() {
        for (v in all) v.silence()
        busL.reset(); busR.reset(); held = 0f; heldR = 0f
    }

    fun tom(v: Float, pitch: Int) {
        toms[tomNext].trigger(v, pitch); tomNext = (tomNext + 1) % toms.size
    }

    fun perc(v: Float) {
        percs[percNext].trigger(v); percNext = (percNext + 1) % percs.size
    }

    /** Overwrites [l]/[r]/[rev] with [n] samples. */
    fun render(l: FloatArray, r: FloatArray, rev: FloatArray, n: Int, pitchMul: Float) {
        l.fill(0f, 0, n); r.fill(0f, 0, n); rev.fill(0f, 0, n)
        for (v in all) v.render(l, r, rev, n, pitchMul)
        val t = tuning
        if (t.drive > 0f) {
            val g = 1f + t.drive * 3f
            val c = 1f / Dsp.tanh(g) * 0.8f
            for (i in 0 until n) {
                l[i] = Dsp.tanh(l[i] * g) * c; r[i] = Dsp.tanh(r[i] * g) * c
            }
        }
        if (t.crush > 1) {
            for (i in 0 until n) {
                if (crushCount-- <= 0) {
                    crushCount = t.crush - 1; held = l[i]; heldR = r[i]
                }
                l[i] = held; r[i] = heldR
            }
        }
        if (t.busCutoff > 0f) {
            for (i in 0 until n) {
                l[i] = busL.lp(l[i]); r[i] = busR.lp(r[i])
            }
        }
    }
}
