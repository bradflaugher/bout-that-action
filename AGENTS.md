# Agent and contributor instructions

'Bout That Action is an endless, portrait-only
Android action game: a stylized, hyper-modern elevator spy caper with
cardboard-box hiding. Sideloaded, and headed for Google Play
(`docs/PLAY_STORE.md`). Read `README.md` for the
player-facing overview and keep it in sync with any behavior you change.

## Latest-only platform policy

Like bf-12c and Blauncher, this project builds with **only the latest stable
everything**, and never carries code just for older devices:

- `targetSdk` and `compileSdk` are the latest stable API level
  (`app/build.gradle.kts`). Bump them together.
- `minSdk` is 31 (Android 12): the newest API the code uses today
  (`VibratorManager`, haptic `PRIMITIVE_THUD`), so older phones come free.
  If a feature needs a newer API, raise `minSdk` to it rather than add a check.
- No `Build.VERSION.SDK_INT` checks, no compat shims for older devices. Lint's
  `NewApi` error keeps the code honest about `minSdk`.
- AGP, Kotlin, Compose BOM and libraries (`gradle/libs.versions.toml`) and
  Gradle (`gradle/wrapper/gradle-wrapper.properties`, checksum-pinned) track
  the latest stable releases. Dependabot keeps them current; `MAINTAINING.md`
  covers the weekly update routine and the repository's security settings.

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
  - `Challenge.kt` — challenges: `Goal` (read straight off `World` stats),
    `Rule`, `Tier`, the generated, append-only catalog (`Challenges.all`, ids
    1..N, a golden checksum in `ChallengeTest`; new ones go in a new batch
    after the last id), each hero's bespoke templates, and
    `Challenges.daily(epochDay, clearedBefore)`. `Challenges.retired` drops the
    noise (restatements of a plain challenge with just a hero or GUNS HOT ONLY,
    and roof UNTOUCHED / SILENT ONLY freebies) from `Challenges.active`, the
    live catalog the board, the daily and side clears use; retired ids keep
    their place, so to cut more, widen `retired` rather than editing the catalog. `RunConfig.challenge` makes
    `World` apply its setup and keep `World.challenge` (progress, cleared,
    failed) with one-shot `ChallengeCleared` / `ChallengeFailed` events.
    `SideClears` (`World.side`, `World.sideCleared`, `GameEvent.SideCleared`)
    ticks off every other challenge any run genuinely meets (same curve,
    start, hero and rules held up to the goal), minus `RunConfig.knownCleared`.
    Side clears are quiet mid-run (no plate, no chime): game over lists them.
    Special floors (`FloorEvent`: blackout, nap time, payday) roll from
    `(seed, floor)` on their own RNG stream in `LevelGen.eventOn`, so they
    never change a floor's layout.
  - `Zone.kt` — the descent: Rooftop → Neon Tower → Black Labs → Deep Metro →
    Iron Mines → Magma Core → Hell (150–199) → the Void (200+, random zones).
  - `Hero.kt` — the four heroes (`RunConfig.hero`): each trait is data on the
    enum, and `Perk.hero` / `Perk.offeredTo` keep three perks per hero.
  - `Entities.kt`, `Perk.kt`, `Fx.kt`, `Events.kt`, `Rng.kt` (SplitMix64).
  - `Guide.kt` — teaching by doing: `Lesson` (every move and HUD part the
    game teaches), the first run's rooftop walkthrough (`RunConfig.tutorial`:
    one step at a time, each waiting for the move, skippable with
    `World.skipTutorial()`) and the one-time tips after it (`RunConfig.coach`,
    minus `RunConfig.learned`). It reads `World` and never changes it; the app
    remembers lessons taught from `GameEvent.LessonTaught`.
  - `SeedCode.kt` — shareable 8-character seed codes (40 bits, no look-alikes)
    and the parser that pulls a code, difficulty and hero out of a pasted brag.
- `input/GestureInput.kt` — multi-touch gesture classifier (run drag with
  instant reversal, flicks mid-drag, zero-latency taps; grenades are a HUD button, not a gesture).
- `render/` — `Gfx.kt` is the tiny drawing interface; `Renderer` draws the
  world, HUD and overlays through it. `HeroArt` paints the player on one shared
  rig, dressed by a `HeroKit` per hero (`HeroBull`, `HeroFox`, `HeroHawk`,
  `HeroMonkey`); `HeroPortrait` draws that same figure for the hero picker.
  `Graffiti` is spray paint (strokes, tags, drips): MONKEY's vandalism on the
  rooftop billboard. `GuideArt` draws the guide: brackets on the target, the
  prompt plate, the ghost thumb, HUD highlights and the walkthrough's SKIP pill
  (`Hud.skipRect`). `Renderer.textScale` is TEXT SIZE for the HUD's labels.
- Fonts: Audiowide (`res/font/audiowide.ttf`, titles, `Gfx.Font.TITLE`,
  `Neon.title`) and Chakra Petch Medium (`chakra_petch.ttf`, body and HUD
  labels, `Gfx.Font.HUD`, `Neon.body`), both SIL OFL 1.1 with their licenses in
  `licenses/`. Any new font must be OFL or Apache 2.0, from google/fonts, with
  its license committed there and README's License line updated.
- `audio/` — procedural synth, sequencer, songs per zone, SFX; `SoundEngine`
  is the API. `AudioOutput.kt` streams it to an `AudioTrack`.
- `AndroidGfx.kt` — `Gfx` on `android.graphics.Canvas`.
- `GameView.kt` — `SurfaceView` + game thread; touch → `GestureInput`.
- `MainActivity.kt`, `ui/` — Compose menus (title, custom run, challenges board
  and briefing, settings, HOW TO PLAY (`HelpScreen.kt`: controls, FAQ, replay
  tutorial, and the title's one-time FIRST TIME HERE? card), pause, game
  over; settings has TEXT SIZE (`TextSizeScope` in `Theme.kt`, capped at
  `MAX_TEXT_SCALE` with the system font size, wrapping the menus but never the
  game view)), `Links.kt` (share the game, rate it, feedback and the privacy policy: intents
  only, never network), `Settings.kt` (prefs, seeds, the challenge log: first-clear day and best progress per id), `Haptics.kt`.
- `app/src/test/` — JVM tests: `engine/` (mechanics, level generation,
  fuzzing, a heuristic bot that plays full runs per preset), `input/`
  (gestures), `render/` (AWT `Gfx` backend, headless screenshots), `audio/`.
- `app/src/androidTest/` — emulator smoke tests (launch, and 20 s of real
  injected touches).
- `tools/icon/gen_icon.py` — generates the adaptive launcher icon. Edit the
  script, not the XML.
- `tools/store-shots/` — the Play listing's captioned screenshots:
  `render.sh` renders raw game scenes and menus, `caption.py` (Pillow, via
  `uv`) captions them into `fastlane/.../images/`. See `docs/PLAY_STORE.md`.

## Rules

- Keep the pure packages pure: no `android.*` in `engine/`, `input/`,
  `render/` or `audio/` (except `AudioOutput.kt`).
- Every engine rule change gets a test in `MechanicsTest` (or a new test
  class, like `StealthAndEventsTest`). Run the bot (`BotPlaythroughTest`,
  it prints a per-preset balance report and a pacing report) after balance
  changes and keep the difficulty ordering CHILL < AGENT < BRUTAL <
  STRAIGHT_TO_HELL.
- Fun, not compulsion: no streaks, daily rewards, timers or "come back"
  nags, and no rating prompts or "please share" reminders (SHARE, RATE and
  FEEDBACK sit quietly in Settings and HOW TO PLAY; RATE just opens the Play
  page, never the In-App Review API). Text is short, silly and family-friendly.
- The daily challenge is a shared pick, nothing more: the same challenge for
  everyone on the same local day (the date is passed in; no clock in
  `engine/`), a deterministic stand-in if you cleared it on an earlier day.
  Its card may show what you already did (CLEARED TODAY, your best try), but
  no streaks, rewards, countdowns, leaderboards or nags; every challenge is playable any
  day from the board. Never generate an impossible challenge (the catalog
  tests enforce it: no melee, SILENT or silent-takeout goals for MONKEY, no
  shot goals under SILENT ONLY), and recalibrate tiers with
  `ChallengeBotTest` when balance moves.
- Enums the renderer switches on exhaustively (`Perk`, `PickupKind`,
  `TextStyle`, `ParticleKind`, `ContextAction`, `EnemyKind`, states) need a
  render change alongside any new value; prefer fields and events.
- Determinism: a run is a pure function of `RunConfig` and the input
  sequence. Never use wall-clock time or unseeded randomness in `engine/`.
  The guide is text and pointers only (`GuideTest.theGuideNeverChangesTheRun`);
  `coach` and `tutorial` default to off, so tests and screenshots never show
  it unless they ask (the `coach`, `walkthrough-*` and `tip-mode` scenes).
- `android:appCategory="game"` keeps the portrait lock on large screens (API
  36+ ignores it for non-games); `MainActivity` pillarboxes any window squatter
  than `MIN_ASPECT`, so the game view itself is always portrait.
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
  and every hero in every key pose, cropped from real frames at 2x, plus the
  hero picker portraits, for character art; `-Pata.scene=heroes` renders just
  the hero rows and portraits (`heroes.png`).
  `-Pata.scene=icons` renders the icon sheet (`IconScreenshotTest`): every perk
  and pickup icon, the heart and the badges at chip, pill and card sizes.

## Invariants

- **No network, no storage permissions.** Only `VIBRATE`. No backups
  (`allowBackup=false`, empty extraction rules).
  `docs/PRIVACY.md` (the Play privacy policy) and the Data safety answers in
  `docs/PLAY_STORE.md` promise this; change them together.
- The Play listing text lives in `fastlane/metadata/android/en-US/`. Keep it
  true to the game, like `README.md`.
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

Every push to `main` builds a signed APK and a signed Play bundle (`.aab`,
plus the R8 `mapping.txt`) and publishes them as the single date-labeled
GitHub release `vYYYY.MM.DD.<run>`, deleting all older releases. The
versionCode is the run number, so it only goes up. Pull requests build unsigned and publish nothing. Signing uses the
repository secrets `ATA_KEYSTORE_BASE64`, `ATA_STORE_PASSWORD`,
`ATA_KEY_ALIAS` and `ATA_KEY_PASSWORD`; until they exist, pushes to `main`
still build and test but skip the release (with a warning). Never commit a
keystore.
