#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ADB="$PROJECT_DIR/.android-sdk/platform-tools/adb"
APK="$PROJECT_DIR/app/build/outputs/opt-in/opt-in-v1.1-debug.apk"

if [[ ! -x "$ADB" ]]; then
    echo "ADB is missing from .android-sdk/platform-tools/." >&2
    exit 1
fi
if [[ ! -f "$APK" ]]; then
    echo "APK not found. Run ./build.sh first." >&2
    exit 1
fi

mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if (( ${#DEVICES[@]} == 0 )); then
    echo "No authorized Android device found. Connect it, enable USB debugging, and accept the debugging prompt." >&2
    "$ADB" devices -l
    exit 1
fi
if (( ${#DEVICES[@]} > 1 )); then
    echo "More than one authorized device found. Leave only the phone you want to install to connected." >&2
    "$ADB" devices -l
    exit 1
fi

DEVICE="${DEVICES[0]}"
"$ADB" -s "$DEVICE" install -r "$APK"
"$ADB" -s "$DEVICE" shell am start -n se.optin/.MainActivity
