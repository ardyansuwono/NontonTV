#!/bin/bash
# REVERT: hapus perubahan UI Trakteer (jam + banner rotasi) KEMBALI ke commit bd74520.
# AMAN: fix DRM multiple-key (PlayerActivity.kt, String.kt, setting_dialog.xml) TIDAK tersentuh.
cd "$(dirname "$0")" || exit 1

echo ">>> Membuat backup commit dulu (supaya tetap bisa dipulihkan nanti)..."
git add -A app/
git commit -m "WIP backup: trakteer widget UI (force-closes on open)" >/dev/null 2>&1 || true

echo ">>> Mengembalikan 8 file UI yang diubah ke commit bd74520..."
git checkout bd74520 -- \
  app/src/main/java/id/tipime/tipistream/MainActivity.kt \
  app/src/main/java/id/tipime/tipistream/dialog/SettingAppFragment.kt \
  app/src/main/java/id/tipime/tipistream/dialog/SettingDialog.kt \
  app/src/main/java/id/tipime/tipistream/extra/Preferences.kt \
  app/src/main/res/layout/activity_main.xml \
  app/src/main/res/layout/setting_app_fragment.xml \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-in/strings.xml

echo ">>> Menghapus 3 file baru buatan fitur Trakteer..."
rm -f app/src/main/java/id/tipime/tipistream/extra/TrakteerClient.kt
rm -f app/src/main/java/id/tipime/tipistream/extra/MarqueeFlipper.kt
rm -f app/src/main/java/id/tipime/tipistream/model/TrakteerRunningText.kt

echo ">>> Membersihkan status index..."
git reset -q HEAD -- app/

echo ""
echo "=== STATUS WORKING TREE SEKARANG (yang tersisa HANYA fix DRM kamu) ==="
git status --short
echo ""
echo ">>> SELESAI. Sekarang rebuild dengan:"
echo "    export JAVA_HOME=/home/codespace/java/21.0.12+1-ms && export ANDROID_HOME=\$HOME/android-sdk && ./gradlew :app:assembleDebug"
