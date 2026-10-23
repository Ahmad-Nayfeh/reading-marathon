#!/usr/bin/env bash
# Writes keystore.properties + the .jks from GitHub Secrets when they are present.
# Without them the release APK is signed with the debug key (still installable).
set -euo pipefail

if [ -n "${MARATHON_KEYSTORE_BASE64:-}" ]; then
  echo "$MARATHON_KEYSTORE_BASE64" | base64 -d > marathon-release.jks
  cat > keystore.properties <<PROPS
storeFile=marathon-release.jks
storePassword=${MARATHON_KEYSTORE_PASSWORD}
keyAlias=${MARATHON_KEY_ALIAS}
keyPassword=${MARATHON_KEY_PASSWORD}
PROPS
  echo "Release keystore decoded: the release APK will be signed with it."
else
  echo "No MARATHON_KEYSTORE_BASE64 secret: the release APK will be signed with the debug key."
fi
