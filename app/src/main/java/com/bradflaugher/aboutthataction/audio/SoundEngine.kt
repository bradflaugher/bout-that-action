package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.AlertPhase
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * 'Bout That Action's whole soundtrack and sound design, synthesized in code.
 *
 * Threading: [trigger] and the setters may be called from any thread (game/UI); they only
 * enqueue commands or write volatile fields. [render] runs on the audio thread, drains the
 * queue at the start of each call and never allocates on its hot path. Output is
 * deterministic for a given command sequence and is always finite and within [-1, 1].
 */
class SoundEngine(val sampleRate: Int = 48000) {
    private val sr = sampleRate

    // ---- Cross-thread inputs ------------------------------------------------------------
    private val queue = ConcurrentLinkedQueue<Any>()
    private val zoneCmds = Zone.entries.map { ZoneCmd(it, false) }
    private val sneakCmds = Zone.entries.map { ZoneCmd(it, true) }

    @Volatile private var intensityIn = 0.35f
    @Volatile private var alertFloor = 0f
    @Volatile private var musicVolIn = 0.8f
    @Volatile private var sfxVolIn = 1f
    @Volatile private var pausedIn = false
    @Volatile private var slowMoIn = false

    private class ZoneCmd(val zone: Zone, val silent: Boolean)
    private class HeroCmd(val hero: Hero?)
    private class ThemeCmd(val hero: Hero)
    private object TitleCmd
    private object GameOverCmd
    private object AlertHitCmd
    private val heroCmds = Hero.entries.map { HeroCmd(it) }
    private val noHeroCmd = HeroCmd(null)
    private val themeCmds = Hero.entries.map { ThemeCmd(it) }

    // ---- Audio-thread state -------------------------------------------------------------
    internal val director = MusicDirector(sr)
    private val bank = SfxBank(sr, SFX_VOICES)
    private val sfx = SfxPlayer(bank, Rng(0xB0A7L))
    private val musicDelay = StereoDelay(sr)
    private val musicVerb = Reverb(sr, rt60 = 2.4f, size = 1.1f, dampHz = 5200f)
    private val sfxVerb = Reverb(sr, rt60 = 1.1f, size = 0.7f, dampHz = 6500f)
    private val musicLpL = Svf()
    private val musicLpR = Svf()
    private val master = MasterBus(sr)

    private val mL = FloatArray(BLOCK)
    private val mR = FloatArray(BLOCK)
    private val mRev = FloatArray(BLOCK)
    private val mDly = FloatArray(BLOCK)
    private val sL = FloatArray(BLOCK)
    private val sR = FloatArray(BLOCK)
    private val sRev = FloatArray(BLOCK)

    private var intensity = 0.35f
    private var rate = 1f
    private var cutoff = 20000f
    private var musicGain = 0f
    private var sfxGain = 1f
    private var sfxEnv = 0f
    private val sfxAtt = Dsp.onePole(0.005f, sr)
    private val sfxRel = Dsp.decay60(0.9f, sr)
    private val gainSmooth = Dsp.onePole(0.03f, sr)
    private var paused = false
    private var gameOverWait = -1
    private var first = true

    /** Whose arrangement plays (audio thread); null is the original soundtrack. */
    private var hero: Hero? = null

    // ---- Public API (any thread) -------------------------------------------------------

    /** Queue a sound effect for [event]. Cheap; safe to call many times per frame. */
    fun trigger(event: GameEvent) {
        queue.add(event)
    }

    /**
     * Move the music to [zone]'s track (on the next bar line, with a fill and riser); [silent]
     * picks its sneak mix. Flipping only the mode switches on the next beat, in the same key,
     * picking up at the same place in the phrase on the nearest chord.
     */
    fun setZone(zone: Zone, silent: Boolean = false) {
        queue.add((if (silent) sneakCmds else zoneCmds)[zone.ordinal])
    }

    /** Combat heat 0..1: adds drums, arp and lead and opens the filters as it rises. */
    fun setIntensity(intensity: Float) {
        intensityIn = if (intensity.isNaN()) 0f else intensity.coerceIn(0f, 1f)
    }

    /**
     * The hallway's alert phase: under ALERT the music drives at full tilt, under CAUTION it
     * stays tense, whatever the heat says. (The host swaps SILENT's sneak mix for the zone's
     * full track outside CALM.)
     */
    fun setAlert(phase: AlertPhase) {
        // Spotted: the band hits the next beat (a SILENT run's flip to the full track lands its own).
        if (phase == AlertPhase.ALERT && alertFloor != ALERT_INTENSITY) queue.add(AlertHitCmd)
        alertFloor = when (phase) {
            AlertPhase.ALERT -> ALERT_INTENSITY
            AlertPhase.CAUTION -> CAUTION_INTENSITY
            AlertPhase.CALM -> 0f
        }
    }

    fun playTitle() {
        queue.add(TitleCmd)
    }

    /**
     * Whose soundtrack plays: every zone track (and its sneak mix) comes in [hero]'s own
     * arrangement, and the game-over stinger gets their sign-off. Takes effect from the next
     * [setZone] (call it before the run's first one; the hero is fixed for the run). Null
     * goes back to the original soundtrack. Ordered with the other commands.
     */
    fun setHero(hero: Hero?) {
        queue.add(if (hero == null) noHeroCmd else heroCmds[hero.ordinal])
    }

    /**
     * [hero]'s signature theme, for the hero picker; also makes them the hero ([setHero]).
     * From the title or another hero's theme it comes in on the next beat; from anything else,
     * on the next bar line.
     */
    fun playHeroTheme(hero: Hero) {
        queue.add(heroCmds[hero.ordinal])
        queue.add(themeCmds[hero.ordinal])
    }

    fun setMusicVolume(v: Float) {
        musicVolIn = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)
    }

    fun setSfxVolume(v: Float) {
        sfxVolIn = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)
    }

    /** Paused: music ducks low-passed and quiet, SFX are silenced (and new ones dropped). */
    fun setPaused(paused: Boolean) {
        pausedIn = paused
    }

    /** Bullet time: music slows and drops in pitch (tape-style) behind a low-pass sweep. */
    fun setSlowMo(on: Boolean) {
        slowMoIn = on
    }

    /** Game-over stinger, then a quiet ambient loop. */
    fun gameOver() {
        queue.add(GameOverCmd)
    }

    /** The track now playing (audio thread; for tests). */
    internal val songName: String? get() = director.current?.name

    // ---- Audio thread ------------------------------------------------------------------

    /** Fill [out] with [frames] interleaved stereo float frames. */
    fun render(out: FloatArray, frames: Int) {
        paused = pausedIn
        drain()
        var done = 0
        while (done < frames) {
            val n = min(BLOCK, frames - done)
            renderBlock(out, done, n)
            done += n
        }
    }

    private fun drain() {
        while (true) {
            when (val c = queue.poll() ?: return) {
                is GameEvent -> if (!paused) sfx.play(c)
                is ZoneCmd -> {
                    gameOverWait = -1
                    val spec = HeroSongs.forZone(hero, c.zone, c.silent)
                    // Same zone, other mode: on the next beat, not a wait for the bar line.
                    val cur = director.current
                    val modeFlip = cur != null && cur !== spec && HeroSongs.forZone(hero, c.zone, !c.silent) === cur
                    val t = when {
                        inGameOver() -> Transition.EXIT
                        !modeFlip -> Transition.BAR
                        c.silent -> Transition.FLIP_DOWN
                        else -> Transition.FLIP_UP
                    }
                    director.request(spec, t)
                }
                is HeroCmd -> hero = c.hero
                is ThemeCmd -> {
                    gameOverWait = -1
                    val spec = HeroSongs.theme(c.hero)
                    val cur = director.current
                    // The picker: from the title, or one hero's theme to the next, on the beat.
                    director.request(spec, menuWay(cur))
                }
                TitleCmd -> {
                    gameOverWait = -1
                    director.request(Songs.title, menuWay(director.current))
                }
                GameOverCmd -> {
                    sfx.gameOverStinger(hero)
                    // The band stops under the stinger; the game-over loop swells up out of its tail.
                    director.stop(GAME_OVER_FADE)
                    gameOverWait = (GAME_OVER_WAIT * sr).toInt()
                }
                AlertHitCmd -> {
                    // Spotted: the heat jumps at once, so a flip to the full track lands its drop.
                    intensity = max(intensity, ALERT_INTENSITY)
                    director.intensity = intensity
                    director.hit()
                }
            }
        }
    }

    /**
     * The game-over dirge is playing: RETRY, NEW RUN or TITLE leaves it with a quick crossfade.
     * Waiting for its slow bar (up to ~5 s at 72 BPM) played the dirge into the next run.
     */
    private fun inGameOver(): Boolean = director.current === Songs.gameOver

    private fun renderBlock(out: FloatArray, off: Int, n: Int) {
        if (gameOverWait >= 0) {
            gameOverWait -= n
            if (gameOverWait < 0) director.request(Songs.gameOver, Transition.NOW, GAME_OVER_SWELL)
        }
        val slow = slowMoIn
        val blockSec = n.toFloat() / sr
        val want = max(intensityIn, alertFloor)
        // Spotted: the music jumps to it; it eases back down on its own time.
        intensity += (want - intensity) * k(blockSec, if (want > intensity) 0.25f else 1.2f)
        val rateTarget = if (slow) SLOWMO_RATE else 1f
        rate += (rateTarget - rate) * k(blockSec, if (slow) 0.35f else 0.2f)
        val cutTarget = when {
            paused -> 480f
            slow -> 1300f
            else -> 20000f
        }
        cutoff = exp(ln(cutoff) + (ln(cutTarget) - ln(cutoff)) * k(blockSec, 0.18f))
        val q = if (slow && !paused) 1.6f else 0.75f
        musicLpL.setHz(cutoff, q, sr)
        musicLpR.setHz(cutoff, q, sr)
        val musicTarget = musicVolIn * MUSIC_LEVEL * (if (paused) 0.35f else 1f)
        val sfxTarget = if (paused) 0f else sfxVolIn * SFX_LEVEL
        if (first) {
            first = false; musicGain = musicTarget; sfxGain = sfxTarget
        }

        director.rate = rate
        director.intensity = intensity
        director.render(n, mL, mR, mRev, mDly)
        musicDelay.setTime(director.delayBeats * 60f / director.bpm)
        bank.render(sL, sR, sRev, n)

        var ok = true
        for (i in 0 until n) {
            musicDelay.process(mDly[i], mDly[i])
            val dl = musicDelay.outL
            val dr = musicDelay.outR
            musicVerb.process(mRev[i] + (dl + dr) * 0.12f)
            var ml = musicLpL.lp(mL[i] + dl + musicVerb.outL)
            var mr = musicLpR.lp(mR[i] + dr + musicVerb.outR)

            sfxVerb.process(sRev[i])
            val xl = sL[i] + sfxVerb.outL
            val xr = sR[i] + sfxVerb.outR
            val e = max(abs(sL[i]), abs(sR[i]))
            sfxEnv = if (e > sfxEnv) sfxEnv + (e - sfxEnv) * sfxAtt else sfxEnv * sfxRel

            musicGain += (musicTarget - musicGain) * gainSmooth
            sfxGain += (sfxTarget - sfxGain) * gainSmooth
            // SFX cut through: the music dips a little under loud effects.
            val duck = musicGain * (1f - min(0.45f, sfxEnv * 0.9f))
            ml *= duck; mr *= duck

            if (!master.process(ml + xl * sfxGain, mr + xr * sfxGain)) ok = false
            out[(off + i) * 2] = master.outL
            out[(off + i) * 2 + 1] = master.outR
        }
        if (paused && sfxGain < 1e-4f) bank.silence()
        if (!ok) panicReset()
    }

    /** A NaN/Inf reached the master: clear all state rather than stay broken. */
    private fun panicReset() {
        director.sanitize()
        bank.sanitize()
        bank.silence()
        musicVerb.clear(); sfxVerb.clear(); musicDelay.clear()
        musicLpL.reset(); musicLpR.reset()
        sfxEnv = 0f
    }

    private fun k(dt: Float, tau: Float): Float = 1f - exp(-dt / tau)

    private fun inMenus(s: SongSpec?): Boolean = s === Songs.title || HeroSongs.isTheme(s)

    /** How a menu track takes over from [cur]: on the beat between menus, at once out of game over, else on the bar. */
    private fun menuWay(cur: SongSpec?): Transition = when {
        inGameOver() -> Transition.EXIT
        inMenus(cur) -> Transition.BEAT
        else -> Transition.BAR
    }

    /** Number of SFX voices currently sounding (for tests/diagnostics). */
    internal val activeSfxVoices: Int get() = bank.activeCount

    /** Name of the song currently playing (audio-thread view; for tests/diagnostics). */
    internal val currentSong: String? get() = director.current?.name

    companion object {
        private const val BLOCK = 256
        private const val SFX_VOICES = 24
        private const val SLOWMO_RATE = 0.7f
        private const val ALERT_INTENSITY = 0.95f
        private const val CAUTION_INTENSITY = 0.5f
        private const val MUSIC_LEVEL = 0.75f
        private const val SFX_LEVEL = 0.95f
        /** Game over: the music fades under the stinger, and the loop swells in from its tail. */
        private const val GAME_OVER_FADE = 0.9f
        private const val GAME_OVER_WAIT = 0.1f
        private const val GAME_OVER_SWELL = 2f
    }
}
