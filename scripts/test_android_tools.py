"""Portable environment and debug-installer tests; no SDK or device required."""
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

SCRIPTS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("install_debug", SCRIPTS / "install-debug.py")
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class ToolsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="livosphere tools ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "scripts").mkdir()
        (self.root / "android").mkdir()
        for name in ("android-env.sh", "doctor.sh", "gradle.sh"):
            shutil.copy2(SCRIPTS / name, self.root / "scripts" / name)
        self.java = self.root / "jdk 17"
        (self.java / "bin").mkdir(parents=True)
        java = self.java / "bin/java"
        java.write_text('#!/bin/sh\nprintf "    java.version = 17.0.1\\n" >&2\n')
        java.chmod(0o755)
        self.sdk = self.root / "sdk with spaces"
        (self.sdk / "platforms/android-37").mkdir(parents=True)
        (self.sdk / "platforms/android-37/android.jar").touch()
        self.env = {k: v for k, v in os.environ.items() if k not in
                    ("JAVA_HOME", "LIVOSPHERE_JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "PHONE_SERIAL")}
        self.env["JAVA_HOME"] = str(self.java)
        self.env["LIVOSPHERE_JAVA_HOME"] = str(self.java)
        self.env["ANDROID_SDK_ROOT"] = str(self.sdk)

    def run_env(self, *args, env=None):
        return subprocess.run([str(self.root / "scripts/android-env.sh"), *args],
                              cwd="/", env=env or self.env, capture_output=True, text=True)

    def test_doctor_and_paths_with_spaces(self):
        result = self.run_env("./scripts/doctor.sh")
        self.assertEqual(result.returncode, 0, result.stderr)
        result = self.run_env(sys.executable, "-c", "import os; print(os.getcwd()); print(os.environ['JAVA_HOME'])")
        self.assertEqual(Path(result.stdout.splitlines()[0]).resolve(), self.root.resolve())
        self.assertEqual(result.stdout.splitlines()[1], str(self.java))

    def test_sdk_from_local_properties_and_conflict(self):
        props = self.root / "android/local.properties"
        props.write_text("sdk.dir=" + str(self.sdk).replace(" ", "\\ ") + "\n")
        env = dict(self.env)
        del env["ANDROID_SDK_ROOT"]
        self.assertEqual(self.run_env("./scripts/doctor.sh", env=env).returncode, 0)
        self.env["ANDROID_SDK_ROOT"] = str(self.root / "other sdk")
        result = self.run_env("./scripts/doctor.sh")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("differs", result.stderr)

    def test_wrong_java_and_missing_platform_fail_before_gradle(self):
        (self.java / "bin/java").write_text('#!/bin/sh\nprintf "    java.version = 21.0.1\\n" >&2\n')
        result = self.run_env("./scripts/doctor.sh")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("JDK 17", result.stderr)
        (self.java / "bin/java").write_text('#!/bin/sh\nprintf "    java.version = 17.0.1\\n" >&2\n')
        (self.sdk / "platforms/android-37/android.jar").unlink()
        result = self.run_env("./scripts/doctor.sh")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Platform 37", result.stderr)

    def test_gradle_wrapper_receives_tasks_from_repo_android_directory(self):
        wrapper = self.root / "android/gradlew"
        wrapper.write_text('#!/bin/sh\npwd\nprintf "%s\\n" "$@"\n')
        wrapper.chmod(0o755)
        result = self.run_env("./scripts/gradle.sh", "assetsCheck", ":hub:app:assembleDebug")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(Path(result.stdout.splitlines()[0]).resolve(), (self.root / "android").resolve())
        self.assertEqual(result.stdout.splitlines()[-2:], ["assetsCheck", ":hub:app:assembleDebug"])

    def install(self, devices, argv, failure=None, launch=None):
        apk = self.root / "debug.apk"
        apk.touch()
        with mock.patch.object(installer, "APK", apk), mock.patch.object(sys, "argv", ["install-debug.py", *argv]), \
             mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(installer.shutil, "which", return_value="adb"), \
             mock.patch.object(installer.subprocess, "run") as run:
            run.side_effect = [subprocess.CompletedProcess([], 0, stdout=devices), failure or subprocess.CompletedProcess([], 0),
                               launch or subprocess.CompletedProcess([], 0, stdout="Status: ok\n", stderr="")]
            try:
                installer.main()
            finally:
                self.adb_calls = run.call_args_list
            return run.call_args_list

    def test_debug_installer_uses_selected_serial_and_never_uninstalls(self):
        calls = self.install("List of devices attached\na device\nb device\n", ["--serial", "b"])
        self.assertEqual(calls[1].args[0][:5], ["adb", "-s", "b", "install", "-r"])
        self.assertEqual(calls[2].args[0], ["adb", "-s", "b", "shell", "am", "start", "-W", "-n",
                                         "app.livosphere/.MainActivity", "-a", "android.intent.action.MAIN",
                                         "-c", "android.intent.category.LAUNCHER"])
        self.assertEqual(len(calls), 3)

    def test_debug_installer_rejects_ambiguity_unauthorized_and_empty_serial(self):
        for devices, args in [("a device\nb device", []), ("a unauthorized", []), ("a device", ["--serial", ""])]:
            with self.subTest(devices=devices, args=args), self.assertRaises(SystemExit):
                self.install(devices, args)

    def test_debug_installer_reports_failed_install(self):
        with self.assertRaises(subprocess.CalledProcessError):
            self.install("a device", [], subprocess.CalledProcessError(1, ["adb", "install"]))
        self.assertEqual(len(self.adb_calls), 2)

    def test_launch_errors_do_not_report_success_after_installation(self):
        for launch in [subprocess.CalledProcessError(1, ["adb", "shell", "am"]),
                       subprocess.TimeoutExpired(["adb", "shell", "am"], 30),
                       subprocess.CompletedProcess([], 0, stdout="Error type 3\nActivity does not exist\n", stderr=""),
                       subprocess.CompletedProcess([], 0, stdout="", stderr="")]:
            with self.subTest(launch=launch), self.assertRaisesRegex(SystemExit, "APK installed, but MainActivity"):
                self.install("a device", [], launch=launch)


if __name__ == "__main__":
    unittest.main()
