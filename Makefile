SHELL := /bin/sh

ANDROID_DIR := android
GRADLE = cd $(ANDROID_DIR) && $(if $(strip $(LIVOSPHERE_JAVA_HOME)),JAVA_HOME="$(LIVOSPHERE_JAVA_HOME)",) ./gradlew --console=plain --no-daemon $(if $(strip $(LIVOSPHERE_JAVA_HOME)),"-Dorg.gradle.java.home=$(LIVOSPHERE_JAVA_HOME)",)

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

.PHONY: doctor assets-check phone watchfaces check device-check offline-smoke verify benchmark-sp06 protocol-sp07 wff-sp02-preflight protocol-sp02 evidence-validator-check

doctor:
	./android/scripts/doctor.sh
	$(GRADLE) doctor

assets-check:
	$(GRADLE) assetsCheck

phone: doctor assets-check
	$(GRADLE) :hub:app:assembleDebug

watchfaces: doctor assets-check
	$(GRADLE) :watchfaces:contour-wff:bundleDebug

check: doctor evidence-validator-check
	$(GRADLE) check

device-check: doctor
	$(GRADLE) :hub:app:connectedDebugAndroidTest

offline-smoke:
	./android/scripts/doctor.sh
	$(GRADLE) --offline doctor :hub:app:assembleDebug :watchfaces:contour-wff:bundleDebug check

verify: phone watchfaces check offline-smoke
	./android/scripts/verify-artifacts.sh

benchmark-sp06:
	$(GRADLE) :quality:macrobenchmark:verifySp06Setup
	./android/scripts/validate-epic-3-evidence.sh _bmad-output/implementation-artifacts/evidence/story-3-7

protocol-sp07:
	./android/scripts/validate-epic-3-evidence.sh _bmad-output/implementation-artifacts/evidence/story-3-8

wff-sp02-preflight:
	./android/scripts/run-wff-sp02-preflight.sh

protocol-sp02:
	./android/scripts/validate-sp02-evidence.sh _bmad-output/implementation-artifacts/evidence/story-4-1
	./android/scripts/test-validate-sp02-evidence.sh

evidence-validator-check:
	./android/scripts/test-validate-epic-3-evidence.sh
	./android/scripts/test-validate-sp02-evidence.sh
	./android/scripts/test-run-wff-sp02-preflight.sh
