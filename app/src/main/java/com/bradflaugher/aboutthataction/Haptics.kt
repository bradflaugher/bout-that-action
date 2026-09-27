package com.bradflaugher.aboutthataction

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bradflaugher.aboutthataction.engine.GameEvent

/** Short, crisp haptic accents for the events that matter. */
class Haptics(context: Context) {
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    var enabled = true
    private var lastShot = 0L

    private val tick = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f).compose()
    private val click = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f).compose()
    private val thud = VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f).compose()
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
                val now = System.nanoTime()
                if (now - lastShot > 60_000_000L) { lastShot = now; play(tick) }
            }
            is GameEvent.PlayerHurt -> play(thud)
            GameEvent.Takedown -> play(takedown)
            is GameEvent.EnemyKilled -> if (e.combo >= 3) play(click)
            is GameEvent.Explosion -> play(boom)
            GameEvent.LightCrash -> play(click)
            GameEvent.PlayerDied -> play(death)
            GameEvent.ShieldBlock -> play(click)
            is GameEvent.PerkChosen -> play(perk)
            is GameEvent.ZoneEntered -> play(boom)
            GameEvent.Land, GameEvent.HideBox, GameEvent.HideDoor -> play(tick)
            else -> Unit
        }
    }

    private fun play(effect: VibrationEffect) {
        vibrator.vibrate(effect)
    }
}
