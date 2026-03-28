# StockSense – FinanceAI Android

AI-powered stock prediction and analysis platform for Android. Uses Microsoft BitNet 1-bit LLM for on-device financial insight generation with TensorFlow Lite for ML-based stock trend prediction.

## Features

- 📊 **Premium Dashboard** – Real-time portfolio overview, sentiment gauges, agent debate threads
- 🤖 **Microsoft BitNet LLM** – On-device 1-bit LLM for natural-language stock insights (auto-downloads on first launch)
- 📈 **ML Predictions** – TensorFlow Lite stock trend prediction with adaptive confidence weighting
- 🔔 **Smart Alerts** – Price-above, price-below, change-percent, and prediction-signal notifications
- 🧠 **Adaptive Learning** – Self-improving prediction weights based on actual outcomes
- 🌙 **Dark Theme** – Premium dark UI with neon accent colors

## Architecture

| Component | Technology |
|-----------|-----------|
| UI | Jetpack Compose + Material 3 |
| LLM | Microsoft BitNet b1.58 via llama.cpp JNI |
| ML | TensorFlow Lite (on-device) |
| Database | Room (SQLite) |
| Background | WorkManager |
| DI | Manual (no Hilt) |

## Requirements

- **Android 13** (API 33) minimum
- **Android 14–16** supported
- **arm64-v8a** or **x86_64** device

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release APKs (split per ABI: arm64-v8a, x86_64, universal)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Enable native llama.cpp build (requires NDK + llama.cpp source)
./gradlew assembleRelease -Pstocksense.enableNativeLlm=true
```

## CI/CD

GitHub Actions workflow (`.github/workflows/android-deploy.yml`):

1. **test** – Unit tests and lint
2. **assemble** – Build split release APKs (arm64-v8a, x86_64, universal)
3. **docker** – Push APK-serving nginx container to GHCR (on `main`/tags)
4. **publish-release** – Create GitHub Release with APKs (on tags)

APKs are uploaded as workflow artifacts for download after each build.

## LLM Model Auto-Download

On first launch, the app schedules a background download of the BitNet 1-bit model:

| Device RAM | Quality Mode | Model File | ~Size |
|-----------|-------------|-----------|-------|
| < 6 GB | LITE | bitnet-b1.58-2B-4T-TQ1_0.gguf | ~400 MB |
| 6–8 GB | BALANCED | bitnet-b1.58-2B-4T-TQ2_0.gguf | ~600 MB |
| ≥ 8 GB | PRO | bitnet-b1.58-2B-4T-Q4_0.gguf | ~1.2 GB |

Download requires Wi-Fi and runs via WorkManager with exponential backoff retry.

## License

MIT
