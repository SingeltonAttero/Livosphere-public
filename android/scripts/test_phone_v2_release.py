#!/usr/bin/env python3
"""Signed local fixtures for the phone-v2 candidate validator."""

import importlib.util
import hashlib
import datetime as dt
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from unittest import mock

SCRIPT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("phone_v2_release", SCRIPT / "phone-v2-release.py")
phone = importlib.util.module_from_spec(spec)
spec.loader.exec_module(phone)


def command(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs)


def expect_failure(action, needle):
    try:
        action()
    except SystemExit as error:
        if needle not in str(error):
            raise AssertionError(f"expected {needle!r}, got {error}") from error
    else:
        raise AssertionError(f"expected failure: {needle}")


def main():
    os.environ.setdefault("ANDROID_SDK_ROOT", str(Path.home() / "Library/Android/sdk"))
    assert len(phone.public_sets()) == 13
    with mock.patch.dict(os.environ, {}, clear=True):
        expect_failure(phone.build, "missing owner input")
    print("PASS: NO_KEY refuses before build")

    with tempfile.TemporaryDirectory(prefix="phone-v2-test-") as temp_name:
        temp = Path(temp_name)
        env_file = temp / ".env"
        env_file.write_text("LIVOSPHERE_RELEASE_STORE_PASSWORD=literal$#= value\nPHONE_VERSION_CODE=1\n")
        env_file.chmod(0o600)
        with mock.patch.dict(os.environ, {}, clear=True):
            phone.load_local_env(env_file)
            assert os.environ["LIVOSPHERE_RELEASE_STORE_PASSWORD"] == "literal$#= value"
            assert os.environ["PHONE_VERSION_CODE"] == "1"
        env_file.chmod(0o644)
        expect_failure(lambda: phone.load_local_env(env_file), ".env must be readable only by its owner")
        env_file.chmod(0o600)
        with mock.patch.dict(os.environ, {"PHONE_VERSION_CODE": "2"}, clear=True):
            expect_failure(lambda: phone.load_local_env(env_file), ".env conflicts with environment")
        print("PASS: local .env literal values, permissions and CI conflict")

        repo = temp / "repo"
        repo.mkdir()
        command(["git", "init", "-q"], cwd=repo)
        command(["git", "config", "user.email", "test@example.invalid"], cwd=repo)
        command(["git", "config", "user.name", "Phone fixture"], cwd=repo)
        (repo / "tracked").write_text("original\n")
        command(["git", "add", "."], cwd=repo)
        command(["git", "commit", "-qm", "fixture"], cwd=repo)
        (repo / "tracked").write_text("dirty\n")
        key = temp / "test-only.jks"
        key.write_bytes(b"test key placeholder")
        env = {"PRODUCT_RELEASE": "1.0.0", "PHONE_VERSION_CODE": "2", "PHONE_PREVIOUS_VERSION_CODE": "1",
               "LIVOSPHERE_RELEASE_KEYSTORE": str(key), "LIVOSPHERE_RELEASE_STORE_PASSWORD": "test",
               "LIVOSPHERE_RELEASE_KEY_ALIAS": "test", "LIVOSPHERE_RELEASE_KEY_PASSWORD": "test",
               "LIVOSPHERE_RELEASE_CERT_SHA256": "a" * 64, "LIVOSPHERE_OWNER_KEY_CONFIRMED": "yes"}
        with mock.patch.object(phone, "ROOT", repo), mock.patch.dict(os.environ, env, clear=True):
            expect_failure(phone.build, "source tree is dirty")
        print("PASS: DIRTY_OR_TAMPERED refuses dirty source")

        apk_source = phone.ANDROID / "hub/app/build/outputs/apk/release/app-release-unsigned.apk"
        aab_source = phone.ANDROID / "hub/app/build/outputs/bundle/release/app-release.aab"
        if not apk_source.is_file() or not aab_source.is_file():
            raise SystemExit("phone-v2 fixture requires assembleRelease and bundleRelease")
        run_dir = temp / "fixture-run"
        artifacts = run_dir / "artifacts"
        artifacts.mkdir(parents=True)
        apk, aab = artifacts / "phone.apk", artifacts / "phone.aab"
        shutil.copy2(apk_source, apk)
        shutil.copy2(aab_source, aab)
        key.unlink()
        command(["keytool", "-genkeypair", "-noprompt", "-keystore", str(key), "-storepass", "testpass",
                 "-keypass", "testpass", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                 "-validity", "365", "-dname", "CN=TEST ONLY, O=Local Fixture, C=RU"])
        cert = next(line.split(":", 1)[1].replace(":", "").strip().lower()
                    for line in command(["keytool", "-list", "-v", "-keystore", str(key),
                                         "-storepass", "testpass", "-alias", "test"]).stdout.splitlines() if "SHA256:" in line)
        command([phone.tool("apksigner"), "sign", "--ks", str(key), "--ks-pass", "pass:testpass",
                 "--key-pass", "pass:testpass", "--ks-key-alias", "test", str(apk)])
        apk.with_name(apk.name + ".idsig").unlink(missing_ok=True)
        command(["jarsigner", "-keystore", str(key), "-storepass", "testpass", "-keypass", "testpass", str(aab), "test"])
        phone.verify_signatures(cert, apk, aab)
        expect_failure(lambda: phone.verify_signatures("0" * 64, apk, aab),
                       "artifact signature or owner certificate mismatch")
        print("PASS: signed APK/AAB match owner certificate; foreign certificate rejected")
        package, code, product = phone.apk_meta(apk)
        assert package == "app.livosphere"
        phone.package_check(apk, aab, code, product)
        missing_widget = temp / "missing-widget.aab"
        shutil.copy2(aab, missing_widget)
        command(["zip", "-q", "-d", str(missing_widget), "base/res/xml/clock_widget_small_info.xml"])
        expect_failure(lambda: phone.package_check(apk, missing_widget, code, product), "AAB widget descriptor missing")
        missing_set = temp / "missing-set.aab"
        shutil.copy2(aab, missing_set)
        command(["zip", "-q", "-d", str(missing_set), "base/res/xml/ls_night_sakura_wallpaper_entrypoint.xml"])
        expect_failure(lambda: phone.package_check(apk, missing_set, code, product), "AAB missing public set resource")
        print("PASS: AAB widget descriptor and 13-set closure tampering refused")
        build_repo = temp / "build-repo"
        build_android = build_repo / "android"
        (build_android / "hub/app/build/outputs/apk/release").mkdir(parents=True)
        (build_android / "hub/app/build/outputs/bundle/release").mkdir(parents=True)
        shutil.copy2(apk, build_android / "hub/app/build/outputs/apk/release/app-release.apk")
        shutil.copy2(aab, build_android / "hub/app/build/outputs/bundle/release/app-release.aab")
        original_settings = (phone.ANDROID / "gradle.properties").read_text()
        (build_android / "gradle.properties").write_text(original_settings)
        for original in (phone.ANDROID / "sets").glob("*/manifest/set.properties"):
            target = build_repo / original.relative_to(phone.ROOT)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(original, target)
        gradle = build_android / "gradlew"
        gradle.write_text("#!/bin/sh\nif [ \"${MUTATE_SOURCE:-}\" = 1 ]; then echo changed >> gradle.properties; fi\nexit 0\n")
        gradle.chmod(0o755)
        command(["git", "init", "-q"], cwd=build_repo)
        command(["git", "config", "user.email", "test@example.invalid"], cwd=build_repo)
        command(["git", "config", "user.name", "Phone fixture"], cwd=build_repo)
        command(["git", "add", "."], cwd=build_repo)
        command(["git", "commit", "-qm", "clean source fixture"], cwd=build_repo)
        build_env = {"PRODUCT_RELEASE": product, "PHONE_VERSION_CODE": str(code),
                     "PHONE_PREVIOUS_VERSION_CODE": str(code - 1), "LIVOSPHERE_RELEASE_KEYSTORE": str(key),
                     "LIVOSPHERE_RELEASE_STORE_PASSWORD": "testpass", "LIVOSPHERE_RELEASE_KEY_ALIAS": "test",
                     "LIVOSPHERE_RELEASE_KEY_PASSWORD": "testpass", "LIVOSPHERE_RELEASE_CERT_SHA256": cert,
                     "LIVOSPHERE_OWNER_KEY_CONFIRMED": "yes", "LIVOSPHERE_RUN_ID": "fixture-build",
                     "LIVOSPHERE_EVIDENCE_DIR": str(temp / "build-evidence")}
        with mock.patch.object(phone, "ROOT", build_repo), mock.patch.object(phone, "ANDROID", build_android), \
                mock.patch.dict(os.environ, build_env):
            phone.build()
            built = temp / "build-evidence/fixture-build"
            built_manifest = json.loads((built / "release-manifest.json").read_text())
            assert len(built_manifest["artifacts"]) == 2
            assert built_manifest["sourceRevision"] == command(["git", "rev-parse", "HEAD"], cwd=build_repo).stdout.strip()
            expect_failure(phone.build, "refusing to overwrite existing run")
            previous_manifest = temp / "previous-phone-v2.json"
            prior = dict(built_manifest)
            prior["versionCode"] = code - 1
            prior["sets"] = [dict(item) for item in built_manifest["sets"]]
            prior["sets"][0]["setRevision"] += 1
            previous_manifest.write_text(json.dumps(prior))
            with mock.patch.dict(os.environ, {"LIVOSPHERE_PREVIOUS_PHONE_V2_MANIFEST": str(previous_manifest),
                                              "LIVOSPHERE_RUN_ID": "revision-regression"}):
                expect_failure(phone.build, "setRevision decreased")
            print("PASS: previous-manifest revision regression rejected")
            with mock.patch.dict(os.environ, {"MUTATE_SOURCE": "1", "LIVOSPHERE_RUN_ID": "source-mutated"}):
                expect_failure(phone.build, "source tree is dirty")
            command(["git", "checkout", "--", "android/gradle.properties"], cwd=build_repo)
            print("PASS: source change during Gradle rejected before artifact copy")

            attestation_key = temp / "owner-attestation-test.pem"
            attestation_public = temp / "owner-attestation-test.pub.pem"
            command(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                     "-out", str(attestation_key)])
            command(["openssl", "pkey", "-in", str(attestation_key), "-pubout", "-out", str(attestation_public)])
            der = subprocess.run(["openssl", "pkey", "-pubin", "-in", str(attestation_public), "-outform", "DER"],
                                 check=True, capture_output=True).stdout
            attestation_env = {"LIVOSPHERE_ATTESTATION_PUBLIC_KEY": str(attestation_public),
                               "LIVOSPHERE_ATTESTATION_PUBLIC_KEY_SHA256": hashlib.sha256(der).hexdigest()}
            read_dir = built / "readiness"
            read_dir.mkdir()
            built_manifest_path = built / "release-manifest.json"
            revisions = {item["setId"]: item["setRevision"] for item in built_manifest["sets"]}
            apk_sha = built_manifest["artifacts"][0]["sha256"]
            aab_sha = built_manifest["artifacts"][1]["sha256"]
            source_sha = built_manifest["sourceRevision"]
            candidate_date = built_manifest["createdAtUtc"][:10]
            gates = {}
            for index, name in enumerate(phone.GATES, start=2):
                protocol = f"P14.{index}"
                artifact_sha = aab_sha if name == "storeDelivery" else apk_sha
                raw_data = {"schemaVersion": 1, "protocol": protocol, "sourceRevision": source_sha,
                            "versionCode": code, "setRevisions": revisions, "artifactSha256": artifact_sha,
                            "verdict": "PASS"}
                if name == "functional":
                    raw_data["scenarios"] = {item: "PASS" for item in
                                             ("wallpaperA_clockB_secondA", "update", "cancel", "reboot", "restore")}
                elif name == "physicalPerformance":
                    raw_data["measurements"] = {"fps": 30, "frameWithin34Pct": 95, "frameP99Ms": 50,
                                                "warmStallMs": 100, "responseP95Ms": 100}
                elif name == "battery":
                    raw_data["pairs"] = [{"wallpaperNormalPct": 10, "wallpaperReducedPct": 5,
                                          "widgetsOnlyPct": 5, "combinedNormalPct": 15,
                                          "combinedReducedPct": 10, "durationMinutes": 60,
                                          "brightness": "50%", "network": "offline", "baseline": "static"} for _ in range(3)]
                elif name == "storeDelivery":
                    raw_data.update(channel="RuStore", uploadReceiptId="fixture-only",
                                    installVerified=True, updateVerified=True, preferencesPreserved=True)
                elif name == "ownerApproval":
                    raw_data.update(decision="APPROVE", manifestSha256=phone.sha(built_manifest_path),
                                    apkSha256=apk_sha, aabSha256=aab_sha, approvedBy="TEST ONLY",
                                    approvalReference="fixture-only")
                raw_path = read_dir / f"{name}.json"
                raw_path.write_text(json.dumps(raw_data))
                record = {"verdict": "PASS", "protocol": protocol, "sourceRevision": source_sha,
                          "versionCode": code, "setRevisions": revisions, "artifactSha256": artifact_sha,
                          "date": candidate_date, "raw": {"path": raw_path.name, "sha256": phone.sha(raw_path)}}
                if name in ("functional", "physicalPerformance", "battery"):
                    record.update(device="Test device", osBuild="Test OS", launcher="Test launcher",
                                  host="Test host", scenario="Fixture only")
                if name == "storeDelivery":
                    record["channel"] = "RuStore"
                if name == "ownerApproval":
                    record["decision"] = "APPROVE"
                gates[name] = record
            readiness_index = read_dir / "index.json"
            readiness = {"schemaVersion": 1, "runId": built.name,
                         "manifestSha256": phone.sha(built_manifest_path), "gates": gates}
            def sign_readiness():
                readiness_index.write_text(json.dumps(readiness, indent=2) + "\n")
                command(["openssl", "dgst", "-sha256", "-sign", str(attestation_key),
                         "-out", str(read_dir / "index.sig"), str(readiness_index)])
            sign_readiness()
            with mock.patch.dict(os.environ, attestation_env):
                phone.validate(built, gate=True)
                print("PASS: full structured, signed phone-v2 local gate fixture")
                readiness["gates"]["functional"]["date"] = "2026-09-28"
                readiness_index.write_text(json.dumps(readiness, indent=2) + "\n")
                expect_failure(lambda: phone.validate(built, gate=True), "attestation signature invalid")
                sign_readiness()
                expect_failure(lambda: phone.validate(built, gate=True), "evidence date outside candidate window")
                readiness["gates"]["functional"]["date"] = "2026-02-30"
                sign_readiness()
                expect_failure(lambda: phone.validate(built, gate=True), "evidence date invalid")
                readiness["gates"]["functional"]["date"] = (dt.date.fromisoformat(candidate_date) - dt.timedelta(days=1)).isoformat()
                sign_readiness()
                expect_failure(lambda: phone.validate(built, gate=True), "evidence date outside candidate window")
                readiness["gates"]["functional"]["date"] = candidate_date
                sign_readiness()
                original_raw_path = readiness["gates"]["functional"]["raw"]["path"]
                readiness["gates"]["functional"]["raw"]["path"] = None
                sign_readiness()
                expect_failure(lambda: phone.validate(built, gate=True), "expected a non-empty string")
                readiness["gates"]["functional"]["raw"]["path"] = original_raw_path
                sign_readiness()
                raw_path = read_dir / "physicalPerformance.json"
                original_raw = raw_path.read_text()
                raw_path.write_text("arbitrary raw text")
                readiness["gates"]["physicalPerformance"]["raw"]["sha256"] = phone.sha(raw_path)
                sign_readiness()
                expect_failure(lambda: phone.validate(built, gate=True), "structured JSON evidence")
                raw_path.write_text(original_raw)
                readiness["gates"]["physicalPerformance"]["raw"]["sha256"] = phone.sha(raw_path)
                owner_path = read_dir / "ownerApproval.json"
                owner_raw = json.loads(owner_path.read_text())
                owner_raw["apkSha256"] = "0" * 64
                owner_path.write_text(json.dumps(owner_raw))
                readiness["gates"]["ownerApproval"]["raw"]["sha256"] = phone.sha(owner_path)
                sign_readiness()
                expect_failure(lambda: phone.validate(built, gate=True), "owner decision does not bind")
                print("PASS: malformed signed PASS and foreign owner digest rejected")
        print("PASS: clean signed build creates one immutable two-artifact run")
        source = command(["git", "-C", str(phone.ROOT), "rev-parse", "HEAD"]).stdout.strip()
        log = run_dir / "build.log"
        log.write_text("local fixture\n")
        manifest = {"schemaVersion": 3, "profile": "phone-v2", "runId": run_dir.name,
                    "createdAtUtc": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "sourceRevision": source,
                    "productRelease": product, "versionCode": code, "previousPublishedVersionCode": code - 1,
                    "sets": phone.public_sets(), "signing": {"status": "OWNER_CONFIRMED", "certSha256": cert},
                    "artifacts": [{"id": identity, "type": kind, "path": f"artifacts/{path.name}",
                                   "sha256": phone.sha(path), "bytes": path.stat().st_size}
                                  for identity, kind, path in (("phone-apk", "APK", apk), ("phone-aab", "AAB", aab))],
                    "buildLog": {"path": "build.log", "sha256": phone.sha(log)}}
        manifest_path = run_dir / "release-manifest.json"
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
        with mock.patch.dict(os.environ, {"LIVOSPHERE_RELEASE_CERT_SHA256": cert}):
            phone.validate(run_dir)
            print("PASS: SIGNED_PHONE two signed artifacts, providers, resources, 13 revisions, no WFF")
            expect_failure(lambda: phone.validate(run_dir, gate=True), "permanent owner signing key has not been confirmed")
            print("PASS: gate refuses without explicit owner key confirmation")
            readiness_dir = run_dir / "readiness"
            readiness_dir.mkdir()
            readiness = {"schemaVersion": 1, "runId": run_dir.name, "manifestSha256": phone.sha(manifest_path),
                         "gates": {name: {"verdict": "UNKNOWN"} for name in phone.GATES}}
            readiness_path = readiness_dir / "index.json"
            readiness_path.write_text(json.dumps(readiness))
            phone.validate(run_dir)
            with mock.patch.dict(os.environ, {"LIVOSPHERE_OWNER_KEY_CONFIRMED": "yes"}):
                try:
                    phone.validate(run_dir, gate=True)
                except SystemExit as error:
                    assert "source tree is dirty" in str(error) or "unresolved evidence" in str(error)
                else:
                    raise AssertionError("UNKNOWN gates passed publication gate")
            print("PASS: UNKNOWN_GATES keep candidate NOT_READY")
            readiness["manifestSha256"] = "0" * 64
            readiness_path.write_text(json.dumps(readiness))
            expect_failure(lambda: phone.validate(run_dir), "foreign readiness manifest digest")
            readiness["manifestSha256"] = phone.sha(manifest_path)
            readiness_path.write_text(json.dumps(readiness))
            with apk.open("ab") as stream:
                stream.write(b"tamper")
            expect_failure(lambda: phone.validate(run_dir), "artifact digest/size mismatch")
            print("PASS: foreign digest and tampered artifact refusal")


if __name__ == "__main__":
    main()
