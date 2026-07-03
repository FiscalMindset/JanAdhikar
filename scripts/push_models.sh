#!/usr/bin/env bash
# Push the on-device model weights to the phone's app-external dir, so the APK
# itself stays small (~30 MB) and installs reliably. Run AFTER installing the
# app at least once (the external dir is created on first launch, but we mkdir
# it here too).
#
#   ./scripts/push_models.sh [device-serial]
#
# Models live in app/src/main/assets/models/ locally (git-ignored) and are
# pushed to /sdcard/Android/data/com.janadhikar/files/models/ on the device.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=com.janadhikar
SRC=app/src/main/assets/models
DEST=/sdcard/Android/data/$PKG/files/models

DEV="${1:-}"
if [[ -z "$DEV" ]]; then
    DEV=$(adb devices | awk '/\tdevice$/{print $1; exit}')
fi
[[ -n "$DEV" ]] || { echo "No device. Plug in the phone (USB debugging on)."; exit 1; }
echo "Device: $DEV"

adb -s "$DEV" shell mkdir -p "$DEST"

for f in embedder_minilm_384.tflite ggml-small-q5_1.bin gemma3-1b-it-int4.task; do
    if [[ -f "$SRC/$f" ]]; then
        echo "→ pushing $f ($(du -h "$SRC/$f" | cut -f1))…"
        adb -s "$DEV" push "$SRC/$f" "$DEST/$f"
    else
        echo "! missing $SRC/$f (fetch it first: scripts/fetch_models.sh / export_tflite.py)"
    fi
done

echo
echo "On device now:"
adb -s "$DEV" shell ls -lh "$DEST" 2>/dev/null | grep -v '^total' || true
echo "Done. Relaunch the app — it will read models from external storage."
