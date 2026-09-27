package com.bradflaugher.aboutthataction.engine

/**
 * SplitMix64: tiny, fast, statistically solid, and trivially reproducible.
 * Floors are generated from [Rng.forKey] streams so any floor can be rebuilt
 * from (seed, floor) alone — the building is endless and never stored.
 */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN
        return mix(state)
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 33) % bound).toInt()
    }

    fun range(min: Float, max: Float): Float = min + (max - min) * nextFloat()

    fun chance(p: Float): Boolean = nextFloat() < p

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    /** Weighted pick; weights need not sum to 1 but must not all be zero. */
    fun <T> pickWeighted(items: List<Pair<T, Float>>): T {
        val total = items.sumOf { it.second.toDouble() }.toFloat()
        var roll = nextFloat() * total
        for ((item, weight) in items) {
            roll -= weight
            if (roll < 0f) return item
        }
        return items.last { it.second > 0f }.first
    }

    companion object {
        private const val GOLDEN = -0x61c8864680b583ebL

        fun mix(z0: Long): Long {
            var z = z0
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        /** An independent stream for (seed, a, b), e.g. (seed, FLOOR, 42). */
        fun forKey(seed: Long, a: Long, b: Long = 0): Rng =
            Rng(mix(mix(seed xor mix(a * 0x9E3779B1L)) + b * 0x632BE5ABL))

        /** Turns any typed seed ("banana", "12345") into a run seed. */
        fun seedFromText(text: String): Long {
            val trimmed = text.trim()
            trimmed.toLongOrNull()?.let { return it }
            var h = 1125899906842597L
            for (c in trimmed.uppercase()) h = 31 * h + c.code
            return mix(h)
        }
    }
}
