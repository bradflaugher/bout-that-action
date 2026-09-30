package com.bradflaugher.aboutthataction.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Allocation-free DSP building blocks shared by the music and SFX engines.
 * Everything here is plain Kotlin so the whole synth runs (and is tested) on the JVM.
 */

internal object Dsp {
    const val PI_F = PI.toFloat()
    private const val SINE_SIZE = 4096
    private val sine = FloatArray(SINE_SIZE + 2) { sin(2.0 * PI * it / SINE_SIZE).toFloat() }

    /** sin(2π·phase) by table lookup; [phase] is in cycles and may be any value. */
    fun sin01(phase: Float): Float {
        var p = phase - phase.toInt()
        if (p < 0f) p += 1f
        val x = p * SINE_SIZE
        val i = x.toInt()
        val a = sine[i]
        return a + (sine[i + 1] - a) * (x - i)
    }

    /** PolyBLEP residual for a discontinuity at phase 0 ([t] in 0..1, [dt] = phase increment). */
    fun polyBlep(t: Float, dt: Float): Float = when {
        t < dt -> {
            val x = t / dt
            x + x - x * x - 1f
        }
        t > 1f - dt -> {
            val x = (t - 1f) / dt
            x * x + x + x + 1f
        }
        else -> 0f
    }

    fun midiToHz(note: Float): Float = (440.0 * 2.0.pow((note - 69.0) / 12.0)).toFloat()

    /** Frequency ratio of [s] semitones. */
    fun semis(s: Float): Float = 2.0.pow(s / 12.0).toFloat()

    /** Per-sample multiplier that decays 60 dB over [seconds]. */
    fun decay60(seconds: Float, sr: Int): Float =
        if (seconds <= 0f) 0f else exp(-6.907755 / (seconds.toDouble() * sr)).toFloat()

    /** One-pole smoothing coefficient for a time constant of [seconds]. */
    fun onePole(seconds: Float, sr: Int): Float =
        if (seconds <= 0f) 1f else (1.0 - exp(-1.0 / (seconds.toDouble() * sr))).toFloat()

    /** Per-sample multiplier that glides exponentially from [from] to [to] in [samples]. */
    fun expStep(from: Float, to: Float, samples: Int): Float =
        if (samples <= 0 || from <= 0f || to <= 0f) 1f
        else (to.toDouble() / from).pow(1.0 / samples).toFloat()

    /** Cheap rational tanh; exact at ±3 and saturates beyond. */
    fun tanh(x: Float): Float {
        if (x >= 3f) return 1f
        if (x <= -3f) return -1f
        val x2 = x * x
        return x * (27f + x2) / (27f + 9f * x2)
    }

    /** ZDF filter prewarp g = tan(π·fc/fs) (Padé approximation, fc clamped below Nyquist). */
    fun svfG(hz: Float, sr: Int): Float {
        val fc = min(max(hz, 12f), sr * 0.45f)
        val x = PI_F * fc / sr
        val x2 = x * x
        return x * (135135f - x2 * (17325f - x2 * (378f - x2))) /
            (135135f - x2 * (62370f - x2 * (3150f - 28f * x2)))
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Equal-power pan gains for [pan] in -1..1 (left channel). */
    fun panL(pan: Float): Float = kotlin.math.cos((pan.coerceIn(-1f, 1f) + 1f) * PI_F / 4f)
    fun panR(pan: Float): Float = kotlin.math.sin((pan.coerceIn(-1f, 1f) + 1f) * PI_F / 4f)

    /** Flush denormals / tiny values in long feedback paths. */
    fun flush(x: Float): Float = if (abs(x) < 1e-15f) 0f else x
}

/** Deterministic xorshift white noise in -1..1. */
internal class Noise(seed: Int) {
    private var s = if (seed == 0) 0x6d2b79f5 else seed

    fun next(): Float {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x * (1f / 2147483648f)
    }
}

/** Deterministic PRNG (xorshift64*) for musical and SFX decisions. */
internal class Rng(seed: Long) {
    private var s = 1L

    init {
        reseed(seed)
    }

    fun reseed(seed: Long) {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        s = z xor (z ushr 31)
        if (s == 0L) s = 1L
    }

    fun nextLong(): Long {
        var x = s
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        s = x
        return x * 2685821657736338717L
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24)
    fun nextInt(n: Int): Int = ((nextLong() ushr 33) % n).toInt()
    fun range(a: Float, b: Float): Float = a + (b - a) * nextFloat()
    fun chance(p: Float): Boolean = nextFloat() < p

    /** Symmetric jitter: 1 ± [amount]. */
    fun vary(amount: Float): Float = 1f + (nextFloat() * 2f - 1f) * amount
}

/** Cytomic/Simper zero-delay-feedback state-variable filter. Outputs LP, BP and HP at once. */
internal class Svf {
    private var ic1 = 0f
    private var ic2 = 0f
    private var a1 = 1f
    private var a2 = 0f
    private var a3 = 0f
    private var k = 1.4142f
    var low = 0f; private set
    var band = 0f; private set
    var high = 0f; private set

    fun set(g: Float, q: Float) {
        k = 1f / max(q, 0.05f)
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun setHz(hz: Float, q: Float, sr: Int) = set(Dsp.svfG(hz, sr), q)

    fun process(x: Float) {
        val v3 = x - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2f * v1 - ic1
        ic2 = 2f * v2 - ic2
        low = v2
        band = v1
        high = x - k * v1 - v2
    }

    fun lp(x: Float): Float {
        process(x); return low
    }

    fun bp(x: Float): Float {
        process(x); return band
    }

    fun hp(x: Float): Float {
        process(x); return high
    }

    fun reset() {
        ic1 = 0f; ic2 = 0f; low = 0f; band = 0f; high = 0f
    }

    fun sanitize() {
        if (!ic1.isFinite() || !ic2.isFinite()) reset()
        ic1 = Dsp.flush(ic1); ic2 = Dsp.flush(ic2)
    }
}

/** One-pole low/high-pass. */
internal class OnePole {
    var a = 1f
    private var z = 0f

    fun setHz(hz: Float, sr: Int) {
        a = (1.0 - exp(-2.0 * PI * min(hz, sr * 0.49f) / sr)).toFloat()
    }

    fun lp(x: Float): Float {
        z += a * (x - z); return z
    }

    fun hp(x: Float): Float {
        z += a * (x - z); return x - z
    }

    fun reset() {
        z = 0f
    }
}

internal object Stage {
    const val IDLE = 0
    const val ATTACK = 1
    const val DECAY = 2
    const val RELEASE = 3
}

/** Linear-attack, exponential decay/release ADSR. Retriggers from the current level (click-free). */
internal class Adsr(private val sr: Int) {
    var level = 0f; private set
    var stage = Stage.IDLE; private set
    private var aInc = 1f
    private var dCoef = 0f
    private var sus = 1f
    private var rCoef = 0f

    fun set(a: Float, d: Float, s: Float, r: Float) {
        aInc = 1f / max(1f, a * sr)
        dCoef = Dsp.decay60(max(d, 0.001f), sr)
        sus = s
        rCoef = Dsp.decay60(max(r, 0.002f), sr)
    }

    fun gateOn() {
        stage = Stage.ATTACK
    }

    fun gateOff() {
        if (stage != Stage.IDLE) stage = Stage.RELEASE
    }

    fun reset() {
        stage = Stage.IDLE; level = 0f
    }

    val active: Boolean get() = stage != Stage.IDLE

    fun next(): Float {
        when (stage) {
            Stage.ATTACK -> {
                level += aInc
                if (level >= 1f) {
                    level = 1f; stage = Stage.DECAY
                }
            }
            Stage.DECAY -> level = sus + (level - sus) * dCoef
            Stage.RELEASE -> {
                level *= rCoef
                if (level < 1e-4f) {
                    level = 0f; stage = Stage.IDLE
                }
            }
        }
        return level
    }
}

/** Fixed-length ring delay (exactly [length] samples). */
internal class FixedDelay(val length: Int) {
    private val buf = FloatArray(max(1, length))
    private var i = 0

    /** Returns the sample written [length] samples ago and stores [x]. */
    fun tick(x: Float): Float {
        val y = buf[i]
        buf[i] = x
        if (++i >= buf.size) i = 0
        return y
    }

    fun peek(): Float = buf[i]

    fun clear() = buf.fill(0f)
}

/** Power-of-two ring buffer with fractional (linear) reads. */
internal class DelayLine(maxSamples: Int) {
    private val size = Integer.highestOneBit(max(4, maxSamples + 2) - 1) shl 1
    private val buf = FloatArray(size)
    private val mask = size - 1
    private var w = 0

    fun write(x: Float) {
        buf[w] = x
        w = (w + 1) and mask
    }

    /** Read [delay] samples behind the most recent write (delay >= 1). */
    fun read(delay: Float): Float {
        val d = delay.coerceIn(1f, (size - 3).toFloat())
        val di = d.toInt()
        val f = d - di
        val a = buf[(w - di) and mask]
        val b = buf[(w - di - 1) and mask]
        return a + (b - a) * f
    }

    fun clear() = buf.fill(0f)
}

/** Ping-pong feedback delay with damped repeats, used as a tempo-synced send. */
internal class StereoDelay(private val sr: Int) {
    private val left = DelayLine(sr * 2)
    private val right = DelayLine(sr * 2)
    private val dampL = OnePole().apply { setHz(3800f, sr) }
    private val dampR = OnePole().apply { setHz(3800f, sr) }
    private var time = sr * 0.3f
    private var target = time
    // A jump in time (a new song's tempo) crossfades to a second tap instead of sliding the
    // one tap there, which would chirp every echo still ringing.
    private var nextTime = time
    private var xf = 1f
    private val xfStep = 1f / (0.05f * sr)
    var feedback = 0.42f
    var outL = 0f; private set
    var outR = 0f; private set

    fun setTime(seconds: Float) {
        target = (seconds * sr).coerceIn(16f, sr * 1.9f)
    }

    fun process(inL: Float, inR: Float) {
        if (xf >= 1f && kotlin.math.abs(target - time) > time * 0.02f) {
            nextTime = target; xf = 0f
        }
        val dl: Float
        val dr: Float
        if (xf < 1f) {
            nextTime += (target - nextTime) * 0.0004f
            xf = kotlin.math.min(1f, xf + xfStep)
            dl = left.read(time) * (1f - xf) + left.read(nextTime) * xf
            dr = right.read(time) * (1f - xf) + right.read(nextTime) * xf
            if (xf >= 1f) time = nextTime
        } else {
            time += (target - time) * 0.0004f
            dl = left.read(time)
            dr = right.read(time)
        }
        left.write((inL + inR) * 0.5f + Dsp.flush(dampR.lp(dr)) * feedback)
        right.write(Dsp.flush(dampL.lp(dl)) * feedback)
        outL = dl
        outR = dr
    }

    fun clear() {
        left.clear(); right.clear(); dampL.reset(); dampR.reset()
    }
}

/** Schroeder all-pass diffuser. */
internal class Allpass(len: Int, private val g: Float) {
    private val d = FixedDelay(len)

    fun process(x: Float): Float {
        val b = d.peek()
        val v = x - g * b
        d.tick(v)
        return b + g * v
    }

    fun clear() = d.clear()
}

/**
 * 8-line feedback delay network reverb (Householder mixing, damped lines) with
 * a pre-delay and series all-pass diffusion. Mono in, decorrelated stereo out.
 */
internal class Reverb(private val sr: Int, rt60: Float, size: Float, dampHz: Float) {
    private val scale = sr / 48000f * size
    private val lines = intArrayOf(1031, 1327, 1523, 1801, 2011, 2269, 2503, 2777)
        .map { FixedDelay((it * scale).toInt()) }.toTypedArray()
    private val gains = FloatArray(8)
    private val damp = FloatArray(8)
    private val dampA: Float = (1.0 - exp(-2.0 * PI * dampHz / sr)).toFloat()
    private val pre = FixedDelay((0.018f * sr).toInt())
    private val diff = arrayOf(
        Allpass((142 * scale).toInt(), 0.7f), Allpass((107 * scale).toInt(), 0.7f),
        Allpass((379 * scale).toInt(), 0.6f), Allpass((277 * scale).toInt(), 0.6f),
    )
    private val y = FloatArray(8)
    var outL = 0f; private set
    var outR = 0f; private set

    init {
        for (i in 0 until 8) {
            gains[i] = 10.0.pow(-3.0 * lines[i].length / (sr * rt60.toDouble())).toFloat()
        }
    }

    fun process(input: Float) {
        var x = pre.tick(input)
        for (a in diff) x = a.process(x)
        var sum = 0f
        for (i in 0 until 8) {
            val o = lines[i].peek()
            damp[i] += dampA * (o - damp[i])
            val v = Dsp.flush(damp[i]) * gains[i]
            damp[i] = Dsp.flush(damp[i])
            y[i] = v
            sum += v
        }
        sum *= 0.25f // 2/N
        for (i in 0 until 8) {
            val inj = if (i and 1 == 0) x else -x
            lines[i].tick(y[i] - sum + inj)
        }
        outL = (y[0] - y[2] + y[4] - y[6]) * 0.5f
        outR = (y[1] - y[3] + y[5] - y[7]) * 0.5f
    }

    fun clear() {
        lines.forEach { it.clear() }; damp.fill(0f); pre.clear(); diff.forEach { it.clear() }
    }
}

/**
 * Master bus: DC blocker, instant-attack peak limiter, soft knee clipper and a final clamp.
 * Output is guaranteed finite and within [-1, 1].
 */
internal class MasterBus(sr: Int) {
    private val threshold = 0.9f
    private val release = Dsp.onePole(0.15f, sr)
    private var gain = 1f
    private var xl = 0f
    private var yl = 0f
    private var xr = 0f
    private var yr = 0f
    var outL = 0f; private set
    var outR = 0f; private set

    /** Returns false if the input was non-finite (caller should reset its DSP state). */
    fun process(inL: Float, inR: Float): Boolean {
        if (!inL.isFinite() || !inR.isFinite()) {
            outL = 0f; outR = 0f; xl = 0f; yl = 0f; xr = 0f; yr = 0f; gain = 1f
            return false
        }
        yl = inL - xl + 0.9995f * yl; xl = inL
        yr = inR - xr + 0.9995f * yr; xr = inR
        var l = yl
        var r = yr
        val peak = max(abs(l), abs(r))
        if (peak * gain > threshold) gain = threshold / peak else gain += (1f - gain) * release
        l *= gain; r *= gain
        outL = softClip(l)
        outR = softClip(r)
        return true
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        val y = if (a <= 0.8f) a else 0.8f + 0.2f * Dsp.tanh((a - 0.8f) * 5f)
        return (if (x < 0f) -y else y).coerceIn(-1f, 1f)
    }
}
