"""Exercise real shell-privileged RAM clearing on a disposable emulator target."""
import json
import subprocess
import time
from pathlib import Path


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True).strip()


def invoke(mode):
    text = adb("shell", "CLASSPATH=/data/local/tmp/stayopen-ram-test.apk",
               "app_process", "/system/bin", "dev.hamweb.stayopen.privileged.RamShellSmoke", mode)
    result = json.loads(next(line.removeprefix("STAYOPEN_RESULT=") for line in text.splitlines()
                             if line.startswith("STAYOPEN_RESULT=")))
    print(mode, json.dumps(result), flush=True)
    return result


fixture = "dev.hamweb.stayopen.ramfixture"
adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("install", "-r", "ramfixture/build/outputs/apk/debug/ramfixture-debug.apk")
adb("push", "app/build/outputs/apk/debug/app-debug.apk", "/data/local/tmp/stayopen-ram-test.apk")
adb("shell", "am", "start", "-W", "-n", fixture + "/.MemoryFixtureActivity")
time.sleep(1)
pid = adb("shell", "pidof", fixture)
assert pid, "Fixture did not start"

# Foreground target must be skipped, even though it is explicitly selected.
foreground = invoke("clear")
assert foreground["outcomes"] == [], "Foreground app was a target"
assert adb("shell", "pidof", fixture) == pid, "Foreground app was killed"

adb("shell", "input", "keyevent", "KEYCODE_HOME")
for _ in range(30):
    scan = invoke("scan")
    if scan["candidates"]:
        break
    time.sleep(1)
assert scan["candidates"], "Fixture never became cached"

# Protected target must survive a real privileged clear request.
protected = invoke("protected")
assert protected["outcomes"] == [], "Protected app was a target"
assert adb("shell", "pidof", fixture) == pid, "Protected app was killed"

cleared = invoke("clear")
assert sum(row["exited"] for row in cleared["outcomes"]) >= 1, "No process exit verified"
assert not adb("shell", "ps", "-A", "-o", "PID,NAME").splitlines().count(pid + " " + fixture)
assert pid not in [line.split()[0] for line in adb("shell", "ps", "-A", "-o", "PID,NAME").splitlines()
                   if line.split() and line.split()[-1] == fixture], "Original fixture PID still alive"
out = Path("screenshots"); out.mkdir(exist_ok=True)
(out / "ram-clear-evidence.json").write_text(json.dumps({"foreground": foreground, "cached_scan": scan,
    "protected": protected, "cleared": cleared, "fixture_pid": pid, "api": adb("shell", "getprop", "ro.build.version.sdk")}, indent=2))
adb("uninstall", fixture)
print("Real foreground exclusion, protected exclusion and cached-process exit verified.", flush=True)
