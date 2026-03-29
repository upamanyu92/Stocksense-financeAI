# SenseQuant — FinanceAI Android

> **Quantified Intelligence · Precision Wealth**

AI-powered stock prediction and portfolio analysis platform for Android. Uses Microsoft BitNet 1-bit LLM for on-device financial insight generation, TensorFlow Lite for ML-based stock trend prediction, and a multi-agent agentic prediction pipeline.

## Features

- 📊 **Premium Dashboard** – Real-time portfolio overview, sentiment gauges, agent debate threads
- 🤖 **On-Device LLM** – SmolLM2-135M bundled with APK (zero network required on first launch), BitNet models downloadable for deeper analysis
- 📈 **ML Predictions** – TensorFlow Lite stock trend prediction with adaptive confidence weighting
- 🧠 **Agentic Prediction Pipeline** – 5-agent orchestrated pipeline with trust scoring
- 💼 **Portfolio Management** – Holdings (manual trades), **Import XLSX** (broker statements), **AI Analysis** (agent evaluation of holdings vs market sentiment)
- 📂 **XLSX Import** – Auto-detects Mutual Fund (`Holdings_Statement_*.xlsx`) and Stock (`Stocks_Holdings_Statement_*.xlsx`) formats, no extra dependencies
- 🤖 **AI Portfolio Analysis** – LLM evaluates holdings, suggests exits/holds/rebalances
- ⭐ **Watchlist** – Personal stock watchlist with live prices
- 💬 **SenseAI Chat** – Conversational AI assistant with persistent history
- 🔔 **Smart Alerts** – Price-above, price-below, change-percent, prediction-signal
- 🌙 **Dark Theme** – Premium dark UI with neon accent colors

## Architecture

| Component | Technology |
|-----------|-----------|
| UI | Jetpack Compose + Material 3 |
| LLM | SmolLM2-135M (bundled) / BitNet via llama.cpp JNI |
| ML | TensorFlow Lite (on-device) |
| Database | Room (SQLite) – 12 entities |
| Background | WorkManager |
| DI | Manual (no Hilt) |
| Prediction | 5-Agent Agentic Pipeline |
| Feature Eng. | Pure Kotlin (rolling window math) |

## Build

```bash
# Bundle SmolLM2 model (run once before first build)
./gradlew downloadBundledModel
# OR
./scripts/download_smollm2.sh

# Debug build
./gradlew assembleDebug

# Release APKs (split per ABI: arm64-v8a, x86_64, universal)
./gradlew assembleRelease

# Unit tests
./gradlew test

# Enable native llama.cpp build (requires NDK)
./gradlew assembleRelease -Pstocksense.enableNativeLlm=true
```

## Portfolio XLSX Import

Supports broker exports from Groww and similar Indian platforms:

| Format | File Pattern | Data Start |
|--------|-------------|-----------|
| Mutual Funds | `Holdings_Statement_*.xlsx` | Row 22 |
| Stocks | `Stocks_Holdings_Statement_*.xlsx` | Row 11 |

Parsed by `XlsxReader` (zero-dependency ZIP+XML parser) + `PortfolioXlsxParser`.

## CI/CD

GitHub Actions (`.github/workflows/android.yml`) — debug build on every push, release APK on `main`.

## Requirements

- Android 13 (API 33) minimum · arm64-v8a or x86_64

## License

MIT
