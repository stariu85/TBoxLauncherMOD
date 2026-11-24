#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    ANDROID_JAR=$(ls "$SDK/platforms/"*/android.jar | tail -n 1)
fi

BUILD_TOOLS="$SDK/build-tools/35.0.0"
AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
KEYSTORE="$HOME/.android/debug.keystore"

BUILD_DIR="$DIR/build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"

echo "1. Compiling MonjaroNavReceiver.java..."
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" -d "$BUILD_DIR/classes" "$DIR/src/plus/monjaro/MonjaroNavReceiver.java"

echo "2. Building classes.dex with d8..."
CLASS_FILES=$(find "$BUILD_DIR/classes" -name "*.class")
"$D8" --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR" $CLASS_FILES

echo "3. Linking APK with aapt2..."
"$AAPT2" link -I "$ANDROID_JAR" --manifest "$DIR/AndroidManifest.xml" --target-sdk-version 29 --min-sdk-version 26 -o "$BUILD_DIR/unaligned.apk"

echo "4. Adding DEX to APK..."
(cd "$BUILD_DIR" && zip -u unaligned.apk classes.dex)

echo "5. Aligning APK with zipalign..."
"$ZIPALIGN" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

echo "6. Signing APK with apksigner..."
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$DIR/monjaro_stub.apk" "$BUILD_DIR/aligned.apk"

mkdir -p "$DIR/../../launcher/src/main/assets"
cp -f "$DIR/monjaro_stub.apk" "$DIR/../../launcher/src/main/assets/monjaro_stub.apk"
echo "Successfully generated launcher/src/main/assets/monjaro_stub.apk"
