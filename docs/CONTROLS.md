# Controls: design notes

Why every gesture and feel rule is the way it is, so tuning stays principled.
Gesture thresholds live in `input/GestureInput.kt` (companion constants, in dp
and ms). Feel constants live in `engine/World.kt` (the "Controls & feel" block
in the companion). Every rule has a test in `GestureInputTest`,
`ControlsTest` or `MechanicsTest`.

Two principles run through everything:

1. **Never eat an input.** A gesture the player clearly made should do
   something useful, even if it arrived a little early or the thing it
   meant isn't available.
2. **Never invent an input.** A resting, settling or repositioning thumb
   must never jump, hide, walk through a door or throw a grenade.

When the two conflict, remember which is worse for the gesture in
question. A missed jump is a lost life. A phantom grenade wastes a
resource and alerts the floor. A phantom passage drops you into a hallway
you didn't choose.

## The scheme

Every verb has exactly one gesture, and no gesture means two things:

| Gesture | Verb |
|---|---|
| Drag | Run |
| Swipe ↑ | Jump (stomp from above) |
| Swipe ↓ | Hide: a doorway in reach, else the box; in a lift, the box in the car |
| Tap | Interact: a passage, a live STASH door, an elevator (ride it if it's open, call it if not) |
| Grenade button | Grenade |
| Walk into a guard | Takedown |
| Jump + tap | Swat out the ceiling lamp overhead |
| Mode button | GUNS HOT ⇄ SILENT |

The gun is not a gesture any more. In **GUNS HOT** it fires itself (see
"Auto-fire" below); in **SILENT** it never fires. That frees the tap, the
most reliable gesture on glass, for the thing every floor now asks of you:
choosing a door.

## Gesture classification (`GestureInput`)

Each finger is classified on its own, so one thumb can run while the other
taps. A finger starts **PENDING** and becomes **HELD** once it runs or
flicks. A held finger with `dir == 0` is a resting thumb: it can still flick,
or drag sideways into a run.

**One boundary everywhere is 45°.** A stroke steeper than 45° from
horizontal is vertical (a flick). A shallower stroke is horizontal (a run).

| Gesture | Threshold | Why |
|---|---|---|
| Run start | 10 dp sideways (`SLOP_DP`) | About 1.6 mm. That's enough to reject tap jitter and still feel instant. |
| Run reverse | 12 dp back from the furthest point (`REVERSE_DP`) | Lets you turn around with no centre point to cross. Suppressed during a vertical stroke, so a flick that drifts backwards never flips the run. |
| Flick, fresh finger | 22 dp vertical (`FLICK_DP`), inside 150 ms (`FLICK_WINDOW_MS`) | The distance is forgiving for short thumb flicks. The time window is a speed gate (≥ ~150 dp/s), so a slow slide or a thumb settling never jumps. A slow vertical slide becomes a resting HELD finger instead. |
| Flick, held finger | 26 dp inside 150 ms (`FLICK_MID_RUN_DP`); direction judged on the last 50 ms (`RECENT_MS`) | A running thumb wobbles more, so it needs a little more distance. Direction comes from the recent stroke only, so an L-shaped run-then-flick counts. |
| Fast flick seen only at lift | 60% of `FLICK_DP` in < 220 ms | Flicks so fast the digitiser gives almost no move events. |
| Flick cooldown / rebound | 260 ms same finger; 600 ms opposite direction ignored | The thumb springing back after a jump is not a hide. A deliberate opposite flick after 0.6 s works. |
| Flick then drag | 14 dp sideways from rest (`RESTART_DP`) | Jump, then run with the same thumb without lifting. |
| Tap | ≤ 10 dp, < 300 ms (`TAP_MS`) | Recognised on release with zero added delay. Longer holds are resting thumbs. |
| Sloppy tap | A finger that became a run but lifted within 150 ms, having travelled ≤ 16 dp | A hard, rolling thumb press slides past the run slop. It was a tap, not a step. |

**Fingers leaving.** `ACTION_CANCEL` means the system took the gesture. The
run stops (`releaseAll`), but flicks and taps that were already recognised
still count. A pointer lifted with `FLAG_CANCELED` was rejected as a palm,
so it's forgotten with no tap (`cancel`).

**Edges.** We don't ignore touches near the screen edges. The floor fills
the screen width, and portrait thumbs often rest at the bezel, so an edge
dead zone would kill real taps and run drags. `GameView` excludes the bottom
200 dp of both side edges, 32 dp wide, from the system back gesture instead.

**Buttons.** Pause and the mode toggle are hit-tested in `GameView` before
gesture input sees the touch, in screen pixels (the HUD is sized to the
screen, not the world). Their generous hit circles overlap a little; the
nearer centre wins, so neither steals the other's taps.

## Engine feel (`World`)

**Input buffer (`BUFFER_TIME` = 0.15 s).** A gesture that can't run yet is
held for 0.15 s and runs the moment it can. That covers a jump swiped just
before landing, and anything during a takedown or a passage. The newest
failed gesture replaces an older one. `Player.bufferedCommand` and
`Player.bufferAge` expose the buffer to the HUD.

**Hit grace (`HIT_GRACE` = 66 ms).** Touch-to-photon latency on phones is
roughly 50–100 ms. When an enemy bullet touches you, it hangs there for
66 ms. If you've boxed under it, jumped over it, slipped into a door or
started a takedown by then, it misses, and you get "CLOSE!". The window only
forgives display latency, not slow reactions.

**Run.** Ground acceleration is 70 u/s² (`RUN_ACCEL`), so you reach full
speed (4.4 u/s) in 63 ms. Reversing accelerates at 2.2× (`TURN_BOOST`), so
a full turnaround takes about 90 ms: one snap, not a skid. The hallway is
14 u wide, about three seconds end to end.

**Jump arc.** Full gravity on the way up (you clear the low lane in about
40 ms), 0.55× through the apex for hang time (aim at a light, line up a
stomp), 1.3× falling for a snappy landing. Airtime is about 0.79 s, peak
1.87 u: enough to land on a Heavy's head (1.7 u).

**Swipe down in mid-air.** High up, it's a ground pound (a stomp). Below
0.6 u while falling (`LATE_POUND_Z`), a pound is pointless, so the swipe is
treated as an early hide: it's buffered and runs on landing.

**Tap and swipe never compete.** Tap targets are passages, live STASH
doors and elevator landings with a ride down; swipe-down targets are hiding
doorways (and an emptied STASH door). A passage is never a hiding place and a
doorway is never a tap target, and generation keeps doors at least 3 u apart
(`Geo.MIN_DOOR_GAP`) and clear of shaft columns, so there's only ever one
thing in reach (`TAP_REACH` 0.8 u, `DOOR_REACH` 0.6 u, `ELEVATOR_REACH`
0.8 u). With nothing in reach, a tap does nothing (never eat an input into
something you didn't mean; the HUD chip shows when a tap will do something).

**Every tap is just a tap.** Grenades used to be a double-tap, which meant
a quick second tap at a door threw one by accident (and the first tap of a
real double-tap had to wait 0.28 s so it wouldn't walk you through the door).
Now grenades have their own HUD button, so taps act the instant you lift, at
any rhythm. Taps that land while you're already mid-passage are dropped, so
mashing a door never bounces you back through it.

**Which lift goes down.** A landing with a ride down from it has cyan-lit
jambs and threshold, a cyan call button and ▼ chevrons that chase down its
shut doors. The bottom of a shaft (the car you arrived in, in hallway A) is
plain steel with a dim LAST STOP plate and no call button; tapping it does
nothing.

**Elevators.** A car only counts as boardable once its doors have been open
for 0.12 s (`ELEVATOR_REACT_TIME`): a car opening under your thumb is called,
not boarded. Idle cars stay parked with their doors shut. Tapping a landing
calls its car: if it's parked right there the doors just open, otherwise it
heads straight for you. Either way it holds its doors 3 s (`CALL_HOLD`), then
shuts them and waits where it is. The ride only ever
goes down, straight to the bottom of the shaft; an express opens once on the
way (`EXPRESS_STOP_TIME` 1.5 s) and you can step off there. At the bottom
you step out on your own after 0.55 s (`AUTO_EXIT_TIME`), into hallway A.
Swipe down in the car to box up: guards at the doors see an empty lift.

**Passages (`PASSAGE_TIME` = 0.42 s).** You step into the door, the hallway
swaps at the halfway mark, and you step out of the matching door on the far
side (its plate names the hallway you came from). The renderer slides the
old hallway out and the new one in, with a green seam, in 0.26 s. You're
untouchable mid-passage, and the far hallway's first ambush waits a few
seconds (`Heat.firstAmbushDelay`), so a door never drops you into a firing
squad.

**Box and door toggles.** Swipe down in the box: if there's a doorway here,
you slip into it; otherwise you stand up. Swipe down in a doorway steps out.
Both need 0.35 s in the hiding place first (`TOGGLE_GUARD`), so a panicky
double flick never undoes a hide.

**Door and elevator exits aren't sticky, and they aren't slippery
either.** The direction held on entry (`Player.holdAxis`) keeps you in.
Lifting that thumb re-arms the exit; then a fresh drag, or reversing the
held drag, steps you out.

**Takedown magnet (`TAKEDOWN_MAGNET` = 0.3 u).** While you push toward a
chokeable guard, takedown reach grows by 0.3 u and the choke snaps you into
place. Heavies still bounce off from the front; a napping guard can be taken
from any side. A guard only senses you behind him inside 1.0 u
(`BEHIND_SENSE`), which is inside the lunge, so pushing into a guard's back
always wins the race, even in GUNS HOT.

**Arrival grace (`ARRIVAL_GRACE` = 0.8 s).** A guard who spots you less than
0.8 s after you stepped into the hallway (through a passage, out of a car)
takes the rest of that 0.8 s extra to react. The passage slide alone takes
0.26 s; this is time to read the new hallway, not free time to dawdle.

**The box double-take.** In the box, moving faster than 0.5 u/s
(`BOX_SUSPICIOUS_SPEED`) in front of a walking guard who's looking at you
puts him in SEARCH ("HUH?"): he walks over to where the box is and looks
around for 3.5 s (`SEARCH_LINGER`) once he gets there. If he reaches the
box, it's an ambush. Heavies and ninjas kick it off instead. GHOST BOX is
never suspected. Sitting still is always just a box.

**Auto-fire (GUNS HOT).** Whenever the gun is ready, it fires at the top
threat within 7.5 u (`AUTO_FIRE_RANGE`, 11 u with the minigun), on the move
and in the air. The target score is `tier × 6 + distance + 3 if behind you`.
Tier 0 is about to hurt you: aiming at you, mid-slash, or a melee charger
within 3 u. Tier 1 is alert and facing you. Tier 2 is everyone else. So the
guard with his gun up behind you beats the idle one in front of you. It only
fires at **threats**: a guard who hasn't noticed you is left alone
unless he's facing you within 2.5 u (`AUTO_FIRE_POINT_BLANK`); one with his
back to you, or asleep, is never shot, so even GUNS HOT keeps the choice of
sneaking past or walking in for the takedown (drones and turrets are always
fair game). Guards stepping out of a door get a quarter-second before the gun
turns on them. Hidden (box, doorway, boxed in the car) the gun holds.

**SILENT.** The gun never fires. Guards only notice what they see, see a
little less far (6.5 u instead of 7.5, `SILENT_SIGHT_RANGE`) and take 35%
longer to react (`SILENT_REACTION`). Every kill that isn't a shot or a blast
(takedown, stomp, light, hazard) scores double. It's the riskier, richer way
down.

**Jump + tap on a light.** An airborne tap swats out the live lamp overhead
(within 1.1 u sideways, `LIGHT_REACH`) by hand: no gun, no ammo, either mode.
The fixture drops a beat later onto anyone within 0.8 u of it, never onto you.
The crash of glass brings every guard within 7 u (`LIGHT_LURE_RADIUS`) over to
look, sleepers included ("HUH?", a SEARCH at the lamp, not an alert): a lure.

**Arriving in SILENT.** Through a passage or out of a car you arrive tucked
into the doorway's shadow, hidden, so a guard facing the door sees nothing:
from a car the instant it stops at the bottom (right in its doorway, never
standing lit in the open car), from a passage straight out of the dark of the
walk-through.
Tap, swipe up, or lift and drag to step out when it's clear. (GUNS HOT arrives
in the open, with the 0.8 s arrival grace.)

**The HUD buttons.** Pause, the mode button and the grenade button (lime,
with your grenade count on it) stack down the top-right corner, all the same
size. A touch that goes down on one never reaches the gesture classifier, so
it can't also run or tap a door. Their hit circles are generous (pause 1.8×,
the others 1.6× their radius) and where two overlap the nearer centre wins
(`Hud.buttonAt`).

**The grenade button with nothing to throw.** With no grenades it says so
("NO GRENADES") and does nothing else; with one already in the air the press
is ignored (the button greys out for both). It never falls back to a door.

## Considered and rejected

- **Swipe up in an elevator to ride up.** The game is an endless descent
  and cars only carry you down.
- **Tap to shoot, with a separate interact gesture.** Every floor now asks
  you to pick a door; the tap is the most reliable gesture on glass, so it
  goes to doors. Aiming was already automatic; firing is now too (or off).
- **Swipe down on passages.** Swipe down is always "hide". Mixing "hide" and
  "leave" on one gesture made the old swipe-down trap (a lift opening under
  your thumb) the rule instead of the exception.
- **Stairs.** Walking off the end of a floor made every floor a straight
  line. Elevators you have to reach, wait for or call make every floor a
  choice.
- **Horizontal scrolling.** The whole hallway is always on screen: every
  guard, door and lift you'll deal with is visible before you commit.
- **Coyote time for jumps.** There are no ledges.
- **A dead zone at the screen edges.** See "Edges" above.
- **Palm rejection by contact size.** `touchMajor` varies too much across
  devices. The platform's own palm rejection (`FLAG_CANCELED`) is honoured.
- **Variable jump height.** A flick has no "hold" to measure, and a
  velocity-scaled jump would make low-shot dodges inconsistent.
