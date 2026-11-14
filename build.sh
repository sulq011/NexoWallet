#!/usr/bin/env bash
# ============================================================
# محفظة nexo-tms — بناء APK بدون Gradle (aapt2 + javac + d8 + apksigner)
# يعمل في GitHub Actions أو أي لينكس فيه JDK 17 + Android build-tools.
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"

BT_DIR="${ANDROID_BUILD_TOOLS_DIR:-}"
if [ -z "$BT_DIR" ]; then
  BT_DIR="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
fi
[ -n "$BT_DIR" ] && [ -d "$BT_DIR" ] || { echo "❌ build-tools غير موجودة"; exit 1; }

PLATFORM_JAR="${ANDROID_PLATFORM_JAR:-}"
if [ -z "$PLATFORM_JAR" ]; then
  PLATFORM_JAR="$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
fi
[ -f "$PLATFORM_JAR" ] || { echo "❌ android.jar غير موجود"; exit 1; }

AAPT2="$BT_DIR/aapt2"
D8="$BT_DIR/d8"
APKSIGNER="$BT_DIR/apksigner"
ZIPALIGN="$BT_DIR/zipalign"

echo "== أدوات البناء =="
echo "build-tools : $BT_DIR"
echo "android.jar : $PLATFORM_JAR"
javac -version

KS=nexowallet.keystore
if [ ! -f "$KS" ]; then
  echo "إنشاء مفتاح توقيع جديد…"
  keytool -genkeypair -keystore "$KS" -alias nexowallet \
    -storepass nexowallet123 -keypass nexowallet123 \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=NexoWallet, O=nexo-tms, C=SA" >/dev/null 2>&1
fi

NAME=NexoWallet
B="build/$NAME"
echo ""
echo "======== بناء $NAME ========"
rm -rf "$B"
mkdir -p "$B/gen" "$B/obj" "$B/dex"

echo "== 1) aapt2 compile (الموارد) =="
"$AAPT2" compile --dir res -o "$B/res.zip"

echo "== 2) aapt2 link (المانيفست + R.java) =="
"$AAPT2" link -o "$B/unsigned.apk" \
  -I "$PLATFORM_JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 24 \
  --target-sdk-version 33 \
  --version-code 4 \
  --version-name 1.3 \
  --java "$B/gen" \
  "$B/res.zip"

echo "== 3) javac (جافا) =="
find src "$B/gen" -name '*.java' > "$B/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -bootclasspath "$PLATFORM_JAR" \
  -d "$B/obj" @"$B/sources.txt"

echo "== 4) d8 (تحويل إلى DEX) =="
find "$B/obj" -name '*.class' > "$B/classes.txt"
"$D8" --release --lib "$PLATFORM_JAR" --output "$B/dex" $(cat "$B/classes.txt")

echo "== 5) تجميع الحزمة =="
cp "$B/unsigned.apk" "$B/app.apk"
(cd "$B/dex" && zip -q ../app.apk classes.dex)

echo "== 6) zipalign =="
"$ZIPALIGN" -f 4 "$B/app.apk" "$B/aligned.apk"

echo "== 7) التوقيع =="
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:nexowallet123 --key-pass pass:nexowallet123 \
  --out "$NAME.apk" "$B/aligned.apk"
"$APKSIGNER" verify --print-certs "$NAME.apk" | head -2

echo ""
echo "✅ BUILD SUCCESSFUL: $NAME.apk ($(du -h "$NAME.apk" | cut -f1))"
