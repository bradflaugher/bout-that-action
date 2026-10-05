package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.AlertPhase
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Listening (and measuring) aid for the music's transitions, off by default.
 * `ATA_TRANS_WAV=<dir>` renders every transition scenario ([TransitionScenarios]) for every
 * hero and the original soundtrack to `<dir>/<hero>_<scenario>.wav`, next to a JSON file with
 * the command timeline and every switch the director made (frame, tempos, grid positions,
 * chords), for `tools/audio/transitions.py`. `ATA_HERO_ONLY=bull,none` limits the heroes,
 * `ATA_TRANS_ONLY=flip_mid,alert_silent` the scenarios.
 */
class TransitionWavExportTest {
    private val dir: String? = System.getProperty("ata.transwav") ?: System.getenv("ATA_TRANS_WAV")
    private val heroes: Set<String> = (System.getenv("ATA_HERO_ONLY") ?: "").split(',').filter { it.isNotBlank() }.toSet()
    private val only: Set<String> = (System.getenv("ATA_TRANS_ONLY") ?: "").split(',').filter { it.isNotBlank() }.toSet()

    @Test
    fun exportTransitions() {
        assumeTrue("set ATA_TRANS_WAV=<dir> to export the music's transitions", dir != null)
        val out = File(dir!!)
        out.mkdirs()
        for (h in listOf<Hero?>(null) + Hero.entries) {
            val tag = h?.name?.lowercase() ?: "none"
            if (heroes.isNotEmpty() && tag !in heroes) continue
            for (sc in TransitionScenarios.all(h)) {
                if (only.isNotEmpty() && sc.name !in only) continue
                val run = TransitionScenarios.run(sc)
                AudioTestUtil.writeWav(File(out, "${tag}_${sc.name}.wav"), run.audio)
                File(out, "${tag}_${sc.name}.json").writeText(run.json(tag, sc.name))
            }
        }
        println("Transition WAVs written to ${out.absolutePath}")
    }
}

/** Scripted transitions: what a player does to the music, and what the music did back. */
internal object TransitionScenarios {
    const val CHUNK = 128

    class Ctx(val e: SoundEngine) {
        val events = ArrayList<Pair<Long, String>>()
        /** The playing chord's pitch classes whenever it changes (frame, mask). */
        val chords = ArrayList<Pair<Long, Int>>()
        var frame = 0L
        val pos: Double get() = e.director.activePlayer.position
        fun mark(label: String) {
            events += frame to label
        }
    }

    /** [script] runs before every [CHUNK]-frame render call, with the time in seconds. */
    class Scenario(val name: String, val seconds: Float, val setup: (SoundEngine) -> Unit, val script: Ctx.(Double) -> Unit)

    class Run(
        val audio: FloatArray,
        val events: List<Pair<Long, String>>,
        val switches: List<MusicDirector.SwitchInfo>,
        val chords: List<Pair<Long, Int>>,
    ) {
        fun json(hero: String, name: String): String = buildString {
            append("{\"hero\":\"$hero\",\"scenario\":\"$name\",\"sr\":${AudioTestUtil.SR},\"events\":[")
            append(events.joinToString(",") { "{\"frame\":${it.first},\"label\":\"${it.second}\"}" })
            append("],\"switches\":[")
            append(
                switches.joinToString(",") {
                    "{\"frame\":${it.frame},\"from\":${it.from?.let { n -> "\"$n\"" } ?: "null"},\"fromBpm\":${it.fromBpm}," +
                        "\"fromPos\":${it.fromPos},\"fromChord\":${it.fromChord},\"to\":\"${it.to}\",\"toBpm\":${it.toBpm}," +
                        "\"toPos\":${it.toPos}}"
                },
            )
            append("],\"chords\":[")
            append(chords.joinToString(",") { "[${it.first},${it.second}]" })
            append("]}")
        }
    }

    fun run(sc: Scenario): Run {
        val e = SoundEngine()
        val switches = ArrayList<MusicDirector.SwitchInfo>()
        e.director.onSwitch = { switches += it }
        sc.setup(e)
        val ctx = Ctx(e)
        val frames = (sc.seconds * AudioTestUtil.SR).toInt()
        val out = FloatArray(frames * 2)
        val buf = FloatArray(CHUNK * 2)
        var done = 0
        while (done < frames) {
            val n = minOf(CHUNK, frames - done)
            ctx.frame = done.toLong()
            sc.script(ctx, done.toDouble() / AudioTestUtil.SR)
            e.render(buf, n)
            val cm = e.director.activePlayer.chordMask()
            if (ctx.chords.isEmpty() || ctx.chords.last().second != cm) ctx.chords += done.toLong() to cm
            System.arraycopy(buf, 0, out, done * 2, n * 2)
            done += n
        }
        return Run(out, ctx.events, switches, ctx.chords)
    }

    private fun once(): (Double, Double) -> Boolean {
        var fired = false
        return { t, at -> if (!fired && t >= at) true.also { fired = true } else false }
    }

    /** Fires once, the first time the grid passes a bar line at or after [at] seconds. */
    private class OnBar(val at: Double) {
        var fired = false
        var last = -1.0
        fun check(t: Double, pos: Double): Boolean {
            val hit = !fired && t >= at && last >= 0 && Math.floorDiv(pos.toLong(), 16L) > Math.floorDiv(last.toLong(), 16L)
            last = pos
            if (hit) fired = true
            return hit
        }
    }

    fun all(h: Hero?): List<Scenario> {
        val z = Zone.TOWER
        // Real play: GUNS HOT sits near 0 heat when calm (CAUTION pins 0.5, ALERT 0.95); SILENT only plays calm, at 0-0.2.
        fun hot(e: SoundEngine, i: Float = 0.05f) {
            e.setHero(h); e.setIntensity(i); e.setZone(z)
        }
        fun sneak(e: SoundEngine, i: Float = 0.1f) {
            e.setHero(h); e.setIntensity(i); e.setZone(z, silent = true)
        }
        val list = ArrayList<Scenario>()
        // SILENT <-> GUNS HOT, mid-bar (at a time that is no musical boundary for any tempo).
        run {
            val f = once()
            list += Scenario("flip_h2s_mid", 14f, { hot(it) }) { t ->
                if (f(t, 6.37)) { mark("flip->silent"); e.trigger(GameEvent.ModeToggled(true)); e.setZone(z, silent = true) }
            }
        }
        run {
            val f = once()
            list += Scenario("flip_s2h_mid", 14f, { sneak(it) }) { t ->
                if (f(t, 6.37)) { mark("flip->hot"); e.trigger(GameEvent.ModeToggled(false)); e.setZone(z, silent = false) }
            }
        }
        // Hot at CAUTION's and ALERT's heat, then sneaking off calm.
        for (heat in listOf(50, 95)) {
            val f = once()
            list += Scenario("flip_h2s_i$heat", 14f, { hot(it, heat / 100f) }) { t ->
                if (f(t, 6.37)) { mark("flip->silent"); e.setZone(z, silent = true); e.setIntensity(0.1f) }
            }
        }
        // The music auditor's flip: HOT at 0.6, SILENT at 0.3, HOT again.
        run {
            val a = once(); val b = once()
            list += Scenario("audit_flip", 30f, { hot(it, 0.6f) }) { t ->
                if (a(t, 10.0)) { mark("flip->silent"); e.setZone(z, silent = true); e.setIntensity(0.3f) }
                if (b(t, 20.0)) { mark("flip->hot"); e.setZone(z); e.setIntensity(0.6f) }
            }
        }
        // ... right on a bar line.
        run {
            val b = OnBar(6.0)
            list += Scenario("flip_h2s_bar", 14f, { hot(it) }) { t ->
                if (b.check(t, pos)) { mark("flip->silent"); e.setZone(z, silent = true) }
            }
        }
        run {
            val b = OnBar(6.0)
            list += Scenario("flip_s2h_bar", 14f, { sneak(it) }) { t ->
                if (b.check(t, pos)) { mark("flip->hot"); e.setZone(z, silent = false) }
            }
        }
        // Mashing the mode button: a flip every 0.35 s for 3 s, then leave it.
        run {
            var next = 6.0
            var silent = false
            list += Scenario("flip_rapid", 16f, { hot(it) }) { t ->
                if (t >= next && t < 9.2) {
                    silent = !silent; mark(if (silent) "flip->silent" else "flip->hot"); e.setZone(z, silent)
                    next += 0.35
                }
            }
        }
        // Zone changes, in both modes.
        run {
            val f = once()
            list += Scenario("zone_hot", 14f, { hot(it, 0.6f) }) { t ->
                if (f(t, 6.37)) { mark("zone"); e.trigger(GameEvent.ZoneEntered(Zone.LABS)); e.setZone(Zone.LABS) }
            }
        }
        run {
            val f = once()
            list += Scenario("zone_silent", 16f, { sneak(it) }) { t ->
                if (f(t, 6.37)) { mark("zone"); e.setZone(Zone.LABS, silent = true) }
            }
        }
        // Spotted while sneaking: ALERT (the host swaps in the full track), CAUTION, calm again.
        run {
            val a = once(); val c = once(); val k = once()
            list += Scenario("alert_silent", 24f, { sneak(it, 0.15f) }) { t ->
                if (a(t, 6.37)) {
                    mark("alert"); e.trigger(GameEvent.Alerted(0.2f)); e.setZone(z, silent = false); e.setAlert(AlertPhase.ALERT)
                }
                if (c(t, 12.1)) { mark("caution"); e.setAlert(AlertPhase.CAUTION) }
                if (k(t, 17.3)) { mark("calm"); e.setAlert(AlertPhase.CALM); e.setZone(z, silent = true) }
            }
        }
        run {
            val a = once(); val c = once(); val k = once()
            list += Scenario("alert_hot", 24f, { hot(it, 0.3f) }) { t ->
                if (a(t, 6.37)) { mark("alert"); e.trigger(GameEvent.Alerted(0.2f)); e.setAlert(AlertPhase.ALERT) }
                if (c(t, 12.1)) { mark("caution"); e.setAlert(AlertPhase.CAUTION) }
                if (k(t, 17.3)) { mark("calm"); e.setAlert(AlertPhase.CALM) }
            }
        }
        // Title -> hero picker -> browsing -> the run (the first frame's setZone).
        run {
            val p = once(); val b1 = once(); val b2 = once(); val go = once()
            val other = Hero.entries[((h?.ordinal ?: 0) + 1) % Hero.entries.size]
            list += Scenario("menus", 30f, { it.playTitle() }) { t ->
                if (h != null) {
                    if (p(t, 6.37)) { mark("picker"); e.playHeroTheme(h) }
                    if (b1(t, 12.2)) { mark("browse"); e.playHeroTheme(other) }
                    if (b2(t, 14.9)) { mark("browse"); e.playHeroTheme(h) }
                }
                if (go(t, 20.4)) { mark("run"); e.setHero(h); e.setIntensity(0.2f); e.setZone(Zone.ROOFTOP, silent = false) }
            }
        }
        run {
            val p = once(); val r = once()
            list += Scenario("pause", 14f, { hot(it, 0.6f) }) { t ->
                if (p(t, 5.37)) { mark("pause"); e.setPaused(true) }
                if (r(t, 9.1)) { mark("resume"); e.setPaused(false) }
            }
        }
        run {
            val d = once()
            list += Scenario("gameover", 12f, { hot(it, 0.7f) }) { t ->
                if (d(t, 5.37)) { mark("died"); e.trigger(GameEvent.PlayerDied); e.gameOver() }
            }
        }
        // Leaving game over (RETRY, then TITLE): at once, never on the dirge's slow bar.
        run {
            val d = once(); val r = once(); val d2 = once(); val q = once()
            list += Scenario("gameover_exit", 26f, { hot(it, 0.7f) }) { t ->
                if (d(t, 3.37)) { mark("died"); e.trigger(GameEvent.PlayerDied); e.gameOver() }
                if (r(t, 9.61)) { mark("retry"); e.setHero(h); e.setIntensity(0.05f); e.setAlert(AlertPhase.CALM); e.setZone(Zone.ROOFTOP) }
                if (d2(t, 15.37)) { mark("died"); e.trigger(GameEvent.PlayerDied); e.gameOver() }
                if (q(t, 21.83)) { mark("title"); e.playTitle() }
            }
        }
        // The drop: heat builds past the threshold, then a flip lands during the held breath,
        // and a flip back while it's heated.
        run {
            val up = once(); val f = once(); val back = once()
            var gapAt = -1.0
            list += Scenario("drop_flip", 20f, { hot(it, 0.2f) }) { t ->
                if (up(t, 3.0)) { mark("heat"); e.setIntensity(0.9f) }
                if (gapAt < 0 && t > 3.0 && (pos % 16.0) >= 12.2) gapAt = t
                if (gapAt > 0 && f(t, gapAt)) { mark("flip->silent"); e.setZone(z, silent = true) }
                if (back(t, 11.37)) { mark("flip->hot"); e.setZone(z, silent = false) }
            }
        }
        return list
    }
}
