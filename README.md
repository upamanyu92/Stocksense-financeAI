# QuantSense — FinanceAI Android

> **Quantified Intelligence · Precision Wealth**

AI-powered stock prediction and portfolio analysis platform for Android.
Language analysis uses NVIDIA Nemotron 3 Super through a backend gateway, with
Hugging Face hosted Nemotron fallback when NVIDIA quota is exhausted or unavailable.
TensorFlow Lite stock prediction remains on-device.

See [gateway setup and migration plan](gateway/README.md) for credentials,
quota/reset behavior, Android configuration, tests, and deployment requirements.

## Features

- 📊 **Premium Dashboard** – Real-time portfolio overview, sentiment gauges, agent debate threads
- 🤖 **Nemotron APIs** – NVIDIA primary, Hugging Face hosted fallback; provider credentials stay on the backend
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
| LLM | HTTPS gateway → NVIDIA Nemotron / Hugging Face |
| ML | TensorFlow Lite (on-device) |
| Database | Room (SQLite) – 13 entities |
| Background | WorkManager |
| DI | Manual (no Hilt) |
| Prediction | 5-Agent Agentic Pipeline |
| Feature Eng. | Pure Kotlin (rolling window math) |

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release APKs (split per ABI: arm64-v8a, x86_64, universal)
./gradlew assembleRelease

# Unit tests
./gradlew test

# Gateway routing tests (no provider calls)
(cd gateway && python -m unittest -v)
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
