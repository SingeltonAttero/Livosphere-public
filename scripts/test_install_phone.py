"""Installer matrix tests: temporary artifacts and fake ADB, no Android build/device."""

import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import selectors
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("install_phone", Path(__file__).with_name("install-phone.py"))
installer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(installer)

FAKE_ADB = r'''import json, os, sys, time
from pathlib import Path
args = sys.argv[1:]
with open(os.environ["FAKE_ADB_LOG"], "a") as log:
    log.write(json.dumps(args) + "\n")
state = json.loads(Path(os.environ["FAKE_ADB_STATE"]).read_text())
if args == ["devices", "-l"]:
    print("List of devices attached")
    for serial, status in state["devices"].items():
        print(serial + "\t" + status + " product:fake model:Fake")
elif args[:1] == ["-s"]:
    serial = args[1]
    command = args[2:]
    if command == ["shell", "getprop", "ro.kernel.qemu"]:
        if state.get("invalid_output"):
            sys.stdout.buffer.write(b"invalid byte: \xff\n")
            sys.exit(2)
        print("1" if serial in state.get("qemu", []) else "0")
    elif command[:2] == ["install", "-r"]:
        print("Performing Streamed Install", flush=True)
        if "continue_file" in state:
            deadline = time.monotonic() + 10
            while not Path(state["continue_file"]).exists():
                if time.monotonic() > deadline:
                    sys.exit(92)
                time.sleep(0.01)
        time.sleep(state.get("delay", 0))
        print(state.get("install_output", "Success"), flush=True)
        sys.exit(state.get("install_code", 0))
    elif command == ["shell", "dumpsys", "package", "app.livosphere"]:
        print(state.get("package", "  versionCode=7 minSdk=29 targetSdk=36\n  versionName=1.2.3"))
    else:
        sys.exit(90)
else:
    sys.exit(91)
'''


class InstallPhoneTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name).resolve()
        self.state_file = self.base / "state.json"
        self.log = self.base / "adb.jsonl"
        self.adb = self.base / "adb"
        self.adb.write_text(f"#!{sys.executable}\n" + FAKE_ADB)
        self.adb.chmod(0o755)
        env = mock.patch.dict(os.environ, {"PATH": str(self.base), "FAKE_ADB_STATE": str(self.state_file),
                                          "FAKE_ADB_LOG": str(self.log)}, clear=True)
        env.start()
        self.addCleanup(env.stop)
        self.state = {"devices": {"phone-one": "device"}}
        self.write_state()
        self.run = self.make_run("candidate")

    def write_state(self):
        self.state_file.write_text(json.dumps(self.state))

    def make_run(self, name, created="2026-09-27T12:00:00Z"):
        run = self.base / name
        (run / "artifacts").mkdir(parents=True)
        data = b"fake signed APK for installer tests"
        (run / "artifacts/phone.apk").write_bytes(data)
        manifest = {"schemaVersion": 3, "profile": "phone-v2", "runId": name,
                    "createdAtUtc": created, "productRelease": "1.2.3", "versionCode": 7,
                    "signing": {"status": "OWNER_CONFIRMED"},
                    "artifacts": [{"id": "phone-apk", "path": "artifacts/phone.apk",
                                   "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}]}
        (run / "release-manifest.json").write_text(json.dumps(manifest))
        return run

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def install(self, serial=None):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            installer.install(str(self.run), serial)
        return output.getvalue()

    def assert_no_install(self):
        self.assertFalse(any("install" in call for call in self.calls()))

    def test_ready_verifies_and_targets_every_device_operation(self):
        output = self.install()
        self.assertIn("Установлено: app.livosphere 1.2.3 (7) на phone-one", output)
        self.assertIn("SHA-256", output)
        self.assertEqual(self.calls(), [
            ["devices", "-l"],
            ["-s", "phone-one", "shell", "getprop", "ro.kernel.qemu"],
            ["-s", "phone-one", "install", "-r", str(self.run / "artifacts/phone.apk")],
            ["-s", "phone-one", "shell", "dumpsys", "package", "app.livosphere"],
        ])

    def test_run_selection_uses_manifest_creation_and_ignores_incomplete(self):
        newest = self.make_run("newer", "2026-09-27T13:00:00Z")
        (self.base / "unfinished").mkdir()
        os.utime(newest / "release-manifest.json", (1, 1))
        self.assertEqual(installer.choose_run(None, self.base), newest)
        self.assertEqual(installer.choose_run(str(self.run), self.base), self.run)
        bad = installer.choose_run(str(self.base / "missing"), self.base)
        with self.assertRaisesRegex(installer.InstallError, "manifest"):
            installer.check_candidate(bad)

    def test_no_completed_runs(self):
        with self.assertRaisesRegex(installer.InstallError, "не найдены"):
            installer.choose_run(None, self.base / "empty")

    def test_invalid_creation_time_names_manifest_without_fallback(self):
        bad = self.make_run("broken-date", "not-a-timestamp")
        with self.assertRaises(installer.InstallError) as error:
            installer.choose_run(None, self.base)
        self.assertIn(str(bad / "release-manifest.json"), str(error.exception))
        self.assert_no_install()

    def test_bad_manifest_fields_refused_before_adb(self):
        path = self.run / "release-manifest.json"
        original = json.loads(path.read_text())
        mutations = [None, [], {**original, "schemaVersion": 2}, {**original, "profile": "legacy"},
                     {**original, "runId": "wrong"}, {**original, "signing": {}},
                     {**original, "versionCode": True}, {**original, "productRelease": ""},
                     {**original, "createdAtUtc": "yesterday"}, {**original, "artifacts": []},
                     {**original, "artifacts": [original["artifacts"][0]] * 2},
                     {**original, "artifacts": [None]}]
        for key, value in [("path", "../phone.apk"), ("bytes", 0), ("sha256", "broken")]:
            mutations.append({**original, "artifacts": [{**original["artifacts"][0], key: value}]})
        for manifest in mutations:
            with self.subTest(manifest=manifest):
                path.write_text(json.dumps(manifest))
                with self.assertRaises(installer.InstallError):
                    self.install()
        path.write_text("{invalid json")
        with self.assertRaises(installer.InstallError):
            self.install()
        self.assertEqual(self.calls(), [])

    def test_missing_changed_and_symlink_apk(self):
        apk = self.run / "artifacts/phone.apk"
        original = apk.read_bytes()
        for payload, expected in [(b"small", "Размер"), (b"x" * len(original), "SHA-256")]:
            apk.write_bytes(payload)
            with self.assertRaisesRegex(installer.InstallError, expected):
                self.install()
        apk.unlink()
        with self.assertRaisesRegex(installer.InstallError, "отсутствует"):
            self.install()
        external = self.base / "external.apk"
        external.write_bytes(original)
        apk.symlink_to(external)
        with self.assertRaisesRegex(installer.InstallError, "ссылкой"):
            self.install()
        self.assertEqual(self.calls(), [])

    def test_no_device_or_emulator_or_unavailable(self):
        for devices, qemu in [({}, []), ({"emulator-5554": "device"}, []),
                              ({"network-emulator": "device"}, ["network-emulator"]),
                              ({"locked": "unauthorized", "lost": "offline"}, [])]:
            with self.subTest(devices=devices):
                self.state.update(devices=devices, qemu=qemu)
                self.write_state()
                with self.assertRaisesRegex(installer.InstallError, "USB debugging"):
                    self.install()
        self.assert_no_install()

    def test_many_devices_require_known_physical_serial(self):
        self.state["devices"] = {"phone-one": "device", "phone-two": "device", "emulator-5554": "device"}
        self.write_state()
        with self.assertRaisesRegex(installer.InstallError, "PHONE_SERIAL"):
            self.install()
        for serial in ["missing", "emulator-5554"]:
            with self.assertRaises(installer.InstallError):
                self.install(serial)
        self.assert_no_install()
        self.assertIn("на phone-two", self.install("phone-two"))

    def test_install_failure_and_wrong_version_never_report_success(self):
        for failure in ["INSTALL_FAILED_UPDATE_INCOMPATIBLE", "INSTALL_FAILED_VERSION_DOWNGRADE"]:
            with self.subTest(failure=failure):
                self.state.update(install_code=1, install_output=failure)
                self.write_state()
                with self.assertRaisesRegex(installer.InstallError, "прежним ключом"):
                    self.install()
        self.state.update(install_code=0, install_output="Success", package="  versionCode=8 minSdk=29\n  versionName=1.2.3")
        self.write_state()
        with self.assertRaisesRegex(installer.InstallError, "версия не подтверждена"):
            self.install()
        self.state["package"] = "  versionCode=7 minSdk=29\n  versionName=wrong"
        self.write_state()
        with self.assertRaisesRegex(installer.InstallError, "версия не подтверждена"):
            self.install()
        self.assertFalse(any("uninstall" in call or "-d" in call for call in self.calls()))

    def test_install_timeout(self):
        self.state["delay"] = 2
        self.write_state()
        with mock.patch.object(installer, "INSTALL_TIMEOUT", 0.1):
            with self.assertRaisesRegex(installer.InstallError, "ADB -s phone-one install -r .*превышено ожидание"):
                self.install()

    def test_adb_invalid_bytes_preserve_failure_context(self):
        self.state["invalid_output"] = True
        self.write_state()
        with self.assertRaises(installer.InstallError) as error:
            self.install()
        self.assertIn("ADB -s phone-one shell getprop ro.kernel.qemu", str(error.exception))
        self.assertIn("invalid byte: \ufffd", str(error.exception))
        self.assert_no_install()

    def test_adb_discovery_sdk_and_missing(self):
        sdk = self.base / "sdk"
        (sdk / "platform-tools").mkdir(parents=True)
        self.adb.rename(sdk / "platform-tools/adb")
        with mock.patch.dict(os.environ, {"ANDROID_SDK_ROOT": str(sdk)}):
            self.assertEqual(installer.find_adb(), str(sdk / "platform-tools/adb"))
        with mock.patch.object(installer.Path, "home", return_value=self.base):
            with self.assertRaisesRegex(installer.InstallError, "ADB не найден"):
                installer.find_adb()

    def test_cli_environment_defaults_and_explicit_precedence(self):
        with mock.patch.dict(os.environ, {"PHONE_RUN_DIR": "env-run", "PHONE_SERIAL": "env-phone",
                                          "ANDROID_SERIAL": "android-phone"}):
            for arguments, expected in [([], ("env-run", "env-phone")),
                                        (["--run-dir", "cli-run", "--serial", "cli-phone"], ("cli-run", "cli-phone"))]:
                with mock.patch.object(sys, "argv", ["install-phone.py", *arguments]), mock.patch.object(installer, "install") as action:
                    self.assertEqual(installer.main(), 0)
                    action.assert_called_once_with(*expected)
            os.environ["PHONE_SERIAL"] = ""
            with mock.patch.object(sys, "argv", ["install-phone.py"]), mock.patch.object(installer, "install") as action:
                installer.main()
                action.assert_called_once_with("env-run", "android-phone")
        with mock.patch.dict(os.environ, {"PHONE_RUN_DIR": "  ", "PHONE_SERIAL": "", "ANDROID_SERIAL": "\t"}):
            with mock.patch.object(sys, "argv", ["install-phone.py"]), mock.patch.object(installer, "install") as action:
                installer.main()
                action.assert_called_once_with(None, None)

    def test_explicit_empty_cli_selection_never_installs_or_falls_back(self):
        with mock.patch.dict(os.environ, {"PHONE_RUN_DIR": str(self.run), "PHONE_SERIAL": "phone-one"}):
            for option in ["--run-dir", "--serial"]:
                for value in ["", " \t"]:
                    with self.subTest(option=option, value=value):
                        result = subprocess.run([sys.executable, installer.__file__, option, value],
                                                capture_output=True, text=True, timeout=5)
                        self.assertEqual(result.returncode, 2)
                        self.assertIn("явно заданное значение не может быть пустым", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_cli_streams_adb_progress_before_install_finishes(self):
        signal = self.base / "continue"
        self.state["continue_file"] = str(signal)
        self.write_state()
        process = subprocess.Popen([sys.executable, installer.__file__, "--run-dir", str(self.run)],
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        output = b""
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                deadline = time.monotonic() + 5
                while b"Performing Streamed Install" not in output:
                    remaining = deadline - time.monotonic()
                    self.assertGreater(remaining, 0, "ADB progress was buffered until installation ended")
                    self.assertTrue(selector.select(remaining), "No live ADB progress received")
                    chunk = os.read(process.stdout.fileno(), 4096)
                    self.assertTrue(chunk, output.decode(errors="replace"))
                    output += chunk
            self.assertIsNone(process.poll())
            self.assertNotIn("Установлено:".encode(), output)
            signal.touch()
            rest, _ = process.communicate(timeout=5)
            output += rest
            self.assertEqual(process.returncode, 0, output.decode(errors="replace"))
            self.assertIn("Установлено: app.livosphere 1.2.3 (7) на phone-one".encode(), output)
        finally:
            signal.touch()
            try:
                process.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.communicate()

    def test_cli_failure_has_nonzero_exit_without_traceback(self):
        result = subprocess.run([sys.executable, str(Path(installer.__file__)), "--run-dir", str(self.base / "missing")],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertIn("Ошибка:", result.stderr)
        self.assertNotIn("Traceback", result.stderr)


if __name__ == "__main__":
    unittest.main()
