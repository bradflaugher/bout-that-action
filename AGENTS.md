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
    elevators, lights, hazards, perks, scoring, camera. `step(dt)` on a fixed
    120 Hz timestep; one-shot `GameEvent`s for audio/haptics.
  - `Level.kt` — `Geo` (world units), `LevelGen`: every floor is rebuilt from
    `(seed, floor)` alone, so the building is endless and never stored.
    Stairs zigzag; elevator shafts cycle through three columns so they never
    collide.
  - `Difficulty.kt` — the player-tunable heat curve and presets; `Heat` maps
    heat to every enemy stat.
  - `Zone.kt` — the descent: Rooftop → Neon Tower → Black Labs → Deep Metro →
    Iron Mines → Magma Core → Hell (150–199) → the Void (200+, random zones).
  - `Entities.kt`, `Perk.kt`, `Fx.kt`, `Events.kt`, `Rng.kt` (SplitMix64).
- `input/GestureInput.kt` — multi-touch gesture classifier (run drag with
  instant reversal, flicks mid-drag, zero-latency taps, double-tap).
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
  class). Run the bot (`BotPlaythroughTest`, it prints a per-preset report)
  after balance changes and keep the difficulty ordering CHILL < AGENT <
  BRUTAL < STRAIGHT_TO_HELL.
- Determinism: a run is a pure function of `RunConfig` and the input
  sequence. Never use wall-clock time or unseeded randomness in `engine/`.
- The game is portrait-only and the floor exactly fills the screen width.
- Screenshots in `docs/screenshots/` come from `./gradlew :app:screenshots`,
  which renders real scenes through the real renderer. Regenerate them when
  the look changes.

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
