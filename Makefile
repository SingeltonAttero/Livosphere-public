SHELL := /bin/sh

ANDROID_DIR := android
GRADLE = cd $(ANDROID_DIR) && $(if $(strip $(LIVOSPHERE_JAVA_HOME)),JAVA_HOME="$(LIVOSPHERE_JAVA_HOME)",) ./gradlew --console=plain --no-daemon --max-workers=2 $(if $(strip $(LIVOSPHERE_JAVA_HOME)),"-Dorg.gradle.java.home=$(LIVOSPHERE_JAVA_HOME)",)
PHONE_GRADLE = $(GRADLE) -Plivosphere.buildProfile=phone
DOCTOR_SCRIPT ?= ./android/scripts/doctor.sh
VERIFY_ARTIFACTS_SCRIPT ?= ./android/scripts/verify-artifacts.sh
SP06_EVIDENCE_DIR ?= .local/evidence/sp06
SP07_EVIDENCE_DIR ?= .local/evidence/sp07

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
export PHONE_RUN_DIR PHONE_SERIAL

.PHONY: doctor assets-check phone check device-check offline-smoke verify benchmark benchmark-sp06 protocol-sp07 evidence-validator-check phone-v2-pipeline-test phone-v2-candidate phone-v2-validate phone-install release

doctor:
	$(DOCTOR_SCRIPT)
	$(PHONE_GRADLE) doctor

assets-check:
	$(PHONE_GRADLE) assetsCheck

phone: doctor assets-check
	$(PHONE_GRADLE) :hub:app:assembleDebug

check: doctor
	$(PHONE_GRADLE) check

device-check: doctor
	$(PHONE_GRADLE) :hub:app:connectedDebugAndroidTest

offline-smoke:
	$(DOCTOR_SCRIPT)
	$(PHONE_GRADLE) --offline doctor :hub:app:assembleDebug check

verify: phone check offline-smoke
	$(VERIFY_ARTIFACTS_SCRIPT) phone

benchmark: benchmark-sp06

release:
	@python3 android/scripts/phone-v2-release.py gate

phone-v2-candidate:
	@python3 android/scripts/phone-v2-release.py build

phone-v2-validate:
	@python3 android/scripts/phone-v2-release.py validate

phone-install:
	@python3 android/scripts/install-phone.py

benchmark-sp06:
	$(GRADLE) :quality:macrobenchmark:verifySp06Setup
	./android/scripts/validate-epic-3-evidence.sh "$(SP06_EVIDENCE_DIR)"

protocol-sp07:
	./android/scripts/validate-epic-3-evidence.sh "$(SP07_EVIDENCE_DIR)"

evidence-validator-check:
	./android/scripts/test-validate-epic-3-evidence.sh

phone-v2-pipeline-test:
	@unset LIVOSPHERE_RELEASE_KEYSTORE LIVOSPHERE_RELEASE_STORE_PASSWORD LIVOSPHERE_RELEASE_KEY_ALIAS LIVOSPHERE_RELEASE_KEY_PASSWORD; $(PHONE_GRADLE) --offline :hub:app:assembleRelease :hub:app:bundleRelease
	python3 android/scripts/test_phone_v2_release.py
