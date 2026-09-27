package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.FloorLabel

/**
 * Allocation-free access to [FloorLabel] strings for things drawn every frame (elevator
 * indicators, station plaques, room plates): direct-mapped caches, filled on first sight.
 * Not thread-safe; the renderer runs on one thread.
 */
internal object EnvLabels {
    private const val SIZE = 1024
    private val shortKeys = IntArray(SIZE) { Int.MIN_VALUE }
    private val shortVals = arrayOfNulls<String>(SIZE)
    private val roomKeys = IntArray(SIZE) { Int.MIN_VALUE }
    private val roomVals = arrayOfNulls<String>(SIZE)

    /** [FloorLabel.short]: "R", "42", "B7". */
    fun short(index: Int): String {
        val slot = index and (SIZE - 1)
        if (shortKeys[slot] == index) return shortVals[slot]!!
        val s = FloorLabel.short(index)
        shortKeys[slot] = index
        shortVals[slot] = s
        return s
    }

    /** A room number for door [i] on floor [index]: the floor label then two digits, "4214", "B714". */
    fun room(index: Int, i: Int): String {
        val key = index * 8 + i
        val slot = key and (SIZE - 1)
        if (roomKeys[slot] == key) return roomVals[slot]!!
        val s = short(index) + (10 + i * 2)
        roomKeys[slot] = key
        roomVals[slot] = s
        return s
    }
}
