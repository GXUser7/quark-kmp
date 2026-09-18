# quark-kmp

A Kotlin Multiplatform port of [quark](https://github.com/z3nsh0w/quark), the Flutter desktop
audio player by PDG. The UI is Compose Multiplatform; the target is the desktop JVM on Windows,
Linux and macOS.

This is a port in progress, not a replacement yet. What runs today is the playback core —
the domain model, the queue and the state machine above it. There is no audio backend
wired in, so nothing comes out of the speakers.

## Modules

| Module      | What is in it                                                                 |
| ----------- | ----------------------------------------------------------------------------- |
| `core`      | Domain model and pure playback logic: tracks, playlists, queue, shuffle. No IO. |
| `data`      | SQLDelight schema and repositories; the library database.                       |
| `network`   | Ktor clients: Yandex Music, YouTube Music, MusicBrainz.                         |
| `player`    | The audio engine contract and the controller that drives it.                    |
| `platform`  | OS integration: application directories, media keys, window chrome.             |
| `app`       | The Compose Multiplatform application.                                          |

`core`, `data`, `network` and `player` keep their code in `commonMain` and touch the platform
only through `expect`/`actual`, so an Android or iOS target can be added without moving code.
Only `platform` and `app` are JVM-shaped by design.

## Building

Needs JDK 21. On this machine the shared toolchain provides it:

```bash
source /e/Projects/.tools/env.sh
```

Then:

```bash
./gradlew build
```

To run the app:

```bash
./gradlew :app:run
```

## Database compatibility

The SQLDelight schema in `data/src/commonMain/sqldelight` matches the Drift schema of the Flutter
build column for column, so an existing `quark.db` opens without a migration step. `AppDirs` also
looks in the directories the Flutter build used, which differ per OS because `path_provider`
derived them from platform metadata rather than a constant.

## Licence

The original project is MIT, by [@z3nsh0w](https://github.com/z3nsh0w) and
[@aror](https://github.com/Aror1). This port keeps that licence.
