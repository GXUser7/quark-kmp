# quark-kmp

A Kotlin Multiplatform port of [quark](https://github.com/z3nsh0w/quark), the Flutter audio
player by PDG — both of its branches: the bug fixes of `main` and the features of `slop`.
The interface is Compose Multiplatform; one codebase builds an **Android APK** and a
**Windows installer** (and runs on Linux and macOS desktops too).

What it does:

* **Local music** — folders, files, the Android media store; tags and covers, folder watching,
  drag and drop onto the window, gapless playback through libmpv (desktop) and Media3
  (Android).
* **Streaming** — Yandex Music (playlists, likes, My Vibe, chart, new releases, albums,
  artists, search, lyrics, uploads), Spotify, SoundCloud, VK Music and YouTube Music, all
  playable side by side and searchable at once.
* **Library** — your own playlists holding tracks from any source, synced through a quark
  account; export to tagged files; offline caching; listening statistics.
* **Integrations** — Windows media controls (SMTC), the Android media notification, Discord
  Rich Presence, the v0 local control API with mDNS.
* **Interface** — the glass look of the original, light and dark themes, a phone layout, and
  12 languages (Russian complete; the others from the original's translations).

The state of the port, module by module, is in [docs/HANDOFF.md](docs/HANDOFF.md).

## Downloads

Every push is built by GitHub Actions ([.github/workflows/build.yml](.github/workflows/build.yml)).
Open the run on the *Actions* tab; its artifacts are:

| Artifact | Contents |
| --- | --- |
| `quark-android` | `quark-android.apk` (release build) and `quark-android-debug.apk` |
| `quark-windows` | `quark-windows-x64.msi`, `quark-windows-x64-setup.exe` and a portable `quark-windows-x64-portable.zip` |

Pushing a tag that starts with `v` (for example `v0.2.0`) also publishes these files as a
GitHub Release.

Without signing secrets the release APK is signed with the debug key: it installs, but a build
signed differently later cannot update it. To sign with your own key, add these repository
secrets: `QUARK_KEYSTORE_BASE64` (the keystore, base64-encoded), `QUARK_KEYSTORE_PASSWORD`,
`QUARK_KEY_ALIAS`, `QUARK_KEY_PASSWORD`.

## Modules

| Module | What is in it |
| --- | --- |
| `core` | Domain model and pure logic: tracks, playlists, the queue and shuffle, settings, LRC, listening analytics. No IO. |
| `data` | SQLDelight schema and repositories, tag reading and writing, the library scanners, caches, file access. |
| `network` | Ktor clients: Yandex Music, the quark backend (account, sync, YouTube, VK), Spotify, SoundCloud; the local API server. |
| `player` | The audio engine contract, `PlayerController`, and the engines: libmpv over JNA, Media3 on Android. |
| `platform` | Application directories per OS, Discord IPC. |
| `services` | The application graph without UI: sessions, catalogs, search, sync, export, stats. Builds and tests on a plain JVM. |
| `app` | The Compose interface for both platforms, and the desktop application. |
| `androidApp` | The Android application: activity, media session service, manifest. |

Everything that can be is `commonMain`; `jvmShared` holds the java.io/nio code the desktop and
Android share, and each platform source set only what really differs.

## Building

JDK 21 and, for Android, the Android SDK.

```bash
./gradlew :app:run                       # the desktop player
./gradlew jvmTest                        # every test, including a render of each screen
./gradlew :androidApp:assembleDebug      # the APK
./gradlew :app:packageMsi :app:packageExe  # Windows installers (on Windows)
```

On Windows the first build downloads libmpv through `:app:fetchMpv`; elsewhere the system's
`libmpv` is used (`apt install libmpv2`, `brew install mpv`).

## Database compatibility

The SQLDelight schema matches the Drift schema of the Flutter build column for column, so an
existing `quark.db` opens without a migration step.

## Licence

The original project is MIT, by [@z3nsh0w](https://github.com/z3nsh0w) and
[@aror](https://github.com/Aror1). This port keeps that licence.
