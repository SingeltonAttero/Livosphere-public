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

.PHONY: doctor assets-check phone watchfaces check offline-smoke verify

doctor:
	./android/scripts/doctor.sh
	$(GRADLE) doctor

assets-check:
	$(GRADLE) assetsCheck

phone: doctor assets-check
	$(GRADLE) :hub:app:assembleDebug

watchfaces: doctor assets-check
	$(GRADLE) :watchfaces:contour-wff:bundleDebug

check: doctor
	$(GRADLE) check

offline-smoke:
	./android/scripts/doctor.sh
	$(GRADLE) --offline doctor :hub:app:assembleDebug :watchfaces:contour-wff:bundleDebug check

verify: phone watchfaces check offline-smoke
	./android/scripts/verify-artifacts.sh
