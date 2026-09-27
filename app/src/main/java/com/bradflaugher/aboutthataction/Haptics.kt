package com.bradflaugher.aboutthataction

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

    private val shotTick = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f).compose()
    private val softTick = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f).compose()
    private val hide = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.45f).compose()
    private val click = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f).compose()
    private val thud = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f).compose()
    private val empty = VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f)
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 50)
        .compose()
    private val takedown = VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.6f)
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 40)
        .compose()
    private val boom = VibrationEffect.createWaveform(longArrayOf(0, 60, 30, 90), intArrayOf(0, 255, 0, 160), -1)
    private val death = VibrationEffect.createWaveform(longArrayOf(0, 120, 60, 300), intArrayOf(0, 255, 0, 200), -1)
    private val perk = VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.7f)
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
        .compose()

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
            is GameEvent.FloorEventStarted -> play(click)
            GameEvent.BoxKicked -> play(thud)
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
    }
}
