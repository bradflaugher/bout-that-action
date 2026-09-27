# <img src="docs/screenshots/icon.png" alt="" height="44" align="top"> 'Bout That Action

*"I'm just 'bout that action, boss."*

An endless, portrait-only Android action game. It's a hyper-modern, neon-noir
take on the arcade classic **Elevator Action**, with a little **Metal Gear
Solid** cardboard box mixed in. A helicopter drops you on the roof of a
skyscraper. Then you go down, and down, and down: through the tower, under
the city, through the Earth's crust and the magma core, into **Hell**, and
past it into a Void where anything goes.

Everything is generated: procedural floors, procedural soundtrack,
procedural sound effects. No asset files, no ads, no network.

<div align="center">
  <img src="docs/screenshots/rooftop.png" width="32%" alt="Rooftop insertion with the tutorial billboard">
  <img src="docs/screenshots/tower.png" width="32%" alt="A firefight in the Neon Tower">
  <img src="docs/screenshots/hell.png" width="32%" alt="Hell: demons, fireballs, a 25x combo">
  <p><em><b>Rooftop</b> insertion · <b>Neon Tower</b> firefight · <b>Hell</b>, B115</em></p>
</div>

## Controls

Built for thumbs. Every gesture works anywhere on the screen, and each finger
is read on its own, so one thumb can run while the other taps.

| Gesture | Does |
|---|---|
| **Drag ← →** and hold | Run. Nudge back a little to turn around instantly. Lift to stop. A held run won't pull you out of a doorway or elevator: lift and drag again to step out. |
| **Tap** | Interact with what you're standing at: go through a green **passage** door into another hallway, enter a red **INTEL** door, ride an open **elevator** down, or call a closed one. With nothing in reach, a tap does nothing. |
| **Swipe ↓** | Hide: press into a nearby **doorway**, otherwise pop the **cardboard box**. In an elevator, box up in the car. Swipe ↓ again to stand up. |
| **Swipe ↑** | Jump. Clears low shots; land on heads to stomp. Works mid-run. |
| **Walk into an enemy** | Instant silent **takedown**. Heavies only from behind; a napping guard from anywhere. Works in GUNS HOT too: the gun never shoots a guard with his back to you. |
| **Double-tap** | Throw a grenade, in either mode. Fast tapping never throws by accident, and mashing a door with nobody around is just the tap. |
| **Jump + tap** | A suppressed shot at the ceiling light ahead, when someone's under it (or there's nobody else to shoot). It crushes them, blacks out the hallway and wakes nobody. Works in both modes. |
| **Mode button** (under pause) | **GUNS HOT**: you auto-fire at threats in range. **SILENT**: you never fire; guards only notice what they see, and quiet kills pay double. Your choice sticks between runs. |

A chip over your head shows what a tap (or a swipe ↓) will do right now, and
the hallway map in the corner shows where you've been and which hallways have
a ride down. An optional thumb guide shows where your run drag started.
Inputs are forgiving: a gesture made a hair early is buffered, and a bullet
that lands the same instant you dodge counts as a miss ("CLOSE!"). On a run
from the roof, **coach tips** pop up the first time each move would help
("SWIPE DOWN: HIDE"); turn them off in Settings. Design
notes and every threshold are in [`docs/CONTROLS.md`](docs/CONTROLS.md).

## How it plays

- **Every floor is a little puzzle.** A floor is two to four hallways joined
  by passage doors, and the whole hallway is always on screen. You arrive in
  hallway **A**; the ride down is always somewhere else. A lift only shows in
  the hallway it opens into; the passage plates and the HALLS map mark the
  hallways with a ride down, so you always know where you're headed. Your
  floor is the stage: the floor below, where you're headed, is a step back,
  and the rest of the tower recedes into the dark. The choice is how: which
  door, which hallway's guards, which lift, and when to move.
- **Elevators are the only way down.** No stairs. Most shafts are short 1–2
  floor hops. Rare gold **express** shafts drop 3–5 floors at once, but open
  their doors once on the way, onto someone waiting. Cars wait behind closed
  landing doors until called: tap a landing to call one, and hide while it comes.
- **Guns hot or silent.** In GUNS HOT the gun fires itself at whoever is about
  to hurt you first, and leaves guards who haven't spotted you to you. In SILENT you never fire: take guards from behind, drop
  from above, wait them out in a doorway or under the box, lure them into
  hazards, or black out the lights. Guards take longer to react to a shadow,
  and every quiet kill is worth double. Flip modes any time.
- **Patrols you can time.** Guards walk a regular beat and stop to look
  around at each end. A couple per hallway, and the doors only rarely spit
  out more. In the box, a patrol that walks into you gets ambushed (BOX'D!).
  Guards who spot you the instant you step into a hallway give you a beat
  to take it in first.
- **The box is a lure.** Move it while a guard is looking right at it and he
  stops ("HUH?") and comes over to check. Let him. Heavies and ninjas aren't
  fooled: they kick the box off. Leave a floor without anyone spotting you
  and it's a **GHOST** bonus (double in SILENT).
- **Special floors.** About one floor in six is something else: a
  **BLACKOUT** (every light dead, for them too), **NAP TIME** (guards asleep
  at their posts: tiptoe up for a NIGHT NIGHT), or **PAYDAY** (somebody left
  the loot lying around). Some rides come with smooth jazz. The first guard
  of every run is napping on the roof.
- **Roguelike runs.** Every red INTEL door offers three perks, and they
  stack: Rapid Fire, Pierce, Ricochet, Split Shot, CQC Master, Ghost Box,
  Double Jump, Demolition, Reflex (auto bullet-time), Kevlar, Shockwave
  stomps and more. Enemies drop shotguns, miniguns, shields, bullet time,
  medkits and cash.
- **Endless and seeded.** Any floor can be rebuilt from `(seed, floor)`, so
  the building never ends and never uses more memory. The same seed plus the
  same difficulty gives the same building, so you can share a seed or play
  the **daily** building.

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/passage.png" alt="Through a passage into hallway B"><p><em><b>Passages.</b> Whoosh: hallway B</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/silent.png" alt="SILENT: a takedown from behind"><p><em><b>SILENT.</b> From behind, for double</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/express.png" alt="An express elevator opens onto a waiting guard"><p><em><b>Express.</b> The doors open on the way</em></p></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/box.png" alt="Hiding in the cardboard box"><p><em><b>The box.</b> "?"</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/intel.png" alt="Perk choice"><p><em><b>INTEL</b>: pick one of three</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/darkness.png" alt="Lights shot out"><p><em><b>Lights out.</b> They can't see you either</em></p></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/naptime.png" alt="NAP TIME: guards dozing at their posts"><p><em><b>Nap time.</b> Zzz. Tiptoe</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/boxd.png" alt="A guard double-takes at the box"><p><em><b>HUH?</b> The box moved</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/kick.png" alt="A Heavy kicks the box off"><p><em><b>HEY!</b> Heavies aren't fooled</em></p></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/ghost.png" alt="GHOST: leaving a floor unseen, with smooth jazz"><p><em><b>GHOST.</b> Nobody saw a thing</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/blackout.png" alt="BLACKOUT: emergency lights only"><p><em><b>Blackout.</b> Follow the red lights</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/payday.png" alt="PAYDAY: loot lying around"><p><em><b>Payday.</b> Somebody's bonus</em></p></td>
  </tr>
</table>

## The descent

You only ever go down, one elevator at a time. Floors count down like a real
building, and the zone (and its music and heat) comes from the floor.

| Floors | Zone | Vibe |
|---|---|---|
| ROOF | **Rooftop** | Helicopter drop, skyline, the tutorial on a billboard, the penthouse lift |
| 49F–26F | **Neon Tower** | Corporate synthwave, 118 BPM |
| 25F–1F | **Black Labs** | Laser grids, drones, cold techno |
| B0–B24 | **Deep Metro** | Steam vents, turrets, breakbeats |
| B25–B49 | **Iron Mines** | The crust. Industrial clank |
| B50–B99 | **Magma Core** | Lava vents, darksynth |
| B100–B149 | **Hell** | Demons and fireballs at 165 BPM. Built to be (nearly) impossible |
| B150+ | **The Void** | Every block of floors rolls a random zone and a random, brutal heat |

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/screenshots/labs.png" alt="Black Labs"><p><em>Black Labs</em></p></td>
    <td align="center" width="25%"><img src="docs/screenshots/metro.png" alt="Deep Metro"><p><em>Deep Metro</em></p></td>
    <td align="center" width="25%"><img src="docs/screenshots/magma.png" alt="Magma Core elevator ride"><p><em>Magma Core</em></p></td>
    <td align="center" width="25%"><img src="docs/screenshots/void.png" alt="The Void"><p><em>The Void</em></p></td>
  </tr>
</table>

## When it's over

The game-over card tells the story of the run: a playstyle title
("CARDBOARD ENTHUSIAST", "BONK SPECIALIST", "THE GHOST"), what got you
("Steamed like a dumpling"), your highlights, and a sign-off ("I'm just
'bout that action, boss."). No streaks, no daily rewards, no timers asking
you back. The building will still be there.

## The front end

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/menu-title.png" alt="Title screen with the neon logo over a live demo run"><p><em><b>Title</b>: a neon sign over a live autopilot run</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/menu-settings.png" alt="Settings with the custom heat curve"><p><em><b>Settings</b>: shape your own heat curve</em></p></td>
    <td align="center" width="33%"><img src="docs/screenshots/menu-gameover.png" alt="Game over with a new deepest floor"><p><em><b>Game over</b>: the depth counts down the building</em></p></td>
  </tr>
</table>

## Difficulty and seeds

Everything scales from one number, **heat**: enemy reaction time, fire rate,
bullet speed, hit points, how many guards each hallway gets, how often doors
spit out reinforcements, and how many hazards a hallway has. Each zone adds
its own bonus, and Hell adds a lot. Pick a preset or shape your own curve:

- **Chill:** slow ramp, 5 hearts.
- **Agent:** the intended descent.
- **Brutal:** hot start, steep ramp, 2 hearts.
- **Straight to Hell:** start on floor 150. Good luck.
- **Custom:** starting heat, ramp, heat cap, hearts, and the zone you start in,
  with a live preview of the curve.

Seeds can be **random**, **daily** (same building for everyone, UTC), or any
word or number you type.

## Sound

The soundtrack is synthesized live: band-limited oscillators, filters,
drums, delay and reverb. Every zone gets its own procedural track, with its
own key, tempo and motif-based melodies. The music gets more intense in a
fight and pitches down in bullet time. Every sound effect is
synthesized too, panned to where it happened on screen, with haptics on the
big moments.

## Install

Grab `bout-that-action.apk` from the [latest release](../../releases/latest)
and sideload it. Every push to `main` publishes a single date-labeled release
(`vYYYY.MM.DD.N`) and deletes the previous one; verify it with the attached
`.sha256`. Releases need the signing secrets described in `AGENTS.md`. Until
they're set, CI builds and tests but publishes nothing.

Android 17 (API 37) or newer only. See `AGENTS.md` for the latest-only
policy. The only permission is vibration.

## Build and test

```sh
./gradlew lint test assembleDebug   # what CI runs (plus assembleRelease)
./gradlew :app:screenshots          # re-render docs/screenshots
./gradlew :app:menuShots            # render the Compose menus to app/build/menushots
                                    # (Robolectric; -PallDevices, -Ponly=title,pause)
```

The whole game (simulation, touch controls, renderer and synth) is pure
Kotlin behind small interfaces, so it's tested on the JVM:

- `MechanicsTest`: every rule, one scripted situation at a time (takedowns,
  GUNS HOT auto-fire, SILENT, the mode toggle and its bonus, box vs high
  shots, jumping low shots, doorways, patrol beats, passages, taps that do
  nothing, elevators that only go down, expresses, calling a car, boxing up
  in the car, intel, suppressed light shots, stomps, grenades, combos, no
  stairs, death, replays).
- `ControlsTest`: input buffering, the hit-grace window, the jump arc,
  turnarounds, auto-aim intent, tap and swipe context, door and car exits.
- `StealthAndEventsTest`: napping guards, the box double-take and the kick,
  GHOST, special floors, the arrival grace, coach tips, the run report.
- `LevelGenTest`: determinism, a reachable ride down from every hallway on
  24,000 floors, rides arriving in hallway A, passage pairs, door spacing,
  shaft consistency, zone order, the heat curve.
- `GestureInputTest`: taps, double-taps, flicks mid-run, instant reversal,
  two-thumb play.
- `BotPlaythroughTest`: an autopilot plays full runs on every preset in both
  modes and prints a balance report (floors, seconds and encounters per
  floor, deaths) and a pacing report (seconds per floor and hallway,
  elevator waits, dead time, the first minute, what hurt you and whether it
  was an ambush or an arrival). The same autopilot plays the demo behind the title screen.
- `WorldFuzzTest`: minutes of random thumbs on every preset.
- `ScreenshotTest`: renders the README screenshots headlessly through the
  real renderer, using a `java.awt` backend.
- Audio tests: DSP, music theory, levels, determinism, and a check that it
  renders faster than real time.
- `GameplaySmokeTest` (emulator): drops into a run and plays with injected
  touches. Runs on an API 37 emulator on demand and weekly; not a merge
  gate, since emulators are slow and flaky.

## License

MIT. Fonts: Audiowide and Share Tech Mono, both under the SIL Open Font
License (`licenses/`).
