#!/usr/bin/env bash
# Generates the Play upload key and the android/key.properties that Gradle reads.
#
# This is the *upload* key, not the app signing key. Play App Signing holds the
# real signing key; this one only proves uploads come from you. If it leaks or
# is lost, Google can reset it — which is the entire reason to opt in to Play
# App Signing rather than uploading a self-managed signing key.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE="$REPO_ROOT/android/upload-keystore.jks"
PROPERTIES="$REPO_ROOT/android/key.properties"
BASE64_FILE="$REPO_ROOT/android/upload-keystore.jks.base64"
ALIAS="upload"

if [ -e "$KEYSTORE" ]; then
  echo "error: $KEYSTORE already exists." >&2
  echo "Refusing to overwrite it — a replaced upload key means Play rejects" >&2
  echo "every subsequent upload until you request a key reset." >&2
  exit 1
fi

command -v keytool >/dev/null || {
  echo "error: keytool not found. Install a JDK 17 (e.g. brew install temurin@17)." >&2
  exit 1
}

# Generated, not prompted for. This password only ever guards a local file that
# is already gitignored and mode 600, so a memorable one buys nothing — while a
# typed one tends to be reused from elsewhere. It is never echoed: read it back
# out of key.properties when you need it.
# `head` bounds the read, `cut` trims. The reverse — piping /dev/urandom into
# `head -c 40` — SIGPIPEs `tr` the moment head is satisfied, which `pipefail`
# turns into a silent abort.
PASSWORD="$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9' | cut -c1-40)"

umask 077

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -keystore "$KEYSTORE" \
  -storetype JKS \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=Luno, OU=Nex NeoTech, O=Nex NeoTech, L=Kathmandu, C=NP"

cat > "$PROPERTIES" <<EOF
storePassword=$PASSWORD
keyPassword=$PASSWORD
keyAlias=$ALIAS
storeFile=$KEYSTORE
EOF

# Written to a file rather than stdout on purpose: the base64 *is* the signing
# key, and stdout ends up in shell scrollback, tmux buffers and CI logs.
base64 < "$KEYSTORE" | tr -d '\n' > "$BASE64_FILE"

chmod 600 "$KEYSTORE" "$PROPERTIES" "$BASE64_FILE"

cat <<EOF

Created (all three gitignored, mode 600):
  $KEYSTORE
  $PROPERTIES
  $BASE64_FILE

Back the keystore up somewhere durable. Losing it costs a support round-trip
with Google, not just a rebuild.

GitHub secrets, under Settings -> Environments -> play-store:

  PLAY_KEYSTORE_BASE64    pbcopy < $BASE64_FILE
  PLAY_KEYSTORE_PASSWORD  grep storePassword $PROPERTIES | cut -d= -f2-
  PLAY_KEY_ALIAS          $ALIAS
  PLAY_KEY_PASSWORD       same as PLAY_KEYSTORE_PASSWORD

Delete $(basename "$BASE64_FILE") once those are in GitHub — the keystore itself is
the copy worth keeping.
EOF
