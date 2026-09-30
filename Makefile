SHELL := /bin/sh
ANDROID_ENV := ./scripts/android-env.sh
GRADLE := $(ANDROID_ENV) ./scripts/gradle.sh
.PHONY: help doctor phone assets-check check scripts-check verify phone-install device-check offline-smoke benchmark-build release-candidate release-check candidate-install release-test

help:
	@printf '%s\n' 'make doctor          Check JDK and Android SDK' 'make phone           Build debug APK' 'make check           Unit tests, lint and module checks' 'make scripts-check   Test repository tools without a device' 'make verify          Build, tests and APK checks' 'make phone-install   Install and launch debug APK (PHONE_SERIAL=serial)' 'make device-check    Instrumentation tests on one connected device' 'make offline-smoke   Build and test using cached dependencies' 'make benchmark-build Build profileable measurement APK' 'make release-test    Test candidate tooling with a temporary key'

doctor:
	$(ANDROID_ENV) ./scripts/doctor.sh
	$(GRADLE) doctor

phone:
	$(ANDROID_ENV) ./scripts/doctor.sh
	$(GRADLE) doctor assetsCheck :hub:app:assembleDebug

assets-check:
	$(GRADLE) assetsCheck

check:
	$(ANDROID_ENV) ./scripts/doctor.sh
	$(GRADLE) check

scripts-check:
	python3 scripts/test_android_tools.py
	python3 scripts/test_install_phone.py

verify: phone check scripts-check
	$(ANDROID_ENV) ./scripts/verify-artifacts.sh

phone-install:
	$(ANDROID_ENV) python3 scripts/install-debug.py $(if $(strip $(PHONE_SERIAL)),--serial "$(PHONE_SERIAL)",)

device-check:
	$(GRADLE) :hub:app:connectedDebugAndroidTest

offline-smoke:
	$(ANDROID_ENV) ./scripts/doctor.sh
	$(GRADLE) --offline doctor assetsCheck :hub:app:assembleDebug check

benchmark-build:
	$(GRADLE) :quality:macrobenchmark:verifySp06Setup

release-candidate:
	$(ANDROID_ENV) python3 scripts/phone-v2-release.py build

release-check:
	$(ANDROID_ENV) python3 scripts/phone-v2-release.py gate $(if $(strip $(PHONE_RUN_DIR)),"$(PHONE_RUN_DIR)",)

candidate-install:
	$(ANDROID_ENV) python3 scripts/install-phone.py $(if $(strip $(PHONE_RUN_DIR)),--run-dir "$(PHONE_RUN_DIR)",) $(if $(strip $(PHONE_SERIAL)),--serial "$(PHONE_SERIAL)",)

release-test:
	@unset LIVOSPHERE_RELEASE_KEYSTORE LIVOSPHERE_RELEASE_STORE_PASSWORD LIVOSPHERE_RELEASE_KEY_ALIAS LIVOSPHERE_RELEASE_KEY_PASSWORD; $(GRADLE) :hub:app:assembleRelease :hub:app:bundleRelease
	$(ANDROID_ENV) python3 scripts/test_phone_v2_release.py
