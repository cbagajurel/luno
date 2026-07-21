#!/usr/bin/env bash
# Converts raw device captures in assets/screenshots/ into Play-compliant
# phone screenshots under android/fastlane/metadata/, ready for `fastlane
# metadata`.
#
# Play rejects a screenshot whose long side exceeds twice its short side. A
# modern phone is 20:9 (2.22), so a raw capture is always rejected — it has to
# be letterboxed onto a compliant canvas. 1080x1920 is Play's recommended size
# and the padding uses the app's own background colour, so the bars are
# invisible against the UI.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO_ROOT/assets/screenshots"
DEST="$REPO_ROOT/android/fastlane/metadata/android/en-US/images/phoneScreenshots"

CANVAS_W=1080
CANVAS_H=1920
BACKGROUND="#0E1113"

# Upload order is filename order, and the first screenshot is the one shoppers
# actually see. Add a name here to include it; anything not listed is skipped.
ORDER=(pair dashboard messages settings)

command -v magick >/dev/null || {
  echo "error: ImageMagick not found. brew install imagemagick" >&2
  exit 1
}

mkdir -p "$DEST"
rm -f "$DEST"/*.png

index=1
for name in "${ORDER[@]}"; do
  src="$SRC/$name.png"
  if [ ! -f "$src" ]; then
    echo "warning: $src not found, skipping" >&2
    continue
  fi

  out=$(printf '%s/%02d_%s.png' "$DEST" "$index" "$name")

  # PNG24 strips the alpha channel, which Play rejects. -strip drops metadata
  # that would otherwise leak the capture device and timestamps.
  magick "$src" \
    -resize "x${CANVAS_H}" \
    -background "$BACKGROUND" \
    -gravity center \
    -extent "${CANVAS_W}x${CANVAS_H}" \
    -strip \
    "PNG24:$out"

  printf '  %-28s %s\n' "$(basename "$out")" "$(magick identify -format '%wx%h %[channels]' "$out")"
  index=$((index + 1))
done

count=$(find "$DEST" -name '*.png' | wc -l | tr -d ' ')
echo
echo "$count screenshot(s) in $DEST"
[ "$count" -ge 2 ] || echo "warning: Play requires at least 2 phone screenshots." >&2
