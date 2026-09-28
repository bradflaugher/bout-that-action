package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Hero
import kotlin.math.sin

/**
 * A hero standing on the picker: the in-game figure drawn large through the real actor
 * painter ([HeroArt], the same rig, costume and gun), idling, so the menu shows exactly who
 * you'll play. Pure [Gfx], no World, no allocation per frame. Not thread-safe: call it from
 * one thread (the UI's).
 */
object HeroPortrait {
    private val f = Frame()
    private val p = ActorPaint(f)
    private val k = Rig()
    private val body = ActorBody(p, k)
    private val art = HeroArt(f, p, k, body)

    /** The figure's height in world units, feet to crown (with a little headroom for hair). */
    private const val FIGURE = 1.92f

    /**
     * Draws [hero] standing, feet at ([cx], [footY]), [height] px tall, idling at [time] s,
     * facing right (or left if [facing] < 0), on a soft pool of the hero's own colour.
     */
    fun draw(g: Gfx, hero: Hero, cx: Float, footY: Float, height: Float, time: Float, facing: Int = 1) {
        if (height <= 1f) return
        val s = height / FIGURE
        f.g = g
        f.t = time
        f.s = s
        f.dt = 0f
        art.hero = hero
        g.save()
        g.translate(cx, footY)
        g.scale(s, s)
        stage(hero, time)
        p.reset()
        // The game's ink is ~2.4 px on a ~140 px figure; blown up that reads as a cartoon, so a
        // portrait keeps the line finer than the scale alone would make it.
        p.weight(((2.4f + height * 0.0065f) / s).coerceIn(0.012f, ActorPaint.OUT))
        p.contactShadow(0f, 0f, 0.46f, 0f)
        pose(if (facing < 0) -1 else 1, time)
        art.draw(ghost = false)
        p.reset()
        g.restore()
    }

    /** The hero's own light on the floor: a soft pool, a crisp ring, a backlight up the figure. */
    private fun stage(hero: Hero, time: Float) {
        val g = f.g
        val c = hero.color
        val pulse = 0.88f + 0.12f * sin(time * 2.2f)
        g.blend(Gfx.Blend.ADD)
        g.save()
        // Kept inside the figure's own box: the picker lays its tiles out by [height].
        g.translate(0f, -0.95f)
        g.scale(0.75f, 1f)
        g.glow(0f, 0f, 0.98f, Col.alpha(c, 0.32f))
        g.restore()
        g.save()
        g.translate(0f, -0.01f)
        g.scale(1f, 0.22f)
        g.glow(0f, 0f, 0.9f, Col.alpha(c, 0.55f * pulse))
        g.strokeCircle(0f, 0f, 0.5f, 0.04f, Col.alpha(art.kit.rim, 0.6f * pulse))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
    }

    /** The game's own idle at low ready. */
    private fun pose(dir: Int, time: Float) {
        art.setup(dir, 0f)
        val breathe = sin(time * 2.4f)
        k.stand(0f, 0.035f + breathe * 0.006f, 0.1f, -0.13f)
        k.spine(0.05f + breathe * 0.01f, 0f)
        k.armFK(k.armB, -0.12f - breathe * 0.02f, 0.3f)
        k.armFK(k.armF, 0.25f, 0.5f)
        art.gunKind = 0
        art.gunX = k.hipX + 0.2f * dir
        art.gunY = k.hipY + 0.02f + breathe * 0.004f
        art.gunUp = -0.85f
        k.ik(k.armF, art.gunX, art.gunY, false)
        art.showGun = true
        art.flash = false
        art.magInHand = false
        art.holstered = false
        art.live = true
        art.run = 0f
        art.fall = 0f
        art.bob = 0f
        art.idle = sin(time * 1.7f) * 0.03f
        art.dim = 1f
    }
}
