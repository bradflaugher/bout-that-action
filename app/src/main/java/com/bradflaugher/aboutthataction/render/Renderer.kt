package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.FloorState
import com.bradflaugher.aboutthataction.engine.Geo
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

        // Rooms, back to front: walls, windows onto the backdrop, furniture, doors, shafts, stairs.
        for (i in first..last) {
            val fs = world.floors[i] ?: continue
            if (i == 0) building.roof(fs) else building.room(fs, backdrop)
        }
        for (i in first..last) {
            val fs = world.floors[i] ?: continue
            building.slab(fs)
        }
        building.outerWalls()
        building.elevators(actors)

        // Actors per floor, then darkness on top of the rooms, then the lights that survive it.
        for (i in first..last) {
            val fs = world.floors[i] ?: continue
            actors.floorActors(i, fs)
        }
        actors.player()
        for (i in first..last) {
            val fs = world.floors[i] ?: continue
            building.darkness(fs)
        }
        val pf = world.player.floorF
        for (i in first..last) {
            if (i == 0 || world.floors[i] == null) continue
            if (i.toFloat() > pf - 1f && i.toFloat() < pf + 1f) continue
            val top = i * Geo.FLOOR_H + 0.35f
            g.fillRect(-0.6f, top, Geo.FLOOR_W + 0.6f, (i + 1) * Geo.FLOOR_H + 0.35f, 0x33000000)
        }
        for (i in first..last) {
            val fs = world.floors[i] ?: continue
            actors.darkEyes(i, fs)
            building.lightsAndHazards(fs)
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
            if (world.phase == Phase.PERK_CHOICE) hud.perkOverlay()
        }
    }

    /** During Phase.PERK_CHOICE: index of the perk card at pixel (x, y), or -1. */
    fun perkCardAt(x: Float, y: Float, width: Float, height: Float, topInset: Float, bottomInset: Float): Int =
        Hud.perkCardAt(x, y, width, height, topInset, bottomInset)

    /** True if (x, y) hits the HUD pause button. */
    fun isPauseButton(x: Float, y: Float, width: Float, height: Float, topInset: Float): Boolean =
        Hud.isPauseButton(x, y, width, height, topInset)
}

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
        val k = w.shake * w.shake * 0.42f + w.shake * 0.08f
        shakeX = (sin(t * 91f) + sin(t * 57f + 1.3f) * 0.6f) * k * s * 0.5f
        shakeY = (sin(t * 83f + 2.1f) + sin(t * 47f) * 0.6f) * k * s * 0.5f
    }

    fun palette(fs: FloorState?): Palette {
        if (fs == null) return Palette.of(w.zone)
        val plan = fs.plan
        return when {
            plan.index == 0 -> Palette.of(Zone.ROOFTOP)
            plan.isVoid -> Palette.void(plan.zone, t, plan.index)
            else -> Palette.of(plan.zone)
        }
    }

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
    fun playerHiddenInDoor(x: Float) = w.player.state == PlayerState.DOOR && kotlin.math.abs(w.player.anchorX - x) < 0.05f

    fun clamp01(v: Float) = min(1f, max(0f, v))
}
