#!/usr/bin/env bash
# Compila o APK sem Gradle. Precisa de JDK 17 e do Android SDK (platform 34 + build-tools 34.0.0).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf build && mkdir -p build/classes build/dex
python3 make_banner.py res/drawable-xhdpi/banner.png

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o build/unsigned.apk build/res.zip --auto-add-overlay
javac -source 8 -target 8 -bootclasspath "$JAR" -Xlint:-options -d build/classes $(find src -name '*.java')
"$BT/d8" --min-api 31 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

KS="${KEYSTORE:-$HOME/.android/tcl-bypass.keystore}"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass tclbypass -keypass tclbypass -alias tclbypass \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tcl-bypass" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:tclbypass --key-pass pass:tclbypass --out tcl-bypass.apk build/aligned.apk
echo "APK: $(pwd)/tcl-bypass.apk"
