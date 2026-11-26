#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ANDROID_JAR="$SDK/platforms/android-28/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    ANDROID_JAR=$(ls "$SDK/platforms/"*/android.jar | head -n 1)
fi
D8="$SDK/build-tools/36.0.0/d8"
if [ ! -f "$D8" ]; then
    D8=$(ls "$SDK/build-tools/"*/d8 | tail -n 1)
fi

if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: android.jar not found in $SDK/platforms"
    exit 1
fi
if [ ! -f "$D8" ]; then
    echo "Error: d8 not found in $SDK/build-tools"
    exit 1
fi

BUILD_DIR="$DIR/build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"

echo "1. Compiling UnlockUsb sources against $ANDROID_JAR..."
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
    -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" \
    "$DIR/../../launcher/src/main/java/vad/dashing/tbox/usbmount/UnlockUsb.java" \
    "$DIR/src/UnlockUsb.java"

echo "2. Building unlockusb.dex with d8..."
CLASS_FILES=$(find "$BUILD_DIR/classes" -name "*.class")
"$D8" --min-api 28 --lib "$ANDROID_JAR" --output "$BUILD_DIR" $CLASS_FILES

cp -f "$BUILD_DIR/classes.dex" "$DIR/../../launcher/src/main/assets/unlockusb.dex"
echo "Successfully generated launcher/src/main/assets/unlockusb.dex"
