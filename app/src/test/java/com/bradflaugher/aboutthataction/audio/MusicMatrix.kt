package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.AlertPhase
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone

/**
 * Every piece of music the game plays, as the app drives it: the title, each hero's theme, each
 * hero in each zone at every heat the game holds the music at (GUNS HOT calm, CAUTION, ALERT, and
 * SILENT's sneak mix), and game over. [MusicQualityTest] measures a sample of it on every build;
 * [MusicRenderExportTest] renders all of it for `tools/audio/music_qa.py`.
 */
internal object MusicMatrix {
    /** The heats a run actually sits at (GUNS HOT calm, CAUTION's floor, ALERT's floor) and SILENT's sneak mix. */
    enum class Heat(val tag: String, val intensity: Float, val alert: AlertPhase, val silent: Boolean) {
        CALM("calm", 0f, AlertPhase.CALM, false),
        CAUTION("caution", 0.5f, AlertPhase.CAUTION, false),
        ALERT("alert", 0.95f, AlertPhase.ALERT, false),
        SNEAK("sneak", 0.1f, AlertPhase.CALM, true),
    }

    /** One scene: how to start it, how long to play, and from when it is steady (fade-in and drop landing aside). */
    class Scene(val name: String, val seconds: Float, val settle: Float, val kind: String, val start: (SoundEngine) -> Unit)

    fun title(seconds: Float = 120f) = Scene("title", seconds, 2f, "title") { it.playTitle() }

    fun theme(h: Hero, seconds: Float = 40f) = Scene("${tag(h)}-theme", seconds, 2f, "theme") { it.playHeroTheme(h) }

    fun zone(h: Hero, z: Zone, heat: Heat, seconds: Float = 36f) =
        Scene("${tag(h)}-${z.name.lowercase()}-${heat.tag}", seconds, 4f, heat.tag) { e ->
            e.setHero(h); e.setIntensity(heat.intensity); e.setAlert(heat.alert); e.setZone(z, heat.silent)
        }

    /** GUNS HOT at any steady [heat] (calm: no alert floor). */
    fun zoneAt(h: Hero, z: Zone, heat: Float, seconds: Float = 24f) =
        Scene("${tag(h)}-${z.name.lowercase()}-heat${(heat * 100).toInt()}", seconds, 4f, "heat") { e ->
            e.setHero(h); e.setIntensity(heat); e.setZone(z)
        }

    /** Game over from a hot run: the stinger, then the dirge (steady once it has swelled in). */
    fun gameOver(h: Hero = Hero.BULL, seconds: Float = 36f) = Scene("gameover", seconds, 6f, "gameover") { e ->
        e.setHero(h); e.setIntensity(0.7f); e.setZone(Zone.TOWER)
        e.render(FloatArray(4800 * 2), 4800) // a tenth of a second of the run, then death
        e.gameOver()
    }

    fun all(): List<Scene> = buildList {
        add(title())
        for (h in Hero.entries) add(theme(h))
        for (h in Hero.entries) for (z in Zone.entries) for (heat in Heat.entries) add(zone(h, z, heat))
        add(gameOver())
    }

    fun tag(h: Hero) = h.name.lowercase()

    /** Render [s] through the whole engine (master bus included) at the default music volume. */
    fun render(s: Scene, musicVolume: Float = 0.8f, chunk: Int = 256): FloatArray {
        val e = SoundEngine()
        e.setMusicVolume(musicVolume)
        s.start(e)
        return AudioTestUtil.render(e, s.seconds, chunk)
    }
}
