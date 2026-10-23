#!/usr/bin/env bash
set -euo pipefail

# Test-runner launcher ANRs must not obscure screenshots of the app under test.
adb shell settings put global hide_error_dialogs 1
./gradlew --no-daemon connectedDebugAndroidTest assembleRelease
adb pull /sdcard/Download/StayOpen-test-screenshots screenshots

# Also exercise the real R8-shrunk release code. This test-only signature is
# unrelated to the maintainer's private production signing key.
stayopen_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK path missing}}"
stayopen_ci_key_dir="$(mktemp -d)"
keytool -genkeypair -noprompt -keystore "$stayopen_ci_key_dir/test.p12" \
  -storetype PKCS12 -storepass android -keypass android -alias ci \
  -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=StayOpen CI Only'
"$stayopen_sdk/build-tools/35.0.0/apksigner" sign \
  --ks "$stayopen_ci_key_dir/test.p12" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias ci --out release-smoke.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
rm "$stayopen_ci_key_dir/test.p12"
rmdir "$stayopen_ci_key_dir"
adb install -r release-smoke.apk
adb shell am start -W -n dev.hamweb.stayopen/.MainActivity
sleep 3
test -n "$(adb shell pidof dev.hamweb.stayopen)"
adb shell uiautomator dump /sdcard/StayOpen-release-window.xml
adb pull /sdcard/StayOpen-release-window.xml screenshots/release-window.xml
python3 - <<'PY'
from pathlib import Path
text = Path('screenshots/release-window.xml').read_text()
assert 'SYSTEM MONITOR' in text, 'Release dashboard did not appear'
assert 'STAYOPEN_' in text, 'Release console did not appear'
PY
adb exec-out screencap -p > screenshots/release-dashboard.png
