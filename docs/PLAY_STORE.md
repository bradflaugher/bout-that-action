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
3. **Graphics.** App icon: `docs/screenshots/icon.png` (512×512). Phone
   screenshots aren't committed yet: render them at 1080×1920 (9:16, which
   Play wants) with the command below. Play also
   wants a 1024×500 feature graphic, which isn't made yet.
4. **Privacy policy.** Link
   <https://github.com/bradflaugher/bout-that-action/blob/main/docs/PRIVACY.md>.
   The repository has to be public for that link to work.

## App content answers

| Question | Answer |
|---|---|
| Ads | No ads |
| In-app purchases | None |
| App access | Everything is available without an account |
| Data safety | Collects no data, shares no data (no network permission at all) |
| Target audience | 13+ (keeps the game out of the Designed for Families program) |
| Content rating (IARC) | Cartoon violence: stylized shooting, grenades and takedowns of guards, robots and demons |
| Government, news, health, finance | None |

## Before you submit: names and likenesses

Play review rejects apps that suggest a real person, team or brand endorses
them. The game leans on a few:

- The title is Marshawn Lynch's catchphrase, and BEAST is a running back in
  Seattle green and navy wearing #24 (Lynch's number and "Beast Mode"
  nickname).
- ACE's joke riffs on James Bond, HARDY's on *Die Hard* and VIPER on
  *Metal Gear Solid*.

The parodies are gentle and there are no real names or logos, but the
BEAST's colours and number plus the title are the riskiest part. Change them
if review pushes back.

## Refreshing the store screenshots

```sh
./gradlew :app:screenshots -Pata.full=true -Pata.size=1080x1920 \
  -Pata.shots=fastlane/metadata/android/en-US/images/phoneScreenshots
```

That renders every game scene; keep the good ones (the listing takes 2–8).
