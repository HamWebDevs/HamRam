# Test coverage and physical-device checklist

## Automated checks

- Domain tests cover memory pressure boundaries, unknown / zero thresholds, ranked storage / thermal recommendations, evidence classification, default profiles and active-profile fallback.
- Samsung guidance tests cover S20+, A17, A14 identifiers, unknown models and explicit One UI overrides.
- Instrumentation tests open all tabs, profile editor and app picker, read actual Android memory / storage APIs, and verify profile / warning-history persistence across repository recreation.
- APK verification checks signatures, application ID, minimum / target SDK and manifest permissions.
- R8 release compilation verifies that shrinking does not break the release build.

## Physical S20+, A17 and A14 validation

1. Install the verified APK and compare model / Android version with Settings. Compare RAM with Android's reported usable physical RAM, allowing hardware reservations.
2. Confirm a real graph develops; pause it, resume, send StayOpen to the background and return. Background gaps must be visible and readings must not be invented.
3. Select two apps, change profiles, relaunch StayOpen and confirm each list / notes / sampling interval is retained independently.
4. Open each app's settings from StayOpen. Check actual Doze exemptions against Android's Battery optimisation list. The app must never claim to verify Samsung's sleep lists or full per-app battery mode.
5. In Samsung Settings, confirm the version-appropriate Background usage limits and RAM Plus paths. Override the One UI selection if firmware uses different labels. Record firmware / carrier variations.
6. Enable optional usage insights, decline special access, return, grant access, return and revoke it. No crash or blocking prompt should occur; disable insights and confirm usage rows disappear.
7. Export a report to local Downloads, inspect actual values, notes and included privacy context, then cancel another export.
8. Clear StayOpen's temporary files. Confirm profiles, saved observations, downloads and exported reports are unaffected.
9. Test dark / light themes, landscape, gesture / three-button navigation, a large font setting and TalkBack. Confirm all actions can be reached by scrolling.
10. After enough normal use, inspect battery usage and Android process records. With Auto clear off, no service or notification should remain after backgrounding. With it explicitly enabled, the Stop notification should remain and end the service when used. No alarm or wake lock is used.

Android emulators do not emulate Samsung's proprietary Settings app. An emulator pass is not evidence of verified physical Samsung menu paths or a measured reduction in app closures.

## Shizuku / RAM checks

CI pins official Shizuku 13.6.0 and starts it as **shell UID 2000**, not root. The end-to-end device test grants StayOpen through the actual Shizuku permission dialog, excludes a foreground fixture, preserves the cached fixture when protected in a profile, then observes its PID exit after unprotecting and clearing. It starts the notification-backed automatic service, backgrounds StayOpen, and verifies explicit stop. A debug-only shell entry also exercises the exact privileged implementation against an independently installed disposable test app. These run on Android 13 / 15 emulators; no personal apps or files are targets.

On each Samsung phone, follow [RAM-CLEARING.md](RAM-CLEARING.md), verify the permission and all exclusions, try Stop from the notification, revoke Shizuku access and reboot. Record cases where firmware restricts shell process access. Test service survival and battery impact during normal use without assuming an emulator reproduces Samsung's background policies.
