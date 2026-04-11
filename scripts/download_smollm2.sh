#!/usr/bin/env bash
# download_smollm2.sh – Download SmolLM2-135M GGUF and bundle it into the APK assets.
#
# Usage:
#   ./scripts/download_smollm2.sh
#
# After running this script, build the APK:
#   ./gradlew assembleDebug
#
# The model is automatically copied to <filesDir>/models/ on first app launch
# via BitNetModelDownloader.copyBundledModelIfNeeded() — no network required.

set -euo pipefail

MODEL_FILE="smollm2-135m-instruct-v0.2-q4_k_m.gguf"
DEST="app/src/main/assets/models/${MODEL_FILE}"
URL="https://huggingface.co/HuggingFaceTB/smollm2-135M-instruct-v0.2-GGUF/resolve/main/${MODEL_FILE}"

echo "QuantSense — SmolLM2-135M bundler"
echo "==================================="

if [ -f "$DEST" ]; then
  SIZE=$(wc -c < "$DEST" | tr -d ' ')
  echo "✓ Model already present at $DEST (${SIZE} bytes). Skipping download."
  exit 0
fi

mkdir -p "$(dirname "$DEST")"

echo "Downloading SmolLM2-135M Q4_K_M (~80 MB) ..."
echo "Source: $URL"
echo ""

if command -v curl &>/dev/null; then
  curl -L --progress-bar -o "$DEST" "$URL"
elif command -v wget &>/dev/null; then
  wget -q --show-progress -O "$DEST" "$URL"
else
  echo "ERROR: Neither curl nor wget found. Please install one and retry."
  exit 1
fi

SIZE=$(wc -c < "$DEST" | tr -d ' ')
echo ""
echo "✓ Downloaded: $DEST (${SIZE} bytes)"
echo ""
echo "Build the APK to bundle the model:"
echo "  ./gradlew assembleDebug"
echo ""
echo "The model will be copied automatically on first app launch."
echo "No network download needed on the device."
