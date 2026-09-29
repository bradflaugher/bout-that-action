package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.FloorEvent
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.KillMethod
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.min

/**
 * Sound design: maps every [GameEvent] to a small stack of [SfxVoice] layers. Pitch and
 * timbre are jittered per trigger (deterministically, from [rng]) so repeats don't fatigue.
 */
internal class SfxPlayer(private val bank: SfxBank, private val rng: Rng) {

    /** Per-event loudness trim applied to every layer (balanced by measurement against the mix). */
    private var trim = 1f

    private inline fun voice(setup: SfxVoice.() -> Unit) {
        val v = bank.alloc()
        v.setup()
        v.gain *= trim
        bank.start(v)
    }

    private fun trimFor(e: GameEvent): Float = when (e) {
        is GameEvent.Shot -> if (e.byPlayer && !e.heavy) 1.3f else 1f
        is GameEvent.BulletHit -> 1.3f
        GameEvent.Jump -> 1.7f
        GameEvent.HideBox -> 1.8f
        GameEvent.Unhide -> 2.4f
        GameEvent.DoorOpen -> 1.2f
        GameEvent.Passage -> 1.6f
        GameEvent.ElevatorCalled -> 1.5f
        is GameEvent.ModeToggled -> 2f
        GameEvent.ShieldBlock -> 1.7f
        is GameEvent.Pickup -> if (e.kind == PickupKind.MEDKIT || e.kind == PickupKind.SHIELD) 1.4f else 1.9f
        is GameEvent.PerkChosen -> 2.8f
        GameEvent.LightShot -> 1.8f
        GameEvent.LightCrash -> 1.4f
        is GameEvent.HazardFire -> 4.5f
        is GameEvent.FloorReached -> 2f
        GameEvent.SpecialEmpty, GameEvent.Reload -> 2f
        is GameEvent.Suspicious -> 1.6f
        GameEvent.Ghost -> 1.8f
        is GameEvent.Snore -> 1.2f
        GameEvent.Muzak -> 1.5f
        else -> 1f
    }

    /** Random multiplier 1 ± [a]. */
    private fun j(a: Float = 0.05f) = rng.vary(a)

    fun play(e: GameEvent) {
        trim = trimFor(e)
        when (e) {
            is GameEvent.Shot -> shot(e.byPlayer, e.heavy, e.pan)
            is GameEvent.BulletHit -> bulletHit(e.onPlayer, e.armored, e.pan)
            is GameEvent.EnemyKilled -> enemyKilled(e.kind, e.how, e.combo, e.pan)
            GameEvent.Takedown -> takedown()
            GameEvent.Jump -> jump()
            GameEvent.Land -> land()
            GameEvent.HideBox -> hideBox()
            GameEvent.HideDoor -> hideDoor()
            GameEvent.Unhide -> unhide()
            GameEvent.DoorOpen -> doorOpen()
            GameEvent.ElevatorDing -> elevatorDing()
            GameEvent.ElevatorMove -> elevatorMove()
            GameEvent.Passage -> passage()
            GameEvent.ElevatorCalled -> elevatorCall()
            is GameEvent.ModeToggled -> if (e.silent) muffle() else cock()
            is GameEvent.PlayerHurt -> playerHurt(e.hpLeft)
            GameEvent.ShieldBlock -> shieldBlock()
            GameEvent.PlayerDied -> playerDied()
            is GameEvent.Pickup -> pickup(e.kind)
            GameEvent.PerkOffered -> perkOffered()
            is GameEvent.PerkChosen -> perkChosen(e.perk.ordinal)
            GameEvent.LightShot -> lightShot()
            GameEvent.LightCrash -> lightCrash(0f)
            is GameEvent.Explosion -> explosion(e.big, e.pan)
            is GameEvent.HazardFire -> hazardFire(e.pan)
            is GameEvent.FloorReached -> floorTick(e.floor)
            is GameEvent.ZoneEntered -> zoneImpact(e.zone)
            GameEvent.SlowMoStart -> slowMo(down = true)
            GameEvent.SlowMoEnd -> slowMo(down = false)
            GameEvent.SpecialEmpty -> emptyClick()
            GameEvent.Reload -> reload()
            is GameEvent.Suspicious -> huh(e.pan)
            is GameEvent.Alerted -> alerted(e.pan)
            GameEvent.Ghost -> ghost()
            is GameEvent.FloorEventStarted -> floorEvent(e.event)
            GameEvent.Muzak -> muzak()
            is GameEvent.Snore -> snore(e.pan)
            GameEvent.BoxKicked -> boxKicked()
            GameEvent.FoundHiding -> hideDoor()
            GameEvent.StashLocked -> emptyClick()
        }
    }

    // ---- Weapons ------------------------------------------------------------------------

    private fun shot(byPlayer: Boolean, heavy: Boolean, pan: Float) {
        if (byPlayer && !heavy) {
            // Suppressed pistol: a tight "pfft", a punchy body and the slide click.
            voice {
                level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 2800f * j(0.1f); cut1 = 900f; cutTime = 0.05f
                q = 1.1f; attack = 0.0008f; decay = 0.09f * j(0.1f); gain = 0.5f; this.pan = pan; reverb = 0.06f; priority = 0.8f
            }
            voice {
                wave = Wave.SINE; f0 = 210f * j(); f1 = 60f; sweep = 0.05f; decay = 0.08f; gain = 0.55f; this.pan = pan
                reverb = 0.02f; priority = 0.8f
            }
            voice {
                level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 6500f; cut1 = 6500f; decay = 0.012f
                gain = 0.25f; this.pan = pan; reverb = 0f; delay = 0.035f * j(0.1f); priority = 0.5f
            }
        } else if (byPlayer) {
            // Heavy player weapon: bigger bark with a saturated low punch.
            voice {
                level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 6000f * j(0.1f); cut1 = 500f; cutTime = 0.18f
                q = 0.9f; attack = 0.0008f; decay = 0.24f; drive = 0.8f; gain = 0.5f; this.pan = pan; reverb = 0.15f
            }
            voice {
                wave = Wave.SINE; f0 = 150f * j(); f1 = 42f; sweep = 0.1f; decay = 0.2f; drive = 1f; gain = 0.6f; this.pan = pan
            }
            voice {
                level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 5000f; cut1 = 5000f; decay = 0.02f; gain = 0.3f
                this.pan = pan; reverb = 0f
            }
        } else {
            // Enemy fire: louder, rougher, roomier — reads as a threat.
            val h = if (heavy) 1.35f else 1f
            voice {
                level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 2200f * j(0.12f); cut1 = 550f; cutTime = 0.12f * h
                q = 0.8f; attack = 0.0008f; decay = 0.17f * h; drive = 0.5f * h; gain = 0.42f; this.pan = pan; reverb = 0.25f
            }
            voice {
                wave = Wave.TRIANGLE; f0 = 170f * j() / h; f1 = 55f; sweep = 0.07f; decay = 0.13f * h; gain = 0.45f
                this.pan = pan; reverb = 0.1f
            }
            if (heavy) voice {
                level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 900f; cut1 = 200f; cutTime = 0.3f; decay = 0.35f
                crush = 4; gain = 0.3f; this.pan = pan; reverb = 0.2f
            }
        }
    }

    private fun bulletHit(onPlayer: Boolean, armored: Boolean, pan: Float) {
        when {
            armored -> {
                // Metallic ping + tick, sometimes a ricochet whine.
                voice {
                    wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 1.41f; fm = 2.2f; f0 = 2400f * j(0.08f); f1 = 2300f
                    sweep = 0.2f; decay = 0.35f; gain = 0.22f; this.pan = pan; reverb = 0.25f
                }
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 5000f; cut1 = 5000f; decay = 0.012f
                    gain = 0.3f; this.pan = pan
                }
                if (rng.chance(0.35f)) voice {
                    wave = Wave.SINE; f0 = 3400f * j(0.1f); f1 = 1500f; sweep = 0.28f; attack = 0.01f; decay = 0.25f
                    gain = 0.09f; this.pan = pan * 0.5f; reverb = 0.3f; delay = 0.02f
                }
            }
            onPlayer -> {
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 1400f; cut1 = 500f; cutTime = 0.06f
                    decay = 0.08f; gain = 0.4f; this.pan = pan
                }
                voice { wave = Wave.SINE; f0 = 120f * j(); f1 = 55f; sweep = 0.06f; decay = 0.09f; gain = 0.45f; this.pan = pan }
            }
            else -> {
                // Soft-target thwack.
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1100f * j(0.15f); cut1 = 600f; cutTime = 0.05f
                    q = 1f; decay = 0.06f; gain = 0.4f; this.pan = pan; reverb = 0.05f
                }
                voice { wave = Wave.SINE; f0 = 150f * j(); f1 = 70f; sweep = 0.05f; decay = 0.06f; gain = 0.35f; this.pan = pan }
            }
        }
    }

    // ---- Kills ---------------------------------------------------------------------------

    private fun enemyKilled(kind: EnemyKind, how: KillMethod, combo: Int, pan: Float) {
        // Chains climb in pitch: +0.35 semitone per combo step, capped.
        val up = Dsp.semis(min(combo, 16) * 0.35f)
        when (how) {
            KillMethod.SHOT -> {
                voice {
                    wave = Wave.SINE; f0 = 95f * up * j(); f1 = 42f; sweep = 0.12f; decay = 0.22f; gain = 0.45f; this.pan = pan
                }
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 1600f * up; cut1 = 600f; cutTime = 0.08f
                    decay = 0.09f; crush = 3; gain = 0.28f; this.pan = pan
                }
            }
            KillMethod.TAKEDOWN -> {
                takedownCrunch(pan, up)
                koExhale(pan)
            }
            KillMethod.STOMP -> {
                voice { wave = Wave.SINE; f0 = 230f * up * j(); f1 = 55f; sweep = 0.06f; decay = 0.13f; gain = 0.55f; this.pan = pan }
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1300f; cut1 = 700f; cutTime = 0.04f; decay = 0.05f
                    gain = 0.35f; this.pan = pan
                }
                voice {
                    wave = Wave.SQUARE; f0 = 330f * up; f1 = 700f * up; sweep = 0.08f; decay = 0.11f; filter = FilterMode.LOW
                    cut0 = 3000f; cut1 = 3000f; gain = 0.1f; this.pan = pan; delay = 0.03f; reverb = 0.15f
                }
            }
            KillMethod.LIGHT -> {
                lightCrash(pan)
                voice { wave = Wave.SINE; f0 = 90f * j(); f1 = 40f; sweep = 0.12f; decay = 0.2f; gain = 0.4f; this.pan = pan; delay = 0.04f }
            }
            KillMethod.EXPLOSION -> explosion(big = false, pan = pan)
            KillMethod.HAZARD -> {
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 2500f; cut1 = 4500f; cutTime = 0.4f
                    attack = 0.01f; decay = 0.45f; tremRate = 23f; tremDepth = 0.6f; gain = 0.2f; this.pan = pan; reverb = 0.2f
                }
                voice { wave = Wave.SINE; f0 = 90f * j(); f1 = 40f; sweep = 0.12f; decay = 0.2f; gain = 0.4f; this.pan = pan }
            }
        }
        when (kind) {
            EnemyKind.DRONE -> voice {
                wave = Wave.SAW; f0 = 1900f * j(0.1f); f1 = 180f; sweep = 0.18f; decay = 0.2f; crush = 4
                filter = FilterMode.BAND; cut0 = 3000f; cut1 = 800f; cutTime = 0.18f; q = 2f; gain = 0.18f; this.pan = pan; reverb = 0.2f
            }
            EnemyKind.TURRET -> voice {
                wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2.71f; fm = 3f; f0 = 620f * j(0.06f); f1 = 560f; sweep = 0.3f
                decay = 0.35f; gain = 0.2f; this.pan = pan; reverb = 0.25f
            }
            EnemyKind.HEAVY -> voice {
                wave = Wave.SINE; f0 = 65f; f1 = 30f; sweep = 0.3f; decay = 0.4f; gain = 0.5f; this.pan = pan; delay = 0.03f
            }
            EnemyKind.DEMON -> voice {
                wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 1.012f; level2 = 0.8f; f0 = 120f * j(); f1 = 38f; sweep = 0.55f
                filter = FilterMode.LOW; cut0 = 1100f; cut1 = 300f; cutTime = 0.5f; q = 1.5f; attack = 0.01f; decay = 0.6f
                drive = 1.8f; vibRate = 9f; vibDepth = 0.04f; gain = 0.28f; this.pan = pan; reverb = 0.35f
            }
            EnemyKind.AGENT, EnemyKind.NINJA -> {}
        }
        if (combo >= 2) {
            // Rising chain chime on a major pentatonic.
            val step = PENTA[combo % PENTA.size] + 12 * (min(combo, 14) / PENTA.size)
            voice {
                wave = Wave.TRIANGLE; wave2 = Wave.SINE; ratio2 = 2f; level2 = 0.4f; f0 = 880f * Dsp.semis(step.toFloat())
                f1 = f0; decay = 0.22f; gain = 0.13f; this.pan = pan * 0.5f; reverb = 0.35f; delay = 0.05f; priority = 0.6f
            }
        }
    }

    private fun takedownCrunch(pan: Float, up: Float) {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 800f * up; cut1 = 280f; cutTime = 0.1f; q = 1.2f
            decay = 0.13f; crush = 6; drive = 2f; gain = 0.42f; this.pan = pan
        }
        voice { wave = Wave.SINE; f0 = 80f * up * j(); f1 = 36f; sweep = 0.12f; decay = 0.27f; drive = 0.5f; gain = 0.6f; this.pan = pan }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 2600f; cut1 = 2600f; decay = 0.016f; gain = 0.2f
            this.pan = pan; delay = 0.025f * j(0.2f)
        }
    }

    /**
     * The grab, and the guard's strangled grunt, MGS style: a catch in the throat, then a
     * choked "uuurgh" (a buzzing voice through two vowel formants, pitch sagging, gurgling
     * under a fast tremolo) with a rasp of breath. Every guard's voice sits a little apart.
     */
    private fun takedown() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 600f; cut1 = 1900f; cutTime = 0.08f; q = 1.2f
            attack = 0.01f; decay = 0.1f; gain = 0.2f
        }
        voice { wave = Wave.SINE; f0 = 110f * j(); f1 = 50f; sweep = 0.1f; decay = 0.13f; gain = 0.4f; delay = 0.03f }
        val v = j(0.14f)
        // The catch: a glottal click as the arm closes.
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1500f; cut1 = 1100f; cutTime = 0.03f; q = 2f
            decay = 0.03f; gain = 0.22f; delay = 0.03f
        }
        // The choked voice: first formant ("uh" sliding toward "oo") and a thinner second one.
        voice {
            wave = Wave.SAW; f0 = 170f * v; f1 = 96f * v; sweep = 0.42f; filter = FilterMode.BAND; cut0 = 820f; cut1 = 480f
            cutTime = 0.4f; q = 3f; attack = 0.025f; hold = 0.14f; decay = 0.24f; tremRate = 31f; tremDepth = 0.55f
            vibRate = 7f; vibDepth = 0.03f; drive = 1.2f; gain = 0.34f; delay = 0.05f; reverb = 0.08f; priority = 2f
        }
        voice {
            wave = Wave.SAW; f0 = 170f * v; f1 = 96f * v; sweep = 0.42f; filter = FilterMode.BAND; cut0 = 2300f; cut1 = 1500f
            cutTime = 0.4f; q = 3f; attack = 0.025f; hold = 0.14f; decay = 0.22f; tremRate = 31f; tremDepth = 0.55f
            gain = 0.12f; delay = 0.05f; priority = 2f
        }
        // Rasping breath under it.
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1150f; cut1 = 900f; cutTime = 0.3f; q = 1.1f
            attack = 0.03f; hold = 0.1f; decay = 0.25f; tremRate = 29f; tremDepth = 0.5f; gain = 0.09f; delay = 0.05f
        }
    }

    /** Out cold: the last breath leaving him as he slumps. */
    private fun koExhale(pan: Float) {
        voice {
            wave = Wave.SAW; f0 = 118f * j(0.1f); f1 = 78f; sweep = 0.25f; filter = FilterMode.BAND; cut0 = 640f; cut1 = 420f
            cutTime = 0.25f; q = 2.2f; attack = 0.02f; decay = 0.22f; gain = 0.13f; this.pan = pan; delay = 0.04f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1800f; cut1 = 800f; cutTime = 0.4f; q = 0.9f
            attack = 0.04f; decay = 0.38f; gain = 0.09f; this.pan = pan; delay = 0.06f; reverb = 0.15f
        }
    }

    /**
     * Spotted: the "!" sting. A bright, slightly dissonant brass stab that snaps up into pitch,
     * a punch underneath, a crash of noise and a high metallic ping ringing out.
     */
    private fun alerted(pan: Float) {
        for ((k, f) in ALERT_STAB.withIndex()) voice {
            wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 1.006f; level2 = 0.8f; f0 = f * 0.93f; f1 = f; sweep = 0.035f
            filter = FilterMode.LOW; cut0 = 7000f; cut1 = 1600f; cutTime = 0.3f; q = 1.1f; attack = 0.002f; hold = 0.07f
            decay = 0.34f; gain = 0.085f; this.pan = pan * 0.3f + (k - 1) * 0.25f; reverb = 0.35f; priority = 3f
        }
        voice { wave = Wave.SINE; f0 = 130f; f1 = 52f; sweep = 0.12f; decay = 0.24f; drive = 0.4f; gain = 0.5f; priority = 3f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 3500f; cut1 = 6000f; cutTime = 0.2f; decay = 0.26f
            gain = 0.16f; reverb = 0.4f; priority = 3f
        }
        voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 3.01f; fm = 0.6f; f0 = 2637f; f1 = 2637f; attack = 0.002f
            decay = 0.6f; gain = 0.05f; this.pan = pan * 0.5f; reverb = 0.5f; delay = 0.02f; priority = 3f
        }
    }

    // ---- Movement ------------------------------------------------------------------------

    private fun jump() {
        voice {
            wave = Wave.SQUARE; f0 = 280f * j(); f1 = 720f * j(); sweep = 0.07f; decay = 0.09f; filter = FilterMode.LOW
            cut0 = 2600f; cut1 = 2600f; gain = 0.16f; reverb = 0.05f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1200f; cut1 = 2400f; cutTime = 0.06f; decay = 0.06f
            gain = 0.08f
        }
    }

    private fun land() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 450f * j(0.1f); cut1 = 300f; cutTime = 0.05f
            decay = 0.07f; gain = 0.35f
        }
        voice { wave = Wave.SINE; f0 = 100f * j(); f1 = 50f; sweep = 0.07f; decay = 0.08f; gain = 0.35f }
    }

    private fun hideBox() {
        // Cardboard rustle, a sneaky whoosh and the box settling.
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1500f * j(0.1f); cut1 = 1100f; cutTime = 0.25f; q = 0.8f
            attack = 0.01f; decay = 0.26f; tremRate = 27f * j(0.1f); tremDepth = 0.75f; crush = 2; gain = 0.28f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 2600f; cut1 = 450f; cutTime = 0.3f; q = 1.4f
            attack = 0.05f; decay = 0.3f; gain = 0.16f; reverb = 0.2f
        }
        voice { wave = Wave.SINE; f0 = 130f; f1 = 80f; sweep = 0.06f; decay = 0.09f; gain = 0.22f; delay = 0.13f }
    }

    private fun hideDoor() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1800f; cut1 = 400f; cutTime = 0.22f; q = 1.2f
            attack = 0.03f; decay = 0.22f; gain = 0.18f; reverb = 0.15f
        }
        voice { wave = Wave.SINE; f0 = 95f; f1 = 60f; sweep = 0.08f; decay = 0.1f; gain = 0.32f; delay = 0.15f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 4000f; cut1 = 4000f; decay = 0.01f; gain = 0.2f
            delay = 0.17f
        }
    }

    private fun unhide() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 400f; cut1 = 2400f; cutTime = 0.15f; q = 1.2f
            attack = 0.12f; decay = 0.1f; gain = 0.2f; reverb = 0.15f
        }
        voice { wave = Wave.TRIANGLE; f0 = 380f; f1 = 620f; sweep = 0.08f; decay = 0.08f; gain = 0.1f; delay = 0.1f }
    }

    private fun doorOpen() {
        // Sci-fi slide door: latch click, pneumatic hiss sweep and a motor underneath.
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 3500f; cut1 = 3500f; decay = 0.012f; gain = 0.3f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 500f; cut1 = 1700f; cutTime = 0.25f; q = 1.5f
            attack = 0.01f; hold = 0.12f; decay = 0.16f; gain = 0.22f; reverb = 0.1f
        }
        voice {
            wave = Wave.SAW; f0 = 70f; f1 = 90f; sweep = 0.25f; filter = FilterMode.LOW; cut0 = 400f; cut1 = 400f
            attack = 0.02f; hold = 0.14f; decay = 0.1f; gain = 0.12f
        }
    }

    private fun elevatorDing() {
        // Classic two-tone chime: FM bell partials, high then low.
        bell(1318.5f, 0f)
        bell(1046.5f, 0.32f)
    }

    private fun bell(hz: Float, delay: Float) {
        voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 3.5f; fm = 1.1f; f0 = hz; f1 = hz; attack = 0.002f; decay = 1.5f
            gain = 0.2f; reverb = 0.35f; this.delay = delay
        }
        voice { wave = Wave.SINE; f0 = hz * 2.76f; f1 = f0; decay = 0.5f; gain = 0.05f; reverb = 0.3f; this.delay = delay }
    }

    private fun elevatorMove() {
        voice {
            wave = Wave.SAW; wave2 = Wave.SINE; ratio2 = 2f; level2 = 0.6f; f0 = 55f; f1 = 62f; sweep = 0.6f
            filter = FilterMode.LOW; cut0 = 280f; cut1 = 380f; cutTime = 0.6f; attack = 0.15f; hold = 0.7f; decay = 0.45f
            tremRate = 6f; tremDepth = 0.2f; gain = 0.22f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 900f; cut1 = 1100f; cutTime = 0.8f; q = 2f
            attack = 0.2f; hold = 0.6f; decay = 0.3f; gain = 0.05f
        }
    }

    /** Through a passage door: a door latch, then a fast airy whoosh sweeping across the stereo field. */
    private fun passage() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 3800f; cut1 = 3800f; decay = 0.012f; gain = 0.25f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 350f * j(0.1f); cut1 = 3200f; cutTime = 0.2f; q = 1.4f
            attack = 0.05f; hold = 0.06f; decay = 0.2f; gain = 0.34f; pan = -0.5f; reverb = 0.12f; delay = 0.03f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 3000f; cut1 = 600f; cutTime = 0.22f; q = 1.2f
            attack = 0.04f; decay = 0.22f; gain = 0.22f; pan = 0.5f; reverb = 0.15f; delay = 0.2f
        }
        voice { wave = Wave.SINE; f0 = 90f * j(); f1 = 55f; sweep = 0.2f; attack = 0.03f; decay = 0.2f; gain = 0.16f; delay = 0.18f }
    }

    /** The call button: a short, bright two-blip beep. */
    private fun elevatorCall() {
        for (k in 0 until 2) voice {
            wave = Wave.SQUARE; pw = 0.3f; f0 = 1760f; f1 = 1760f; filter = FilterMode.LOW; cut0 = 4000f; cut1 = 4000f
            attack = 0.002f; hold = 0.03f; decay = 0.04f; gain = 0.08f; reverb = 0.1f; delay = k * 0.09f
        }
    }

    /** GUNS HOT: the slide racks back and snaps home. */
    private fun cock() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 2600f * j(0.08f); cut1 = 1500f; cutTime = 0.05f; q = 2.2f
            attack = 0.001f; decay = 0.05f; gain = 0.32f
        }
        voice { wave = Wave.SINE; f0 = 420f; f1 = 180f; sweep = 0.04f; decay = 0.05f; gain = 0.22f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 4200f; cut1 = 4200f; decay = 0.018f; gain = 0.38f; delay = 0.11f
        }
        voice { wave = Wave.SINE; f0 = 260f * j(); f1 = 90f; sweep = 0.05f; decay = 0.07f; drive = 0.4f; gain = 0.32f; delay = 0.11f }
    }

    /** SILENT: a soft, muffled thump, like a hand closing over the muzzle. */
    private fun muffle() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 700f; cut1 = 250f; cutTime = 0.15f
            attack = 0.02f; decay = 0.18f; gain = 0.28f; reverb = 0.08f
        }
        voice { wave = Wave.SINE; f0 = 180f; f1 = 110f; sweep = 0.12f; attack = 0.01f; decay = 0.16f; gain = 0.2f }
    }

    // ---- Player state --------------------------------------------------------------------

    private fun playerHurt(hpLeft: Int) {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 3200f; cut1 = 400f; cutTime = 0.15f; decay = 0.18f
            crush = 4; drive = 2.5f; gain = 0.4f
        }
        voice { wave = Wave.SINE; f0 = 130f * j(); f1 = 35f; sweep = 0.25f; decay = 0.3f; drive = 0.6f; gain = 0.55f }
        if (hpLeft <= 1) {
            for (k in 0 until 2) voice {
                wave = Wave.SQUARE; f0 = 880f; f1 = 880f; filter = FilterMode.LOW; cut0 = 3000f; cut1 = 3000f
                decay = 0.08f; hold = 0.04f; gain = 0.1f; delay = 0.22f + k * 0.16f; priority = 1.5f
            }
        }
    }

    private fun shieldBlock() {
        voice {
            wave = Wave.SAW; f0 = 380f * j(); f1 = 1900f; sweep = 0.1f; filter = FilterMode.BAND; cut0 = 1400f; cut1 = 5200f
            cutTime = 0.1f; q = 3f; decay = 0.22f; vibRate = 31f; vibDepth = 0.08f; gain = 0.3f; reverb = 0.2f
        }
        voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 1.5f; fm = 3f; f0 = 2400f; f1 = 2600f; sweep = 0.3f; decay = 0.32f
            gain = 0.11f; reverb = 0.35f
        }
    }

    private fun playerDied() {
        voice {
            wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 1.01f; level2 = 0.8f; f0 = 720f; f1 = 35f; sweep = 1.3f
            filter = FilterMode.LOW; cut0 = 4000f; cut1 = 180f; cutTime = 1.3f; q = 1.5f; hold = 0.3f; decay = 1.2f
            drive = 1f; gain = 0.28f; reverb = 0.4f; priority = 3f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 1000f; cut1 = 100f; cutTime = 0.8f; decay = 0.8f
            gain = 0.42f; reverb = 0.3f; priority = 3f
        }
        voice { wave = Wave.SINE; f0 = 80f; f1 = 25f; sweep = 1f; decay = 1.2f; gain = 0.55f; priority = 3f }
    }

    // ---- Pickups & perks -----------------------------------------------------------------

    private fun arp(rootHz: Float, semis: IntArray, step: Float, w: Wave, decay: Float, gain: Float, start: Float = 0f, verb: Float = 0.2f) {
        for (k in semis.indices) voice {
            val s = semis[k]
            wave = w; pw = 0.25f; f0 = rootHz * Dsp.semis(s.toFloat()); f1 = f0; filter = FilterMode.LOW; cut0 = 7000f
            cut1 = 3000f; cutTime = decay; this.decay = decay; this.gain = gain; reverb = verb; delay = start + k * step
            priority = 1.2f
        }
    }

    private fun pickup(kind: PickupKind) {
        when (kind) {
            PickupKind.MEDKIT -> {
                arp(523.25f, MAJOR_ARP, 0.06f, Wave.TRIANGLE, 0.3f, 0.2f)
                voice {
                    wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 1.5f; level2 = 0.5f; f0 = 261.6f; f1 = f0; attack = 0.05f
                    decay = 0.6f; gain = 0.12f; reverb = 0.3f
                }
            }
            PickupKind.CASH -> {
                voice { wave = Wave.SQUARE; f0 = 987.8f; f1 = f0; decay = 0.07f; hold = 0.05f; filter = FilterMode.LOW; cut0 = 6000f; cut1 = 6000f; gain = 0.13f }
                voice {
                    wave = Wave.SQUARE; f0 = 1318.5f; f1 = f0; decay = 0.4f; filter = FilterMode.LOW; cut0 = 6000f; cut1 = 3000f
                    cutTime = 0.4f; gain = 0.13f; delay = 0.075f; reverb = 0.15f
                }
            }
            PickupKind.SHOTGUN -> {
                for (k in 0 until 2) voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1600f - k * 500f; cut1 = cut0; q = 1.5f
                    decay = 0.04f; gain = 0.35f; delay = k * 0.12f
                }
                arp(392f, MAJOR_ARP, 0.045f, Wave.PULSE, 0.18f, 0.12f, start = 0.2f)
            }
            PickupKind.MINIGUN -> {
                voice {
                    wave = Wave.SAW; f0 = 80f; f1 = 420f; sweep = 0.35f; filter = FilterMode.LOW; cut0 = 600f; cut1 = 2400f
                    cutTime = 0.35f; attack = 0.05f; decay = 0.3f; tremRate = 38f; tremDepth = 0.5f; gain = 0.15f
                }
                arp(440f, MAJOR_ARP, 0.045f, Wave.PULSE, 0.18f, 0.12f, start = 0.25f)
            }
            PickupKind.SHIELD -> {
                for (k in SHIELD_ARP.indices) voice {
            val s = SHIELD_ARP[k]
                    wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2f; fm = 1.5f; f0 = 659.3f * Dsp.semis(s.toFloat()); f1 = f0
                    decay = 0.45f; gain = 0.12f; reverb = 0.45f; delay = k * 0.05f; tremRate = 12f; tremDepth = 0.3f
                }
            }
            PickupKind.SLOWMO -> {
                voice {
                    wave = Wave.SINE; f0 = 1200f; f1 = 300f; sweep = 0.4f; decay = 0.45f; gain = 0.14f; reverb = 0.4f
                }
                arp(440f, MINOR_ARP, 0.07f, Wave.TRIANGLE, 0.35f, 0.16f, start = 0.05f, verb = 0.4f)
            }
            PickupKind.GRENADE -> {
                voice {
                    wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 1.73f; fm = 2.5f; f0 = 2600f; f1 = 2500f; sweep = 0.1f
                    decay = 0.16f; gain = 0.2f
                }
                arp(349.2f, MAJOR_ARP, 0.05f, Wave.PULSE, 0.18f, 0.12f, start = 0.1f)
            }
        }
    }

    private fun perkOffered() {
        // Mysterious minor-add9 swell.
        for (k in PERK_SWELL.indices) voice {
            val s = PERK_SWELL[k]
            wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 1.006f; level2 = 0.8f; f0 = 146.8f * Dsp.semis(s.toFloat()); f1 = f0
            filter = FilterMode.LOW; cut0 = 350f; cut1 = 2600f; cutTime = 0.9f; q = 1.2f; attack = 0.45f; hold = 0.2f
            decay = 1.3f; gain = 0.075f; pan = (k - 1.5f) * 0.3f; reverb = 0.6f; priority = 1.5f
        }
        voice {
            wave = Wave.SINE; f0 = 1760f; f1 = 1760f; attack = 0.3f; decay = 1.2f; tremRate = 7f; tremDepth = 0.6f
            gain = 0.05f; reverb = 0.7f; delay = 0.2f
        }
    }

    private fun perkChosen(ordinal: Int) {
        val root = 261.6f * Dsp.semis(ROOT_SHIFT[ordinal % ROOT_SHIFT.size].toFloat())
        for (k in MAJOR_ARP.indices) voice {
            val s = MAJOR_ARP[k]
            wave = Wave.SAW; wave2 = Wave.SQUARE; level2 = 0.5f; ratio2 = 1.003f; f0 = root * Dsp.semis(s.toFloat()); f1 = f0
            filter = FilterMode.LOW; cut0 = 6500f; cut1 = 1400f; cutTime = 0.55f; attack = 0.003f; decay = 0.65f
            gain = 0.085f; pan = (k - 1.5f) * 0.25f; reverb = 0.35f; priority = 2f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 2500f; cut1 = 1200f; cutTime = 0.08f; decay = 0.08f
            gain = 0.2f
        }
        arp(root * 2f, FIFTHS, 0.04f, Wave.PULSE, 0.2f, 0.07f, start = 0.08f, verb = 0.35f)
    }

    // ---- Environment -------------------------------------------------------------------

    private fun lightShot() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 4200f; cut1 = 4200f; decay = 0.08f; crush = 2; gain = 0.24f
        }
        voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2.3f; fm = 1.5f; f0 = 3200f * j(0.08f); f1 = f0; decay = 0.2f
            gain = 0.1f; reverb = 0.2f
        }
        voice { wave = Wave.SAW; f0 = 2400f; f1 = 600f; sweep = 0.06f; decay = 0.07f; crush = 3; gain = 0.1f }
    }

    /** Glass shatter: a scatter of bright pings and noise shards. */
    private fun lightCrash(pan: Float) {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 3000f; cut1 = 6000f; cutTime = 0.3f; decay = 0.35f
            gain = 0.3f; this.pan = pan; reverb = 0.25f
        }
        for (k in 0 until 5) voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2.76f; fm = 0.8f; f0 = rng.range(2800f, 7000f); f1 = f0
            decay = rng.range(0.08f, 0.25f); gain = 0.07f; this.pan = pan + rng.range(-0.4f, 0.4f); reverb = 0.3f
            delay = rng.range(0f, 0.16f); priority = 0.5f
        }
        voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2.9f; fm = 2f; f0 = 700f; f1 = 650f; sweep = 0.3f; decay = 0.4f
            gain = 0.14f; this.pan = pan; delay = 0.02f
        }
    }

    private fun explosion(big: Boolean, pan: Float) {
        val s = if (big) 1f else 0.45f
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = (if (big) 4500f else 3200f) * j(0.1f); cut1 = 120f
            cutTime = 1.2f * s; decay = 1.4f * s; drive = 1.5f; gain = if (big) 0.6f else 0.45f; this.pan = pan * 0.7f
            reverb = 0.35f; priority = 2f
        }
        voice {
            wave = Wave.SINE; f0 = (if (big) 90f else 115f) * j(); f1 = if (big) 28f else 40f; sweep = 0.8f * s
            decay = 1.1f * s; gain = if (big) 0.7f else 0.5f; this.pan = pan * 0.3f; priority = 2f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 2200f; cut1 = 900f; cutTime = 0.8f * s; q = 0.8f
            crush = 10; tremRate = 17f; tremDepth = 0.7f; decay = 0.9f * s; gain = 0.14f; this.pan = pan; delay = 0.05f
            reverb = 0.3f
        }
    }

    private fun hazardFire(pan: Float) {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 400f; cut1 = 1500f; cutTime = 0.25f; q = 0.9f
            attack = 0.08f; decay = 0.5f; gain = 0.3f; this.pan = pan; reverb = 0.2f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 320f; cut1 = 320f; attack = 0.05f; decay = 0.5f
            tremRate = 9f * j(0.2f); tremDepth = 0.5f; gain = 0.28f; this.pan = pan
        }
    }

    private fun floorTick(floor: Int) {
        voice { wave = Wave.SINE; f0 = 2400f; f1 = 2400f; decay = 0.025f; gain = 0.07f; reverb = 0.05f; priority = 0.3f }
        if (floor % 10 == 0) voice {
            wave = Wave.SINE; f0 = 3200f; f1 = 3200f; decay = 0.04f; gain = 0.06f; reverb = 0.1f; delay = 0.06f; priority = 0.3f
        }
    }

    private fun zoneImpact(zone: Zone) {
        val root = 55f * Dsp.semis(ZONE_ROOT[zone.ordinal].toFloat())
        voice { wave = Wave.SINE; f0 = 75f; f1 = 30f; sweep = 1.2f; decay = 1.8f; gain = 0.6f; priority = 3f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 2600f; cut1 = 180f; cutTime = 0.9f; decay = 0.9f
            gain = 0.38f; reverb = 0.4f; priority = 3f
        }
        voice {
            // Cinematic "braam": detuned saw fifth opening up.
            wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 1.498f; level2 = 0.7f; f0 = root; f1 = root * 0.985f; sweep = 2f
            filter = FilterMode.LOW; cut0 = 250f; cut1 = 1400f; cutTime = 0.6f; q = 1.3f; attack = 0.02f; decay = 2.2f
            drive = 1.5f; gain = 0.2f; reverb = 0.5f; priority = 3f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 600f; cut1 = 6000f; cutTime = 1.6f; q = 2f
            attack = 1.2f; decay = 0.25f; gain = 0.13f; reverb = 0.4f; delay = 0.2f
        }
        when (zone) {
            Zone.HELL -> voice {
                wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 1.06f; level2 = 1f; f0 = 46f; f1 = 38f; sweep = 2f
                filter = FilterMode.LOW; cut0 = 700f; cut1 = 250f; cutTime = 2f; attack = 0.1f; decay = 2.5f; drive = 2.5f
                vibRate = 5f; vibDepth = 0.05f; gain = 0.22f; reverb = 0.5f; priority = 3f
            }
            Zone.VOID -> voice {
                wave = Wave.SQUARE; f0 = 220f; f1 = 1760f; sweep = 1f; crush = 12; tremRate = 16f; tremDepth = 1f
                decay = 1.2f; gain = 0.1f; reverb = 0.4f; priority = 3f
            }
            else -> {}
        }
    }

    private fun slowMo(down: Boolean) {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = if (down) 3000f else 250f; cut1 = if (down) 250f else 3000f
            cutTime = if (down) 0.6f else 0.45f; q = 2f; attack = if (down) 0.02f else 0.3f; decay = if (down) 0.7f else 0.2f
            gain = 0.25f; reverb = 0.4f
        }
        voice {
            wave = Wave.SINE; f0 = if (down) 700f else 90f; f1 = if (down) 90f else 700f; sweep = if (down) 0.6f else 0.45f
            attack = if (down) 0.005f else 0.3f; decay = if (down) 0.6f else 0.2f; gain = 0.18f; reverb = 0.4f
        }
    }

    private fun emptyClick() {
        voice { level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 3500f; cut1 = 3500f; decay = 0.008f; gain = 0.4f; reverb = 0f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 2500f; cut1 = 2500f; decay = 0.006f; gain = 0.3f
            reverb = 0f; delay = 0.05f
        }
    }

    /** Magazine out, magazine in, slide racked: click … thunk … clack-clack. */
    private fun reload() {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 3000f * j(0.1f); cut1 = cut0; decay = 0.018f
            gain = 0.22f; reverb = 0.02f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1300f * j(0.1f); cut1 = 800f; cutTime = 0.04f; q = 1.4f
            decay = 0.05f; gain = 0.3f; delay = 0.17f
        }
        voice { wave = Wave.SINE; f0 = 190f * j(); f1 = 110f; sweep = 0.03f; decay = 0.04f; gain = 0.2f; delay = 0.17f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 1800f; cut1 = 4200f; cutTime = 0.06f; q = 1.6f
            attack = 0.004f; decay = 0.06f; gain = 0.2f; delay = 0.3f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 2600f; cut1 = 2600f; decay = 0.025f; gain = 0.32f
            delay = 0.37f
        }
        voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2.3f; fm = 1.2f; f0 = 1500f * j(0.05f); f1 = f0; decay = 0.05f
            gain = 0.08f; delay = 0.37f
        }
    }

    // ---- Silly business ---------------------------------------------------------------

    /** A guard's "huh?": two quick rising, slightly bent blips. */
    private fun huh(pan: Float) {
        voice {
            wave = Wave.TRIANGLE; f0 = 420f * j(); f1 = 470f; sweep = 0.06f; attack = 0.005f; decay = 0.07f; gain = 0.14f
            this.pan = pan; reverb = 0.1f
        }
        voice {
            wave = Wave.TRIANGLE; f0 = 480f * j(); f1 = 760f; sweep = 0.14f; attack = 0.005f; hold = 0.05f; decay = 0.12f
            vibRate = 9f; vibDepth = 0.02f; gain = 0.15f; this.pan = pan; reverb = 0.15f; delay = 0.11f
        }
    }

    /** GHOST: an airy, shimmering pentatonic run that evaporates. */
    private fun ghost() {
        for (k in PENTA.indices) voice {
            wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2f; fm = 0.4f; f0 = 1046.5f * Dsp.semis(PENTA[k].toFloat()); f1 = f0
            attack = 0.02f; decay = 0.55f; gain = 0.07f; pan = (k - 2f) * 0.3f; reverb = 0.6f; delay = k * 0.045f
            tremRate = 10f; tremDepth = 0.3f; priority = 1.2f
        }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 6000f; cut1 = 9000f; cutTime = 0.4f; attack = 0.1f
            decay = 0.4f; gain = 0.05f; reverb = 0.5f
        }
    }

    private fun floorEvent(event: FloorEvent) {
        when (event) {
            FloorEvent.BLACKOUT -> {
                // Power-down: the hum sags to nothing, then a breaker thunks.
                voice {
                    wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 2f; level2 = 0.5f; f0 = 120f; f1 = 30f; sweep = 0.9f
                    filter = FilterMode.LOW; cut0 = 1200f; cut1 = 120f; cutTime = 0.9f; attack = 0.01f; decay = 1f; gain = 0.16f
                    priority = 2f
                }
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 700f; cut1 = 200f; cutTime = 0.1f; decay = 0.15f
                    gain = 0.35f; delay = 0.85f; priority = 2f
                }
                voice { wave = Wave.SINE; f0 = 70f; f1 = 40f; sweep = 0.15f; decay = 0.2f; gain = 0.4f; delay = 0.85f; priority = 2f }
            }
            FloorEvent.NAP_TIME -> {
                // A music-box lullaby, three notes down.
                for ((k, sm) in LULLABY.withIndex()) voice {
                    wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 4.2f; fm = 0.7f; f0 = 1568f * Dsp.semis(sm.toFloat()); f1 = f0
                    attack = 0.002f; decay = 0.8f; gain = 0.1f; reverb = 0.5f; delay = k * 0.26f; priority = 1.5f
                }
            }
            FloorEvent.PAYDAY -> {
                // Cha-ching: register bell, then the drawer rolling out.
                bell(2093f, 0f)
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 2200f; cut1 = 1400f; cutTime = 0.2f; q = 1.5f
                    attack = 0.01f; decay = 0.22f; tremRate = 40f; tremDepth = 0.6f; gain = 0.2f; delay = 0.12f
                }
                arp(1046.5f, MAJOR_ARP, 0.05f, Wave.PULSE, 0.2f, 0.08f, start = 0.25f)
            }
            FloorEvent.NONE -> Unit
        }
    }

    /** Smooth elevator jazz: a soft electric-piano maj7 to min7 vamp with a brushed shaker. */
    private fun muzak() {
        for ((c, chord) in MUZAK_CHORDS.withIndex()) {
            for ((k, sm) in chord.withIndex()) voice {
                wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 1f; fm = 0.9f; f0 = 261.6f * Dsp.semis(sm.toFloat()); f1 = f0
                attack = 0.004f; hold = 0.1f; decay = 1.1f; tremRate = 5f; tremDepth = 0.25f; gain = 0.045f
                pan = (k - 1.5f) * 0.35f; reverb = 0.45f; delay = c * 0.72f + k * 0.03f; priority = 0.8f
            }
        }
        for (k in 0 until 8) voice {
            level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 7000f; cut1 = 7000f; attack = 0.01f; decay = 0.05f
            gain = if (k % 2 == 0) 0.05f else 0.03f; delay = k * 0.18f; priority = 0.5f
        }
    }

    /** A soft, low snore: filtered breath with a wobble. */
    private fun snore(pan: Float) {
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 260f * j(0.1f); cut1 = 380f; cutTime = 0.5f; q = 2.5f
            attack = 0.35f; decay = 0.3f; tremRate = 22f; tremDepth = 0.7f; gain = 0.05f; this.pan = pan; priority = 0.2f
        }
    }

    /** A Heavy boots the box: a hollow cardboard thump and a flutter. */
    private fun boxKicked() {
        voice { wave = Wave.SINE; f0 = 150f; f1 = 70f; sweep = 0.08f; decay = 0.12f; gain = 0.4f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.BAND; cut0 = 900f; cut1 = 1800f; cutTime = 0.3f; q = 0.9f
            attack = 0.005f; decay = 0.35f; tremRate = 30f; tremDepth = 0.6f; crush = 2; gain = 0.25f
        }
    }

    /** Game-over stinger: a dying minor chord over a boom. */
    /** The game-over chord, signed off in [hero]'s style (null: just the chord). */
    fun gameOverStinger(hero: Hero?) {
        gameOverStinger()
        when (hero) {
            Hero.BULL -> {
                // Sad trombone: three falling "wah"s and a long, wobbling fourth.
                for (k in 0 until 4) voice {
                    val last = k == 3
                    wave = Wave.SAW; wave2 = Wave.SQUARE; level2 = 0.3f; f0 = 146.8f * Dsp.semis(-k.toFloat()); f1 = f0 * (if (last) 0.97f else 0.99f)
                    sweep = if (last) 1.2f else 0.3f; filter = FilterMode.LOW; cut0 = 400f; cut1 = 1400f; cutTime = 0.12f; q = 2f
                    attack = 0.03f; hold = if (last) 0.7f else 0.18f; decay = if (last) 0.6f else 0.12f
                    vibRate = if (last) 6f else 0f; vibDepth = 0.012f; gain = 0.16f; reverb = 0.25f; delay = 0.9f + k * 0.34f; priority = 4f
                }
            }
            Hero.FOX -> {
                // Vibes, rolled: a cool D minor 9 fading out on tremolo.
                for (k in FOX_EXIT.indices) voice {
                    val s = FOX_EXIT[k]
                    wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 4f; level2 = 0.2f; f0 = 293.7f * Dsp.semis(s.toFloat()); f1 = f0
                    attack = 0.003f; decay = 2.4f; tremRate = 5.5f; tremDepth = 0.5f; gain = 0.07f
                    pan = (k - 2f) * 0.2f; reverb = 0.55f; delay = 0.8f + k * 0.09f; priority = 4f
                }
            }
            Hero.BADGER -> {
                // A shake of sleigh bells and three tolling bells: ho... ho... no.
                voice {
                    level1 = 0f; noise = 1f; filter = FilterMode.HIGH; cut0 = 6000f; cut1 = 6000f; attack = 0.02f; hold = 0.35f
                    decay = 0.4f; tremRate = 17f; tremDepth = 0.8f; gain = 0.1f; reverb = 0.3f; delay = 0.8f; priority = 4f
                }
                for (k in HO_HO_NO.indices) voice {
                    val s = HO_HO_NO[k]
                    wave = Wave.SINE; wave2 = Wave.SINE; ratio2 = 2.76f; fm = 0.9f; f0 = 293.7f * Dsp.semis(s.toFloat()); f1 = f0
                    attack = 0.002f; decay = 2f; gain = 0.12f; reverb = 0.45f; delay = 1.1f + k * 0.42f; priority = 4f
                }
            }
            Hero.VIPER -> {
                // Two war drums in the distance, and a lone horn falling to rest.
                for (k in 0 until 2) voice {
                    wave = Wave.SINE; f0 = 82f; f1 = 52f; sweep = 0.25f; attack = 0.002f; decay = 0.9f; noise = 0.15f
                    filter = FilterMode.LOW; cut0 = 900f; cut1 = 200f; cutTime = 0.3f; gain = 0.14f - k * 0.05f; reverb = 0.5f
                    delay = 0.8f + k * 0.45f; priority = 4f
                }
                for (k in VIPER_EXIT.indices) voice {
                    val s = VIPER_EXIT[k]
                    val last = k == VIPER_EXIT.size - 1
                    wave = Wave.SAW; wave2 = Wave.TRIANGLE; level2 = 0.6f; f0 = 146.8f * Dsp.semis(s.toFloat()); f1 = f0
                    filter = FilterMode.LOW; cut0 = 500f; cut1 = 1100f; cutTime = 0.15f; attack = 0.05f
                    hold = if (last) 0.8f else 0.25f; decay = if (last) 1.2f else 0.15f; vibRate = 5f; vibDepth = 0.006f
                    gain = 0.1f; reverb = 0.5f; delay = 1.5f + k * 0.42f; priority = 4f
                }
            }
            null -> {}
        }
    }

    private fun gameOverStinger() {
        trim = 1f
        for (k in STINGER.indices) voice {
            val s = STINGER[k]
            wave = Wave.SAW; wave2 = Wave.SAW; ratio2 = 1.008f; level2 = 0.8f; f0 = 293.7f * Dsp.semis(s.toFloat())
            f1 = f0 * 0.94f; sweep = 2f; filter = FilterMode.LOW; cut0 = 3200f; cut1 = 350f; cutTime = 2f; q = 1.1f
            attack = 0.005f; decay = 2.6f; gain = 0.09f; pan = (k - 1.5f) * 0.3f; reverb = 0.6f; priority = 4f
        }
        voice { wave = Wave.SINE; f0 = 65f; f1 = 25f; sweep = 1.5f; decay = 2f; gain = 0.6f; priority = 4f }
        voice {
            level1 = 0f; noise = 1f; filter = FilterMode.LOW; cut0 = 1800f; cut1 = 120f; cutTime = 1.5f; decay = 1.5f
            gain = 0.35f; reverb = 0.5f; priority = 4f
        }
    }

    companion object {
        private val PENTA = intArrayOf(0, 2, 4, 7, 9)
        /** The alert stab: E5, F5 and B5, a bright cluster with a bite. */
        private val ALERT_STAB = floatArrayOf(659.3f, 698.5f, 987.8f)
        private val LULLABY = intArrayOf(0, -3, -7)
        /** Cmaj7, then Am7 (semitones above middle C). */
        private val MUZAK_CHORDS = arrayOf(intArrayOf(0, 4, 7, 11), intArrayOf(-3, 0, 4, 7))
        private val MAJOR_ARP = intArrayOf(0, 4, 7, 12)
        private val MINOR_ARP = intArrayOf(12, 7, 3, 0)
        private val SHIELD_ARP = intArrayOf(0, 4, 7, 12, 16)
        private val PERK_SWELL = intArrayOf(0, 3, 7, 14)
        private val STINGER = intArrayOf(-12, 0, 3, 7)
        /** Dm9 (D F A C E), rolled up. */
        private val FOX_EXIT = intArrayOf(0, 3, 7, 10, 14)
        private val HO_HO_NO = intArrayOf(7, 3, -5)
        /** D minor horn: A, F, then down to D. */
        private val VIPER_EXIT = intArrayOf(7, 3, 0)
        private val FIFTHS = intArrayOf(0, 7, 12, 19)
        private val ROOT_SHIFT = intArrayOf(0, 2, -3, 5)
        /** Semitones above A1 for each zone's impact "braam". */
        private val ZONE_ROOT = intArrayOf(0, 0, 5, -2, -5, 3, -5, 1)
    }
}
