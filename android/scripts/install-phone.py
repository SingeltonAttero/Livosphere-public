#!/usr/bin/env python3
"""Install a completed phone-v2 candidate without build or signing environment."""

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "_bmad-output/implementation-artifacts/evidence/phone-v2"
PACKAGE = "app.livosphere"
COMMAND_TIMEOUT = 20
INSTALL_TIMEOUT = 180


class InstallError(Exception):
    """An actionable owner-facing failure."""


def say(message):
    print(message, flush=True)


def read_manifest(run):
    path = run / "release-manifest.json"
    if path.is_symlink() or not path.is_file():
        raise InstallError(f"Нет обычного release-manifest.json: {run}. Выберите завершённый run.")
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError) as error:
        raise InstallError(f"Не удалось прочитать manifest {path}: {error}") from error
    if not isinstance(manifest, dict):
        raise InstallError(f"Неправильный manifest: {path}.")
    return manifest


def created_at(manifest, path):
    value = manifest.get("createdAtUtc")
    try:
        return dt.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
    except (TypeError, ValueError) as error:
        raise InstallError(f"Неправильное createdAtUtc в {path}; укажите корректный PHONE_RUN_DIR.") from error


def choose_run(explicit, evidence=EVIDENCE):
    if explicit:
        return Path(explicit).expanduser().resolve()
    candidates = []
    for path in evidence.glob("*/release-manifest.json"):
        candidates.append((created_at(read_manifest(path.parent), path), path.parent.name, path.parent))
    if not candidates:
        raise InstallError(f"Завершённые phone-v2 run не найдены в {evidence}. Укажите PHONE_RUN_DIR.")
    return max(candidates)[2].resolve()


def check_candidate(run):
    manifest = read_manifest(run)
    created_at(manifest, run / "release-manifest.json")
    signing = manifest.get("signing")
    if (type(manifest.get("schemaVersion")) is not int or manifest["schemaVersion"] != 3
            or manifest.get("profile") != "phone-v2"
            or manifest.get("runId") != run.name
            or not re.fullmatch(r"[A-Za-z0-9._-]+", run.name)
            or not isinstance(signing, dict) or signing.get("status") != "OWNER_CONFIRMED"):
        raise InstallError("Manifest не подтверждает завершённый подписанный phone-v2 run (schema/profile/runId/signing).")
    version = manifest.get("productRelease")
    code = manifest.get("versionCode")
    if not isinstance(version, str) or not version.strip() or "\n" in version or "\r" in version:
        raise InstallError("Неправильный productRelease в manifest.")
    if type(code) is not int or code <= 0:
        raise InstallError("Неправильный versionCode в manifest.")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list) or any(not isinstance(item, dict) for item in artifacts):
        raise InstallError("Неправильный список artifacts в manifest.")
    apks = [item for item in artifacts if item.get("id") == "phone-apk"]
    if len(apks) != 1:
        raise InstallError("Manifest должен содержать ровно один phone-apk.")
    artifact = apks[0]
    digest = artifact.get("sha256")
    size = artifact.get("bytes")
    if (artifact.get("path") != "artifacts/phone.apk"
            or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", digest)
            or type(size) is not int or size <= 0):
        raise InstallError("Неправильные path/sha256/bytes у phone-apk в manifest.")
    apk = run / "artifacts/phone.apk"
    if apk.is_symlink() or apk.parent.is_symlink() or not apk.is_file():
        raise InstallError(f"APK отсутствует или является ссылкой: {apk}.")
    say("Проверка размера и SHA-256 APK…")
    if apk.stat().st_size != size:
        raise InstallError("Размер APK не совпадает с manifest. Не изменяйте run; выберите целый candidate.")
    actual = hashlib.sha256()
    with apk.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            actual.update(block)
    if actual.hexdigest() != digest.lower():
        raise InstallError("SHA-256 APK не совпадает с manifest. Не изменяйте run; выберите целый candidate.")
    return apk, code, version


def find_adb():
    found = shutil.which("adb")
    if found:
        return found
    sdk_roots = [os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME"),
                 str(Path.home() / "Library/Android/sdk")]
    for sdk in sdk_roots:
        if sdk:
            candidate = Path(sdk).expanduser() / "platform-tools/adb"
            if candidate.is_file() and os.access(candidate, os.X_OK):
                return str(candidate)
    raise InstallError("ADB не найден. Установите Android SDK Platform-Tools и задайте PATH или ANDROID_SDK_ROOT.")


def adb_command(adb, args, timeout=COMMAND_TIMEOUT, visible=False):
    context = "ADB " + " ".join(args)
    try:
        # Inherit stdout for installation: ADB progress is visible while it runs.
        result = subprocess.run([adb, *args], timeout=timeout, text=True, errors="replace",
                                stdout=None if visible else subprocess.PIPE, stderr=subprocess.STDOUT)
    except subprocess.TimeoutExpired as error:
        raise InstallError(f"{context}: превышено ожидание {timeout} с. Проверьте USB и разрешение RSA; повторите команду. "
                           "Скрипт не выполнял удаление приложения или очистку данных; "
                           "результат установки проверьте на телефоне.") from error
    except OSError as error:
        raise InstallError(f"Не удалось запустить {context}: {error}") from error
    if result.returncode:
        detail = (result.stdout or "См. вывод ADB выше.").strip()
        raise InstallError(f"{context} завершился с ошибкой ({result.returncode}): {detail}\n"
                           "Проверьте USB/RSA. При конфликте подписи нужен APK с прежним ключом; "
                           "при понижении версии — candidate с подходящим versionCode. "
                           "Автоматическое удаление приложения и обход проверок не выполняются.")
    return result.stdout or ""


def choose_device(adb, requested):
    say("Поиск физического телефона через ADB…")
    output = adb_command(adb, ["devices", "-l"])
    devices = {}
    for line in output.splitlines():
        fields = line.split()
        if len(fields) >= 2 and not line.startswith(("List of devices", "*")):
            devices[fields[0]] = fields[1]
    if requested and devices.get(requested) != "device":
        raise InstallError(f"Телефон {requested} недоступен (состояние: {devices.get(requested, 'не найден')}). "
                           "Включите USB debugging, проверьте кабель и разрешите RSA на телефоне.")
    physical = []
    serials = [requested] if requested else sorted(devices)
    for serial in serials:
        if devices[serial] != "device" or serial.startswith("emulator-"):
            continue
        qemu = adb_command(adb, ["-s", serial, "shell", "getprop", "ro.kernel.qemu"]).strip()
        if qemu == "1":
            continue
        physical.append(serial)
    if not physical:
        states = ", ".join(f"{serial}: {state}" for serial, state in devices.items()) or "устройств нет"
        raise InstallError(f"Доступный физический телефон не найден ({states}). "
                           "Подключите телефон, включите USB debugging и разрешите RSA; эмуляторы исключены.")
    if len(physical) > 1:
        raise InstallError(f"Доступно несколько телефонов: {', '.join(physical)}. Укажите PHONE_SERIAL или --serial.")
    return physical[0]


def install(run_dir=None, serial=None):
    run = choose_run(run_dir)
    say(f"Выбран run: {run}")
    apk, code, version = check_candidate(run)
    say(f"Версия candidate: {version} (versionCode={code})")
    adb = find_adb()
    selected = choose_device(adb, serial)
    say(f"Устройство: {selected}")
    say(f"Установка обновления (install -r), ожидание до {INSTALL_TIMEOUT} с…")
    adb_command(adb, ["-s", selected, "install", "-r", str(apk)], INSTALL_TIMEOUT, visible=True)
    say("Проверка установленной версии…")
    package = adb_command(adb, ["-s", selected, "shell", "dumpsys", "package", PACKAGE])
    codes = set(re.findall(r"^\s*versionCode=(\d+)\b", package, re.MULTILINE))
    versions = set(re.findall(r"^\s*versionName=([^\r\n]*)", package, re.MULTILINE))
    if codes != {str(code)} or versions != {version}:
        raise InstallError(f"Установленная версия не подтверждена: ожидалась {version} ({code}), "
                           f"получены versionName={sorted(versions)}, versionCode={sorted(codes)}. "
                           "Проверьте приложение на выбранном телефоне и повторите установку нужного run.")
    say(f"Установлено: {PACKAGE} {version} ({code}) на {selected}.")
    say("Версия проверена. Физическая приёмка, проверка функций и готовность релиза остаются отдельными шагами.")


def main():
    parser = argparse.ArgumentParser(description="Установить готовый phone-v2 APK на физический телефон без пересборки.")
    parser.add_argument("--run-dir",
                        help="каталог run (по умолчанию PHONE_RUN_DIR или последний manifest по createdAtUtc)")
    parser.add_argument("--serial",
                        help="serial телефона (по умолчанию PHONE_SERIAL / ANDROID_SERIAL)")
    args = parser.parse_args()
    for option, value in (("--run-dir", args.run_dir), ("--serial", args.serial)):
        if value is not None and not value.strip():
            parser.error(f"{option}: явно заданное значение не может быть пустым")

    def env_value(name):
        value = os.environ.get(name)
        return value if value and value.strip() else None

    run_dir = args.run_dir if args.run_dir is not None else env_value("PHONE_RUN_DIR")
    serial = args.serial if args.serial is not None else env_value("PHONE_SERIAL") or env_value("ANDROID_SERIAL")
    try:
        install(run_dir, serial)
    except (InstallError, OSError) as error:
        print(f"Ошибка: {error}", file=sys.stderr, flush=True)
        return 1
    except KeyboardInterrupt:
        print("Установка прервана. Проверьте состояние приложения на телефоне перед повтором.", file=sys.stderr)
        return 130
    return 0


if __name__ == "__main__":
    sys.exit(main())
