# Publishing to Google Play

The game ships two ways from the same signed build: the sideload APK on the
GitHub release, and a Google Play listing. Play gets the same app with the
same platform policy (`AGENTS.md`): it targets the latest Android and installs
on Android 12 (API 31) and up.

## What CI hands you

Every signed push to `main` attaches these to the GitHub release:

| File | For |
|---|---|
| `bout-that-action.apk` | Sideloading |
| `bout-that-action.aab` | Play upload (Play only takes bundles) |
| `mapping.txt` | Play Console → the release → App bundle explorer → Deobfuscation file, so crash reports are readable |

The `versionCode` is the workflow run number, so each release goes up by
one and is always a valid next upload.

## One-time setup

1. **Signing.** In Play Console, turn on Play App Signing and choose to use
   **your own key** (upload the existing release key). Then Play installs and
   sideloaded APKs share a signature and update over each other. If you let
   Google generate the key instead, the two can't update over each other:
   people would have to uninstall to switch.
2. **Store listing.** The text is in `fastlane/metadata/android/en-US/`:
   `title.txt` (≤ 30 chars), `short_description.txt` (≤ 80) and
   `full_description.txt` (≤ 4000). Paste it in, or upload it with fastlane
   `supply`.
3. **Graphics.** Everything is in `fastlane/metadata/android/en-US/images/`:
   `icon.png` (512×512, same as `docs/screenshots/icon.png`),
   `featureGraphic.png` (1024×500, the hero lineup under the title), eight
   captioned 1080×1920 `phoneScreenshots` and the same eight rendered at
   tablet size in `sevenInchScreenshots` (1200×1920) and `tenInchScreenshots`
   (1600×2560), so the listing shows the game on large screens. Re-render
   them with `tools/store-shots/render.sh` (below); the feature graphic is
   `./gradlew :app:screenshots -x menuShots -Pata.scene=feature -Pata.shots=fastlane/metadata/android/en-US/images`.
4. **What's new.** `changelogs/default.txt` is the release note `supply`
   uses for every versionCode (≤ 500 chars). Keep it short and silly.
5. **Privacy policy.** The listing links
   <https://bradflaugher.com/privacy/bout-that-action/>, which says the same
   as `docs/PRIVACY.md`. Change both together.
6. **Testing tracks.** Upload the first `.aab` by hand (Play only accepts API
   uploads after that) to **Internal testing** and install it from Play on a
   phone and a tablet. A personal developer account made after November 2023
   must then run a **Closed test with at least 12 opted-in testers for 14
   days in a row** before it can apply for production access; an
   organization account can go straight to production.
7. **Ratings.** RATE (next to SHARE and FEEDBACK in Settings and HOW TO
   PLAY) opens `market://details?id=com.bradflaugher.aboutthataction`, or the
   listing's web page when there's no Play Store app. It's only ever a tap:
   no In-App Review API, no rating prompts or reminders.
   No leaderboards either: the game has no network permission, so daily
   challenge results stay on the device.
8. **Production access.** The closed-test questionnaire answers are drafted
   in `docs/tester-feedback/production-access-answers.md`, with the tester
   report itself and what came of each suggestion in
   `docs/tester-feedback/README.md`.
9. **Pre-launch report.** Play runs every upload on a handful of real devices
   and flags crashes, accessibility and layout issues. Check it before each
   promotion to production.

## App content answers

| Question | Answer |
|---|---|
| Ads | No ads |
| In-app purchases | None |
| App access | Everything is available without an account |
| Data safety | Collects no data, shares no data (no network permission at all). Encryption in transit and deletion requests: not applicable, nothing is collected |
| Target audience | 13+ (keeps the game out of the Designed for Families program) |
| Content rating (IARC) | Cartoon violence: stylized shooting, grenades and takedowns of guards, robots and demons. Fantasy horror themes (demons, Hell) with no gore. No gambling: PAYDAY floors pay score only, and nothing is bought |
| Category | Game → Action (the manifest also says `appCategory="game"`) |
| Device support | Phones, tablets, foldables and ChromeOS with a touchscreen, Android 12+. Portrait only; games keep their portrait lock on large screens, and a window that's too wide plays in a centred column |
| Government, news, health, finance | None |

## Before you submit: names and likenesses

Play review rejects apps that suggest a real person, team or brand endorses
them. The game leans on a few:

- The title, 'Bout That Action, is a common phrase. Nothing else in the game
  quotes or names a real athlete: BULL is an original heavyweight in a
  quilted bomber and shades, with no number, team colours, logo or name, and
  the sign-offs and perk names are all original. Keep it that way.
- FOX (a ponytailed martial-arts brawler in baggy fighting pants), MONKEY (a small runaway
  circus monkey with a big, generic rifle) and HAWK (a parcel courier with no company name
  or logo) are original characters on genre archetypes, drawn to look like nobody else's: no film or game quotes,
  names, catchphrases or signature costumes.
- No third-party trademarks in game text either: armor is a VEST (not
  Kevlar), the slow-motion pickup is SLOW-MO (not bullet time).
- Wall text (murals, graffiti, neon, departure boards in `EnvWalls`) is
  all original: no brands, slogans or trademarks. Store screenshots count as
  listing metadata, so keep it that way.

Neither the Play listing, the README nor the game names another game or
brand, and Play's metadata policy forbids other people's trademarks in the
title and descriptions, so keep it that way.

The parodies are gentle and there are no real names or logos. If review
pushes back, the title is the thing left to look at.

## Refreshing the store screenshots

```sh
tools/store-shots/render.sh            # render, then caption
tools/store-shots/render.sh --caption  # re-caption the raw shots only
```

Each listing screenshot shows one thing the game does, with a headline and a
subline over it in the menus' own look (Audiowide and Chakra Petch on the
menu near-black, a chamfered neon frame in the shot's accent colour):

| # | Shot | Headline |
|---|---|---|
| 1 | Title with the daily | DROP IN. GO DOWN. |
| 2 | Neon Tower takedown | SNEAK UP. TAKE DOWN. |
| 3 | The box, a guard wondering | HIDE IN THE BOX |
| 4 | The walkthrough's lift step | LEARN IT ON THE ROOF |
| 5 | Black Labs firefight | GUNS HOT OR SILENT |
| 6 | Hero picker (MONKEY) | FOUR HEROES, ALL FREE |
| 7 | Hell | ALL THE WAY TO HELL |
| 8 | The challenges board | 1,234 CHALLENGES |

`render.sh` renders the real game scenes (`:app:screenshots` at
1080×1920, 1200×1920 and 1600×2560) and the real Compose menus
(`:app:menuShots -Pphone=<qualifiers>`, which renders the "phone" menus at a
9:16 size into `build/store-raw/` and leaves `docs/screenshots/` alone), then
`tools/store-shots/caption.py` (Python + Pillow, run through `uv`) captions
them into the three folders. The captions and the scene for each slot are the
`SHOTS` list at the top of `caption.py`. Look at every image before you upload
it.
