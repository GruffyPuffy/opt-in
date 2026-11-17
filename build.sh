#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="$PROJECT_DIR/.android-sdk"
GRADLE="$SDK_DIR/gradle-8.9/bin/gradle"
APK="$PROJECT_DIR/app/build/outputs/opt-in/opt-in-v1.1-debug.apk"

if [[ ! -x "$GRADLE" || ! -x "$SDK_DIR/jdk/bin/java" ]]; then
    echo "Local Android build tools are missing from .android-sdk/." >&2
    exit 1
fi

JAVA_HOME="$SDK_DIR/jdk" \
ANDROID_SDK_ROOT="$SDK_DIR" \
GRADLE_USER_HOME="$SDK_DIR/gradle-home" \
PATH="$SDK_DIR/jdk/bin:$PATH" \
    "$GRADLE" --no-daemon -p "$PROJECT_DIR" copyDebugApk

echo "Built: $APK"
