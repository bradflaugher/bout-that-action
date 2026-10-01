package com.bradflaugher.aboutthataction

import android.annotation.SuppressLint
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.KillMethod

/**
 * Short, crisp haptic accents. Two tiers:
 *
 *  - **Confirmations** (light ticks): every gesture the game recognised
 *    (shot, jump, hide) and every landing. There are no buttons to feel, so
 *    this is how the thumb knows a flick registered. They're rate-limited so
 *    a busy moment never blurs into a buzz.
 *  - **Impacts** (clicks, thuds, waveforms): takedowns, hits, explosions.
 *    These always play.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    var enabled = true
    private var lastShot = 0L
    private var lastLight = 0L

    private val shotTick = compose(VibrationEffect.EFFECT_TICK, Part(TICK, 0.5f))
    private val softTick = compose(VibrationEffect.EFFECT_TICK, Part(TICK, 0.3f))
    private val hide = compose(VibrationEffect.EFFECT_CLICK, Part(CLICK, 0.45f))
    private val click = compose(VibrationEffect.EFFECT_CLICK, Part(CLICK, 0.8f))
    private val thud = compose(VibrationEffect.EFFECT_HEAVY_CLICK, Part(THUD, 1f))
    private val empty = compose(VibrationEffect.EFFECT_DOUBLE_CLICK, Part(TICK, 0.3f), Part(TICK, 0.3f, 50))
    private val takedown = compose(VibrationEffect.EFFECT_HEAVY_CLICK, Part(QUICK_RISE, 0.6f), Part(CLICK, 1f, 40))
    private val boom = VibrationEffect.createWaveform(longArrayOf(0, 60, 30, 90), intArrayOf(0, 255, 0, 160), -1)
    private val death = VibrationEffect.createWaveform(longArrayOf(0, 120, 60, 300), intArrayOf(0, 255, 0, 200), -1)
    /** CHALLENGE CLEARED: two quick taps and a long swell. */
    private val cleared = compose(
        VibrationEffect.EFFECT_HEAVY_CLICK, Part(CLICK, 0.7f), Part(CLICK, 0.7f, 110), Part(SLOW_RISE, 0.9f, 60), Part(THUD, 1f, 20),
    )
    private val perk = compose(VibrationEffect.EFFECT_HEAVY_CLICK, Part(SLOW_RISE, 0.7f), Part(CLICK, 0.9f))

    private class Part(val primitive: Int, val scale: Float, val delayMs: Int = 0)

    /**
     * A crisp primitive composition, or, on a motor that can't play one of its primitives
     * (a composition with any unsupported primitive plays nothing at all), the closest
     * predefined effect, which every vibrator renders.
     */
    @SuppressLint("WrongConstant") // Every Part is built from the PRIMITIVE_* constants below.
    private fun compose(fallback: Int, vararg parts: Part): VibrationEffect {
        if (!vibrator.areAllPrimitivesSupported(*parts.map { it.primitive }.toIntArray())) return VibrationEffect.createPredefined(fallback)
        val c = VibrationEffect.startComposition()
        for (p in parts) c.addPrimitive(p.primitive, p.scale, p.delayMs)
        return c.compose()
    }

    fun onEvent(e: GameEvent) {
        if (!enabled) return
        when (e) {
            is GameEvent.Shot -> if (e.byPlayer) {
                // Throttled harder than the minigun's fire rate so full-auto stays a purr.
                val now = System.nanoTime()
                if (now - lastShot > SHOT_GAP_NS) { lastShot = now; lastLight = now; play(shotTick) }
            }
            GameEvent.Jump -> light(softTick)
            GameEvent.Land -> light(softTick)
            GameEvent.HideBox, GameEvent.HideDoor -> light(hide)
            GameEvent.Passage -> light(hide)
            GameEvent.ElevatorCalled -> light(softTick)
            is GameEvent.ModeToggled -> play(if (e.silent) softTick else click)
            GameEvent.SpecialEmpty -> light(empty)
            is GameEvent.PlayerHurt -> play(thud)
            GameEvent.Takedown -> play(takedown)
            is GameEvent.EnemyKilled -> if (e.combo >= 3 || e.how == KillMethod.STOMP) play(click)
            is GameEvent.Explosion -> play(boom)
            GameEvent.LightCrash -> play(click)
            GameEvent.PlayerDied -> play(death)
            GameEvent.ShieldBlock -> play(click)
            is GameEvent.PerkChosen -> play(perk)
            is GameEvent.ZoneEntered -> play(boom)
            GameEvent.Ghost -> play(perk)
            is GameEvent.Suspicious -> light(softTick)
            is GameEvent.Bonk -> play(click)
            is GameEvent.Alerted -> play(click)
            is GameEvent.FloorEventStarted -> play(click)
            GameEvent.BoxKicked, GameEvent.FoundHiding -> play(thud)
            GameEvent.StashLocked -> light(empty)
            is GameEvent.ChallengeCleared -> play(cleared)
            is GameEvent.ChallengeFailed -> play(thud)
            else -> Unit
        }
    }

    /** A confirmation tick, dropped if another light tick just played. */
    private fun light(effect: VibrationEffect) {
        val now = System.nanoTime()
        if (now - lastLight < LIGHT_GAP_NS) return
        lastLight = now
        play(effect)
    }

    private fun play(effect: VibrationEffect) {
        vibrator.vibrate(effect)
    }

    private companion object {
        const val SHOT_GAP_NS = 95_000_000L
        const val LIGHT_GAP_NS = 45_000_000L
        const val TICK = VibrationEffect.Composition.PRIMITIVE_TICK
        const val CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
        const val THUD = VibrationEffect.Composition.PRIMITIVE_THUD
        const val QUICK_RISE = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        const val SLOW_RISE = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
    }
}
