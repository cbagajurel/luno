#!/usr/bin/env bash
# Checks every Play store graphic against Google's published constraints, so a
# bad asset fails here rather than after a twenty-minute build and upload.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGES="$REPO_ROOT/android/fastlane/metadata/android/en-US/images"

command -v magick >/dev/null || {
  echo "error: ImageMagick not found. brew install imagemagick" >&2
  exit 1
}

fail=0
note() { printf '  %-8s %s\n' "$1" "$2"; }

# Play rejects any alpha channel outright, and any side outside 320..3840.
# %A reports Undefined when there is no alpha.
check_image() { # path, expected_w, expected_h ("" = any), label
  local f=$1 want_w=$2 want_h=$3 label=$4
  if [ ! -f "$f" ]; then
    note "MISSING" "$label — $(basename "$f")"
    fail=1
    return
  fi

  local w h alpha kb problems=()
  w=$(magick identify -format '%w' "$f")
  h=$(magick identify -format '%h' "$f")
  alpha=$(magick identify -format '%A' "$f")
  kb=$(( $(wc -c < "$f") / 1024 ))

  [ "$alpha" != "Undefined" ] && problems+=("has alpha channel")
  [ "$kb" -ge 8192 ] && problems+=("${kb}KB exceeds 8MB")

  if [ -n "$want_w" ]; then
    # Fixed-size assets. The icon and feature graphic must match Play's exact
    # dimensions; the 320-3840 range and the 2:1 ratio cap are screenshot rules
    # and do not apply to them — a 1024x500 feature graphic is 2.05 by design.
    [ "$w" != "$want_w" ] && problems+=("width $w != $want_w")
    [ "$h" != "$want_h" ] && problems+=("height $h != $want_h")
  else
    awk -v w="$w" -v h="$h" 'BEGIN{exit !(w<320||h<320||w>3840||h>3840)}' \
      && problems+=("side outside 320-3840")
    awk -v w="$w" -v h="$h" 'BEGIN{r=(h>w?h/w:w/h); exit !(r>2.0)}' \
      && problems+=("aspect ratio exceeds 2:1")
  fi

  if [ ${#problems[@]} -eq 0 ]; then
    note "ok" "$label — ${w}x${h}, ${kb}KB"
  else
    note "FAIL" "$label — $(IFS='; '; echo "${problems[*]}")"
    fail=1
  fi
}

echo "Store graphics:"
check_image "$IMAGES/icon.png" 512 512 "icon"
check_image "$IMAGES/featureGraphic.png" 1024 500 "feature graphic"

echo
echo "Phone screenshots:"
shots=("$IMAGES/phoneScreenshots"/*.png)
if [ ! -e "${shots[0]}" ]; then
  note "FAIL" "none found — Play requires at least 2"
  fail=1
else
  for f in "${shots[@]}"; do
    check_image "$f" "" "" "$(basename "$f")"
  done
  count=${#shots[@]}
  if [ "$count" -lt 2 ]; then
    note "FAIL" "only $count screenshot(s); Play requires at least 2"
    fail=1
  elif [ "$count" -gt 8 ]; then
    note "FAIL" "$count screenshots; Play allows at most 8"
    fail=1
  fi
fi

echo
echo "Listing text:"
meta="$REPO_ROOT/android/fastlane/metadata/android/en-US"
check_text() { # file, limit, label
  local f="$meta/$1" limit=$2 label=$3
  if [ ! -f "$f" ]; then
    note "MISSING" "$label"
    fail=1
    return
  fi
  # Trailing newline is stripped by supply, so it must not count against the limit.
  local n
  n=$(printf '%s' "$(cat "$f")" | wc -m | tr -d ' ')
  if [ "$n" -le "$limit" ]; then
    note "ok" "$label — $n/$limit chars"
  else
    note "FAIL" "$label — $n chars exceeds $limit"
    fail=1
  fi
}
check_text title.txt 30 "title"
check_text short_description.txt 80 "short description"
check_text full_description.txt 4000 "full description"

echo
if [ "$fail" -eq 0 ]; then
  echo "All store assets satisfy Play's constraints."
else
  echo "Some assets need attention before uploading." >&2
fi
exit $fail
