package com.bradflaugher.aboutthataction.engine

/**
 * One-shot things that happened during a simulation step. The Android layer
 * drains them each frame to drive sound effects, music and haptics; the
 * engine itself uses them for nothing but tests.
 */
sealed interface GameEvent {
    /** [pan] is -1 (left wall) .. 1 (right wall) for stereo placement. */
    data class Shot(val byPlayer: Boolean, val heavy: Boolean, val pan: Float) : GameEvent
    data class BulletHit(val onPlayer: Boolean, val armored: Boolean, val pan: Float) : GameEvent
    data class EnemyKilled(val kind: EnemyKind, val how: KillMethod, val combo: Int, val pan: Float) : GameEvent
    data object Takedown : GameEvent
    data object Jump : GameEvent
    data object Land : GameEvent
    data object HideBox : GameEvent
    data object HideDoor : GameEvent
    data object Unhide : GameEvent
    data object DoorOpen : GameEvent
    data object ElevatorDing : GameEvent
    data object ElevatorMove : GameEvent
    /** Through a passage door into another hallway (a whoosh). */
    data object Passage : GameEvent
    /** Tapped a landing to call its car. */
    data object ElevatorCalled : GameEvent
    /** The GUNS HOT / SILENT toggle flipped; [silent] is the new mode. */
    data class ModeToggled(val silent: Boolean) : GameEvent
    data class PlayerHurt(val hpLeft: Int) : GameEvent
    data object ShieldBlock : GameEvent
    data object PlayerDied : GameEvent
    data class Pickup(val kind: PickupKind) : GameEvent
    data object PerkOffered : GameEvent
    data class PerkChosen(val perk: Perk) : GameEvent
    data object LightShot : GameEvent
    data object LightCrash : GameEvent
    data class Explosion(val big: Boolean, val pan: Float) : GameEvent
    data class HazardFire(val pan: Float) : GameEvent
    data class FloorReached(val floor: Int) : GameEvent
    data class ZoneEntered(val zone: Zone) : GameEvent
    data object SlowMoStart : GameEvent
    data object SlowMoEnd : GameEvent
    data object SpecialEmpty : GameEvent
    /** The magazine ran dry (or the player paused shooting) and a reload started. */
    data object Reload : GameEvent
    /** A guard double-took at a box that moved ("HUH?"): he's coming over to look. */
    data class Suspicious(val pan: Float) : GameEvent
    /** Left a floor without anyone on it ever spotting you. */
    data object Ghost : GameEvent
    /** Stepped onto a special floor (a blackout, nap time, payday). */
    data class FloorEventStarted(val event: FloorEvent) : GameEvent
    /** This ride comes with smooth elevator jazz. */
    data object Muzak : GameEvent
    /** A napping guard snores (only in your hallway). */
    data class Snore(val pan: Float) : GameEvent
    /** A Heavy kicked your box off: busted. */
    data object BoxKicked : GameEvent
}

enum class KillMethod { SHOT, TAKEDOWN, STOMP, LIGHT, EXPLOSION, HAZARD }
