package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.KillMethod
import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.Zone
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

internal object AudioTestUtil {
    const val SR = 48000

    /** Every GameEvent subtype, with every enum-valued variant. */
    fun allEvents(): List<GameEvent> = buildList {
        for (p in listOf(true, false)) for (h in listOf(true, false)) add(GameEvent.Shot(p, h, if (p) -0.3f else 0.6f))
        for (o in listOf(true, false)) for (a in listOf(true, false)) add(GameEvent.BulletHit(o, a, 0.2f))
        for (k in EnemyKind.entries) for (m in KillMethod.entries) add(GameEvent.EnemyKilled(k, m, (k.ordinal + m.ordinal) % 6, -0.5f))
        add(GameEvent.Takedown); add(GameEvent.Jump); add(GameEvent.Land); add(GameEvent.HideBox); add(GameEvent.HideDoor)
        add(GameEvent.Unhide); add(GameEvent.DoorOpen); add(GameEvent.ElevatorDing); add(GameEvent.ElevatorMove)
        add(GameEvent.Passage); add(GameEvent.ElevatorCalled); add(GameEvent.ModeToggled(true)); add(GameEvent.ModeToggled(false)); add(GameEvent.PlayerHurt(3)); add(GameEvent.PlayerHurt(1)); add(GameEvent.ShieldBlock)
        add(GameEvent.PlayerDied)
        for (k in PickupKind.entries) add(GameEvent.Pickup(k))
        add(GameEvent.PerkOffered)
        for (p in Perk.entries) add(GameEvent.PerkChosen(p))
        add(GameEvent.LightShot); add(GameEvent.LightCrash)
        add(GameEvent.Explosion(true, 0f)); add(GameEvent.Explosion(false, 0.8f)); add(GameEvent.HazardFire(-0.7f))
        add(GameEvent.FloorReached(7)); add(GameEvent.FloorReached(30))
        for (z in Zone.entries) add(GameEvent.ZoneEntered(z))
        add(GameEvent.SlowMoStart); add(GameEvent.SlowMoEnd); add(GameEvent.SpecialEmpty); add(GameEvent.Reload)
    }

    /** One representative event per GameEvent subclass (for a compact reel). */
    fun oneOfEach(): List<GameEvent> = allEvents().distinctBy { it::class }

    /** Render [seconds] of audio in callback-sized chunks; [each] runs before every chunk. */
    fun render(engine: SoundEngine, seconds: Float, chunk: Int = 480, each: (Int) -> Unit = {}): FloatArray {
        val frames = (seconds * engine.sampleRate).toInt()
        val out = FloatArray(frames * 2)
        val buf = FloatArray(chunk * 2)
        var done = 0
        var i = 0
        while (done < frames) {
            val n = minOf(chunk, frames - done)
            each(i++)
            engine.render(buf, n)
            System.arraycopy(buf, 0, out, done * 2, n * 2)
            done += n
        }
        return out
    }

    fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var s = 0.0
        for (i in from until to) s += x[i].toDouble() * x[i]
        return sqrt(s / maxOf(1, to - from))
    }

    fun peak(x: FloatArray): Float = x.maxOf { kotlin.math.abs(it) }

    /** Loudest 20 ms window RMS. */
    fun maxWindowRms(x: FloatArray, window: Int = 1920): Double {
        var best = 0.0
        var i = 0
        while (i + window <= x.size) {
            best = maxOf(best, rms(x, i, i + window)); i += window / 2
        }
        return best
    }

    fun mono(stereo: FloatArray): FloatArray = FloatArray(stereo.size / 2) { (stereo[it * 2] + stereo[it * 2 + 1]) * 0.5f }

    /** Averaged magnitude spectrum (Hann windows of [n] samples) of a mono signal. */
    fun spectrum(x: FloatArray, n: Int = 4096): DoubleArray {
        val mag = DoubleArray(n / 2)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        var start = 0
        var count = 0
        while (start + n <= x.size) {
            for (i in 0 until n) {
                re[i] = x[start + i] * (0.5 - 0.5 * cos(2 * PI * i / n)); im[i] = 0.0
            }
            fft(re, im)
            for (k in 0 until n / 2) mag[k] += hypot(re[k], im[k])
            start += n; count++
        }
        if (count > 0) for (k in mag.indices) mag[k] /= count
        return mag
    }

    fun centroid(mag: DoubleArray, sr: Int = SR): Double {
        val n = mag.size * 2
        var num = 0.0
        var den = 0.0
        for (k in mag.indices) {
            num += k * sr.toDouble() / n * mag[k]; den += mag[k]
        }
        return if (den > 0) num / den else 0.0
    }

    /** Fraction of spectral magnitude between [lo] and [hi] Hz. */
    fun bandShare(mag: DoubleArray, lo: Double, hi: Double, sr: Int = SR): Double {
        val n = mag.size * 2
        var inBand = 0.0
        var total = 0.0
        for (k in mag.indices) {
            val f = k * sr.toDouble() / n
            total += mag[k]
            if (f in lo..hi) inBand += mag[k]
        }
        return if (total > 0) inBand / total else 0.0
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit; bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** 16-bit PCM stereo WAV. */
    fun writeWav(file: File, stereo: FloatArray, sr: Int = SR) {
        file.parentFile?.mkdirs()
        DataOutputStream(FileOutputStream(file).buffered()).use { o ->
            val dataLen = stereo.size * 2
            fun le32(v: Int) = o.writeInt(Integer.reverseBytes(v))
            fun le16(v: Int) = o.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
            o.writeBytes("RIFF"); le32(36 + dataLen); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(2); le32(sr); le32(sr * 4); le16(4); le16(16)
            o.writeBytes("data"); le32(dataLen)
            for (s in stereo) le16((s.coerceIn(-1f, 1f) * 32767f).toInt())
        }
    }
}
