#!/bin/sh
# 谷歌区域伪装 —— 手动工具链构建脚本
# 用法：配置 ANDROID_HOME（以及可选 KS / KS_PASS / KEY_PASS）后执行 ./build.sh
# 产物：build/GoogleRegionSpoof-v1.4.apk
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJ/app/src/main"
BUILD="$PROJ/build"
VERSION_CODE="${VERSION_CODE:-6}"
VERSION_NAME="${VERSION_NAME:-1.4}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
XPOSED_JAR="$PROJ/libs/api-82.jar"
KS="${KS:-$HOME/workspace/android-tools/muse-test.keystore}"
KS_PASS="${KS_PASS:-muse1234}"
KEY_PASS="${KEY_PASS:-muse1234}"
OUT_APK="$BUILD/GoogleRegionSpoof-v${VERSION_NAME}.apk"

: "${ANDROID_HOME:?请先设置 ANDROID_HOME}"

# 若 build-tools 未在 PATH 中，自动探测最新版本加入 PATH
if ! command -v aapt2 >/dev/null 2>&1; then
    LATEST_BT="$(ls -1d "$ANDROID_HOME/build-tools"/* 2>/dev/null | sort -V | tail -n 1)"
    if [ -n "$LATEST_BT" ]; then
        PATH="$LATEST_BT:$PATH"
        export PATH
    fi
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

echo "== aapt2 compile =="
aapt2 compile --dir "$SRC/res" -o "$BUILD/res.zip"

echo "== aapt2 link =="
aapt2 link -o "$BUILD/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    -A "$SRC/assets" \
    --java "$BUILD/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    "$BUILD/res.zip"

echo "== javac =="
find "$SRC/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac --release 8 -encoding UTF-8 \
    -cp "$ANDROID_JAR:$XPOSED_JAR" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "== d8 =="
# 只打包本模块的类；Xposed API 是框架提供的，不进 dex
d8 --lib "$ANDROID_JAR" --classpath "$XPOSED_JAR" --output "$BUILD/dex" \
    $(find "$BUILD/classes" -name '*.class')

echo "== package =="
cp "$BUILD/base.apk" "$BUILD/app.apk"
cd "$BUILD/dex" && zip -q "$BUILD/app.apk" classes.dex && cd "$PROJ"

echo "== zipalign =="
zipalign -p -f 4 "$BUILD/app.apk" "$BUILD/aligned.apk"

echo "== sign =="
apksigner sign \
    --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
    --out "$OUT_APK" "$BUILD/aligned.apk"

echo "== verify =="
apksigner verify --print-certs "$OUT_APK" | head -3
ls -la "$OUT_APK"
echo "BUILD OK"
