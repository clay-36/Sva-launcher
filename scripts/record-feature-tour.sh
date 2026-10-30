#!/usr/bin/env bash
set -euo pipefail

# Capture a guided Sva Launcher feature tour from a configured Android TV device.
# The navigation taps target the standard 1280x720 layout and default tab order.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="${SVA_RECORDING_DIR:-$PROJECT_ROOT/promo/recordings/feature-tour}"
PACKAGE="com.sva.launcher"
MAIN_ACTIVITY="$PACKAGE/.MainActivity"
SERIAL="${ANDROID_SERIAL:-}"
BIT_RATE="${SVA_RECORDING_BIT_RATE:-12000000}"
SCENE_NAMES=(home live_tv_guide activities games events saved news screensaver)
SCENE_DURATIONS=(10 18 9 11 9 9 19 13)

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }
for tool in adb ffmpeg; do command -v "$tool" >/dev/null 2>&1 || die "$tool is required."; done

if [[ -z "$SERIAL" ]]; then
  ready_count="$(adb devices | awk 'NR>1 && $2 == "device" {count++} END {print count+0}')"
  [[ "$ready_count" == 1 ]] || die "Connect one device or set ANDROID_SERIAL. Found $ready_count ready devices."
  SERIAL="$(adb devices | awk 'NR>1 && $2 == "device" {print $1; exit}')"
fi
ADB=(adb -s "$SERIAL")
${ADB[@]} get-state >/dev/null 2>&1 || die "ADB device $SERIAL is not ready."
${ADB[@]} shell pm path "$PACKAGE" >/dev/null 2>&1 || die "Sva Launcher is not installed on $SERIAL."

mkdir -p "$OUTPUT_DIR"
WIDTH="$(
  ${ADB[@]} shell wm size | awk -F'[:x]' '/Physical size|Override size/ {print $2; exit}' | tr -d '\r'
)"
HEIGHT="$(
  ${ADB[@]} shell wm size | awk -F'[:x]' '/Physical size|Override size/ {print $3; exit}' | tr -d '\r'
)"
[[ "$WIDTH" =~ ^[0-9]+$ && "$HEIGHT" =~ ^[0-9]+$ ]] || { WIDTH=1280; HEIGHT=720; }

printf 'ADB device: %s\nScreen: %sx%s\nOutput: %s\n' "$SERIAL" "$WIDTH" "$HEIGHT" "$OUTPUT_DIR"
printf 'Preflight assumes configured IPTV/guide, RSS, Activities, games, calendar and screensaver content.\n'
if [[ -t 0 ]]; then
  read -r -p 'Start the guided recording? [y/N] ' answer
else
  answer="${SVA_CONFIRM:-}"
fi
[[ "$answer" =~ ^[Yy]$ ]] || exit 0

tap() { ${ADB[@]} shell input tap "$1" "$2" >/dev/null; }
key() { ${ADB[@]} shell input keyevent "$1" >/dev/null; }
launch_main() { ${ADB[@]} shell am start -n "$MAIN_ACTIVITY" >/dev/null; sleep 2; }

nav_home() {
  launch_main
  key KEYCODE_DPAD_UP; sleep 0.5; key KEYCODE_DPAD_RIGHT; sleep 0.5; key KEYCODE_DPAD_RIGHT; sleep 1
}
nav_live_tv_guide() {
  launch_main; tap 270 650; sleep 2; tap 1048 74; sleep 2
  key KEYCODE_DPAD_DOWN; sleep 0.5; key KEYCODE_DPAD_RIGHT; sleep 0.5; key KEYCODE_DPAD_LEFT; sleep 1
  key KEYCODE_BACK; sleep 1; tap 450 340; sleep 2; key KEYCODE_BACK
}
nav_activities() { launch_main; tap 390 650; sleep 3; key KEYCODE_DPAD_RIGHT; sleep 0.5; key KEYCODE_DPAD_LEFT; sleep 1; }
nav_games() { launch_main; tap 510 650; sleep 3; key KEYCODE_DPAD_UP; sleep 0.5; key KEYCODE_DPAD_RIGHT; sleep 1; }
nav_events() { launch_main; tap 635 650; sleep 3; key KEYCODE_DPAD_RIGHT; sleep 0.5; key KEYCODE_DPAD_LEFT; sleep 1; }
nav_saved() { launch_main; tap 760 650; sleep 3; key KEYCODE_DPAD_RIGHT; sleep 0.5; key KEYCODE_DPAD_LEFT; sleep 1; }
nav_news() {
  launch_main; tap 880 650; sleep 3; tap 600 410; sleep 3
  tap 145 646; sleep 5; key KEYCODE_BACK; sleep 1; key KEYCODE_BACK
}
nav_screensaver() {
  ${ADB[@]} shell am start -n "$PACKAGE/.ScreensaverSettingsActivity" >/dev/null
  sleep 2; tap 930 630; sleep 8; key KEYCODE_BACK
}

NAV_FUNCTIONS=(nav_home nav_live_tv_guide nav_activities nav_games nav_events nav_saved nav_news nav_screensaver)
RAW_FILES=()
cleanup_device_file() { ${ADB[@]} shell rm -f "$1" >/dev/null 2>&1 || true; }

record_scene() {
  local index="$1" name="${SCENE_NAMES[$1]}" duration="${SCENE_DURATIONS[$1]}"
  local remote="/sdcard/sva_tour_${name}_$$.mp4"
  local raw="$OUTPUT_DIR/$(printf '%02d' "$((index+1))")_${name}.mp4"
  local nav="${NAV_FUNCTIONS[$index]}"
  printf '\n[%d/%d] %s (%ss)\n' "$((index+1))" "${#SCENE_NAMES[@]}" "$name" "$duration"
  ${ADB[@]} shell screenrecord --size "${WIDTH}x${HEIGHT}" --bit-rate "$BIT_RATE" --time-limit "$duration" "$remote" >/dev/null 2>&1 &
  local recorder_pid=$!
  sleep 1
  "$nav" || { kill "$recorder_pid" 2>/dev/null || true; cleanup_device_file "$remote"; return 1; }
  sleep 1
  wait "$recorder_pid" || true
  ${ADB[@]} pull "$remote" "$raw" >/dev/null || die "Failed to pull scene $name."
  cleanup_device_file "$remote"
  [[ -s "$raw" ]] || die "Scene $name produced an empty video."
  RAW_FILES+=("$raw")
  ffmpeg -y -i "$raw" -vf 'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2' \
    -c:v libx264 -preset fast -crf 19 -pix_fmt yuv420p -movflags +faststart \
    "$OUTPUT_DIR/$(printf '%02d' "$((index+1))")_${name}_1080p.mp4" >/dev/null 2>&1
}

for i in "${!SCENE_NAMES[@]}"; do record_scene "$i"; done

CONCAT_LIST="$OUTPUT_DIR/feature-tour-concat.txt"
FINAL="$OUTPUT_DIR/sva-feature-tour.mp4"
: > "$CONCAT_LIST"
for file in "${RAW_FILES[@]}"; do printf "file '%s'\n" "$file" >> "$CONCAT_LIST"; done
ffmpeg -y -f concat -safe 0 -i "$CONCAT_LIST" -c:v libx264 -preset fast -crf 19 \
  -pix_fmt yuv420p -movflags +faststart "$FINAL" >/dev/null 2>&1
printf '\nTour ready: %s\nIndividual scene MP4s: %s\n' "$FINAL" "$OUTPUT_DIR"
