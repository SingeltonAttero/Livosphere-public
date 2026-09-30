#!/usr/bin/env python3
"""Local, phone-only release candidate and artifact-bound readiness checks."""

import datetime as dt
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "android"
SCRIPT = Path(__file__).resolve().parent
SET_IDS = frozenset({
    "night-sakura", "electric-harbor", "last-light", "moscow-facets",
    "synthetic-dawn", "neon-express", "rainforest", "emerald-cove",
    "orbital-window", "star-river", "golden-dunes", "moon-tide",
    "chromatic-flow",
})
GATES = ("functional", "physicalPerformance", "battery", "storeDelivery", "ownerApproval")
WIDGETS = {"Small": "small", "Medium": "medium", "Large": "large"}
LOCAL_ENV_KEYS = frozenset({
    "LIVOSPHERE_RELEASE_KEYSTORE", "LIVOSPHERE_RELEASE_STORE_PASSWORD",
    "LIVOSPHERE_RELEASE_KEY_ALIAS", "LIVOSPHERE_RELEASE_KEY_PASSWORD",
    "LIVOSPHERE_RELEASE_CERT_SHA256", "LIVOSPHERE_OWNER_KEY_CONFIRMED",
    "PRODUCT_RELEASE", "PHONE_PREVIOUS_VERSION_CODE", "PHONE_VERSION_CODE",
    "LIVOSPHERE_RELEASE_RUN_DIR", "LIVOSPHERE_PREVIOUS_PHONE_V2_MANIFEST",
})

_aab_spec = importlib.util.spec_from_file_location("livosphere_aab_manifest", SCRIPT / "read-aab-manifest.py")
_aab_xml = importlib.util.module_from_spec(_aab_spec)
_aab_spec.loader.exec_module(_aab_xml)


def fail(message):
    raise SystemExit(f"phone-v2: {message}")


def load_local_env(path=None):
    """Read the ignored local file as literal KEY=value lines; CI can use env only."""
    path = ROOT / ".env" if path is None else Path(path)
    if not path.exists() and not path.is_symlink():
        return
    regular(path)
    if path.stat().st_mode & (stat.S_IRWXG | stat.S_IRWXO):
        fail(".env must be readable only by its owner (chmod 600 .env)")
    seen = set()
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key not in LOCAL_ENV_KEYS or key in seen:
            fail(f"invalid .env entry at line {number}")
        if not value:
            fail(f".env value missing: {key} (line {number})")
        if key in os.environ and os.environ[key] != value:
            fail(f".env conflicts with environment: {key}")
        os.environ[key] = value
        seen.add(key)


def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def sha(path):
    value = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def regular(path):
    if not path.is_file() or path.is_symlink():
        fail(f"missing regular file: {path}")


def clean_source(allowed_run=None):
    status = run(["git", "-C", str(ROOT), "status", "--porcelain", "--untracked-files=all", "-z"], capture_output=True).stdout
    dirty = []
    for record in status.split("\0"):
        if not record:
            continue
        name = record[3:]
        if allowed_run is not None and record[:2] == "??" and (name == allowed_run or name.startswith(allowed_run + "/")):
            continue
        dirty.append(record)
    if dirty:
        fail("source tree is dirty: " + "; ".join(dirty[:3]))
    return run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True).stdout.strip()


def public_sets():
    settings = (ANDROID / "gradle.properties").read_text(encoding="utf-8")
    match = re.search(r"(?m)^livosphere\.setManifests=(.+)$", settings)
    if not match:
        fail("set manifest registry missing")
    selected = []
    for name in match.group(1).split(","):
        path = ANDROID / name.strip()
        regular(path)
        fields = dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines() if "=" in line and not line.startswith("#"))
        if fields.get("distribution") != "public":
            continue
        if fields.get("contentStatus") != "html-approved":
            fail(f"public set is not approved: {fields.get('setId')}")
        revision = int(fields["setRevision"])
        if revision < 1:
            fail("setRevision must be positive")
        selected.append({"setId": fields["setId"], "setRevision": revision,
                         "manifestPath": str(path.relative_to(ROOT)), "manifestSha256": sha(path)})
    if {item["setId"] for item in selected} != SET_IDS or len(selected) != 13:
        fail("public closure must contain exactly the approved 13 sets")
    return sorted(selected, key=lambda item: item["setId"])


def tool(name):
    found = shutil.which(name)
    if found:
        return found
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    candidates = sorted((Path(sdk) / "build-tools").glob(f"*/{name}")) if sdk else []
    if candidates:
        return str(candidates[-1])
    fail(f"{name} not found")


def apk_meta(path):
    output = run([tool("aapt2"), "dump", "badging", str(path)], capture_output=True).stdout
    match = re.search(r"(?m)^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", output)
    if not match:
        fail("cannot read APK package/version")
    return match.group(1), int(match.group(2)), match.group(3)


def aab_meta(path):
    value = run([sys.executable, str(SCRIPT / "read-aab-manifest.py"), str(path)], capture_output=True).stdout.strip()
    package, code, name = value.split("|", 2)
    return package, int(code), name


def package_check(apk, aab, code, product):
    if apk_meta(apk) != ("app.livosphere", code, product) or aab_meta(aab) != ("app.livosphere", code, product):
        fail("APK/AAB package or version mismatch")
    for package in (apk, aab):
        with zipfile.ZipFile(package) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                fail("duplicate package entry")
            if any("contour" in name.lower() or "fixture" in name.lower() or "watchface" in name.lower() or "wff" in name.lower() for name in names):
                fail("debug or WFF resource in release package")
    xml = run([tool("aapt2"), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)], capture_output=True).stdout
    for name in ("SmallClockWidgetProvider", "MediumClockWidgetProvider", "LargeClockWidgetProvider",
                 "ClockWidgetConfigurationActivity", "ClockWidgetPrePinActivity", "ClockLaunchRouterActivity"):
        if name not in xml:
            fail(f"release APK missing {name}")
    for name in ("DigitalClockProbeProvider", "AnalogClockProbeProvider", "ContourWallpaperService", "FixtureWallpaperService"):
        if name in xml:
            fail(f"debug component in release APK: {name}")
    resources = run([tool("aapt2"), "dump", "resources", str(apk)], capture_output=True).stdout
    if re.search(r"(?i)contour|fixture|clock_probe|wff", resources):
        fail("debug or WFF resource in release APK")
    for name in ("clock_widget_small_info", "clock_widget_medium_info", "clock_widget_large_info",
                 "clock_widget_needs_configuration"):
        if name not in resources:
            fail(f"release APK missing widget resource {name}")
    for set_id in SET_IDS:
        if f"ls_{set_id.replace('-', '_')}_preview_wallpaper" not in resources:
            fail(f"release APK missing public set resources: {set_id}")
    resource_ids = {name: resource_id for resource_id, name in re.findall(r"resource (0x[0-9a-f]+) xml/(clock_widget_(?:small|medium|large)_info)", resources)}
    for size, label in WIDGETS.items():
        provider = f"app.livosphere.widgets.runtime.{size}ClockWidgetProvider"
        wanted_id = resource_ids.get(f"clock_widget_{label}_info")
        if not wanted_id:
            fail(f"missing APK widget descriptor: {label}")
        block = apk_element_block(xml, "receiver", provider)
        if not block or "android:enabled" not in block or "=true" not in block.split("android:enabled", 1)[1].splitlines()[0]:
            fail(f"APK widget provider disabled: {label}")
        if "android.appwidget.action.APPWIDGET_UPDATE" not in block or "android.appwidget.provider" not in block or f"=@{wanted_id}" not in block:
            fail(f"APK widget provider intent/metadata mismatch: {label}")
    tree = _aab_xml.read_xml(str(aab), "base/manifest/AndroidManifest.xml")
    application = next((child for child in tree[2] if child[0] == "application"), None)
    if application is None:
        fail("AAB has no application")
    for size, label in WIDGETS.items():
        provider = f"app.livosphere.widgets.runtime.{size}ClockWidgetProvider"
        receivers = [node for node in application[2] if node[0] == "receiver" and node[1].get("name") == provider]
        if len(receivers) != 1 or receivers[0][1].get("enabled") != "true":
            fail(f"AAB widget provider missing/disabled: {label}")
        receiver = receivers[0]
        actions = [action[1].get("name") for item in receiver[2] if item[0] == "intent-filter" for action in item[2] if action[0] == "action"]
        metadata = [item[1] for item in receiver[2] if item[0] == "meta-data"]
        if "android.appwidget.action.APPWIDGET_UPDATE" not in actions or {"name": "android.appwidget.provider", "resource": f"@xml/clock_widget_{label}_info"} not in metadata:
            fail(f"AAB widget intent/metadata mismatch: {label}")
        with zipfile.ZipFile(aab) as archive:
            for entry in (f"base/res/xml/clock_widget_{label}_info.xml", f"base/res/xml-v31/clock_widget_{label}_info.xml"):
                if entry not in archive.namelist():
                    fail(f"AAB widget descriptor missing: {entry}")
                descriptor = _aab_xml.read_xml(str(aab), entry)
                if descriptor[0] != "appwidget-provider" or descriptor[1].get("configure") != "app.livosphere.widgets.ClockWidgetConfigurationActivity" or descriptor[1].get("initialLayout") != "@layout/clock_widget_needs_configuration":
                    fail(f"AAB widget descriptor invalid: {entry}")
    with zipfile.ZipFile(aab) as archive:
        if "base/res/layout/clock_widget_needs_configuration.xml" not in archive.namelist():
            fail("AAB widget configuration layout missing")
        table = archive.read("base/resources.pb")
    services = [node for node in application[2] if node[0] == "service" and any(child[0] == "meta-data" and child[1].get("name") == "android.service.wallpaper" for child in node[2])]
    expected_entries = {f"@xml/ls_{set_id.replace('-', '_')}_wallpaper_entrypoint" for set_id in SET_IDS}
    actual_entries = {child[1].get("resource") for service in services for child in service[2] if child[0] == "meta-data" and child[1].get("name") == "android.service.wallpaper"}
    if actual_entries != expected_entries or len(services) != 13:
        fail("AAB public wallpaper closure is not the approved 13 sets")
    with zipfile.ZipFile(aab) as archive:
        names = set(archive.namelist())
        for set_id in SET_IDS:
            token = f"ls_{set_id.replace('-', '_')}"
            if f"{token}_preview_wallpaper".encode() not in table or f"base/res/xml/{token}_wallpaper_entrypoint.xml" not in names:
                fail(f"AAB missing public set resource: {set_id}")
    for archive in (apk, aab):
        with zipfile.ZipFile(archive) as content:
            if content.testzip() is not None:
                fail(f"invalid archive: {archive}")


def apk_element_block(xml, tag, name):
    lines = xml.splitlines()
    for index, line in enumerate(lines):
        match = re.match(rf"^(\s*)E: {re.escape(tag)} \(", line)
        if not match:
            continue
        indent = len(match.group(1))
        end = index + 1
        while end < len(lines):
            child = re.match(r"^(\s*)E: ", lines[end])
            if child and len(child.group(1)) <= indent:
                break
            end += 1
        block = "\n".join(lines[index:end])
        if name in block:
            return block
    return None


def verify_signatures(cert, apk, aab):
    if not re.fullmatch(r"[0-9a-fA-F]{64}", cert):
        fail("owner certificate fingerprint must be SHA-256")
    try:
        apk_result = run([tool("apksigner"), "verify", "--print-certs", str(apk)], capture_output=True)
        aab_result = run(["jarsigner", "-verify", "-verbose", str(aab)], capture_output=True)
        aab_cert = run(["keytool", "-printcert", "-jarfile", str(aab)], capture_output=True)
    except (subprocess.CalledProcessError, OSError):
        fail("artifact signature or owner certificate mismatch")
    apk_fingerprints = {value.lower() for value in re.findall(
        r"(?m)^(?:Signer #\d+|V[0-9.]+ Signer:).*certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
        apk_result.stdout)}
    aab_fingerprints = {value.replace(":", "").lower() for value in re.findall(
        r"(?m)^\s*SHA256:\s*((?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2})\s*$",
        aab_cert.stdout)}
    if "jar verified." not in aab_result.stdout or apk_fingerprints != {cert.lower()} or \
            aab_fingerprints != {cert.lower()}:
        fail("artifact signature or owner certificate mismatch")


def previous_revisions(path, code, sets):
    if not path:
        return
    previous = json.loads(Path(path).read_text(encoding="utf-8"))
    if previous.get("profile") != "phone-v2":
        fail("previous manifest is not phone-v2")
    if code < previous.get("versionCode", 0):
        fail("versionCode decreased from previous manifest")
    old = {item["setId"]: item["setRevision"] for item in previous.get("sets", [])}
    for item in sets:
        if item["setId"] in old and item["setRevision"] < old[item["setId"]]:
            fail(f"setRevision decreased: {item['setId']}")


def build():
    env = os.environ
    needed = ("PRODUCT_RELEASE", "PHONE_VERSION_CODE", "PHONE_PREVIOUS_VERSION_CODE",
              "LIVOSPHERE_RELEASE_KEYSTORE", "LIVOSPHERE_RELEASE_STORE_PASSWORD",
              "LIVOSPHERE_RELEASE_KEY_ALIAS", "LIVOSPHERE_RELEASE_KEY_PASSWORD",
              "LIVOSPHERE_RELEASE_CERT_SHA256", "LIVOSPHERE_OWNER_KEY_CONFIRMED")
    missing = [name for name in needed if not env.get(name)]
    if missing:
        fail("missing owner input: " + ", ".join(missing))
    if env["LIVOSPHERE_OWNER_KEY_CONFIRMED"] != "yes":
        fail("permanent owner signing key has not been confirmed")
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:[-.][0-9A-Za-z.-]+)?", env["PRODUCT_RELEASE"]):
        fail("invalid PRODUCT_RELEASE")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", env["LIVOSPHERE_RELEASE_CERT_SHA256"]):
        fail("invalid owner certificate fingerprint")
    try:
        code = int(env["PHONE_VERSION_CODE"])
        previous = int(env["PHONE_PREVIOUS_VERSION_CODE"])
    except ValueError:
        fail("versionCode inputs must be integers")
    if previous < 0 or code <= previous or code < 1:
        fail("PHONE_VERSION_CODE must exceed previous published versionCode")
    if not Path(env["LIVOSPHERE_RELEASE_KEYSTORE"]).is_absolute():
        fail("keystore path must be absolute and outside checkout")
    key = Path(env["LIVOSPHERE_RELEASE_KEYSTORE"]).resolve()
    regular(key)
    if key == ROOT or ROOT in key.parents:
        fail("keystore must be outside checkout")
    source = clean_source()
    sets = public_sets()
    previous_revisions(env.get("LIVOSPHERE_PREVIOUS_PHONE_V2_MANIFEST"), code, sets)
    run_id = env.get("LIVOSPHERE_RUN_ID") or dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + f"-{os.getpid()}"
    if not re.fullmatch(r"[A-Za-z0-9._-]+", run_id):
        fail("invalid run id")
    evidence = Path(env.get("LIVOSPHERE_EVIDENCE_DIR", str(ROOT / ".local/evidence/phone-v2"))).resolve()
    target = evidence / run_id
    if target.exists():
        fail("refusing to overwrite existing run")
    target.mkdir(parents=True)
    (target / "artifacts").mkdir()
    log = target / "build.log"
    command = [str(ANDROID / "gradlew"), "--offline", "--console=plain", "--no-daemon", "--max-workers=2",
               "-Plivosphere.buildProfile=phone", f"-Plivosphere.productRelease={env['PRODUCT_RELEASE']}",
               f"-Plivosphere.phoneVersionCode={code}", ":hub:app:assembleRelease", ":hub:app:bundleRelease"]
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(command, cwd=ANDROID, stdout=output, stderr=subprocess.STDOUT, env=env)
    if result.returncode:
        fail(f"Gradle failed; see {log}")
    allowed_run = str(target.relative_to(ROOT)) if ROOT in target.parents else None
    if clean_source(allowed_run) != source or public_sets() != sets:
        fail("source commit or set manifests changed during build")
    apk = target / "artifacts/phone.apk"
    aab = target / "artifacts/phone.aab"
    shutil.copyfile(ANDROID / "hub/app/build/outputs/apk/release/app-release.apk", apk)
    shutil.copyfile(ANDROID / "hub/app/build/outputs/bundle/release/app-release.aab", aab)
    package_check(apk, aab, code, env["PRODUCT_RELEASE"])
    verify_signatures(env["LIVOSPHERE_RELEASE_CERT_SHA256"], apk, aab)
    manifest = {"schemaVersion": 3, "profile": "phone-v2", "runId": run_id,
                "createdAtUtc": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "sourceRevision": source, "productRelease": env["PRODUCT_RELEASE"], "versionCode": code,
                "previousPublishedVersionCode": previous, "sets": sets,
                "signing": {"status": "OWNER_CONFIRMED", "certSha256": env["LIVOSPHERE_RELEASE_CERT_SHA256"].lower()},
                "artifacts": [
                    {"id": "phone-apk", "type": "APK", "path": "artifacts/phone.apk", "sha256": sha(apk), "bytes": apk.stat().st_size},
                    {"id": "phone-aab", "type": "AAB", "path": "artifacts/phone.aab", "sha256": sha(aab), "bytes": aab.stat().st_size},
                ], "buildLog": {"path": "build.log", "sha256": sha(log)}}
    manifest_path = target / "release-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for path in (apk, aab, log, manifest_path):
        path.chmod(0o444)
    (target / "artifacts").chmod(0o555)
    print(f"phone-v2 candidate: {target}")
    print("readiness: NOT_READY (functional, physicalPerformance, battery, storeDelivery, ownerApproval UNKNOWN)")


def safe_file(base, relative):
    if not isinstance(relative, str) or not relative:
        fail("unsafe relative path: expected a non-empty string")
    if Path(relative).is_absolute() or ".." in Path(relative).parts:
        fail("unsafe relative path")
    original = base / relative
    if original.is_symlink():
        fail("symlink is not an immutable run file")
    path = original.resolve()
    if base != path and base not in path.parents:
        fail("path escapes run")
    regular(path)
    return path


def verify_attestation(index_path):
    public_name = os.environ.get("LIVOSPHERE_ATTESTATION_PUBLIC_KEY", "")
    expected = os.environ.get("LIVOSPHERE_ATTESTATION_PUBLIC_KEY_SHA256", "").lower()
    if not public_name or not Path(public_name).is_absolute() or not re.fullmatch(r"[0-9a-f]{64}", expected):
        fail("owner attestation public key and expected fingerprint are required for PASS evidence")
    public = Path(public_name).resolve()
    regular(public)
    if public == ROOT or ROOT in public.parents:
        fail("attestation public key must be outside checkout")
    signature = index_path.with_suffix(".sig")
    regular(signature)
    try:
        der = subprocess.run(["openssl", "pkey", "-pubin", "-in", str(public), "-outform", "DER"],
                             check=True, capture_output=True).stdout
        if hashlib.sha256(der).hexdigest() != expected:
            fail("owner attestation public key fingerprint mismatch")
        run(["openssl", "dgst", "-sha256", "-verify", str(public), "-signature", str(signature), str(index_path)], capture_output=True)
    except subprocess.CalledProcessError:
        fail("owner readiness attestation signature invalid")


def validate_raw_evidence(name, record, raw_path, source, version, revisions, manifest_sha, apk_sha, aab_sha):
    try:
        evidence = json.loads(raw_path.read_text(encoding="utf-8"),
                              parse_constant=lambda value: fail(f"non-finite {name} evidence: {value}"))
    except (UnicodeError, json.JSONDecodeError):
        fail(f"{name} PASS requires structured JSON evidence")
    artifact_sha = aab_sha if name == "storeDelivery" else apk_sha
    required = {"schemaVersion": 1, "protocol": record["protocol"], "sourceRevision": source,
                "versionCode": version, "setRevisions": revisions, "artifactSha256": artifact_sha,
                "verdict": "PASS"}
    if not isinstance(evidence, dict) or any(evidence.get(key) != value for key, value in required.items()):
        fail(f"{name} raw evidence does not bind candidate/protocol")
    if name == "functional":
        needed = {"wallpaperA_clockB_secondA", "update", "cancel", "reboot", "restore"}
        scenarios = evidence.get("scenarios")
        if not isinstance(scenarios, dict) or any(scenarios.get(scenario) != "PASS" for scenario in needed):
            fail("functional evidence lacks required scenarios")
    elif name == "physicalPerformance":
        values = evidence.get("measurements")
        bounds = {"fps": (30, None), "frameWithin34Pct": (95, 100), "frameP99Ms": (0, 50),
                  "warmStallMs": (0, 100), "responseP95Ms": (0, 100)}
        if not isinstance(values, dict) or any(
            not isinstance(values.get(key), (int, float)) or isinstance(values.get(key), bool)
            or not math.isfinite(values[key])
            or values[key] < low or (high is not None and values[key] > high)
            for key, (low, high) in bounds.items()
        ):
            fail("physical performance measurements do not meet protocol budgets")
    elif name == "battery":
        pairs = evidence.get("pairs")
        bounds = {"wallpaperNormalPct": 10, "wallpaperReducedPct": 5, "widgetsOnlyPct": 5,
                  "combinedNormalPct": 15, "combinedReducedPct": 10}
        if not isinstance(pairs, list) or len(pairs) < 3:
            fail("battery evidence requires three comparable pairs")
        for pair in pairs:
            if not isinstance(pair, dict) or any(
                not isinstance(pair.get(key), (int, float)) or isinstance(pair.get(key), bool)
                or not math.isfinite(pair[key])
                or pair[key] < 0 for key in bounds
            ) or not isinstance(pair.get("durationMinutes"), (int, float)) or isinstance(pair.get("durationMinutes"), bool) \
                    or not math.isfinite(pair["durationMinutes"]) or pair["durationMinutes"] <= 0 \
                    or not pair.get("brightness") or not pair.get("network") or not pair.get("baseline"):
                fail("battery pair is incomplete")
        if any(sum(pair[key] for pair in pairs) / len(pairs) > limit for key, limit in bounds.items()):
            fail("battery measurements exceed protocol budgets")
    elif name == "storeDelivery":
        if evidence.get("channel") != "RuStore" or not evidence.get("uploadReceiptId") or \
                any(evidence.get(key) is not True for key in ("installVerified", "updateVerified", "preferencesPreserved")):
            fail("RuStore upload/install/update evidence is incomplete")
    elif name == "ownerApproval":
        if evidence.get("decision") != "APPROVE" or evidence.get("manifestSha256") != manifest_sha or \
                evidence.get("apkSha256") != apk_sha or evidence.get("aabSha256") != aab_sha or \
                not evidence.get("approvedBy") or not evidence.get("approvalReference"):
            fail("owner decision does not bind exact candidate digests")


def validate(run_dir, gate=False):
    run_dir = run_dir.resolve()
    if gate and os.environ.get("LIVOSPHERE_OWNER_KEY_CONFIRMED") != "yes":
        fail("permanent owner signing key has not been confirmed")
    manifest_path = run_dir / "release-manifest.json"
    regular(manifest_path)
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 3 or data.get("profile") != "phone-v2" or data.get("runId") != run_dir.name:
        fail("invalid phone-v2 manifest identity")
    try:
        created_at = dt.datetime.strptime(data.get("createdAtUtc", ""), "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
    except (TypeError, ValueError):
        fail("candidate creation time is invalid")
    if created_at > dt.datetime.now(dt.timezone.utc):
        fail("candidate creation time is in the future")
    source = data.get("sourceRevision")
    if not isinstance(source, str) or not re.fullmatch(r"[0-9a-f]{40}", source):
        fail("candidate source is not one exact commit")
    run(["git", "-C", str(ROOT), "cat-file", "-e", source + "^{commit}"], capture_output=True)
    if gate:
        allowed = str(run_dir.relative_to(ROOT)) if ROOT in run_dir.parents else None
        if clean_source(allowed) != source:
            fail("current source commit differs from candidate")
    if data.get("signing", {}).get("status") != "OWNER_CONFIRMED":
        fail("candidate is not owner-signed")
    cert = data["signing"].get("certSha256", "")
    if cert != os.environ.get("LIVOSPHERE_RELEASE_CERT_SHA256", "").lower():
        fail("owner certificate fingerprint mismatch")
    if set(run_dir.glob("artifacts/*")) != {run_dir / "artifacts/phone.apk", run_dir / "artifacts/phone.aab"}:
        fail("run contains unexpected artifact")
    items = data.get("artifacts")
    if not isinstance(items, list) or [(i.get("id"), i.get("type"), i.get("path")) for i in items] != [
        ("phone-apk", "APK", "artifacts/phone.apk"), ("phone-aab", "AAB", "artifacts/phone.aab")]:
        fail("candidate requires exactly two phone artifacts")
    paths = [safe_file(run_dir, item["path"]) for item in items]
    for item, path in zip(items, paths):
        if item.get("sha256") != sha(path) or item.get("bytes") != path.stat().st_size:
            fail(f"artifact digest/size mismatch: {item['id']}")
    version = data.get("versionCode")
    previous_code = data.get("previousPublishedVersionCode")
    if not isinstance(version, int) or not isinstance(previous_code, int) or previous_code < 0 or version <= previous_code:
        fail("versionCode is not monotonic")
    package_check(*paths, version, data.get("productRelease"))
    verify_signatures(cert, *paths)
    expected_sets = public_sets()
    if data.get("sets") != expected_sets:
        fail("set revisions or manifest digests differ from source")
    if data.get("buildLog", {}).get("sha256") != sha(safe_file(run_dir, "build.log")):
        fail("build log digest mismatch")
    readiness_path = run_dir / "readiness/index.json"
    unknown = list(GATES)
    if readiness_path.exists():
        regular(readiness_path)
        readiness = json.loads(readiness_path.read_text(encoding="utf-8"))
        if readiness.get("schemaVersion") != 1 or readiness.get("runId") != run_dir.name or readiness.get("manifestSha256") != sha(manifest_path):
            fail("foreign readiness manifest digest")
        gates = readiness.get("gates")
        if not isinstance(gates, dict) or set(gates) != set(GATES):
            fail("readiness gates are incomplete")
        if any(isinstance(record, dict) and record.get("verdict") == "PASS" for record in gates.values()):
            verify_attestation(readiness_path)
        unknown = []
        revisions = {item["setId"]: item["setRevision"] for item in expected_sets}
        for name in GATES:
            record = gates[name]
            verdict = record.get("verdict") if isinstance(record, dict) else None
            if verdict != "PASS":
                if verdict not in ("UNKNOWN", "FAIL"):
                    fail(f"invalid verdict for {name}")
                unknown.append(name + "=" + verdict)
                continue
            expected_artifact = items[1] if name == "storeDelivery" else items[0]
            if record.get("artifactSha256") != expected_artifact["sha256"] or record.get("versionCode") != version:
                fail(f"foreign artifact digest: {name}")
            if record.get("setRevisions") != revisions:
                fail(f"foreign set revisions: {name}")
            if record.get("sourceRevision") != source or record.get("protocol") != "P14." + str(GATES.index(name) + 2):
                fail(f"evidence source/protocol mismatch: {name}")
            date_value = record.get("date")
            if not isinstance(date_value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date_value):
                fail(f"evidence date missing: {name}")
            try:
                evidence_date = dt.date.fromisoformat(date_value)
            except ValueError:
                fail(f"evidence date invalid: {name}")
            if evidence_date < created_at.date() or evidence_date > dt.datetime.now(dt.timezone.utc).date():
                fail(f"evidence date outside candidate window: {name}")
            if name in ("functional", "physicalPerformance", "battery"):
                for field in ("device", "osBuild", "launcher", "host", "scenario"):
                    if not isinstance(record.get(field), str) or not record[field].strip() or record[field] == "UNKNOWN":
                        fail(f"evidence {field} missing: {name}")
            if name == "storeDelivery" and record.get("channel") != "RuStore":
                fail("store evidence must identify RuStore")
            if name == "ownerApproval" and record.get("decision") != "APPROVE":
                fail("owner approval must be explicit")
            raw = record.get("raw")
            if not isinstance(raw, dict):
                fail(f"raw evidence missing: {name}")
            raw_path = safe_file(run_dir / "readiness", raw.get("path"))
            if raw.get("sha256") != sha(raw_path):
                fail(f"raw evidence digest mismatch: {name}")
            validate_raw_evidence(name, record, raw_path, source, version, revisions,
                                  sha(manifest_path), items[0]["sha256"], items[1]["sha256"])
    if unknown:
        print("readiness: NOT_READY; " + ", ".join(unknown))
        if gate:
            fail("publication gate has unresolved evidence")
    else:
        print("readiness: LOCAL_GATE_PASS; direct owner authorization is still required for publication")


def main():
    load_local_env()
    if len(sys.argv) == 2 and sys.argv[1] == "build":
        build()
    elif len(sys.argv) in (2, 3) and sys.argv[1] in ("validate", "gate"):
        run_dir = sys.argv[2] if len(sys.argv) == 3 else os.environ.get("LIVOSPHERE_RELEASE_RUN_DIR")
        if not run_dir:
            fail("set LIVOSPHERE_RELEASE_RUN_DIR in .env or pass RUN_DIR")
        validate(Path(run_dir), sys.argv[1] == "gate")
    else:
        fail("usage: phone-v2-release.py build|validate RUN_DIR|gate RUN_DIR")


if __name__ == "__main__":
    main()
