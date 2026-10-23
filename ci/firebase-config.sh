#!/usr/bin/env bash
# The real google-services.json is never committed: CI decodes it from a secret, else uses the placeholder
# (the app then hides "shared marathon" and works fully on-device).
set -euo pipefail
if [ -n "${MARATHON_GOOGLE_SERVICES_BASE64:-}" ]; then
  echo "$MARATHON_GOOGLE_SERVICES_BASE64" | base64 -d > app/google-services.json
  echo "google-services.json: real"
else
  cp app/google-services.placeholder.json app/google-services.json
  echo "google-services.json: placeholder (shared marathons unavailable in this build)"
fi
