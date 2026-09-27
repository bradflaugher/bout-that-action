package com.bradflaugher.aboutthataction.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Streams a [SoundEngine] to a low-latency float [AudioTrack] from a dedicated
 * urgent-audio thread. Safe to drive from Activity lifecycle callbacks: [start] is
 * idempotent (and resumes if paused), [pause]/[resume] can be called in any order, and
 * [release] can be followed by a fresh [start].
 */
class AudioOutput(private val engine: SoundEngine) {
    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var dead = false

    fun start() {
        // A track that died (e.g. audio device change) is rebuilt from scratch.
        if (dead) release()
        lock.withLock {
            if (thread != null) {
                resumeLocked(); return
            }
            val sr = engine.sampleRate
            val minBytes = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val minFrames = if (minBytes > 0) minBytes / BYTES_PER_FRAME else 1024
            // No usable audio output (or the device refused the format): play on in silence.
            val t = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setBufferSizeInBytes(minFrames * BUFFER_MULTIPLE * BYTES_PER_FRAME)
                    .build()
            }.getOrNull() ?: return
            if (t.state != AudioTrack.STATE_INITIALIZED || runCatching { t.play() }.isFailure) {
                t.release()
                return
            }
            // Write in small chunks: a fraction of the device buffer keeps latency low.
            val chunk = (minFrames / 2).coerceIn(64, 512)
            track = t
            running = true
            paused = false
            thread = Thread({ loop(t, chunk) }, "ata-audio").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        }
    }

    fun pause() {
        lock.withLock {
            val t = track ?: return
            paused = true
            runCatching { t.pause() }
        }
    }

    fun resume() {
        lock.withLock { resumeLocked() }
    }

    private fun resumeLocked() {
        val t = track ?: return
        if (!paused) return
        paused = false
        runCatching { t.play() }
        wake.signalAll()
    }

    /** Stop the thread and free the track. [start] may be called again afterwards. */
    fun release() {
        val t: AudioTrack?
        val th: Thread?
        lock.withLock {
            t = track
            th = thread
            running = false
            paused = false
            wake.signalAll()
            track = null
            thread = null
            dead = false
        }
        if (t != null) {
            // Unblock a pending blocking write: pause + flush frees the buffer.
            runCatching { t.pause(); t.flush() }
        }
        th?.join(500)
        t?.let { runCatching { it.release() } }
    }

    private fun loop(t: AudioTrack, chunk: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = FloatArray(chunk * 2)
        while (running) {
            if (paused) {
                lock.withLock {
                    while (paused && running) wake.await()
                }
                continue
            }
            engine.render(buf, chunk)
            val written = try {
                t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            } catch (_: IllegalStateException) {
                -1
            }
            if (written < 0) {
                dead = true
                break
            }
        }
    }

    private companion object {
        const val BYTES_PER_FRAME = 8 // stereo float
        const val BUFFER_MULTIPLE = 2
    }
}
