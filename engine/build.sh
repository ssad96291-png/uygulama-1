#!/usr/bin/env bash
set -euo pipefail

# Prerequisites: Go 1.25.5, JDK 21, Android SDK platform 36 and NDK
# 28.2.13676358. Set JAVA_HOME, ANDROID_HOME and ANDROID_NDK_HOME first.
# In the supplied workspace: source /workspace/toolchains/android-env.sh
ENGINE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ENGINE_ROOT"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export GOMODCACHE="${GOMODCACHE:-$ENGINE_ROOT/.cache/mod}"
export GOCACHE="${GOCACHE:-$ENGINE_ROOT/.cache/build}"
export GOPATH="${GOPATH:-$ENGINE_ROOT/.cache/gopath}"
export GOBIN="$ENGINE_ROOT/bin"
export PATH="$GOBIN:$JAVA_HOME/bin:$PATH"
mkdir -p "$GOBIN" build ../app/libs

MOBILE_VERSION="$(go list -m -f '{{.Version}}' golang.org/x/mobile)"
go install "golang.org/x/mobile/cmd/gomobile@$MOBILE_VERSION"
go install "golang.org/x/mobile/cmd/gobind@$MOBILE_VERSION"
# `gomobile init` installs gobind@latest even when gomobile itself is pinned,
# which can silently switch Go versions. Bind needs only this work directory
# and the pinned gobind above; Akış does not use init's optional OpenAL build.
mkdir -p "${GOPATH%%:*}/pkg/gomobile"
go test -race ./mobile
gomobile bind -target=android/arm64,android/arm,android/amd64 \
  -androidapi 29 -javapkg=dev.akiskontrol.engine -trimpath \
  -ldflags='-s -w' -o build/engine.aar ./mobile
mkdir -p build/aar-notices/assets/engine-licenses
cp licenses/*.txt THIRD_PARTY_NOTICES.md build/aar-notices/assets/engine-licenses/
jar uf build/engine.aar -C build/aar-notices assets
cp build/engine.aar ../app/libs/engine.aar
printf 'Built %s\n' "$ENGINE_ROOT/../app/libs/engine.aar"
