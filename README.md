# StayOpen

**Understand your phone. Make space for what matters.**

StayOpen is a native Kotlin / Jetpack Compose Android memory and performance utility. It helps you understand memory pressure, review app background restrictions and make safe changes in Android Settings. It works offline, without root, advertising, subscriptions or analytics.

**It cannot reserve RAM, override Android's low-memory killer or guarantee that another app stays alive.** “Protected Apps” is a personal setup plan, not an operating-system protection mechanism.

## Install the real APK

1. Sign in to GitHub and open [Actions → Android APK](https://github.com/HamWebDevs/HamRam/actions/workflows/android.yml).
2. Choose the latest run with a green **Verify and build APKs** job. Open its summary.
3. Under **Artifacts**, download **StayOpen-debug-apk**. GitHub downloads a ZIP; extract it.
4. Open **StayOpen-1.0.0-debug.apk** on your Android phone. If prompted, allow “Install unknown apps” for the file manager or browser you are using, then tap **Install**. You can revoke that install permission afterwards.
5. Open **StayOpen**. Live monitoring works without any runtime permission. Optional usage insights require a separate, clearly explained Android special-access grant.

The debug APK is signed and installable. It uses `dev.hamweb.stayopen.debug` and can coexist with a production release. GitHub artifacts require sign-in and expire after 90 days; rerun the workflow to regenerate them. An unsigned release APK is **not** an installation download.

Debug signing keys are build-machine keys. A later debug APK from a different runner may need the previous debug app uninstalled first, which removes its local profiles. Use your own stable release signing key for reliable long-term updates. See [release instructions](docs/RELEASING.md).

## What is implemented

| Area | Real behaviour |
| --- | --- |
| Dashboard | `ActivityManager.MemoryInfo` total / available RAM, Android low-memory flag and threshold, bounded RAM graph, `StatFs` internal storage, device / Android details |
| Preferred apps | Search launchable apps, real app icons, per-profile selection, Doze-allowlist check, app settings shortcuts, manual setup review |
| Diagnostics | Ranked findings, explicitly labelled estimates / possible explanations, storage and thermal checks, power-saving status, low-memory observations |
| Safe actions | Clear only StayOpen's own cache, system storage tools, app cache instructions, battery settings and RAM Plus guidance |
| Profiles | Gaming, Everyday Use, Maximum Multitasking, Battery Saver and Custom; separate app lists, self-review records, editable names / notes / sampling preferences |
| Samsung assistant | Detects Samsung and known S20+ / A17 / A14 model prefixes, adapts menu guidance to Android and an optional user-selected One UI version |
| Optional usage | Explicit consent followed by Android usage-access settings; aggregated foreground time for visible apps, not memory use |
| Export | User-selected text document through Android's Storage Access Framework, with measurements, methods, recommendations and privacy context |
| Design | Material 3, light / dark / system themes, large controls, responsive navigation rail on wider displays, efficient Canvas charts |

## Compatibility

- Minimum Android 8.0 (API 26); compile / target Android 16 (API 36). Android 13, 15 and newer are supported through public APIs.
- Intended Samsung targets: Galaxy S20+ (`SM-G985*` / `SM-G986*`), A17 (`SM-A175*` / `SM-A176*`) and A14 (`SM-A145*` / `SM-A146*`). Unknown variants retain their actual model string and still receive generic Samsung guidance.
- No native binaries or CPU-specific code; works on practical ARM and x86 Android devices.
- This app does not upgrade a phone's Android version. Hardware and firmware vary by region.
- Exact Samsung sleeping lists, full per-app battery restriction modes and One UI version are not exposed through a stable public Android API. Those statuses are never reported as verified.
- RAM Plus is guidance only. Not every phone offers every option.

## Monitoring and privacy

- A cancellable coroutine runs within `repeatOnLifecycle(STARTED)` and stops when the activity is not visible. There are no services, boot receivers, jobs, alarms, wake locks or background polling.
- Default sampling is every 5 seconds; profiles can use 5, 10, 15 or 30 seconds. At most 360 RAM samples remain in memory. Graph gaps represent unmonitored time.
- At most 100 observed transitions into Android's `lowMemory` state are stored locally. Returning to StayOpen during an existing warning creates a new observation. History is not evidence that a particular app was killed.
- Usage insights are off by default, read only with user opt-in **and** Android permission, and remain in memory. Android aggregates usage in time buckets, so its returned totals may extend beyond the requested 24-hour window.
- Preferences and observations use private app storage. Android backup is disabled. Reports can contain app names, foreground usage and your profile notes; export only to a destination you choose and share deliberately.
- The application has **no INTERNET permission**. No broad installed-app enumeration, accessibility service, storage permission or device administrator access is requested. Launcher visibility is scoped through `<queries>`.
- StayOpen's own managed heap figure excludes native, graphics and shared memory; it is not total app RAM.

## How memory pressure is estimated

The ring and graph show `(totalMem - availMem) / totalMem`. In-use memory includes useful caches. Android's reported physical memory may be less than the advertised RAM because of hardware reservations. Internal storage is the data partition, not the device's advertised flash capacity.

- **High:** Android's `lowMemory` flag is on, or available RAM is at / below its positive reported low-memory threshold.
- **Elevated:** available RAM is below `max(2 × threshold, 15% of total physical RAM)`.
- **Normal:** neither condition is true.

This conservative heuristic is not kernel PSI, a performance benchmark or a diagnosis. A low available-RAM snapshot cannot prove why another app reloaded. Storage warnings use a conservative threshold of 10% or 2 GiB, whichever is larger.

## Build in Android Studio or Gradle

Requirements: JDK 17, Android SDK platform 36, Build Tools 35.0.0. Gradle 8.13, Android Gradle Plugin 8.11.1, Kotlin 2.1.21 and a pinned Compose BOM are configured. The real Gradle Wrapper is included.

Open this directory in Android Studio, let Gradle sync, then run `app` on a phone / emulator. Or:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Windows: use `gradlew.bat`. Set `ANDROID_HOME` or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.

Outputs:

- Installable debug: `app/build/outputs/apk/debug/app-debug.apk`
- Unsigned optimised release: `app/build/outputs/apk/release/app-release-unsigned.apk`

Without production signing environment variables, release builds deliberately remain unsigned; they are never silently signed with a debug key. See [RELEASING.md](docs/RELEASING.md).

## Verification

The Actions workflow builds debug and R8-shrunk release APKs, runs domain / Samsung guidance tests, runs Android lint, compiles device tests, verifies the debug APK signature, and checks the APK does not request network or intrusive management permissions. Separate emulator jobs exercise real system APIs, all five screens, app selection UI, profile editing and persistence on API 33 and 35.

For local device testing:

```sh
./gradlew connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See [TESTING.md](docs/TESTING.md) for physical Samsung checks and honest limits of emulator coverage. Build / test claims must be based on an actual successful run; the presence of this workflow alone is not proof.

## Architecture

`domain/` contains platform-independent models and ranked diagnostic logic. `data/` owns public system APIs, bounded private persistence, settings-intent fallback and report formatting. `StayOpenViewModel` exposes immutable `StateFlow` state, serialises preference writes and coordinates foreground monitoring. `ui/` contains Compose Material 3 screens and lightweight charts; `MainActivity` owns activity lifecycle, file-picker contracts and adaptive navigation.

No database, dependency-injection framework, chart SDK, web view or third-party telemetry is required.

## References

- [Android MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo)
- [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager)
- [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [Samsung sleeping apps](https://www.samsung.com/us/support/answer/ANS10003442/)

Copyright © 2026 HamWebDevs. MIT licence; see [LICENSE](LICENSE).
