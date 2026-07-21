#!/usr/bin/env bash
# Builds the 1024x500 Play feature graphic from brand assets and real captures.
#
# Play requires this image and will not publish without it. It is composed here
# rather than by hand so a brand or copy change is a one-line edit and a re-run,
# and so it never drifts from the design system: the colours below are the same
# Graphite/Signal Teal values the app itself renders.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$REPO_ROOT/android/fastlane/metadata/android/en-US/images/featureGraphic.png"
SHOTS="$REPO_ROOT/assets/screenshots"
FONT="$REPO_ROOT/assets/fonts/Geist.ttf"
LOGO="$REPO_ROOT/assets/brand/play_store_icon_512.png"

W=1024
H=500
BG_TOP="#13181C"
BG_BOTTOM="#0A0D0F"
TEAL="#14B8A6"
TEXT="#F1F5F9"
MUTED="#94A3B0"

TITLE="Self-Hosted SMS Gateway"
SUB_1="Send and receive SMS through your own"
SUB_2="phone and SIM, from a backend you run."

command -v magick >/dev/null || { echo "error: ImageMagick not found." >&2; exit 1; }
[ -f "$FONT" ] || { echo "error: $FONT not found." >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# A phone: screenshot scaled, corners rounded via a copied alpha mask, then a
# soft drop shadow so it lifts off the background.
phone() { # source, height, output
  local src=$1 ph=$2 out=$3
  local pw radius
  pw=$(awk -v h="$ph" 'BEGIN{printf "%d", h*720/1600}')
  radius=$(awk -v w="$pw" 'BEGIN{printf "%d", w*0.09}')

  # Two separate invocations, deliberately. The mask is filled white because
  # CopyOpacity reads intensity as the new alpha, so a black rounded-rect
  # (ImageMagick's default fill) would erase the image. And rounding must be
  # written out before the shadow is added: chaining both in one pipeline makes
  # the result inherit the mask's grayscale colorspace, and the screenshot
  # renders as a featureless black slab. The PNG round-trip normalises it.
  local rounded="$TMP/rounded-$(basename "$out")"
  magick "$src" -resize "${pw}x${ph}!" \
    \( -size "${pw}x${ph}" xc:black -fill white \
       -draw "roundrectangle 0,0,$((pw-1)),$((ph-1)),$radius,$radius" \) \
    -alpha off -compose CopyOpacity -composite \
    "$rounded"

  magick "$rounded" \
    \( +clone -background black -shadow 55x14+0+10 \) \
    +swap -background none -layers merge +repage \
    "$out"
}

phone "$SHOTS/dashboard.png" 330 "$TMP/back.png"
phone "$SHOTS/pair.png" 392 "$TMP/front.png"

magick -size "${W}x${H}" "gradient:${BG_TOP}-${BG_BOTTOM}" \
  \( -size "${W}x${H}" xc:none -fill "$TEAL" \
     -draw "circle 830,250 830,40" -blur 0x110 \
     -channel A -evaluate multiply 0.20 +channel \) -compose over -composite \
  \( "$LOGO" -resize 60x60 \) -geometry +64+62 -composite \
  \( "$TMP/back.png" \) -geometry +812+112 -composite \
  \( "$TMP/front.png" \) -geometry +636+72 -composite \
  -font "$FONT" -fill "$TEXT" -pointsize 54 -interline-spacing 6 \
  -annotate +64+200 "$TITLE" \
  -fill "$MUTED" -pointsize 25 -interline-spacing 10 \
  -annotate +64+262 "$SUB_1" \
  -annotate +64+296 "$SUB_2" \
  -fill "$TEAL" -pointsize 20 \
  -annotate +64+392 "Open source · Apache-2.0" \
  -strip "PNG24:$OUT"

echo "$OUT"
magick identify -format '  %wx%h  alpha:%A  %b\n' "$OUT"
