#!/usr/bin/env python3
"""Install the existing debug APK and launch MainActivity on the selected device."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "android/hub/app/build/outputs/apk/debug/app-debug.apk"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("PHONE_SERIAL"))
    args = parser.parse_args()
    adb = shutil.which("adb")
    if not adb:
        raise SystemExit("adb not found. Install Android SDK Platform-Tools and run make phone-install.")
    if not APK.is_file():
        raise SystemExit("Debug APK not found. Run make phone first.")
    devices = subprocess.run([adb, "devices"], check=True, capture_output=True, text=True, timeout=20)
    ready = [parts[0] for line in devices.stdout.splitlines() if len(parts := line.split()) == 2 and parts[1] == "device"]
    if args.serial:
        if args.serial not in ready:
            raise SystemExit("Selected device is unavailable. Check adb devices and USB authorization.")
        serial = args.serial
    elif args.serial is not None:
        raise SystemExit("Device serial cannot be empty.")
    elif len(ready) == 1:
        serial = ready[0]
    else:
        raise SystemExit("Connect one authorized device or specify PHONE_SERIAL. See adb devices.")
    subprocess.run([adb, "-s", serial, "install", "-r", str(APK)], check=True, timeout=180)
    print("Debug APK installed. Starting Livosphere...", flush=True)
    try:
        launch = subprocess.run(
            [adb, "-s", serial, "shell", "am", "start", "-W", "-n", "app.livosphere/.MainActivity",
             "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"],
            check=True, capture_output=True, text=True, timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f"APK installed, but MainActivity launch failed: {error}") from error
    output = launch.stdout + launch.stderr
    if any(line.lstrip().startswith("Error") for line in output.splitlines()) or "Status: ok" not in output:
        raise SystemExit(f"APK installed, but MainActivity launch was not confirmed:\n{output.strip()}")
    print("Livosphere started. Follow docs/TESTING.md.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, subprocess.SubprocessError) as error:
        print(f"Installation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
