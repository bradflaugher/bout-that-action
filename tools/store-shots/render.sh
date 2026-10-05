#!/usr/bin/env bash
# Renders the Play listing's screenshots: real game scenes and real Compose menus at a
# shape that fits the caption frame, then captions them into fastlane's image folders.
#
#   tools/store-shots/render.sh            # render everything, then caption
#   tools/store-shots/render.sh --caption  # caption the raw shots already in build/store-raw
#
# Needs the Android SDK (for Gradle) and uv (it runs caption.py with Pillow).
set -euo pipefail
cd "$(dirname "$0")/../.."

# device  game size  menu qualifiers (the same screen: Play's 9:16 phone, 7" and 10" tablet)
DEVICES=(
  "phone 1080x1920 w411dp-h731dp-420dpi"
  "seven 1200x1920 w600dp-h960dp-xhdpi"
  "ten   1600x2560 w800dp-h1280dp-xhdpi"
)
MENUS="phone-title-daily,phone-board,phone-heroes-monkey"

if [[ "${1:-}" != "--caption" ]]; then
  for d in "${DEVICES[@]}"; do
    read -r name size quals <<<"$d"
    out="build/store-raw/$name"
    rm -rf "$out"
    ./gradlew -q :app:screenshots -x menuShots -Pata.full=true -Pata.size="$size" -Pata.shots="$out"
    ./gradlew -q :app:menuShots -Pphone="$quals" -Pata.menushots="$out" -Ponly="$MENUS"
  done
fi

uv run --quiet tools/store-shots/caption.py
