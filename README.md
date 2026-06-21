# Pocket Shift VR

Pocket Shift VR is an original Android prototype inspired by hands-on VR workplace games without copying Job Simulator assets, levels, characters, names, or tasks.

The project builds two APKs:

- `vrgame` - the phone placed in mobile VR goggles. It renders a split-screen VR scene and listens for controller packets.
- `controller` - the second Android phone. It sends aim and action commands over Wi-Fi UDP.

## Ready-to-download APK bundle

A debug APK bundle is committed here:

```text
downloads/pocket-shift-vr-debug-apks.zip
```

Open that file on GitHub and press `Download raw file`. The zip contains:

- `PocketShiftVR-vrgame-debug.apk` - install on the phone inside the VR goggles.
- `PocketShiftVR-controller-debug.apk` - install on the second Android phone.

## Gameplay

The VR phone shows a small "shift room" with three objects and three stations:

1. Put `MUG` on the coffee bot.
2. Put `BOX` on the scanner.
3. Put `ORB` into the green tray.

Use the controller phone touchpad to aim. Press `Grab / Drop` to pick up or release an object.

## Network setup

1. Put both phones on the same Wi-Fi network or hotspot.
2. Install and open `Pocket Shift VR` on the first phone.
3. Put that phone in landscape orientation and place it into VR goggles.
4. Read the IP shown at the top of the VR screen.
5. Install and open `Pocket Shift Controller` on the second phone.
6. Enter the VR phone IP and press `Connect`.

The default UDP port is `45454`.

## Build

This repository includes a lightweight `gradlew` bootstrap script. It downloads Gradle locally if needed.

```bash
chmod +x ./gradlew
./gradlew :vrgame:assembleDebug :controller:assembleDebug
```

APK outputs:

```text
vrgame/build/outputs/apk/debug/vrgame-debug.apk
controller/build/outputs/apk/debug/controller-debug.apk
```

Android SDK with API 35 is required in the build environment.
