#!/bin/bash
# TipiStream build helper.
#   ./build.sh            → debug APK (signed debug, untuk test; nomor rilis TIDAK berubah)
#   ./build.sh release    → release APK (signed, untuk publikasi; versionCode naik +1)
# Lihat cara-compile.txt untuk catatan versi & upload.
set -e

cd "$(dirname "$0")"
export JAVA_HOME=/home/codespace/java/21.0.12+1-ms
export ANDROID_HOME=$HOME/android-sdk

MODE="${1:-debug}"

case "$MODE" in
  release)
    echo "→ Building SIGNED release APK..."
    ./gradlew :app:assembleRelease
    APK=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | tail -1)
    ;;
  *)
    echo "→ Building debug APK..."
    ./gradlew :app:assembleDebug
    ;;
esac

echo ""
echo "=== SELESAI ==="
ls -lh app/build/outputs/apk/$MODE/*.apk 2>/dev/null
echo ""
echo "Versi terbaru di release.json: $(grep versionCode json/release.json | grep -oE '[0-9]+')"
echo "(ingat: release build menaikkan VERSION_CODE di app/version.prop — lihat cara-compile.txt)"
