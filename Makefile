SHELL := /bin/sh

ANDROID_DIR := android
GRADLE = cd $(ANDROID_DIR) && $(if $(strip $(LIVOSPHERE_JAVA_HOME)),JAVA_HOME="$(LIVOSPHERE_JAVA_HOME)",) ./gradlew --console=plain --no-daemon --max-workers=2 $(if $(strip $(LIVOSPHERE_JAVA_HOME)),"-Dorg.gradle.java.home=$(LIVOSPHERE_JAVA_HOME)",)
PHONE_GRADLE = $(GRADLE) -Plivosphere.buildProfile=phone
LEGACY_GRADLE = $(GRADLE) -Plivosphere.buildProfile=legacy
DOCTOR_SCRIPT ?= ./android/scripts/doctor.sh
VERIFY_ARTIFACTS_SCRIPT ?= ./android/scripts/verify-artifacts.sh
WFF_PREFLIGHT_SCRIPT ?= ./android/scripts/run-wff-sp02-preflight.sh

ifeq ($(shell uname -s),Darwin)
HOMEBREW_JAVA_17 := $(shell brew --prefix openjdk@17 2>/dev/null)/libexec/openjdk.jdk/Contents/Home
LIVOSPHERE_JAVA_HOME ?= $(if $(wildcard $(HOMEBREW_JAVA_17)),$(HOMEBREW_JAVA_17),$(shell /usr/libexec/java_home -v 17 2>/dev/null))
ANDROID_SDK_ROOT ?= $(if $(ANDROID_HOME),$(ANDROID_HOME),$(HOME)/Library/Android/sdk)
else
LIVOSPHERE_JAVA_HOME ?= $(JAVA_HOME)
ANDROID_SDK_ROOT ?= $(ANDROID_HOME)
endif

ifneq ($(strip $(LIVOSPHERE_JAVA_HOME)),)
export JAVA_HOME := $(LIVOSPHERE_JAVA_HOME)
endif
export ANDROID_SDK_ROOT

.PHONY: doctor assets-check phone watchfaces check device-check offline-smoke verify validate-wff benchmark benchmark-sp06 protocol-sp07 wff-sp02-preflight protocol-sp02 evidence-validator-check release-pipeline-test phone-v2-pipeline-test contour-wff-assets-test candidate signed-candidate phone-v2-candidate phone-v2-validate release legacy-release

doctor:
	$(DOCTOR_SCRIPT)
	$(PHONE_GRADLE) doctor

assets-check:
	$(PHONE_GRADLE) assetsCheck

phone: doctor assets-check
	$(PHONE_GRADLE) :hub:app:assembleDebug

watchfaces: doctor
	$(LEGACY_GRADLE) assetsCheck :watchfaces:contour-wff:bundleDebug

check: doctor
	$(PHONE_GRADLE) check

device-check: doctor
	$(PHONE_GRADLE) :hub:app:connectedDebugAndroidTest

offline-smoke:
	$(DOCTOR_SCRIPT)
	$(PHONE_GRADLE) --offline doctor :hub:app:assembleDebug check

verify: phone check offline-smoke
	$(VERIFY_ARTIFACTS_SCRIPT) phone

validate-wff: wff-sp02-preflight

benchmark: benchmark-sp06

candidate:
	PRODUCT_RELEASE="$${PRODUCT_RELEASE:-0.1.0-dev}" \
	PHONE_VERSION_CODE="$${PHONE_VERSION_CODE:-1}" \
	WATCH_VERSION_CODE="$${WATCH_VERSION_CODE:-1}" \
	LIVOSPHERE_EVIDENCE_DIR="$${LIVOSPHERE_EVIDENCE_DIR:-_bmad-output/implementation-artifacts/evidence/release-candidate}" \
	./android/scripts/build-release-candidate.sh candidate

signed-candidate:
	PRODUCT_RELEASE="$${PRODUCT_RELEASE:-}" \
	PHONE_VERSION_CODE="$${PHONE_VERSION_CODE:-}" \
	WATCH_VERSION_CODE="$${WATCH_VERSION_CODE:-}" \
	LIVOSPHERE_EVIDENCE_DIR="$${LIVOSPHERE_EVIDENCE_DIR:-_bmad-output/implementation-artifacts/evidence/release-candidate}" \
	./android/scripts/build-release-candidate.sh signed-candidate

release:
	@[ -n "$${LIVOSPHERE_RELEASE_RUN_DIR:-}" ] || { echo "phone-v2: NOT_READY — set LIVOSPHERE_RELEASE_RUN_DIR" >&2; exit 64; }
	@python3 android/scripts/phone-v2-release.py gate "$$LIVOSPHERE_RELEASE_RUN_DIR"

phone-v2-candidate:
	@python3 android/scripts/phone-v2-release.py build

phone-v2-validate:
	@[ -n "$${LIVOSPHERE_RELEASE_RUN_DIR:-}" ] || { echo "phone-v2: NOT_READY — set LIVOSPHERE_RELEASE_RUN_DIR" >&2; exit 64; }
	@python3 android/scripts/phone-v2-release.py validate "$$LIVOSPHERE_RELEASE_RUN_DIR"

legacy-release:
	LIVOSPHERE_RELEASE_RUN_DIR="$${LIVOSPHERE_RELEASE_RUN_DIR:-}" \
	LIVOSPHERE_RELEASE_READINESS_INDEX="$${LIVOSPHERE_RELEASE_READINESS_INDEX:-}" \
	./android/scripts/promote-release.sh

benchmark-sp06:
	$(GRADLE) :quality:macrobenchmark:verifySp06Setup
	./android/scripts/validate-epic-3-evidence.sh _bmad-output/implementation-artifacts/evidence/story-3-7

protocol-sp07:
	./android/scripts/validate-epic-3-evidence.sh _bmad-output/implementation-artifacts/evidence/story-3-8

wff-sp02-preflight:
	$(WFF_PREFLIGHT_SCRIPT)

protocol-sp02:
	./android/scripts/validate-sp02-evidence.sh _bmad-output/implementation-artifacts/evidence/story-4-1
	./android/scripts/test-validate-sp02-evidence.sh

evidence-validator-check:
	./android/scripts/test-validate-epic-3-evidence.sh
	./android/scripts/test-validate-sp02-evidence.sh
	./android/scripts/test-run-wff-sp02-preflight.sh

release-pipeline-test: doctor
	$(LEGACY_GRADLE) :hub:app:assembleDebug :hub:app:bundleDebug :watchfaces:contour-wff:assembleDebug :watchfaces:contour-wff:bundleDebug
	./android/scripts/test-release-pipeline.sh

phone-v2-pipeline-test:
	@unset LIVOSPHERE_RELEASE_KEYSTORE LIVOSPHERE_RELEASE_STORE_PASSWORD LIVOSPHERE_RELEASE_KEY_ALIAS LIVOSPHERE_RELEASE_KEY_PASSWORD; $(PHONE_GRADLE) --offline :hub:app:assembleRelease :hub:app:bundleRelease
	python3 android/scripts/test_phone_v2_release.py

contour-wff-assets-test:
	./android/scripts/test-contour-wff-assets.sh
