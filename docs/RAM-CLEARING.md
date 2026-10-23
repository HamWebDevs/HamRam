# Real RAM clearing with Shizuku (1.2.0)

StayOpen can now request termination of **selected cached background app processes**, using an explicitly authorised Shizuku worker running as Android's `shell` user. Root is not required. Android 14+ does not let an ordinary third-party app kill other apps through `killBackgroundProcesses`; adding a normal permission, changing the target SDK or keeping distribution private does not remove that limit.

## Setup on your Samsung phones

1. Install the signed StayOpen 1.2.0 APK. A production-signed 1.1.0 installation updates in place with the same private signing key. The earlier debug app has a separate package and can coexist.
2. Install [Shizuku from its official download page](https://shizuku.rikka.app/download/).
3. Enable Developer options: Settings → About phone → Software information → tap Build number seven times. Android may ask for your lock-screen PIN.
4. On Android 11+, connect to Wi-Fi, enable Wireless debugging in Developer options, then follow Shizuku's **Start via wireless debugging → Pairing** instructions. Enter the pairing code as its guide explains, then start Shizuku. If wireless debugging is unavailable on your firmware, follow its computer / USB method instead. See the [official setup guide](https://shizuku.rikka.app/guide/setup/).
5. Open StayOpen → Optimise → **Grant Shizuku access** and approve its permission dialog. Recheck access if needed. Previously denied permission can be changed in Shizuku → Authorised applications.
6. Put messaging, music, navigation, work and other essentials in Protected Apps. **Every profile's app list is excluded**, even when that profile is inactive.
7. In Optimise → **Choose apps to clear**, select downloaded apps whose cached processes may be discarded. There is a shortcut to select all unprotected user apps. No app is selected by default.
8. Try **Scan cached apps**, then **Clear cached RAM now**. A zero result is valid if none of the selected apps is currently eligible.
9. For automatic operation, choose the pressure threshold and check interval, enable **Auto clear RAM**, and allow the notification. Its **Stop** action ends the monitor. Start Shizuku and Auto clear again after a reboot.

Samsung battery management can stop Shizuku or StayOpen. Review battery / sleeping settings for both if the optional monitor stops. Exact settings vary by One UI / firmware. StayOpen does not disable Samsung security controls or change protected settings automatically.

## What happens

- The Shizuku permission is separate from usage access and Android's notification permission. No usage access is needed for RAM clearing.
- Only explicitly selected, downloaded apps in the same Android user can be candidates. System and updated-system apps, StayOpen, Shizuku, the default launcher, keyboard, dialer and every protected profile app are excluded.
- All processes and packages sharing a UID are considered together. One active or excluded member blocks the UID. Candidate processes must have Android importance `IMPORTANCE_CACHED` or lower priority; a foreground app or foreground service is skipped.
- Eligibility is rechecked before each request. The worker calls the same `IActivityManager.killBackgroundProcesses(package, userId)` operation used by Android's `am kill` shell command. It never calls `force-stop`, `kill-all`, Linux `kill`, `drop_caches` or an arbitrary command supplied by a user / file.
- Up to 20 apps are processed per run. The worker observes process IDs afterwards (up to one second) and reports the number observed exited. Processes can restart, and some requests legitimately terminate nothing.
- Results include real system available-RAM readings before / after. Concurrent activity affects those readings, so they are **not labelled RAM freed by StayOpen**. Twenty runs are retained privately and included in diagnostic exports.

Clearing a cached app may discard unsaved in-memory work and cause a reload. It does not delete its private files or mark it force-stopped. Clearing repeatedly can increase app reloads and battery use. Neither Shizuku nor StayOpen can guarantee another app stays alive or override Android's low-memory killer.

## Automatic mode and resource use

Automatic RAM clearing is **off by default**, with no boot receiver or restart job. Starting it is a visible user action and requires a usable Stop notification. A `specialUse` foreground service is declared for this user-requested memory utility; this sideloaded utility makes no claim about Play Store review eligibility.

Checks use only `ActivityManager.getMemoryInfo` at 30, 60 (default) or 120 seconds, while the screen is interactive. Two consecutive pressure readings are required. Pressure means Android's low-memory flag or available RAM at / below the higher of Android's threshold and the selected 10%, 15% (default) or 20% threshold. Automatic attempts have a five-minute cooldown, also reset by manual clearing. Zero-candidate attempts count for cooldown too.

Screen-off time is skipped without a wake lock, alarm or network activity. A coroutine delay does not wake the sleeping device. Shizuku must remain available. Leaving StayOpen keeps this explicitly enabled service running; Stop, loss of Shizuku access, Android stopping the service or reboot ends the session. There is no guarantee of continuous execution on Samsung firmware.

The privileged worker is non-daemon and is released when the app becomes hidden with automation off, or when the automatic session ends while hidden. It never runs a private daemon indefinitely. The existing own-cache **Automatic housekeeping** feature remains separate and operates only while the dashboard's visible monitoring is active.

## Permissions and privacy

The app adds Shizuku's client permission, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS`. It still has no internet permission, accessibility service, root requirement, broad package visibility, file-management permission, boot permission or wake lock. Optional usage insights are unchanged. Shizuku is a separate app; its wireless setup uses the local network.

Non-SDK Android interfaces are used only inside the privileged worker, as recommended for Shizuku UserServices. OEM changes or future Android versions can still break or restrict them; failures are displayed instead of claiming successful clearing. Emulator checks do not establish physical Samsung compatibility.
