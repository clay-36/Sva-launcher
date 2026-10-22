#!/bin/bash
set -euo pipefail

# Sva Launcher Promo Video Recording & Processing Script
# Records high-bitrate Android TV clips via adb and optimizes them using ffmpeg.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/promo/recordings"

mkdir -p "$OUTPUT_DIR"

command -v adb >/dev/null 2>&1 || { echo >&2 "Error: 'adb' is not installed or not in PATH."; exit 1; }
command -v ffmpeg >/dev/null 2>&1 || { echo >&2 "Error: 'ffmpeg' is not installed or not in PATH."; exit 1; }

# Determine target device
TARGET_DEVICE="${ANDROID_SERIAL:-}"
if [ -z "$TARGET_DEVICE" ]; then
    # Prefer emulator-5554 if running, otherwise first available device
    if adb devices | grep -q "emulator-5554[[:space:]]*device"; then
        TARGET_DEVICE="emulator-5554"
    else
        TARGET_DEVICE=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
    fi
fi

if [ -z "$TARGET_DEVICE" ]; then
    echo "⚠️  No Android TV or emulator detected by ADB."
    exit 1
fi

ADB_CMD="adb -s $TARGET_DEVICE"
echo "Targeting ADB device: $TARGET_DEVICE"

# Function to record a scene
record_scene() {
    local scene_num="$1"
    local scene_name="$2"
    local duration="$3"
    local script_fn="$4"

    echo ""
    echo "=========================================================="
    echo "🎬 Recording Scene $scene_num: $scene_name ($duration sec)"
    echo "=========================================================="

    local timestamp
    timestamp=$(date +%Y%m%d_%H%M%S)
    local raw_on_device="/sdcard/promo_${scene_name}_${timestamp}.mp4"
    local dest_raw="$OUTPUT_DIR/${scene_num}_${scene_name}_raw.mp4"
    local dest_1080p="$OUTPUT_DIR/${scene_num}_${scene_name}_1080p60.mp4"

    # Start adb screenrecord in background
    $ADB_CMD shell screenrecord --size 1280x720 --bit-rate 12000000 --time-limit "$duration" "$raw_on_device" &
    local rec_pid=$!

    # Execute automated navigation script in parallel if provided
    if [ -n "$script_fn" ]; then
        ($script_fn) &
    fi

    # Wait for screenrecord to finish
    wait "$rec_pid" 2>/dev/null || true
    sleep 1

    echo "📥 Pulling recording from device..."
    $ADB_CMD pull "$raw_on_device" "$dest_raw" >/dev/null 2>&1
    $ADB_CMD shell rm -f "$raw_on_device" >/dev/null 2>&1

    echo "⚡ Scaling to 1080p 60fps (Lanczos) with faststart for video editors..."
    ffmpeg -y -i "$dest_raw" -vf "scale=1920:1080:flags=lanczos" -r 60 \
        -c:v libx264 -preset fast -crf 17 -pix_fmt yuv420p -movflags +faststart \
        "$dest_1080p" >/dev/null 2>&1

    echo "✅ Scene $scene_num ready: $dest_1080p"
}

# Navigation scripts for each scene
nav_scene1_home() {
    sleep 1.0
    $ADB_CMD shell am start -n com.sva.launcher/.MainActivity >/dev/null 2>&1
    sleep 0.8
    # Navigate across apps and weather widget
    $ADB_CMD shell input keyevent KEYCODE_DPAD_UP
    sleep 0.8
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 0.8
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 0.8
    $ADB_CMD shell input keyevent KEYCODE_DPAD_LEFT
    sleep 0.8
    $ADB_CMD shell input keyevent KEYCODE_DPAD_UP
    sleep 1.2
    $ADB_CMD shell input keyevent KEYCODE_DPAD_DOWN
    sleep 0.8
    $ADB_CMD shell input keyevent KEYCODE_DPAD_DOWN
}

nav_scene2_screensaver() {
    sleep 0.5
    # Launch Screensaver settings and trigger Preview
    $ADB_CMD shell am start -n com.sva.launcher/.ScreensaverSettingsActivity >/dev/null 2>&1
    sleep 1.0
    $ADB_CMD shell input tap 935 630 >/dev/null 2>&1
    # Let screensaver animate and cycle
    sleep 11.0
}

nav_scene3_livetv() {
    sleep 0.8
    $ADB_CMD shell am start -n com.sva.launcher/.MainActivity >/dev/null 2>&1
    sleep 0.8
    # Switch to Live TV tab
    $ADB_CMD shell input tap 430 650 >/dev/null 2>&1
    sleep 1.5
    # Scroll through channels on Home tab
    $ADB_CMD shell input keyevent KEYCODE_DPAD_UP
    sleep 1.0
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 1.0
    # Click "Open TV guide ->"
    $ADB_CMD shell input tap 1050 75 >/dev/null 2>&1
    sleep 2.0
    # In TV Guide: browse upcoming schedule
    $ADB_CMD shell input keyevent KEYCODE_DPAD_DOWN
    sleep 1.0
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 1.0
    $ADB_CMD shell input keyevent KEYCODE_DPAD_LEFT
    sleep 1.0
    # Select Live channel to trigger actual playback
    $ADB_CMD shell input tap 500 380 >/dev/null 2>&1
    sleep 3.5
    # Zap to next channel in video player
    $ADB_CMD shell input keyevent KEYCODE_DPAD_DOWN
    sleep 2.5
}

nav_scene4_games() {
    sleep 0.8
    $ADB_CMD shell am start -n com.sva.launcher/.MainActivity >/dev/null 2>&1
    sleep 1.0
    # Ensure bottom bar is focused, then move to Games tab
    $ADB_CMD shell input keyevent KEYCODE_DPAD_DOWN
    sleep 0.4
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 0.4
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 1.5
    # Move into game cards: focuses Road Runner
    $ADB_CMD shell input keyevent KEYCODE_DPAD_UP
    sleep 0.4
    $ADB_CMD shell input keyevent KEYCODE_DPAD_DOWN
    sleep 1.2
    # Open Game Details
    $ADB_CMD shell input keyevent KEYCODE_DPAD_CENTER
    sleep 1.5
    # Click Play in GameDetailActivity
    $ADB_CMD shell input keyevent KEYCODE_DPAD_CENTER
    # Tengen intro runs for ~8.5s then Road Runner title screen appears
    sleep 8.5
    # Press Start to advance from Title Screen to Game
    $ADB_CMD shell input keyevent KEYCODE_BUTTON_START
    sleep 1.0
    $ADB_CMD shell input keyevent KEYCODE_BUTTON_START
    # Active Road Runner gameplay on highway!
    sleep 7.0
}

nav_scene5_events() {
    sleep 0.8
    $ADB_CMD shell am start -n com.sva.launcher/.MainActivity >/dev/null 2>&1
    sleep 0.8
    # Focus Events tab
    $ADB_CMD shell input tap 677 650 >/dev/null 2>&1
    sleep 1.0
    $ADB_CMD shell input keyevent KEYCODE_DPAD_UP
    sleep 1.0
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 0.8
    $ADB_CMD shell input keyevent KEYCODE_DPAD_RIGHT
    sleep 0.8
    $ADB_CMD shell input keyevent KEYCODE_DPAD_LEFT
    sleep 1.0
}

assemble_rough_cut() {
    echo ""
    echo "🎬 Assembling continuous full rough cut video..."
    local concat_list="$OUTPUT_DIR/concat_list.txt"
    cat <<EOF > "$concat_list"
file '$OUTPUT_DIR/scene1_home_wallpapers_1080p60.mp4'
file '$OUTPUT_DIR/scene2_screensaver_memories_1080p60.mp4'
file '$OUTPUT_DIR/scene3_livetv_guide_1080p60.mp4'
file '$OUTPUT_DIR/scene4_retro_games_1080p60.mp4'
file '$OUTPUT_DIR/scene5_calendar_events_1080p60.mp4'
EOF
    local rough_cut="$OUTPUT_DIR/full_rough_cut_1080p60.mp4"
    ffmpeg -y -f concat -safe 0 -i "$concat_list" -c copy "$rough_cut" >/dev/null 2>&1
    echo "✅ Continuous rough cut assembled: $rough_cut"
}

# Parse CLI arguments or run interactive menu
MODE="${1:-}"

if [ "$MODE" = "--all" ] || [ "$MODE" = "all" ]; then
    record_scene "scene1" "home_wallpapers" 10 nav_scene1_home
    record_scene "scene2" "screensaver_memories" 14 nav_scene2_screensaver
    record_scene "scene3" "livetv_guide" 16 nav_scene3_livetv
    record_scene "scene4" "retro_games" 25 nav_scene4_games
    record_scene "scene5" "calendar_events" 9 nav_scene5_events
    assemble_rough_cut

    echo ""
    echo "🎉 ALL PROMO SCENES RECORDED & PROCESSED IN 1080p 60fps!"
    ls -lh "$OUTPUT_DIR"/*1080p60.mp4
    exit 0
elif [ "$MODE" = "--scene" ] && [ -n "${2:-}" ]; then
    case "$2" in
        1) record_scene "scene1" "home_wallpapers" 10 nav_scene1_home ;;
        2) record_scene "scene2" "screensaver_memories" 14 nav_scene2_screensaver ;;
        3) record_scene "scene3" "livetv_guide" 16 nav_scene3_livetv ;;
        4) record_scene "scene4" "retro_games" 25 nav_scene4_games ;;
        5) record_scene "scene5" "calendar_events" 9 nav_scene5_events ;;
        *) echo "Unknown scene $2"; exit 1 ;;
    esac
    exit 0
fi

# Interactive menu
echo "=========================================================="
echo "🎬 Sva Launcher - Promo Video Capture Studio"
echo "Target Device: $TARGET_DEVICE"
echo "Output Directory: $OUTPUT_DIR"
echo "=========================================================="
echo "  a) Record ALL scenes automatically (Scenes 1-5 + Full Reel)"
echo "  1) Scene 1: Home Screen & Wallpapers (10s)"
echo "  2) Scene 2: Sva Memories Screensaver Themes (14s)"
echo "  3) Scene 3: Actual Live TV & Full TV Guide + Player (16s)"
echo "  4) Scene 4: Actual Retro NES Game Emulation (25s)"
echo "  5) Scene 5: Calendar, Weather & Events (9s)"
echo "  r) Reassemble full rough cut from existing clips"
echo "  q) Quit"
echo "----------------------------------------------------------"
read -r -p "Enter choice [a, 1-5, r, or q]: " choice

case "$choice" in
    a|A)
        record_scene "scene1" "home_wallpapers" 10 nav_scene1_home
        record_scene "scene2" "screensaver_memories" 14 nav_scene2_screensaver
        record_scene "scene3" "livetv_guide" 16 nav_scene3_livetv
        record_scene "scene4" "retro_games" 25 nav_scene4_games
        record_scene "scene5" "calendar_events" 9 nav_scene5_events
        assemble_rough_cut
        ;;
    1) record_scene "scene1" "home_wallpapers" 10 nav_scene1_home ;;
    2) record_scene "scene2" "screensaver_memories" 14 nav_scene2_screensaver ;;
    3) record_scene "scene3" "livetv_guide" 16 nav_scene3_livetv ;;
    4) record_scene "scene4" "retro_games" 14 nav_scene4_games ;;
    5) record_scene "scene5" "calendar_events" 9 nav_scene5_events ;;
    r|R) assemble_rough_cut ;;
    q|Q) exit 0 ;;
    *) echo "Invalid selection"; exit 1 ;;
esac

echo ""
echo "✨ Recordings are saved in $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR"/*1080p60.mp4 2>/dev/null || true
