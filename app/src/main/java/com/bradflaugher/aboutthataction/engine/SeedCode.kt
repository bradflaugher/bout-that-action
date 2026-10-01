package com.bradflaugher.aboutthataction.engine

/**
 * Shareable seed codes, Balatro-style: 8 characters from an alphabet with no look-alikes
 * (no I, O, 0 or 1), 5 bits each, so a code is exactly a 40-bit run seed. Shown as
 * "K7QM 2XAB". Random runs draw a seed below [LIMIT], so every run has a code.
 *
 * Parsing is deliberately plain: uppercase, drop everything that isn't a letter or digit,
 * and if that leaves 8 characters all in [ALPHABET], it's a code; anything else isn't.
 * There's no look-alike mapping (the alphabet already avoids them, and the menus show codes
 * in the same font people type them from).
 */
object SeedCode {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 8

    /** Seeds below this have a code: 2^40. */
    const val LIMIT = 1L shl 40

    /** The 8-character code for a seed in [0, LIMIT), or null for any other seed. */
    fun encode(seed: Long): String? {
        if (seed < 0 || seed >= LIMIT) return null
        val out = CharArray(LENGTH)
        var v = seed
        for (i in LENGTH - 1 downTo 0) {
            out[i] = ALPHABET[(v and 31).toInt()]
            v = v ushr 5
        }
        return String(out)
    }

    /** The seed a typed or pasted code stands for, or null when it isn't a code. */
    fun decode(text: String): Long? {
        val n = normalize(text)
        if (n.length != LENGTH) return null
        var v = 0L
        for (c in n) {
            val d = ALPHABET.indexOf(c)
            if (d < 0) return null
            v = (v shl 5) or d.toLong()
        }
        return v
    }

    /** Uppercase, letters and digits only. */
    fun normalize(text: String): String = text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** "K7QM2XAB" → "K7QM 2XAB". */
    fun pretty(code: String): String = if (code.length == LENGTH) code.substring(0, 4) + " " + code.substring(4) else code

    /** How a run's seed reads: its code, or just the number for a seed without one. */
    fun labelOf(seed: Long): String = encode(seed)?.let(::pretty) ?: seed.toString()

    /** What a share message carries: a code, and maybe a difficulty (a preset or a whole custom curve) and a hero. */
    data class Shared(val code: String?, val preset: Difficulty.Preset?, val hero: Hero?, val curve: Difficulty? = null)

    /**
     * A custom curve as a tag a share message can carry and [find] can read back:
     * "CURVE 0.8/1.7/5/2/50" (starting heat / ramp / heat cap / hearts / start floor).
     */
    fun curveTag(d: Difficulty): String =
        "CURVE " + listOf(num(d.start), num(d.ramp), num(d.cap), d.hearts.toString(), d.startFloor.toString()).joinToString("/")

    /** Up to two decimals (AGENT starts at 0.15), no trailing zeros: 0.8, 1.7, 5. */
    private fun num(f: Float): String =
        String.format(java.util.Locale.US, "%.2f", f).trimEnd('0').trimEnd('.')

    private val CURVE = Regex("CURVE\\s*(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)/(\\d+)/(\\d+)")

    /** A [curveTag] read back, clamped to what the CUSTOM RUN steppers allow; null if there's none. */
    private fun curveIn(up: String): Difficulty? {
        val m = CURVE.find(up) ?: return null
        val (start, ramp, cap, hearts, floor) = m.destructured
        val starts = Zone.entries.filter { it != Zone.ROOFTOP }.map { it.startFloor }
        return Difficulty(
            start = start.toFloat().coerceIn(0f, 5f),
            ramp = ramp.toFloat().coerceIn(0f, 4f),
            cap = cap.toFloat().coerceIn(0.5f, 8f),
            hearts = hearts.toInt().coerceIn(1, 9),
            startFloor = floor.toInt().takeIf { it == 0 || it in starts } ?: 0,
        )
    }

    private const val C = "[A-HJ-NP-Z2-9]"
    private val AFTER_SEED = Regex("SEED\\W*($C{4})[ -]?($C{4})(?![A-Z0-9])")
    private val ANY_CODE = Regex("(?<![A-Z0-9])($C{4})[ -]?($C{4})(?![A-Z0-9])")
    private fun word(w: String) = Regex("(?<![A-Z])$w(?![A-Z])")
    private val PRESETS = listOf(
        // Most specific first: "STRAIGHT TO HELL" and "HELL" are the same template.
        word("HELL") to Difficulty.Preset.STRAIGHT_TO_HELL,
        word("BRUTAL") to Difficulty.Preset.BRUTAL,
        word("CHILL") to Difficulty.Preset.CHILL,
        word("AGENT") to Difficulty.Preset.AGENT,
    )

    /**
     * Pulls a code (and a difficulty and hero, when named) out of a pasted message like
     * "I hit B42 as MONKEY on AGENT in 'Bout That Action. Seed K7QM 2XAB. Beat that."
     * A code right after the word SEED wins; otherwise the first 4+4 code with a digit in it
     * (so ordinary words like "BEAT THAT" aren't mistaken for one); otherwise the whole text
     * if it is a code by itself.
     */
    fun find(message: String): Shared {
        val up = message.uppercase()
        val code = AFTER_SEED.find(up)?.let { it.groupValues[1] + it.groupValues[2] }
            ?: ANY_CODE.findAll(up).map { it.groupValues[1] + it.groupValues[2] }.firstOrNull { c -> c.any { it.isDigit() } }
            ?: normalize(up).takeIf { decode(it) != null }
        // The difficulty is read from the text around the code, not from inside it.
        val rest = if (code == null) up else up.replace(Regex("${code.substring(0, 4)}[ -]?${code.substring(4)}"), " ")
        val curve = curveIn(rest)
        val preset = if (curve != null) null else PRESETS.firstOrNull { it.first.containsMatchIn(rest) }?.second
        val hero = Hero.entries.firstOrNull { word(it.name).containsMatchIn(rest) }
        return Shared(code, preset, hero, curve)
    }
}
