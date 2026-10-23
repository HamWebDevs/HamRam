#!/usr/bin/env bash
set -euo pipefail
# A real, pinned upstream Shizuku release, only installed on the disposable emulator.
stayopen_shizuku_dir="$(mktemp -d)"
curl --fail --location --retry 3 \
  https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk \
  --output "$stayopen_shizuku_dir/shizuku.apk"
printf '6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f  %s\n' "$stayopen_shizuku_dir/shizuku.apk" | sha256sum --check
adb install -r "$stayopen_shizuku_dir/shizuku.apk"
python3 - "$stayopen_shizuku_dir" <<'PY'
import sys, zipfile
from pathlib import Path
root = Path(sys.argv[1])
with zipfile.ZipFile(root / 'shizuku.apk') as archive:
    (root / 'shizuku_starter').write_bytes(archive.read('lib/x86_64/libshizuku.so'))
PY
adb push "$stayopen_shizuku_dir/shizuku_starter" /data/local/tmp/stayopen_shizuku_starter
adb shell chmod 700 /data/local/tmp/stayopen_shizuku_starter
stayopen_shizuku_apk="$(adb shell pm path moe.shizuku.privileged.api | tr -d '\r' | sed -n 's/^package://p' | head -1)"
adb shell /data/local/tmp/stayopen_shizuku_starter "--apk=$stayopen_shizuku_apk"
rm -r "$stayopen_shizuku_dir"
