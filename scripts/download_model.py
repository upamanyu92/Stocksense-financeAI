#!/usr/bin/env python3
"""Download SmolLM2-135M GGUF and place it in the APK assets directory."""

import os
import sys
import shutil

DEST_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "models")
TARGET_NAME = "smollm2-135m-instruct-v0.2-q4_k_m.gguf"
TARGET_PATH = os.path.join(DEST_DIR, TARGET_NAME)

os.makedirs(DEST_DIR, exist_ok=True)

if os.path.exists(TARGET_PATH) and os.path.getsize(TARGET_PATH) > 1_000_000:
    print(f"✓ Model already present: {TARGET_PATH} ({os.path.getsize(TARGET_PATH)/1024/1024:.1f} MB)")
    sys.exit(0)

try:
    from huggingface_hub import hf_hub_download
except ImportError:
    print("Installing huggingface_hub...")
    os.system(f"{sys.executable} -m pip install -q huggingface_hub")
    from huggingface_hub import hf_hub_download

# Try primary source
SOURCES = [
    ("HuggingFaceTB/smollm2-135M-instruct-v0.2-GGUF", "smollm2-135m-instruct-v0.2-q4_k_m.gguf"),
    ("bartowski/SmolLM2-135M-Instruct-GGUF", "SmolLM2-135M-Instruct-Q4_K_M.gguf"),
    ("second-state/SmolLM2-135M-Instruct-GGUF", "SmolLM2-135M-Instruct-Q4_K_M.gguf"),
]

for repo_id, filename in SOURCES:
    try:
        print(f"Trying {repo_id}/{filename} ...")
        tmp = hf_hub_download(
            repo_id=repo_id,
            filename=filename,
            local_dir=DEST_DIR,
        )
        # Rename to canonical name if needed
        if os.path.basename(tmp) != TARGET_NAME:
            shutil.copy(tmp, TARGET_PATH)
            os.remove(tmp)
        size_mb = os.path.getsize(TARGET_PATH) / 1024 / 1024
        print(f"✓ Downloaded: {TARGET_PATH} ({size_mb:.1f} MB)")
        sys.exit(0)
    except Exception as e:
        print(f"  Failed: {e}")

print("All sources failed. Please manually download from:")
print("  https://huggingface.co/HuggingFaceTB/smollm2-135M-instruct-v0.2-GGUF")
print(f"  Place as: {TARGET_PATH}")
sys.exit(1)

