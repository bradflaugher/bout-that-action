# Maintaining 'Bout That Action

How to keep this repository current and its supply chain locked down.
Nothing here needs special tools beyond `gh` and the Android SDK.

## The quarterly routine

Dependabot follows one policy: patch vulnerabilities fast, refresh everything
else rarely.

- **Security updates** open a PR as soon as an advisory affects the app, any
  day of the year. Treat those first.
- **Version updates** arrive as one combined PR per quarter: Gradle plugins
  and libraries, the Gradle wrapper, fastlane and the Actions pins
  together. Only releases at least 30 days old are proposed, so a broken or
  hijacked release has usually been pulled first. Majors come in the same PR;
  read their release notes before merging. Actions are pinned to commit SHAs,
  and Dependabot moves the SHA and the `# vX.Y.Z` comment together.

Merge a PR (squash) once its checks are green. The `Main` ruleset requires
`build`, `Unit tests`, `Android lint`, `Analyze (java-kotlin)`, `Analyze (actions)`, `Analyze (python)` to pass, so a red PR can't be merged by accident.

## Toolchain bumps by hand

Dependabot covers the Gradle plugins, the libraries and the Gradle wrapper.
For anything else:

- **Gradle wrapper** (if Dependabot hasn't): take the checksum from
  <https://gradle.org/release-checksums/> and run
  `./gradlew wrapper --gradle-version <v> --gradle-distribution-sha256-sum <sha>`.
  Keep `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`.
- **New Android API level**: change `compileSdk` and `targetSdk` in
  `app/build.gradle.kts`, and the `platforms;android-NN.0 build-tools;NN.0.0`
  packages in `ci.yml`, `codeql.yml`, `emulator-smoke.yml`, `build-release.yml` under `.github/workflows/`.
- **JDK**: keep the `java-version` in every workflow in step with the
  Java/Kotlin target in `app/build.gradle.kts`.
- **A new GitHub Action**: pin it to a full commit SHA with a version comment.
  The repository only runs GitHub's own actions plus the ones on its allow
  list (see below); add the new one there first.

## Releasing

Everything ships from GitHub: merging to `main` is the release.

### Shipping an update

1. Make the change on a branch and open a pull request to `main`.
2. In the same pull request, update the release notes in
   `fastlane/metadata/android/en-US/changelogs/default.txt` (at most 500
   characters). Testers, and later Play users, see this text. A
   `changelogs/<versionCode>.txt` overrides it for one build.
3. CI runs the build, tests, lint, CodeQL and the `store listing` check, and
   Codex reviews the pull request. Squash-merge once it is green.
4. The push to `main` builds and signs the APK and Play bundle, replaces the
   GitHub release (the sideload APK), uploads the bundle to Google Play's
   **Closed testing - Alpha** track with the release notes, and syncs the
   store listing. The version code is the workflow run number, so it is never
   bumped by hand.
5. Play reviews the update by itself (managed publishing is off); follow it in
   Play Console › the app › **Publishing overview**.

### Changing the store listing

The listing lives in `fastlane/metadata/android/en-US/`: `title.txt` (at
most 30 characters), `short_description.txt` (80), `full_description.txt`
(4000) and `images/` (icon, feature graphic, screenshots). For the images,
re-render them with `tools/store-shots/render.sh` (`--caption` to redo only the captions; see `docs/PLAY_STORE.md`), then commit them. Store screenshots are promo shots: a headline and
subline outside the framed capture, never bare captures. Only images that
changed are uploaded (Play's copies are compared by checksum), and each change
goes through Play's review.

### Changing the website

The website, <https://bradflaugher.github.io/bout-that-action/>, is the
static files in `site/`: the landing page, the privacy policy
(`site/privacy/index.html`, which the app's PRIVACY POLICY button and Play
both link) and a 404 page. Edit them and preview with
`python3 -m http.server -d site`. A pull request that touches `site/` runs
the `Site check` (`.github/scripts/check_site.py`: every local link
resolves, nothing loads from another origin, and every stylesheet link
carries the stylesheet's content hash); the push to `main` deploys it
with `.github/workflows/pages.yml`. After editing `site/assets/site.css`,
run `python3 .github/scripts/check_site.py site --fix` to stamp the new
hash into the pages (`site.css?v=…`), so no browser pairs a new page with
a stale cached stylesheet. Images in `site/assets/img/` are
downscaled WebP copies of the store and README screenshots; re-export them
when those change. If the privacy policy changes in substance, bump its
"Last updated" date and check the Data safety answers in
`docs/PLAY_STORE.md`.

### Going to production

Production needs Play's production access first: for a personal developer
account, 12 testers opted in to the closed test for 14 days, then apply in
Play Console. After that, run **Actions › Promote to production**
(`play-promote.yml`):

- action `promote`, rollout `1`: the completed release on Closed testing -
  Alpha goes to everyone in production, as is, release notes included. A
  rollout below 1 (say `0.2`) starts a staged rollout instead.
- action `update rollout`: widens a staged rollout (a larger fraction) or
  finishes it (`1`).

Both check the release's status first: `promote` only takes a completed alpha
release and `update rollout` only an in-progress production rollout, so a
draft is never published. Before production access is granted, Play refuses
and the run fails.

### When something fails

- **`Google Play` job fails:** nothing reached Play. Its log says why (often a
  listing rule); fix it, or rerun the job from the Actions page.
- **`store listing` fails on a pull request:** the error names the file and
  the Play rule it breaks. Run the check locally with
  `python3 .github/scripts/check_store_listing.py` (needs `python3-pil` or
  `pip install pillow`).
- **Never rename `.github/workflows/build-release.yml`:** the run number, and with
  it the version code, would restart, and Play rejects any version code that
  isn't higher than the last.

### How it works

The `Google Play` job in `.github/workflows/build-release.yml` runs fastlane `supply`
(pinned in `Gemfile.lock`, on the latest stable Ruby) after `build`. It
uploads the bundle and `mapping.txt`, then syncs the listing text, icon,
feature graphic and screenshots from `fastlane/metadata/android`. It signs in
as the Google Cloud service account
`github-play-publisher@play-publishing-511400.iam.gserviceaccount.com`, whose
JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the `google-play`
environment (which only `main` can deploy to). `play-promote.yml` uses the
same environment, and `.github/scripts/play_release.rb` does its status
checks. The `release` job attests the APK's and bundle's build provenance and
replaces the single GitHub release.

## Security settings that live outside the repository

These are GitHub settings, not files, so they are listed here to check or
recreate them:

| Setting | Value |
|---|---|
| Ruleset `Main` (default branch) | No deletion or force push, linear history, changes through a squash-merged PR, required checks: `build`, `Unit tests`, `Android lint`, `Analyze (java-kotlin)`, `Analyze (actions)`, `Analyze (python)`, `store listing` |
| Merge buttons | Squash only, PR title as the commit title, branches deleted after merge |
| Actions permissions | GitHub-owned actions plus `android-actions/setup-android`, `gradle/actions/*`, `reactivecircus/android-emulator-runner` and `ruby/setup-ruby`; full-SHA pinning required |
| GitHub Pages | Source: GitHub Actions (`pages.yml` deploys `site/` through the `github-pages` environment, from `main`) |
| Google Play API access | Service account `github-play-publisher@play-publishing-511400.iam.gserviceaccount.com` (Google Cloud project `play-publishing-511400`), invited in Play Console › Users and permissions with account-wide: view app information, release to production, release to testing tracks, manage store presence. Its JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the `google-play` environment |
| Environment `google-play` | Deployable from `main` only; holds `PLAY_SERVICE_ACCOUNT_JSON`; the `Google Play` job and `play-promote.yml` run in it |
| Default `GITHUB_TOKEN` | Read-only; jobs ask for more themselves |
| Fork PR workflows | Approval required for every outside contributor |
| Dependabot | Alerts and security updates on; version updates from `.github/dependabot.yml` |
| Dependency graph | Gradle dependencies submitted by `.github/workflows/dependency-graph.yml` on every push to `main` (GitHub can't read Gradle builds itself; without it there are no alerts for the Android dependencies) |
| Code scanning | CodeQL from `.github/workflows/codeql.yml` (not default setup: Kotlin needs a traced build) |
| Secret scanning | On, with push protection |
| Private vulnerability reporting | On (see `SECURITY.md`) |

Possible next step: move the four signing secrets from repository secrets
into a `release` environment that only `main` can deploy to, and add
`environment: release` to the build job. The workflow already keeps them
away from PRs and other branches; the environment would enforce that in
GitHub itself. Secrets can't be read back, so this means pasting them in
again.
