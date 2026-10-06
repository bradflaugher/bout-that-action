package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.Hero
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The music's measuring tape, off by default. `ATA_MUSIC_WAV=<dir>` renders every scene of
 * [MusicMatrix] (title, themes, every hero x zone x heat, game over) and every transition scenario
 * ([TransitionScenarios]) for every hero as 32-bit float WAVs, with `manifest.json` saying where
 * each one is steady, for `tools/audio/music_qa.py` (loudness, true peak, DC, clicks, spectra,
 * mono and phone-speaker checks, transitions). `ATA_MUSIC_ONLY=title,bull-` keeps scenes whose
 * name starts with one of those.
 */
class MusicRenderExportTest {
    private val dir: String? = System.getProperty("ata.musicwav") ?: System.getenv("ATA_MUSIC_WAV")
    private val only: List<String> = (System.getenv("ATA_MUSIC_ONLY") ?: "").split(',').filter { it.isNotBlank() }

    private fun keep(name: String) = only.isEmpty() || only.any { name.startsWith(it) }

    @Test
    fun exportMusic() {
        assumeTrue("set ATA_MUSIC_WAV=<dir> to render every piece of music", dir != null)
        val out = File(dir!!)
        out.mkdirs()
        val entries = ArrayList<String>()
        for (s in MusicMatrix.all()) {
            if (!keep(s.name)) continue
            val x = MusicMatrix.render(s)
            AudioTestUtil.writeWavFloat(File(out, "${s.name}.wav"), x)
            entries += "{\"name\":\"${s.name}\",\"kind\":\"${s.kind}\",\"settle\":${s.settle}}"
        }
        for (h in Hero.entries) {
            for (sc in TransitionScenarios.all(h)) {
                val name = "trans-${MusicMatrix.tag(h)}-${sc.name}"
                if (!keep(name)) continue
                val run = TransitionScenarios.run(sc)
                AudioTestUtil.writeWavFloat(File(out, "$name.wav"), run.audio)
                File(out, "$name.json").writeText(run.json(MusicMatrix.tag(h), sc.name))
                entries += "{\"name\":\"$name\",\"kind\":\"transition\",\"settle\":0}"
            }
        }
        File(out, "manifest.json").writeText("[\n" + entries.joinToString(",\n") + "\n]\n")
        println("Music WAVs written to ${out.absolutePath}")
    }
}
