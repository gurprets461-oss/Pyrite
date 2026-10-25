# NOTES.md — Pyrite working notes

Resume from here. PLAN.md has the milestones; AGENTS.md has the rules and decisions.

## Status
- Current milestone: **M0 — Skeleton** (in progress)

## Build & test

Fresh machine (Linux/macOS, Java 17+, curl, unzip, python3):

```sh
scripts/setup-sdk.sh            # SDK + NDK + CMake; writes local.properties
scripts/fetch-python.sh         # python.org Android packages -> cross-build/<triplet>/prefix
./gradlew assembleDebug test    # APK at app/build/outputs/apk/debug/app-debug.apk
```

Instrumented tests need an emulator or device:

```sh
scripts/setup-sdk.sh --with-emulator
./gradlew connectedDebugAndroidTest
```

CI (`.github/workflows/ci.yml`) runs build + unit tests, then instrumented tests on an
API 35 x86_64 emulator (GitHub runners have KVM). `fresh-setup.yml` re-checks the M0
acceptance command against an empty SDK directory whenever `scripts/` changes.

## Pinned versions
| What | Version | Where |
|---|---|---|
| Python | 3.15.1 (python.org Android embeddable) | `scripts/fetch-python.sh` (+ sha256) |
| AGP / Gradle | 9.4.1 / 9.8.2 | `gradle/libs.versions.toml`, wrapper |
| Kotlin | 2.4.21 (AGP built-in Kotlin) | `gradle/libs.versions.toml` |
| Compose BOM | 2026.09.00 | `gradle/libs.versions.toml` |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 | `app/build.gradle.kts` |
| NDK | 27.3.13750724 (same as CPython's Android build) | `scripts/setup-sdk.sh` |
| CMake | 3.31.5 | `scripts/setup-sdk.sh` |

## Environment notes (Claude Code cloud sandbox)
- The sandbox's network policy blocks `dl.google.com` (SDK, NDK, Google Maven: AGP,
  AndroidX), `www.python.org`, and `downloads.gradle.org`. So nothing Android builds
  locally there; all building and testing runs in GitHub Actions, and results are read
  back through the GitHub API. No `/dev/kvm` locally either.
- Allowing those three hosts in the environment's network settings would enable local
  builds (instrumented tests still need CI: no KVM).

## Open problems
- (none yet)
