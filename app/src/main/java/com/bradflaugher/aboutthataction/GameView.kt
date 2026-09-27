package com.bradflaugher.aboutthataction

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.bradflaugher.aboutthataction.engine.Autopilot
import com.bradflaugher.aboutthataction.engine.Command
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Phase
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.input.GestureInput
import com.bradflaugher.aboutthataction.render.Renderer
import java.util.concurrent.atomic.AtomicInteger

/**
 * The game surface: a dedicated thread steps the [World] on a fixed 120 Hz
 * timestep and draws it through the [Renderer] as fast as the display takes
 * frames. Touches become commands via [GestureInput].
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val host: Host) : SurfaceView(context), SurfaceHolder.Callback {

    interface Host {
        /** Called on the game thread for every simulation event. */
        fun onGameEvent(event: GameEvent, world: World)
        /** Called on the game thread after every simulated frame. */
        fun onFrame(world: World)
        /** Called on the main thread. */
        fun onPauseRequested()
        /** Called on the main thread once the run is over. */
        fun onGameOver(world: World)
        /** Called on the main thread when the attract-mode demo run ends. */
        fun onDemoOver()
    }

    /** The world being played (or shown behind the title screen). */
    @Volatile var world: World? = null
        set(value) {
            field = value
            synchronized(inputLock) { input.cancelAll() }
            reportedOver = false
            accumulator = 0.0
        }

    /** Frames posted to the screen so far, for instrumented tests. */
    @Volatile var framesDrawn = 0L
        private set

    /** Attract mode: the world ignores touches, hides the HUD and plays itself. */
    @Volatile var attract = true
    @Volatile var autopilot: Autopilot? = null
    /** While paused, touches are ignored and any held finger is forgotten. */
    @Volatile var paused = false
        set(value) {
            field = value
            if (value) synchronized(inputLock) { input.cancelAll() }
        }
    @Volatile var topInset = 0f
    @Volatile var bottomInset = 0f
    @Volatile var touchGuide = true

    private val renderer = Renderer()
    private val gfx = AndroidGfx(context)
    private val density = resources.displayMetrics.density
    private val input = GestureInput(density)
    private val inputLock = Any()
    private val pendingPerk = AtomicInteger(-1)
    private val pendingToggle = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pendingGrenade = java.util.concurrent.atomic.AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    /** The one live loop thread; a loop exits as soon as it's no longer this. */
    @Volatile private var thread: Thread? = null
    private var accumulator = 0.0
    @Volatile private var reportedOver = false
    private val startNanos = System.nanoTime()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    /**
     * Portrait thumbs live in the bottom of the screen, often right at its
     * edges. Opt that band out of the system back swipe so a run drag that
     * starts at the bezel steers instead of pausing the game. Android honours
     * up to 200 dp per edge; the upper edges still go back (which pauses).
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return
        val band = (EDGE_EXCLUSION_DP * density).toInt()
        val tall = (EDGE_EXCLUSION_TALL_DP * density).toInt().coerceAtMost(h)
        systemGestureExclusionRects = listOf(
            Rect(0, h - tall, band, h),
            Rect(w - band, h - tall, w, h),
        )
    }

    /**
     * The loop runs only while the activity is resumed AND the surface exists. Stopping on
     * pause matters: an activity on its way out can have its surface disconnected before
     * surfaceDestroyed arrives on the main thread, and drawing into it then crashes hwui.
     */
    private var hostResumed = false

    fun onHostResume() {
        hostResumed = true
        if (holder.surface.isValid) startLoop()
    }

    fun onHostPause() {
        hostResumed = false
        stopLoop()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (hostResumed) startLoop()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = stopLoop()

    private fun startLoop() {
        if (thread != null) return
        thread = Thread(::loop, "game-loop").also {
            it.priority = Thread.MAX_PRIORITY - 1
            it.start()
        }
    }

    /**
     * Waits for the in-flight frame to finish, however long it takes: once onPause or
     * surfaceDestroyed returns the surface may be gone, and a frame still drawing into it
     * crashes hwui's RenderThread. (A slow frame outlasted the old 500 ms cap on the emulator's software
     * GPU.) The loop never waits on the main thread, so this cannot deadlock.
     */
    private fun stopLoop() {
        val old = thread
        thread = null
        old?.join()
    }

    private fun loop() {
        var last = System.nanoTime()
        val me = Thread.currentThread()
        while (thread === me) {
            val now = System.nanoTime()
            val frame = ((now - last) / 1e9).coerceAtMost(0.1)
            last = now
            val w = world
            if (w != null && !paused) {
                w.viewAspect = if (width > 0) height.toFloat() / width else 2.1f
                val perk = pendingPerk.getAndSet(-1)
                if (perk >= 0) w.choosePerk(perk)
                if (pendingToggle.getAndSet(false) && !attract) w.commands += Command.TOGGLE_MODE
                if (pendingGrenade.getAndSet(false) && !attract) w.commands += Command.GRENADE
                synchronized(inputLock) {
                    if (attract) {
                        w.moveAxis = 0
                        input.drain { }
                    } else {
                        w.moveAxis = input.moveAxis
                        input.drain { w.commands += it }
                    }
                }
                accumulator += frame
                var steps = 0
                val pilot = if (attract) autopilot else null
                while (accumulator >= STEP && steps < 12) {
                    pilot?.act(w, STEP.toFloat())
                    w.step(STEP.toFloat())
                    accumulator -= STEP
                    steps++
                }
                if (steps == 12) accumulator = 0.0
                w.drainEvents { host.onGameEvent(it, w) }
                host.onFrame(w)
                if (w.phase == Phase.OVER && !reportedOver) {
                    reportedOver = true
                    if (attract) main.post { host.onDemoOver() } else main.post { host.onGameOver(w) }
                }
            }
            draw(w)
        }
    }

    private fun draw(w: World?) {
        val surface = holder.surface
        if (!surface.isValid) {
            Thread.sleep(16)
            return
        }
        val canvas = try {
            holder.lockHardwareCanvas()
        } catch (_: IllegalStateException) {
            null
        } ?: run {
            Thread.sleep(16)
            return
        }
        try {
            gfx.begin(canvas)
            val time = (System.nanoTime() - startNanos) / 1e9f
            if (w == null) {
                canvas.drawColor(0xFF07060F.toInt())
            } else {
                renderer.render(gfx, w, time, topInset, bottomInset, showHud = !attract)
                if (!attract && touchGuide) drawTouchGuide()
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
            framesDrawn++
        }
    }

    /** A faint ring under the running thumb, so the "joystick" is discoverable. */
    private fun drawTouchGuide() {
        val anchor = synchronized(inputLock) { input.runAnchor } ?: return
        val dir = synchronized(inputLock) { input.moveAxis }
        val r = 34f * density
        gfx.strokeCircle(anchor.first, anchor.second, r, 2f * density, 0x55FFFFFF)
        val ax = anchor.first + dir * r * 1.45f
        val ay = anchor.second
        val s = 9f * density
        gfx.fillPolygon(floatArrayOf(ax + dir * s, ay, ax - dir * s * 0.4f, ay - s, ax - dir * s * 0.4f, ay + s), 0x88FFFFFF.toInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = world ?: return true
        if (attract || paused) return true
        val idx = event.actionIndex
        val id = event.getPointerId(idx)
        val t = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(idx)
                val y = event.getY(idx)
                if (renderer.isPauseButton(x, y, width.toFloat(), height.toFloat(), topInset)) {
                    host.onPauseRequested()
                    return true
                }
                if (w.phase == Phase.PLAYING && renderer.isModeButton(x, y, width.toFloat(), height.toFloat(), topInset)) {
                    pendingToggle.set(true)
                    return true
                }
                if (w.phase == Phase.PLAYING && renderer.isGrenadeButton(x, y, width.toFloat(), height.toFloat(), topInset)) {
                    pendingGrenade.set(true)
                    return true
                }
                if (w.phase == Phase.PERK_CHOICE) {
                    val card = renderer.perkCardAt(x, y, width.toFloat(), height.toFloat(), topInset, bottomInset)
                    if (card >= 0) pendingPerk.set(card)
                    return true
                }
                synchronized(inputLock) { input.down(id, x, y, t) }
            }
            MotionEvent.ACTION_MOVE -> synchronized(inputLock) {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    for (h in 0 until event.historySize) {
                        input.move(pid, event.getHistoricalX(i, h), event.getHistoricalY(i, h), event.getHistoricalEventTime(h))
                    }
                    input.move(pid, event.getX(i), event.getY(i), t)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> synchronized(inputLock) {
                // Palm rejection lifts a pointer with FLAG_CANCELED: it never happened.
                if (event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    input.cancel(id)
                } else {
                    input.up(id, event.getX(idx), event.getY(idx), t)
                }
            }
            // The system took the gesture: drop the fingers (the run stops) but keep
            // flicks and taps that were already recognised.
            MotionEvent.ACTION_CANCEL -> synchronized(inputLock) { input.releaseAll() }
        }
        return true
    }

    companion object {
        private const val STEP = 1.0 / 120.0
        private const val EDGE_EXCLUSION_DP = 32f
        private const val EDGE_EXCLUSION_TALL_DP = 200f
    }
}
