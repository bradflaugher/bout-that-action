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
   1080×1920 `phoneScreenshots` and the same eight scenes rendered at tablet
   size in `sevenInchScreenshots` (1200×1920) and `tenInchScreenshots`
   (1600×2560), so the listing shows the game on large screens. Re-render
   them with the commands below.
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
7. **Pre-launch report.** Play runs every upload on a handful of real devices
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

- The title is Marshawn Lynch's catchphrase, and BEAST is a running back in
  Seattle green and navy wearing #24 (Lynch's number and "Beast Mode"
  nickname).
- ACE's joke riffs on James Bond, HARDY's on *Die Hard* and VIPER on
  *Metal Gear Solid*.

- The murals in the halls say BEAST MODE (Lynch's own trademark) and
  SKITTLES (Mars's), and store screenshots count as listing metadata.

The Play listing text itself names no other game or brand: the README keeps
the Elevator Action and Metal Gear Solid homage, but Play's metadata policy
forbids other people's trademarks in the title and descriptions, so keep them
out of `fastlane/`.

The parodies are gentle and there are no real names or logos, but the
BEAST's colours and number, the BEAST MODE murals and the title are the
riskiest part. Change them if review pushes back.

## Refreshing the store screenshots

```sh
./gradlew :app:screenshots -Pata.full=true -Pata.size=1080x1920 \
  -Pata.shots=fastlane/metadata/android/en-US/images/phoneScreenshots
```

That renders every game scene; keep the good ones (the listing takes 2–8).
For the tablet slots, render the same scenes at tablet size and keep the
same eight (`-x menuShots` skips the Compose menus):

```sh
./gradlew :app:screenshots -x menuShots -Pata.full=true -Pata.size=1200x1920 -Pata.shots=<dir>
./gradlew :app:screenshots -x menuShots -Pata.full=true -Pata.size=1600x2560 -Pata.shots=<dir>
```
