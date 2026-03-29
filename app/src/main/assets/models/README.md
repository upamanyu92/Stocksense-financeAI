# Bundled Nano Model — Local Testing

Place the SmolLM2-135M GGUF here to embed it inside the APK.
No network download is required at runtime when this file is present.

## File to download

| File | Size | Source |
|------|------|--------|
| `smollm2-135m-instruct-v0.2-q4_k_m.gguf` | ~80 MB | https://huggingface.co/HuggingFaceTB/smollm2-135M-instruct-v0.2-GGUF |

## Quick download

```bash
curl -L -o app/src/main/assets/models/smollm2-135m-instruct-v0.2-q4_k_m.gguf \
  "https://huggingface.co/HuggingFaceTB/smollm2-135M-instruct-v0.2-GGUF/resolve/main/smollm2-135m-instruct-v0.2-q4_k_m.gguf"
```

## How it works

On every app launch `BitNetModelDownloader.copyBundledModelIfNeeded()` checks whether
`assets/models/smollm2-135m-instruct-v0.2-q4_k_m.gguf` exists in the APK.
If it does, the file is copied once to `<filesDir>/models/` and the LLM engine
loads it from there — exactly like a downloaded model, but with zero network traffic.

If the file is absent (normal release builds), the call is a silent no-op.

## Why this model?

- **~80 MB** — the only LLM small enough to fit in an APK asset without splitting
- **Q4_K_M quantisation** — ~4-bit, fast inference on ARM64
- **SmolLM2-135M-Instruct** — instruction-tuned, reasonable for Q&A and chat
- Works with the existing llama.cpp JNI bridge unchanged

## Notes

- `aaptOptions { noCompress "gguf" }` in build.gradle prevents compression
  so Android's AssetManager can open the file via a direct file descriptor.
- The GGUF is gitignored (see root .gitignore) — do not commit it to source control.
- For production, remove this file and let users download their preferred model.

