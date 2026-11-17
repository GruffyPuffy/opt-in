# opt-in

A small, offline Android call-screening app. The user selects a home country, which is always allowed, and can add other allowed countries or individual numbers. Calls from other identifiable countries are rejected and shown under Blocked calls. The app stores its lists only in private on-device preferences.

Phone numbers are parsed locally with Google's libphonenumber. Calls with private or unparseable caller IDs are allowed because their country cannot be identified. Countries that share a calling code are distinguished using the full phone number where the numbering plan permits it. The app needs to be selected as the system Call screening app to screen calls.

## Build

The Android command-line tools, JDK 17, API 35 platform, build tools, and platform tools are installed locally under `.android-sdk/` and excluded from Git. `local.properties` points Android builds to this SDK.

Run `./build.sh` to create `app/build/outputs/opt-in/opt-in-v1.1-debug.apk`. Connect one Android device with USB debugging enabled and accepted, then run `./upload.sh` to install and open the APK. Both scripts use the local tools in `.android-sdk/`.

The app requests Android's Call screening role from its main screen; approve that system prompt to enable filtering.
