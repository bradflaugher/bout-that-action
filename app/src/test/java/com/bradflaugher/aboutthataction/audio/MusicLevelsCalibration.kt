package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Regenerates [MusicLevels] (off by default): `ATA_MUSIC_CALIBRATE=1` renders every song the game
 * plays at every heat it is held at, measures its integrated loudness and trims it onto its target
 * ([MusicQuality]), three passes (the master limiter isn't linear), then rewrites the table in
 * `MusicLevels.kt`. `ATA_MUSIC_SECONDS` sets the render length (default 64 s).
 */
class MusicLevelsCalibration {
    private class Point(val song: String, val slot: Int, val slots: Int, val scene: MusicMatrix.Scene, val target: Double)

    private fun points(seconds: Float): List<Point> = buildList {
        add(Point("title", 0, 1, MusicMatrix.title(seconds + 2f), MusicQuality.target("title")))
        for (h in Hero.entries) add(Point(HeroSongs.theme(h).name, 0, 1, MusicMatrix.theme(h, seconds + 2f), MusicQuality.target("theme")))
        add(Point("gameover", 0, 1, MusicMatrix.gameOver(seconds = seconds + 6f), MusicQuality.target("gameover")))
        for (h in Hero.entries) for (z in Zone.entries) {
            val spec = HeroSongs.forZone(h, z, false)
            val hot = spec.name
            // The heats to measure at, and the target at each (calm and every point before a
            // drop at the calm target; then up the band's gentle slope to ALERT).
            val heats = if (spec.dropThreshold >= 0f) MusicLevels.preDropGrid(spec.dropThreshold).map { it to MusicQuality.target("calm") } +
                MusicLevels.GRID.drop(2).map { it to MusicQuality.targetAt(it) }
            else MusicLevels.GRID.map { it to MusicQuality.targetAt(it) }
            for ((k, ht) in heats.withIndex()) {
                val (heat, target) = ht
                val scene = when (heat) {
                    0f -> MusicMatrix.zone(h, z, MusicMatrix.Heat.CALM, seconds + 4f)
                    0.5f -> MusicMatrix.zone(h, z, MusicMatrix.Heat.CAUTION, seconds + 4f)
                    0.95f -> MusicMatrix.zone(h, z, MusicMatrix.Heat.ALERT, seconds + 4f)
                    else -> MusicMatrix.zoneAt(h, z, heat, seconds + 4f)
                }
                add(Point(hot, k, heats.size, scene, target))
            }
            add(Point(HeroSongs.forZone(h, z, true).name, 0, 1, MusicMatrix.zone(h, z, MusicMatrix.Heat.SNEAK, seconds + 4f), MusicQuality.target("sneak")))
        }
    }

    @Test
    fun calibrate() {
        assumeTrue("set ATA_MUSIC_CALIBRATE=1 to regenerate MusicLevels", System.getenv("ATA_MUSIC_CALIBRATE") != null)
        val seconds = System.getenv("ATA_MUSIC_SECONDS")?.toFloat() ?: 64f
        val pts = points(seconds)
        val trims = HashMap<String, FloatArray>()
        for (p in pts) {
            val have = MusicLevels.table[p.song]
            trims[p.song] = if (have != null && have.size == p.slots) have.copyOf() else FloatArray(p.slots)
        }
        val pool = Executors.newFixedThreadPool(minOf(6, Runtime.getRuntime().availableProcessors()))
        try {
            for (pass in 1..3) {
                MusicLevels.overrides = trims.mapValues { it.value.copyOf() }
                val measured = pool.invokeAll(pts.map { p -> Callable { Loudness.integrated(MusicMatrix.render(p.scene), (p.scene.settle * 48000).toInt()) } }).map { it.get() }
                var worst = 0.0
                for ((p, m) in pts.zip(measured)) {
                    val err = p.target - m
                    worst = maxOf(worst, kotlin.math.abs(err))
                    trims.getValue(p.song)[p.slot] += err.toFloat()
                }
                println("CALIBRATE pass $pass: worst error %.2f LU".format(worst))
            }
        } finally {
            pool.shutdown()
            MusicLevels.overrides = null
        }
        val lines = trims.keys.sortedWith(compareBy({ order(it) }, { it })).joinToString("\n") { k ->
            "        \"$k\" to floatArrayOf(" + trims.getValue(k).joinToString(", ") { "%.2ff".format(it) } + "),"
        }
        val f = File("src/main/java/com/bradflaugher/aboutthataction/audio/MusicLevels.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/bradflaugher/aboutthataction/audio/MusicLevels.kt")
        val src = f.readText()
        val a = src.indexOf("// BEGIN GENERATED")
        val b = src.indexOf("        // END GENERATED")
        f.writeText(src.substring(0, src.indexOf('\n', a) + 1) + lines + "\n" + src.substring(b))
        println("CALIBRATE wrote ${trims.size} trims to ${f.absolutePath}")
    }

    private fun order(name: String) = when {
        name == "title" -> 0
        name.endsWith("-theme") -> 1
        name == "gameover" -> 2
        name.endsWith("-sneak") -> 4
        else -> 3
    }
}
