# Controls: design notes

Why every gesture and feel rule is the way it is, so tuning stays principled.
Gesture thresholds live in `input/GestureInput.kt` (companion constants, in dp
and ms). Feel constants live in `engine/World.kt` (the "Controls & feel" block
in the companion). Every rule has a test in `GestureInputTest` or
`ControlsTest`.

Two principles run through everything:

1. **Never eat an input.** A gesture the player clearly made should do
   something useful, even if it arrived a little early or the thing it
   meant isn't available.
2. **Never invent an input.** A resting, settling or repositioning thumb
   must never jump, hide, shoot or throw a grenade.

When the two conflict, remember which is worse for the gesture in
question. A missed jump is a lost life. A phantom grenade wastes a
resource and alerts the floor.

## Gesture classification (`GestureInput`)

Each finger is classified on its own, so one thumb can run while the other
taps. A finger starts **PENDING** and becomes **HELD** once it runs or
flicks. A held finger with `dir == 0` is a resting thumb: it can still flick,
or drag sideways into a run.

**One boundary everywhere is 45°.** A stroke steeper than 45° from
horizontal is vertical (a flick). A shallower stroke is horizontal (a run).
The old code used different angles in different places (40° to start a
flick, 32° mid-run), so a thumb's diagonal could mean different things
depending on what it had just done.

| Gesture | Threshold | Why |
|---|---|---|
| Run start | 10 dp sideways (`SLOP_DP`) | About 1.6 mm. That's enough to reject tap jitter and still feel instant. |
| Run reverse | 12 dp back from the furthest point (`REVERSE_DP`) | Lets you turn around with no centre point to cross. Suppressed during a vertical stroke, so a flick that drifts backwards never flips the run. |
| Flick, fresh finger | 22 dp vertical (`FLICK_DP`), inside 150 ms (`FLICK_WINDOW_MS`) | The distance is forgiving for short thumb flicks. The time window is a speed gate (≥ ~150 dp/s), so a slow slide or a thumb settling never jumps. A slow vertical slide becomes a resting HELD finger instead. |
| Flick, held finger | 26 dp inside 150 ms (`FLICK_MID_RUN_DP`); direction judged on the last 50 ms (`RECENT_MS`) | A running thumb wobbles more, so it needs a little more distance. Direction comes from the recent stroke only, so an L-shaped run-then-flick counts: the run travel just before the flick doesn't tilt the flick sideways. A slightly diagonal flick mid-run registers up to 45°. |
| Fast flick seen only at lift | 60% of `FLICK_DP` in < 220 ms | Flicks so fast the digitiser gives almost no move events. |
| Flick cooldown / rebound | 260 ms same finger; 600 ms opposite direction ignored | The thumb springing back after a jump is not a hide. A deliberate opposite flick after 0.6 s works. |
| Flick then drag | 14 dp sideways from rest (`RESTART_DP`) | Jump, then run with the same thumb without lifting. The extra 4 dp over the run slop absorbs flick follow-through. |
| Tap | ≤ 10 dp, < 300 ms (`TAP_MS`) | Fires on release with zero added delay. 300 ms covers hurried presses. Longer holds are resting thumbs. |
| Sloppy tap | A finger that became a run but lifted within 150 ms, having travelled ≤ 16 dp | A hard, rolling thumb press slides past the run slop. It was a shot, not a step. Deliberate nudges last longer than 150 ms, or travel further. |
| Double-tap (grenade) | Second press within 170 ms of the first lift (`DOUBLE_TAP_GAP_MS`), within 48 dp | Deliberate double-taps have a 60–150 ms gap. Firing at the gun's own rate (0.27 s cooldown) leaves a gap of about 200 ms or more, so it stays shots. Only the **second** tap of a burst is a grenade: mashing gives one grenade, not one per pair. Two thumbs alternating are shots (too far apart). A flick between taps breaks the burst. |

**Fingers leaving.** `ACTION_CANCEL` means the system took the gesture. The
run stops (`releaseAll`), but flicks and taps that were already recognised
still count. A pointer lifted with `FLAG_CANCELED` was rejected as a palm,
so it's forgotten with no tap (`cancel`).

**Edges.** We don't ignore touches near the screen edges. The floor fills
the screen width, and portrait thumbs often rest at the bezel, so an edge
dead zone would kill real taps and run drags. The system back swipe is the
real hazard: a run drag that starts at the edge would pause the game. So
`GameView` excludes the bottom 200 dp of both side edges (Android's per-edge
maximum), 32 dp wide, from the back gesture. The upper edges still go back,
and back pauses the game.

## Engine feel (`World`)

**Input buffer (`BUFFER_TIME` = 0.15 s).** A gesture that can't run yet is
held for 0.15 s and runs the moment it can. That covers a jump swiped just
before landing, anything during a takedown or on the stairs, and a shot in
a closed elevator. The newest failed gesture replaces an older one.
Commands that can run immediately never touch the buffer, so a tap while a
jump is buffered still fires at once. `Player.bufferedCommand` and
`Player.bufferAge` expose the buffer to the HUD.

**Hit grace (`HIT_GRACE` = 66 ms).** Touch-to-photon latency on phones is
roughly 50–100 ms: what you see is behind the simulation. When an enemy
bullet touches you, it hangs there for 66 ms (8 ticks). If you've boxed
under it, jumped over it, slipped into a door or started a takedown by
then, it misses, and you get "CLOSE!" (`World.closeCalls`,
`Player.sinceCloseCall`). Otherwise it hits exactly as before. The window
only forgives display latency, not slow reactions, and melee, hazards and
falling lights get no grace because each has its own long telegraph. In
the bot test (12 runs × 6 min per preset), hits taken went 68→59 on
CHILL, 87→53 on AGENT, 43→40 on BRUTAL and 37→36 on STRAIGHT_TO_HELL. The
difficulty ordering held.

**Run.** Ground acceleration is 70 u/s² (`RUN_ACCEL`), so you reach full
speed (4.4 u/s) in 63 ms, and stopping takes the same. Reversing
accelerates at 2.2× (`TURN_BOOST`), so a full turnaround takes about 90 ms,
down from 160 ms. It reads as one snap, not a skid. Air control is 30 u/s².

**Jump arc.** Launch speed and rising gravity are unchanged, so low shots
are cleared exactly as fast as before (you're above the low lane in about
40 ms). Through the apex (|vz| < 2.2) gravity is 0.55×, which gives hang
time for "jump + tap" on a ceiling light. Falling gravity is 1.3×, for a
snappy landing. Airtime went from 0.69 s to about 0.79 s and the peak from
1.80 to 1.87 u, so the jump spends about 0.1 s longer above vent height.
The hard-landing threshold moved to −12.5 u/s, so normal jumps don't shake
the camera and ground pounds still do.

**Swipe down in mid-air.** High up, it's a ground pound (a stomp). Below
0.6 u while falling (`LATE_POUND_Z`), a pound is pointless, so the swipe is
treated as an early hide: it's buffered and runs on landing.

**Swipe down in context.** Priority is simply the thing under you. The
nearest door or elevator in reach wins, with a 0.1 u nudge toward what
you're facing. Door reach (0.55) and shaft reach (0.65) can't both cover
one spot, because slots are 1.2 u apart. The real "elevator instead of
box" trap was a car opening under your thumb mid-swipe. Now a car only
counts once its doors have been open for 0.12 s (`ELEVATOR_REACT_TIME`),
which is about human reaction time. The HUD hint uses the same rule, so
the hint never lies.

**Box and door toggles.** Swipe down in the box: if there's a door or
elevator here, you slip into it (box-sneak up to a door, then swipe);
otherwise you stand up. Swipe down in a doorway steps out. Both need 0.35
s in the hiding place first (`TOGGLE_GUARD`), so a panicky double flick
never undoes a hide.

**Door and elevator exits aren't sticky, and they aren't slippery
either.** You usually flick into a door while your other thumb is still
holding the run. Before, that held run popped you straight back out (and
out of an elevator before it left). Now the direction held on entry
(`Player.holdAxis`) keeps you in. Lifting that thumb re-arms the exit;
then a fresh drag, or reversing the held drag, steps you out.

**Takedown magnet (`TAKEDOWN_MAGNET` = 0.3 u).** While you push toward a
chokeable guard, takedown reach grows by 0.3 u and the choke snaps you
into place. Standing still, reach is unchanged. Heavies still bounce off
from the front at normal reach.

**Auto-aim intent.** The target score is `tier × 6 + distance + 3 if
behind you`. Tier 0 is about to hurt you: aiming at you, mid-slash, or a
melee charger within 3 u. Tier 1 is alert and facing you. Tier 2 is
everyone else. So the guard with his gun up behind you beats the idle one
in front of you, and among equal threats what's in front and near wins.
Ducking targets are shot low, as before.

**Jump + tap on a light.** An airborne tap goes to the ceiling light ahead
only when that's clearly meant: the light would land on someone (a guard
within 0.9 u of it), or there's nobody in front to shoot. Before, every
airborne tap near a light went into the ceiling, even when you jumped a
low shot to return fire.

**Double-tap never costs a shot.** With no grenade to throw (none left,
one already in the air, or in an elevator), a double-tap fires. It still
flashes "NO GRENADES" when you're out.

**Stairs.** These are unchanged on purpose. They trigger when you're
grounded, within 0.6 u of the stairwell wall, and still pushing toward it.
Knockback or momentum alone never takes them, and you arrive on the
opposite side (stairs zigzag), so they can't chain.

## Considered and rejected

- **Swipe up in an elevator to ride up.** The game is an endless descent
  and cars only carry you down. An "up" gesture that does nothing
  meaningful is worse than one that's ignored.
- **Coyote time for jumps.** There are no ledges; floors are flat and the
  stairs are automatic.
- **A dead zone at the screen edges.** See "Edges" above; system gesture
  exclusion solves the real problem without killing edge touches.
- **Palm rejection by contact size.** `touchMajor` varies too much across
  devices. The platform's own palm rejection (`FLAG_CANCELED`) is honoured
  instead.
- **Delaying taps to disambiguate double-taps.** Any delay on the primary
  fire button feels worse than an occasional grenade. The gap and burst
  rules and the grenade-in-flight fallback make that rare.
- **Variable jump height.** A flick has no "hold" to measure, and a
  velocity-scaled jump would make low-shot dodges inconsistent.
