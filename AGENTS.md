# Agent and contributor instructions

'Bout That Action (a Marshawn Lynch reference) is an endless, portrait-only
Android action game: a stylized, hyper-modern take on Elevator Action with
Metal Gear Solid box hiding. Sideloaded only. Read `README.md` for the
player-facing overview and keep it in sync with any behavior you change.

## Latest-only platform policy

Like bf-12c and Blauncher, this project supports **only the latest stable
everything**:

- `minSdk`, `targetSdk` and `compileSdk` are the latest stable API level, all
  equal (`app/build.gradle.kts`). Bump all three together.
- No `Build.VERSION.SDK_INT` checks, no compat shims for older devices.
- AGP, Kotlin, Compose BOM and libraries (`gradle/libs.versions.toml`) and
  Gradle (`gradle/wrapper/gradle-wrapper.properties`, checksum-pinned) track
  the latest stable releases. Dependabot keeps them current.

## Layout

Everything under `engine/`, `input/`, `render/` and `audio/` (except
`audio/AudioOutput.kt`) is **pure Kotlin with no Android imports**, so the
whole game — simulation, controls, drawing and sound — is unit-testable on
the JVM.

- `engine/` — the simulation.
  - `World.kt` — the game: player, enemies, bullets, grenades, pickups,
    elevators, passages, lights, hazards, perks, the GUNS HOT / SILENT mode,
    scoring, camera. `step(dt)` on a fixed 120 Hz timestep; one-shot
    `GameEvent`s for audio/haptics. Everything that lives on a floor also
    has a hallway (`hall`); `viewHall(floor)` says which one is on screen.
  - `Level.kt` — `Geo` (world units: a 14 u hallway, 8 door/shaft slots),
    `LevelGen`: every floor is rebuilt from `(seed, floor)` alone, so the
    building is endless and never stored. A floor is 2–4 hallways
    (`HallPlan`) joined by paired passage doors. There are no stairs: every
    floor has a ride down (odd floors always start a local shaft, and the
    one above an empty even floor runs two floors), rides arrive in hallway
    A and leave from the others, local shafts cycle through three columns and
    expresses through two, so shafts never collide.
  - `Difficulty.kt` — the player-tunable heat curve and presets; `Heat` maps
    heat to every enemy stat.
  - `Autopilot.kt` — the bot (balance tests and the title-screen demo): it
    routes through passages to a ride down, calls cars, and plays both modes.
  - `RunStats.kt` (highlights, the hurt log, the fatal hit) and
    `RunReport.kt` (the game-over card: playstyle title, death line, quip).
    Special floors (`FloorEvent`: blackout, nap time, payday) roll from
    `(seed, floor)` on their own RNG stream in `LevelGen.eventOn`, so they
    never change a floor's layout.
  - `Zone.kt` — the descent: Rooftop → Neon Tower → Black Labs → Deep Metro →
    Iron Mines → Magma Core → Hell (150–199) → the Void (200+, random zones).
  - `Hero.kt` — the four heroes (`RunConfig.hero`): each trait is data on the
    enum, and `Perk.hero` / `Perk.offeredTo` keep three perks per hero.
  - `Entities.kt`, `Perk.kt`, `Fx.kt`, `Events.kt`, `Rng.kt` (SplitMix64).
- `input/GestureInput.kt` — multi-touch gesture classifier (run drag with
  instant reversal, flicks mid-drag, zero-latency taps; grenades are a HUD button, not a gesture).
- `render/` — `Gfx.kt` is the tiny drawing interface; `Renderer` draws the
  world, HUD and overlays through it.
- `audio/` — procedural synth, sequencer, songs per zone, SFX; `SoundEngine`
  is the API. `AudioOutput.kt` streams it to an `AudioTrack`.
- `AndroidGfx.kt` — `Gfx` on `android.graphics.Canvas`.
- `GameView.kt` — `SurfaceView` + game thread; touch → `GestureInput`.
- `MainActivity.kt`, `ui/` — Compose menus (title, settings, pause, game
  over), `Settings.kt` (prefs, seeds), `Haptics.kt`.
- `app/src/test/` — JVM tests: `engine/` (mechanics, level generation,
  fuzzing, a heuristic bot that plays full runs per preset), `input/`
  (gestures), `render/` (AWT `Gfx` backend, headless screenshots), `audio/`.
- `app/src/androidTest/` — emulator smoke tests (launch, and 20 s of real
  injected touches).
- `tools/icon/gen_icon.py` — generates the adaptive launcher icon. Edit the
  script, not the XML.

## Rules

- Keep the pure packages pure: no `android.*` in `engine/`, `input/`,
  `render/` or `audio/` (except `AudioOutput.kt`).
- Every engine rule change gets a test in `MechanicsTest` (or a new test
  class, like `StealthAndEventsTest`). Run the bot (`BotPlaythroughTest`,
  it prints a per-preset balance report and a pacing report) after balance
  changes and keep the difficulty ordering CHILL < AGENT < BRUTAL <
  STRAIGHT_TO_HELL.
- Fun, not compulsion: no streaks, daily rewards, timers or "come back"
  nags. Text is short, silly and family-friendly.
- Enums the renderer switches on exhaustively (`Perk`, `PickupKind`,
  `TextStyle`, `ParticleKind`, `ContextAction`, `EnemyKind`, states) need a
  render change alongside any new value; prefer fields and events.
- Determinism: a run is a pure function of `RunConfig` and the input
  sequence. Never use wall-clock time or unseeded randomness in `engine/`.
- The game is portrait-only and the floor exactly fills the screen width:
  the whole hallway is always visible, never scrolled. The HUD, the context
  chip and touch thresholds are sized to the screen (px/dp), not the world.
- Every floor must have a ride down reachable from every hallway
  (`LevelGenTest.everyFloorHasAReachableRideDown`), and doors are never
  closer than `Geo.MIN_DOOR_GAP`.
- Tap and swipe ↓ never compete: taps use passages, STASH doors and elevators;
  swipe ↓ only hides. See `docs/CONTROLS.md`.
- Screenshots in `docs/screenshots/` come from `./gradlew :app:screenshots`,
  which renders real scenes through the real renderer and then runs
  `:app:menuShots` for the Compose menus (Robolectric). Regenerate them when
  the look changes. `-Pata.scene=<name>` renders one game scene,
  `-Pata.shots=<dir>` writes elsewhere and `-Pata.full=true` keeps full
  1080x2400 resolution, for iterating on the look. `-Pata.scene=cast` renders
  the cast sheet instead (`CastScreenshotTest`): every archetype in every zone
  and the agent's key poses, cropped from real frames at 2x, for character art.
  `-Pata.scene=icons` renders the icon sheet (`IconScreenshotTest`): every perk
  and pickup icon, the heart and the badges at chip, pill and card sizes.

## Invariants

- **No network, no storage permissions.** Only `VIBRATE`. No backups
  (`allowBackup=false`, empty extraction rules).
- CI actions stay pinned to commit SHAs.

## Build, test, release

```sh
./gradlew lint test assembleDebug
./gradlew :app:screenshots        # re-render docs/screenshots
```

`.github/workflows/ci.yml` runs unit tests and lint as separate checks on
every pull request and push. `emulator-smoke.yml` (manual, plus weekly;
deliberately not a merge gate because emulators are slow and flaky) boots an
API 37 emulator, runs the instrumented tests, plays a little with `adb input`
and uploads screenshots and logcat.

Every push to `main` builds a signed APK and publishes it as the single
date-labeled GitHub release `vYYYY.MM.DD.<run>`, deleting all older
releases. Pull requests build unsigned and publish nothing. Signing uses the
repository secrets `ATA_KEYSTORE_BASE64`, `ATA_STORE_PASSWORD`,
`ATA_KEY_ALIAS` and `ATA_KEY_PASSWORD`; until they exist, pushes to `main`
still build and test but skip the release (with a warning). Never commit a
keystore.
