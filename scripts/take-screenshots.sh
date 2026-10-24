#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SCREENSHOTS_DIR="$ROOT_DIR/.screenshots"
ASSETS_DIR="$(mktemp -d -t uploid-assets-XXXXXX)"
SLEEP=3
trap 'rm -rf "$ASSETS_DIR"' EXIT
AVD_NAME="uploid-pixel7pro"

mkdir -p "$SCREENSHOTS_DIR"

declare -A PICSUM_IMAGES=(
    ["img_1_square.jpg"]="https://picsum.photos/id/10/800/800"
    ["img_2_portrait.jpg"]="https://picsum.photos/id/20/800/1200"
    ["img_3_landscape.jpg"]="https://picsum.photos/id/28/1200/800"
    ["img_4_tall.jpg"]="https://picsum.photos/id/40/600/1200"
    ["img_5_wide.jpg"]="https://picsum.photos/id/50/1200/600"
)

for filename in "${!PICSUM_IMAGES[@]}"; do
    target="$ASSETS_DIR/$filename"
    if [ ! -s "$target" ]; then
        url="${PICSUM_IMAGES[$filename]}"
        echo "Fetching $filename from $url..."
        curl -sSL "$url" -o "$target"
    fi
done

echo "Generating thumbnail assets for database..."
for i in {1..5}; do
    thumb="$ASSETS_DIR/thumb${i}.jpg"
    python3 -c "
from PIL import Image
import glob
src = glob.glob('$ASSETS_DIR/img_${i}_*.jpg')[0]
img = Image.open(src)
img.thumbnail((128, 128))
img.convert('RGB').save('$thumb', 'JPEG', quality=80)
"
done

echo "Seeding SQLite database..."
python3 "$SCRIPT_DIR/seed_database.py" "$ASSETS_DIR/uploid_database" "$ASSETS_DIR"

if ! command -v adb >/dev/null 2>&1; then
    echo "ERROR: adb not found. Please run within 'nix develop'."
    exit 1
fi

DEVICE_ID=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1 || true)
STARTED_EMU=0

if [ -z "$DEVICE_ID" ]; then
    echo "No running emulator found. Checking AVD: $AVD_NAME..."
    if ! avdmanager list avd | grep -q "Name: $AVD_NAME"; then
        echo "Creating AVD $AVD_NAME with Pixel 7 Pro profile..."
        echo "no" | avdmanager create avd -n "$AVD_NAME" -k "system-images;android-35;default;x86_64" -d "pixel_7_pro" --force
        cat <<EOT >> "$HOME/.android/avd/$AVD_NAME.avd/config.ini"
skin.name=1440x3120
hw.lcd.density=560
hw.lcd.width=1440
hw.lcd.height=3120
hw.ramSize=4096M
vm.heapSize=576M
EOT
    fi

    emulator -avd "$AVD_NAME" -skin 1440x3120 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -camera-back none -camera-front none &
    EMU_PID=$!
    STARTED_EMU=1

    adb wait-for-device
    DEVICE_ID=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1)
fi

echo "Connected to emulator: $DEVICE_ID"
echo "Waiting for Android boot to complete..."
while [ "$(adb -s "$DEVICE_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 2
done
echo "Emulator is fully booted!"

# Enforce display resolution and density.
adb -s "$DEVICE_ID" shell wm size 1440x3120
adb -s "$DEVICE_ID" shell wm density 560

# Programmatically hide navigation bar.
adb -s "$DEVICE_ID" shell settings put global policy_control immersive.navigation=*
adb -s "$DEVICE_ID" shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true

# Eye candy.
adb -s "$DEVICE_ID" shell settings put system time_12_24 24
adb -s "$DEVICE_ID" shell settings put global sysui_demo_allowed 1
adb -s "$DEVICE_ID" shell am broadcast -a com.android.systemui.demo -e command enter
adb -s "$DEVICE_ID" shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1337
adb -s "$DEVICE_ID" shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb -s "$DEVICE_ID" shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4
adb -s "$DEVICE_ID" shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

(cd "$ROOT_DIR" && nix build)
APK_PATH="$ROOT_DIR/result/uploid-release.apk"

echo "Installing $APK_PATH..."
adb -s "$DEVICE_ID" uninstall com.uploid >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" install -r -d -g "$APK_PATH"

adb -s "$DEVICE_ID" root || true
sleep 1
adb -s "$DEVICE_ID" wait-for-device

adb -s "$DEVICE_ID" shell pm clear com.uploid
adb -s "$DEVICE_ID" shell mkdir -p /data/data/com.uploid/databases /data/data/com.uploid/files /sdcard/Download

for filename in "${!PICSUM_IMAGES[@]}"; do
    adb -s "$DEVICE_ID" push "$ASSETS_DIR/$filename" "/data/data/com.uploid/files/$filename"
    adb -s "$DEVICE_ID" push "$ASSETS_DIR/$filename" "/sdcard/Download/$filename"
done

adb -s "$DEVICE_ID" push "$ASSETS_DIR/uploid_database" /data/data/com.uploid/databases/uploid_database
adb -s "$DEVICE_ID" shell rm -f /data/data/com.uploid/databases/uploid_database-wal /data/data/com.uploid/databases/uploid_database-shm
adb -s "$DEVICE_ID" shell chmod 666 /data/data/com.uploid/databases/uploid_database* /data/data/com.uploid/files/*

APP_OWNER=$(adb -s "$DEVICE_ID" shell stat -c '%U:%G' /data/data/com.uploid | tr -d '\r')
if [ -n "$APP_OWNER" ]; then
    adb -s "$DEVICE_ID" shell chown -R "$APP_OWNER" /data/data/com.uploid/databases /data/data/com.uploid/files
fi

SCREEN_SIZE=$(adb -s "$DEVICE_ID" shell wm size | grep -oE "[0-9]+x[0-9]+" | head -n 1)
echo "Screen resolution: ${SCREEN_SIZE}"

adb -s "$DEVICE_ID" shell "rm -rf /sdcard/screenshots && mkdir -p /sdcard/screenshots"

# View 1: Landing page with 2 providers.
adb -s "$DEVICE_ID" shell am force-stop com.uploid
adb -s "$DEVICE_ID" shell am start -n com.uploid/.MainActivity
sleep ${SLEEP}
adb -s "$DEVICE_ID" shell screencap -p /sdcard/screenshots/01_landing.png

# View 2: Upload view with one image and sliders set to non-default values.
adb -s "$DEVICE_ID" shell am force-stop com.uploid
adb -s "$DEVICE_ID" shell am start -a android.intent.action.SEND \
    -t "image/jpeg" \
    --eu android.intent.extra.STREAM "file:///data/data/com.uploid/files/img_3_landscape.jpg" \
    --ef size_slider 2.0 \
    --ef quality_slider 3.0 \
    -n com.uploid/.UploadActivity
sleep ${SLEEP}
adb -s "$DEVICE_ID" shell screencap -p /sdcard/screenshots/02_upload.png

# View 3: Upload view with 3 images.
adb -s "$DEVICE_ID" shell am force-stop com.uploid
adb -s "$DEVICE_ID" shell am start -a android.intent.action.SEND_MULTIPLE \
    -t "image/jpeg" \
    --esa android.intent.extra.STREAM "file:///data/data/com.uploid/files/img_1_square.jpg,file:///data/data/com.uploid/files/img_2_portrait.jpg,file:///data/data/com.uploid/files/img_3_landscape.jpg" \
    -n com.uploid/.UploadActivity
sleep ${SLEEP}
adb -s "$DEVICE_ID" shell screencap -p /sdcard/screenshots/03_upload_multiple.png

# View 4: History view with 5 images, including a group-upload of 3 images.
adb -s "$DEVICE_ID" shell am force-stop com.uploid
adb -s "$DEVICE_ID" shell am start -n com.uploid/.MainActivity --es select_tab history
sleep ${SLEEP}
adb -s "$DEVICE_ID" shell screencap -p /sdcard/screenshots/04_history.png

adb -s "$DEVICE_ID" pull /sdcard/screenshots/. "$SCREENSHOTS_DIR/"

if [ "$STARTED_EMU" -eq 1 ] && [ -n "${EMU_PID:-}" ]; then
    adb -s "$DEVICE_ID" emu kill || kill "$EMU_PID" 2>/dev/null || true
fi

echo "=== Screenshots successfully captured to $SCREENSHOTS_DIR ==="
ls -lh "$SCREENSHOTS_DIR"/*.png
