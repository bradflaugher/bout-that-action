package com.bradflaugher.aboutthataction.audio

/**
 * The music's quality bars, shared by [MusicLevelsCalibration] (which aims at them) and
 * [MusicQualityTest] (which holds every build to them).
 */
internal object MusicQuality {
    /** The loudness every track sits around (integrated, BS.1770, at the default music volume). */
    const val TARGET = -16.0

    /** How far any track may stray from [TARGET]. */
    const val BAND = 1.5

    /**
     * Where each kind of scene aims, inside the band: a fight a touch louder than a calm bed,
     * so heat still lifts the music while the calm beds stay full; the dirge a touch under.
     */
    fun target(kind: String): Double = TARGET + when (kind) {
        "alert" -> 0.25
        "caution" -> 0.0
        "calm", "sneak" -> -0.5
        "gameover" -> -0.75
        else -> 0.0
    }

    /** The target along GUNS HOT's heat: calm's, rising to CAUTION's and ALERT's. */
    fun targetAt(heat: Float): Double {
        val calm = target("calm")
        val caution = target("caution")
        val alert = target("alert")
        return if (heat <= 0.5f) calm + (caution - calm) * heat / 0.5 else caution + (alert - caution) * ((heat - 0.5) / 0.45).coerceAtMost(1.0)
    }

    /** The highest true peak any music may reach. */
    const val MAX_TRUE_PEAK = -1.0
}
