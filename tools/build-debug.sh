#!/usr/bin/env bash
set -euo pipefail

# Optional: source an explicitly selected, trusted development environment file.
if [[ -n "${AKIS_ENV_FILE:-}" ]]; then
    if [[ ! -f "$AKIS_ENV_FILE" ]]; then
        printf 'Environment file not found: %s\n' "$AKIS_ENV_FILE" >&2
        exit 1
    fi
    source "$AKIS_ENV_FILE"
fi

AKIS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$AKIS_ROOT"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21 (including javac)}"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -x "$JAVA_HOME/bin/javac" ]] || [[ "$("$JAVA_HOME/bin/javac" -version 2>&1)" != "javac 21"* ]]; then
    printf 'A full JDK 21 is required at JAVA_HOME.\n' >&2
    exit 1
fi
if ! command -v go >/dev/null 2>&1 || [[ "$(go env GOVERSION)" != "go1.25.5" ]]; then
    printf 'Put Go 1.25.5 on PATH to match the verified native toolchain.\n' >&2
    exit 1
fi
for AKIS_REQUIRED in \
    "$ANDROID_HOME/platforms/android-36/android.jar" \
    "$ANDROID_HOME/build-tools/36.0.0/aapt2" \
    "$ANDROID_NDK_HOME/source.properties"; do
    if [[ ! -f "$AKIS_REQUIRED" ]]; then
        printf 'Missing Android toolchain file: %s\n' "$AKIS_REQUIRED" >&2
        exit 1
    fi
done

./engine/build.sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
printf 'Debug APK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$AKIS_ROOT"
