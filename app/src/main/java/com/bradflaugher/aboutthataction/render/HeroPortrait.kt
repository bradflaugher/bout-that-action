package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero

/**
 * A hero standing on the picker: the in-game figure drawn large through the real actor
 * painter, so the menu shows exactly who you'll play. Pure [Gfx]; no allocation per frame.
 */
object HeroPortrait {
    /**
     * Draws [hero] standing, feet at ([cx], [footY]), [height] px tall, idling at [time] s.
     * TODO(render agent): the real figure.
     */
    fun draw(g: Gfx, hero: Hero, cx: Float, footY: Float, height: Float, time: Float) {
        g.fillCircle(cx, footY - height * 0.5f, height * 0.3f, hero.color)
    }
}
