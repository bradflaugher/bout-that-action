package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.FloorState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.HallPlan
import com.bradflaugher.aboutthataction.engine.HallState
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the game: a neon-noir cutaway of an endless building, through [Gfx].
 * Pure Kotlin so the unit tests render the very same frames the phone does.
 */
class Renderer {
    private val f = Frame()
    private val building = Building(f)
    private val backdrop = Backdrop(f)
    private val actors = Actors(f)
    private val effects = Effects(f)
    private val hud = Hud(f)

    /** Optional film grain over the whole frame (off by default; a settings toggle). */
    var filmGrain: Boolean
        get() = effects.grain
        set(v) { effects.grain = v }

    /** Optional CRT scanlines over the whole frame (off by default; a settings toggle). */
    var scanlines: Boolean
        get() = effects.scanlines
        set(v) { effects.scanlines = v }

    /**
     * Draw one frame. [time] = real seconds (for idle anims), insets in px keep HUD clear of
     * status/nav bars. [showHud] = false draws the world only (attract mode behind menus).
     */
    fun render(g: Gfx, world: World, time: Float, topInset: Float, bottomInset: Float, showHud: Boolean = true) {
        world.viewAspect = g.height / g.width
        f.setup(g, world, time, topInset, bottomInset)

        // Base fill in the current zone's darkest tone, in case anything leaves a gap.
        g.fillRect(0f, 0f, g.width, g.height, f.palette(world.floors[world.player.floor]).skyTop)

        g.save()
        g.translate(f.shakeX, f.shakeY)
        g.scale(f.s, f.s)
        g.translate(0.6f, -f.camY)

        val first = max(0, floor(f.camY / Geo.FLOOR_H).toInt() - 1)
        val last = floor((f.camY + f.viewH) / Geo.FLOOR_H).toInt()
        f.first = first
        f.last = last

        // Sky and skyline above the roof.
        if (f.camY < Geo.groundY(0) + 1f) backdrop.sky(world.floors[0] != null)

        // Rooms, back to front: walls, windows onto the backdrop, furniture, doors, shafts. Each
        // floor shows one hallway (the player's own, else the main one); a passage slides it.
        for (i in first..last) {
            if (i == 0) world.floors[0]?.let { building.roof(it.hall(0)) } else f.views(i) { building.room(it, backdrop) }
        }
        for (i in first..last) {
            val fs = world.floors[i] ?: continue
            building.slab(fs.hall(0))
        }
        building.outerWalls()
        building.elevators(actors)
        for (i in first..last) f.views(i) { building.sealedShafts(it) }

        // Actors per floor, then darkness on top of the rooms, then the lights that survive it.
        for (i in first..last) f.views(i) { actors.floorActors(i, it) }
        // Mid-slide the player rides in with the new hallway, stepping out of its door.
        val pdx = f.playerSlideDx()
        g.save()
        g.translate(pdx, 0f)
        actors.player()
        g.restore()
        for (i in first..last) f.views(i) { building.darkness(it) }
        val pf = world.player.floorF
        // Focus: the zoomed-out tower shows many floors, so the further a floor is from the
        // player's, the further it recedes. The stage stays at full strength.
        for (i in first..last) {
            if (i == 0 || world.floors[i] == null) continue
            val d = kotlin.math.abs(i - pf)
            if (d < 1f) continue
            val a = (0.2f + 0.09f * (d - 1f)).coerceAtMost(0.46f)
            val top = i * Geo.FLOOR_H + 0.35f
            g.fillRect(-0.6f, top, Geo.FLOOR_W + 0.6f, (i + 1) * Geo.FLOOR_H + 0.35f, Col.alpha(0xFF000000.toInt(), a))
        }
        for (i in first..last) f.views(i) {
            actors.darkEyes(i, it)
            building.lightsAndHazards(it)
        }
        actors.playerOverlay()
        effects.bloom()
        effects.world()
        if (showHud && world.phase == Phase.PLAYING) hud.contextHint()
        g.restore()

        effects.screen()
        effects.texts()
        if (showHud) {
            if (world.phase != Phase.PERK_CHOICE) hud.banner()
            hud.draw()
        }
        if (showHud && world.phase == Phase.PERK_CHOICE) hud.perkOverlay()
    }

    /** During Phase.PERK_CHOICE: index of the perk card at pixel (x, y), or -1. */
    fun perkCardAt(x: Float, y: Float, width: Float, height: Float, topInset: Float, bottomInset: Float): Int =
        Hud.perkCardAt(x, y, width, height, topInset, bottomInset)

    /** True if (x, y) hits the HUD pause button. */
    fun isPauseButton(x: Float, y: Float, width: Float, height: Float, topInset: Float): Boolean =
        Hud.isPauseButton(x, y, width, height, topInset)

    /** True if (x, y) hits the HUD's GUNS HOT / SILENT toggle beside the pause button. */
    fun isModeButton(x: Float, y: Float, width: Float, height: Float, topInset: Float): Boolean =
        Hud.isModeButton(x, y, width, height, topInset)
}

/** A per-hallway seed for looks: hallway A keeps the floor's own, the others get their own. */
internal val HallPlan.look: Int get() = if (hall == 0) index else index * 31 + hall * 977 + 5000

/** Per-frame shared state for the renderer parts. */
internal class Frame {
    lateinit var g: Gfx
    lateinit var w: World
    val poly = Poly()

    /** Real seconds, for idle animation. */
    var t = 0f
    /** Simulation seconds (stops in menus, slows in bullet time). */
    var wt = 0f
    /** Pixels per world unit. */
    var s = 1f
    var camY = 0f
    var viewH = 0f
    var topInset = 0f
    var bottomInset = 0f
    var shakeX = 0f
    var shakeY = 0f
    var first = 0
    var last = 0
    var dt = 0f
    private var lastT = -1f

    /** Screen-space rect (l, t, r, b) the HUD reserved this frame, e.g. the context chip; floating text avoids it. */
    val reserved = FloatArray(4)
    var hasReserved = false

    // The hallway slide: when the player's floor swaps hallways, both slide across for a moment.
    private var viewFloor = Int.MIN_VALUE
    private var viewHall = 0
    var slideFloor = Int.MIN_VALUE
        private set
    var slideFrom = 0
        private set
    var slideTo = 0
        private set
    private var slideAt = -9f
    /** 0..1 progress of the running slide (1 = none). */
    var slideU = 1f
        private set
    /** Horizontal offset of the hallway being drawn right now (non-zero only mid-slide). */
    var viewDx = 0f

    private fun trackSlide() {
        val pf = w.player.floor
        val vh = w.viewHall(pf)
        if (pf == viewFloor && vh != viewHall && dt > 0f) {
            slideFloor = pf
            slideFrom = viewHall
            slideTo = vh
            slideAt = t
        }
        if (pf != viewFloor) slideFloor = Int.MIN_VALUE
        viewFloor = pf
        viewHall = vh
        slideU = if (slideFloor == Int.MIN_VALUE) 1f else ((t - slideAt) / SLIDE_TIME).coerceIn(0f, 1f)
        if (slideU >= 1f) slideFloor = Int.MIN_VALUE
    }

    /** Starts a slide by hand (screenshots): floor [fi] from hallway [from] to [to], [u] of the way. */
    fun forceSlide(fi: Int, from: Int, to: Int, u: Float) {
        slideFloor = fi
        slideFrom = from
        slideTo = to
        slideAt = t - u * SLIDE_TIME
        slideU = u
    }

    /**
     * Draw floor [fi]'s visible hallway with [draw]. Mid-slide the old hallway whooshes out and
     * the new one in (clipped to the floor), each drawn at its own offset ([viewDx]).
     */
    inline fun views(fi: Int, draw: (HallState) -> Unit) {
        val fs = w.floors[fi] ?: return
        if (fi != slideFloor || slideU >= 1f) {
            draw(fs.hall(w.viewHall(fi)))
            return
        }
        val e = slideEase(slideU)
        val dir = if (slideTo > slideFrom) 1f else -1f
        val span = Geo.FLOOR_W + 0.6f
        for (k in 0..1) {
            val hall = if (k == 0) slideFrom else slideTo
            val dx = if (k == 0) -dir * span * e else dir * span * (1f - e)
            if (kotlin.math.abs(dx) >= span) continue
            g.save()
            g.clipRect(0f, fi * Geo.FLOOR_H, Geo.FLOOR_W, (fi + 1) * Geo.FLOOR_H + 0.36f)
            g.translate(dx, 0f)
            viewDx = dx
            draw(fs.hall(hall))
            viewDx = 0f
            g.restore()
        }
        slideStreak(fi, dir, dir * span * (1f - e), e)
    }

    /**
     * The whoosh: a bright seam where the incoming hallway's edge leads, trailing speed lines
     * across the floor, in the passages' wayfinding green. Fades as the slide lands.
     */
    fun slideStreak(fi: Int, dir: Float, dxTo: Float, e: Float) {
        val seam = if (dir > 0f) dxTo else dxTo + Geo.FLOOR_W
        if (seam <= 0f || seam >= Geo.FLOOR_W) return
        val top = fi * Geo.FLOOR_H + 0.35f
        val gy = (fi + 1) * Geo.FLOOR_H
        val a = (1f - e) * 0.9f + 0.1f
        g.save()
        g.clipRect(0f, top, Geo.FLOOR_W, gy)
        g.blend(Gfx.Blend.ADD)
        for (k in 0 until 5) {
            val w0 = 0.05f + k * 0.16f
            val x0 = if (dir > 0f) seam else seam - w0
            g.fillRect(x0, top, x0 + w0, gy, Col.alpha(Building.PASSAGE, 0.1f * a))
        }
        g.fillRect(seam - 0.02f, top, seam + 0.02f, gy, Col.alpha(0xFFE8FFF4.toInt(), 0.8f * a))
        for (k in 0 until 7) {
            val y = top + 0.3f + (gy - top - 0.6f) * ((k * 0.618f) % 1f)
            val len = (1.2f + (k % 3) * 0.9f) * (0.4f + a)
            val x1 = seam + dir * len
            g.line(seam, y, x1, y, 0.025f, Col.alpha(Building.PASSAGE, 0.35f * a))
        }
        g.blend(Gfx.Blend.NORMAL)
        g.restore()
    }

    /** Offset of the hallway the player is in, while their floor is mid-slide (else 0). */
    fun playerSlideDx(): Float {
        if (slideFloor != w.player.floor || slideU >= 1f || w.player.hall != slideTo) return 0f
        val dir = if (slideTo > slideFrom) 1f else -1f
        return dir * (Geo.FLOOR_W + 0.6f) * (1f - slideEase(slideU))
    }

    /** Fast out, soft landing: reads as a whoosh. */
    fun slideEase(u: Float): Float = 1f - (1f - u) * (1f - u) * (1f - u)

    fun setup(g: Gfx, w: World, t: Float, topInset: Float, bottomInset: Float) {
        this.g = g
        g.blend(Gfx.Blend.NORMAL)
        hasReserved = false
        this.w = w
        dt = if (lastT < 0f) 0f else (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        this.t = t
        this.wt = w.time
        this.topInset = topInset
        this.bottomInset = bottomInset
        s = g.width / Geo.VIEW_W
        camY = w.camY
        viewH = g.height / s
        trackSlide()
        val k = w.shake * w.shake * 0.42f + w.shake * 0.08f
        shakeX = (sin(t * 91f) + sin(t * 57f + 1.3f) * 0.6f) * k * s * 0.5f
        shakeY = (sin(t * 83f + 2.1f) + sin(t * 47f) * 0.6f) * k * s * 0.5f
    }

    fun palette(hs: HallState?): Palette {
        if (hs == null) return Palette.of(w.zone)
        val plan = hs.plan
        return when {
            plan.index == 0 -> Palette.of(Zone.ROOFTOP)
            plan.isVoid -> Palette.void(plan.zone, t, plan.index)
            else -> Palette.of(plan.zone)
        }
    }

    fun palette(fs: FloorState?): Palette = palette(fs?.hall(0))

    /** Draws [text] at world (x, y) with a pixel-space font size (crisp on every backend). */
    fun worldText(text: String, x: Float, y: Float, sizeWorld: Float, color: Int, font: Gfx.Font = Gfx.Font.HUD, align: Gfx.Align = Gfx.Align.CENTER) {
        g.save()
        g.translate(x, y)
        g.scale(1f / s, 1f / s)
        g.text(text, 0f, 0f, sizeWorld * s, color, font, align)
        g.restore()
    }

    /** Neon glow line: soft halo + bright core. */
    fun glowLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int, core: Int = 0xFFFFFFFF.toInt(), halo: Float = 1f) {
        g.line(x1, y1, x2, y2, width * 4f, Col.fade(color, 0.16f * halo))
        g.line(x1, y1, x2, y2, width * 2.2f, Col.fade(color, 0.35f * halo))
        g.line(x1, y1, x2, y2, width, color)
        if (core != 0) g.line(x1, y1, x2, y2, width * 0.4f, Col.fade(core, Col.a(color) / 255f * 0.85f))
    }

    fun glowDot(x: Float, y: Float, r: Float, color: Int, intensity: Float = 1f) {
        g.fillCircle(x, y, r * 3f, Col.fade(color, 0.1f * intensity))
        g.fillCircle(x, y, r * 1.8f, Col.fade(color, 0.25f * intensity))
        g.fillCircle(x, y, r, Col.fade(color, intensity))
    }

    fun visibleY(top: Float, bottom: Float) = bottom >= camY - 1f && top <= camY + viewH + 1f

    val playerDrawFloor: Int get() = w.player.floor
    val inPerk: Boolean get() = w.phase == Phase.PERK_CHOICE
    val dying: Boolean get() = w.phase == Phase.DYING || w.phase == Phase.OVER

    /** Is hallway [hall] of floor [floor] on screen (the new hallway, mid-slide)? */
    fun shows(floor: Int, hall: Int) = w.viewHall(floor) == hall

    /** Is the player in hallway [hs]? */
    fun playerIn(hs: HallState) = w.player.floor == hs.plan.index && w.player.hall == hs.plan.hall

    fun playerHiddenInDoor(x: Float) =
        (w.player.state == PlayerState.DOOR || w.player.state == PlayerState.PASSAGE) && kotlin.math.abs(w.player.anchorX - x) < 0.05f

    fun clamp01(v: Float) = min(1f, max(0f, v))

    companion object {
        /** Seconds a hallway slide takes. */
        const val SLIDE_TIME = 0.26f
    }
}
