# StayOpen 1.2.0 · Privileged RAM edition

Real permission-based RAM clearing is now available through Shizuku. It includes a downloaded-app allowlist, exclusions from every Protected Apps profile, foreground / service / shared-UID guards, preview scans, observed process exits, before / after system readings, and optional background automation with a persistent Stop notification. Automation is off by default and does not restart after reboot.

See [setup, limitations and exact policy](RAM-CLEARING.md). The same production package and private signing key allow a direct update from production 1.1.0. The previous terminal features below are retained; their own-cache housekeeping is separate from this new privileged process-clearing mode.

---

# StayOpen 1.1.0 · Terminal edition

## Console interface

Near-black panels, crisp neon borders, a static grid, monospace telemetry, a terminal launcher icon and green / cyan / amber accents. Light and system themes remain available. There is no animated rain, continuous glow loop, web view or downloaded font.

The app selector is now near the top of App Guard. Preferred apps can be launched directly as well as reviewed in Settings.

## Automatic care

Auto care is enabled by default for this requested upgrade and can be switched off in Optimise or Settings. It uses the existing foreground monitoring loop; it adds no worker, alarm, service, notification or background timer.

- Clear **only StayOpen's own cache directory**.
- Evict reusable icon-cache references without recycling bitmaps that visible UI may still be using.
- Back off StayOpen's configured sampling to at least 10 seconds during estimated high memory pressure.
- Run initially when no recent run exists, after 15 minutes of elapsed time while visible, or on a new high-pressure edge after a two-minute cooldown.
- Keep at most 20 real run records with disk bytes deleted, icons evicted, timestamps and observed before / after available RAM.

This is housekeeping for StayOpen. It cannot clear another app's RAM, control Android's low-memory killer or guarantee another app survives. Before / after RAM changes include unrelated system activity and are never counted as RAM freed by care. Zero bytes cleared is a valid result.

Pausing monitoring stops automatic care too. Manual care can still be requested while the activity is visible. Backgrounding stops the automatic loop.

## New diagnostics and exports

- Actual battery level, battery-sensor temperature, charging state and elapsed time since boot. Unknown values remain unknown; battery temperature is not CPU temperature.
- A readable `Settings.Global.ALWAYS_FINISH_ACTIVITIES` value flags Don't keep activities when Android exposes it. The app cannot change it or verify the full background-process limit.
- Current Android low-memory threshold on the graph, and minimum / average / maximum available RAM from captured samples.
- Session baselines compare actual RAM and storage readings across normal phone use.
- RAM CSV export uses UTC timestamps and raw byte values.
- A chronological event console combines low-memory observations and care runs.
- Diagnostic reports include telemetry, care history and any saved session baseline.

## Portable setup

Export all profiles, app selections, self-reviewed flags, notes, appearance and care preferences to a JSON file chosen through Android's file picker. Import checks the format, schema, nesting depth, app identifiers, profile limits and a 200 KB size limit, then asks before replacing the current setup. Usage insights are off after import; backups cannot grant Android permission or change Samsung settings.

The five original profiles and private preference namespace are retained. New installs default to the dark console; legacy System appearance migrates to dark for this visual redesign, while an explicitly saved Light or Dark preference is retained. Android backup remains excluded; setup export is a deliberate user action.

## Installation identities

Version 1.0.0 was distributed as a debug build (`dev.hamweb.stayopen.debug`). A production-signed release uses `dev.hamweb.stayopen`, so it installs separately and does not overwrite or remove the original test build. Keep a stable **private** production signing key for subsequent production updates. Never put it in this repository.

GitHub Actions continues to publish installable debug APKs and unsigned release output, plus tests and screenshots. Debug certificates are for testing and can differ between runners. The release device smoke check uses a runner-local test signature solely to exercise the actual R8-shrunk release code.
