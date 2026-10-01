# Security

'Bout That Action is distributed as a signed APK from this repository's
GitHub Releases and on Google Play. The Play build is the
`bout-that-action.aab` from the same CI run.

## Reporting a vulnerability

Please report vulnerabilities privately via
[GitHub security advisories](https://github.com/bradflaugher/bout-that-action/security/advisories/new)
rather than opening a public issue.

## Verifying a release

Each release APK is built by GitHub Actions from the `main` branch and
published alongside a `bout-that-action.apk.sha256` checksum. Verify a
download with:

```sh
sha256sum -c bout-that-action.apk.sha256
```

Each APK and bundle also carries a signed build provenance attestation,
which proves it was built by this repository's CI from a specific commit:

```sh
gh attestation verify bout-that-action.apk --repo bradflaugher/bout-that-action
```

## Design notes

- The only permission is `VIBRATE`. There is no `INTERNET` permission, so
  nothing is sent off the device; there are no ads, analytics or accounts.
- Sharing a run's seed goes through the system share sheet, which needs no
  permission; the app hands over the text and makes no network request.
- Cloud backup is off (`android:allowBackup="false"`). Android 12+
  device-to-device transfer ignores that flag, so a phone-to-phone move can
  carry the local save (best scores, challenge clears and settings).
- The only exported component is the launcher activity.
- CI actions are pinned to commit SHAs, the Gradle distribution is checksum
  pinned, signing secrets only reach builds of `main`, Dependabot keeps
  dependencies and action pins current, and CodeQL scans the Kotlin
  sources, the workflows and the CI scripts.
