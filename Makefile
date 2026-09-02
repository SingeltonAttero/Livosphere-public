SHELL := /bin/sh

ANDROID_DIR := android
GRADLE := cd $(ANDROID_DIR) && ./gradlew --console=plain --no-daemon

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

.PHONY: doctor phone watchfaces check offline-smoke verify

doctor:
	./android/scripts/doctor.sh
	$(GRADLE) doctor

phone: doctor
	$(GRADLE) :hub:app:assembleDebug

watchfaces: doctor
	$(GRADLE) :watchfaces:contour-wff:bundleDebug

check: doctor
	$(GRADLE) check

offline-smoke:
	./android/scripts/doctor.sh
	$(GRADLE) --offline doctor :hub:app:assembleDebug :watchfaces:contour-wff:bundleDebug check

verify: phone watchfaces check offline-smoke
	./android/scripts/verify-artifacts.sh
