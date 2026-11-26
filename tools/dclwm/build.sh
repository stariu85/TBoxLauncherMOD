#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ANDROID_JAR="$SDK/platforms/android-28/android.jar"
D8="$SDK/build-tools/36.0.0/d8"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: android.jar not found at $ANDROID_JAR"
    exit 1
fi
if [ ! -f "$D8" ]; then
    echo "Error: d8 not found at $D8"
    exit 1
fi

BUILD_DIR="$DIR/build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/check" "$BUILD_DIR/classes"

echo "1. Compiling and running SessionTest..."
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -d "$BUILD_DIR/check" \
    "$DIR/src/vad/dashing/dclwm/Protocol.java" \
    "$DIR/src/vad/dashing/dclwm/WindowTask.java" \
    "$DIR/src/vad/dashing/dclwm/StackListParser.java" \
    "$DIR/src/vad/dashing/dclwm/LauncherUid.java" \
    "$DIR/src/vad/dashing/dclwm/AmFailure.java" \
    "$DIR/src/vad/dashing/dclwm/Am.java" \
    "$DIR/src/vad/dashing/dclwm/Session.java" \
    "$DIR/src/vad/dashing/dclwm/ProcessAm.java" \
    "$DIR/src/vad/dashing/dclwm/ArmScript.java" \
    "$DIR/test/SessionTest.java"

java -cp "$BUILD_DIR/check" vad.dashing.dclwm.SessionTest

echo "2. Compiling full dclwm source against android-28..."
SOURCES=$(find "$DIR/src" -name "*.java" ! -name "Starter.java")
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" -d "$BUILD_DIR/classes" $SOURCES

echo "3. Building DEX with d8..."
CLASS_FILES=$(find "$BUILD_DIR/classes" -name "*.class")
"$D8" --min-api 28 --lib "$ANDROID_JAR" --output "$BUILD_DIR" $CLASS_FILES

cp -f "$BUILD_DIR/classes.dex" "$DIR/dclwm.dex"
cp -f "$BUILD_DIR/classes.dex" "$DIR/../../launcher/src/main/assets/dclwm.dex"
echo "Successfully generated launcher/src/main/assets/dclwm.dex"
