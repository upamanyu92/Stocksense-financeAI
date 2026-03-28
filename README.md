# StockSense – FinanceAI Android

AI-powered stock prediction and analysis platform for Android. Uses Microsoft BitNet 1-bit LLM for on-device financial insight generation with TensorFlow Lite for ML-based stock trend prediction and a multi-agent agentic prediction pipeline.

## Features

- 📊 **Premium Dashboard** – Real-time portfolio overview, sentiment gauges, agent debate threads
- 🤖 **Microsoft BitNet LLM** – On-device 1-bit LLM for natural-language stock insights (auto-downloads on first launch)
- 📈 **ML Predictions** – TensorFlow Lite stock trend prediction with adaptive confidence weighting
- 🧠 **Agentic Prediction Pipeline** – 5-agent orchestrated pipeline: DataEnrichment → AdaptiveLearning → Ensemble (parallel LLM calls) → PredictionEvaluator → OutcomeEvaluator, with trust scoring and serving-action gating
- 📉 **Feature Engineering** – Pure-Kotlin technical indicators (SMA, EMA, RSI, MACD, Bollinger Bands, ATR, ADX, OBV, Stochastic, Fibonacci levels) with market regime detection
- ⭐ **Watchlist** – Personal stock watchlist with add/remove, live price display, and quick-predict access
- 💼 **Portfolio Management** – Track holdings, record BUY/SELL trades, view invested value, current value, and P&L with colour-coded gains/losses
- 💬 **AI Chat** – Conversational AI assistant for stock queries, predictions, and watchlist management with suggested prompts and persistent chat history
- 🔔 **Smart Alerts** – Price-above, price-below, change-percent, and prediction-signal notifications
- 🧠 **Adaptive Learning** – Self-improving prediction weights based on actual outcomes
- 🏆 **Gamification** – User levels, XP points, streak tracking, and badges
- 🌙 **Dark Theme** – Premium dark UI with neon accent colors

## Architecture

| Component | Technology |
|-----------|-----------|
| UI | Jetpack Compose + Material 3 |
| LLM | Microsoft BitNet b1.58 via llama.cpp JNI |
| ML | TensorFlow Lite (on-device) |
| Database | Room (SQLite) – 12 entities |
| Background | WorkManager |
| DI | Manual (no Hilt) |
| Prediction | 5-Agent Agentic Pipeline |
| Feature Eng. | Pure Kotlin (rolling window math) |

## Room Database Schema

| Entity | Purpose |
|--------|---------|
| `Stock` | Tracked stock symbols with latest price |
| `StockHistory` | OHLCV data points per stock |
| `Prediction` | ML prediction records with confidence and direction |
| `Alert` | User-defined price/prediction alert rules |
| `LearningData` | Per-symbol adaptive learning weights |
| `WatchlistItem` | User's personal watchlist |
| `PortfolioHolding` | Aggregated portfolio holdings with P&L |
| `Trade` | BUY/SELL trade history |
| `UserLevel` | Gamification: level, XP, streaks, badges |
| `NseSecurity` | NSE/BSE securities catalog |
| `ChatMessage` | AI chat conversation history |
| `SystemSetting` | Key-value app configuration |

## Screens & Navigation

| Screen | Route | Description |
|--------|-------|-------------|
| Dashboard | `dashboard` | Portfolio snapshot, sentiment, AI prediction cards, stock list |
| Watchlist | `watchlist` | Personal stock watchlist with add/remove |
| Portfolio | `portfolio` | Holdings, P&L, record trades |
| Prediction | `prediction/{symbol}` | 60-day chart, ML prediction result |
| Insights | `insights/{symbol}` | LLM-powered insights + quality mode selector |
| Alerts | `alerts` | Alert management (create/dismiss/delete) |
| Chat | `chat` | AI conversational assistant (via FAB) |
| Profile | `profile` | User settings, preferences, logout |

**Bottom Navigation:** Dashboard · Watchlist · Portfolio · Alerts · Profile
**Chat FAB:** Floating action button available on all screens except Chat

## Agentic Prediction Pipeline

Five coordinated agents run in sequence for each prediction:

1. **DataEnrichmentAgent** – Computes 20+ technical features via `FeatureEngineering`, data quality score (0.0–1.0)
2. **AdaptiveLearningAgent** – Detects market regime (bull/bear/volatile/sideways), maps to model preference + confidence adjustment
3. **EnsembleAgent** – Runs 2 parallel ML predictions (30-day technical + 60-day fundamental windows), combines via confidence-weighted average
4. **PredictionEvaluatorAgent** – Computes trust score, applies serving-action gate (PROCEED/PROCEED_WITH_CAUTION/SHADOW_ONLY/BLOCK_PREDICTION)
5. **OutcomeEvaluatorAgent** – Post-hoc evaluation feeding errors back to AdaptiveLearningAgent

**Trust Score:** `(confidence × 0.5) + (dataQuality × 0.3) + (1/(1+uncertainty) × 0.2)`

## Feature Engineering

Pure-Kotlin technical indicator calculations (no external libraries):

| Category | Indicators |
|----------|-----------|
| Moving Averages | SMA (20, 50), EMA (20, 50) |
| Momentum | RSI (14), MACD (12,26,9), Stochastic %K/%D |
| Volatility | Bollinger Bands (20, 2σ), ATR (14) |
| Trend | ADX (14), Market Regime Detection |
| Volume | OBV (On-Balance Volume) |
| Price Patterns | Fibonacci Retracement (38.2%, 50%, 61.8%) |

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

# Override the packaged version for a release build
./gradlew assembleRelease -Pstocksense.versionName=1.2.0 -Pstocksense.versionCode=10200

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

App version defaults live in `version.properties`. Tagged releases derive the packaged
version from the tag (for example `v1.2.0` → `versionName=1.2.0`) and rename APKs to
include the release version in the filename before publishing.

## LLM Model Auto-Download

On first launch the app presents a full-screen **Initial Setup Wizard** that:

1. **Detects device specs** (total RAM, Android version, architecture) and auto-selects the best-matched model.
2. Shows a **dropdown** with all supported models plus a `⭐ Best for You` recommendation badge.
3. Each model entry displays **key specs** (size, min RAM, quantization) and a detailed explanation of *why* that model suits the app's finance AI goals.
4. On confirmation, downloads the selected model with a **live progress bar** (animated shimmer gradient), current **download speed (MB/s)**, and **ETA**.
5. A pulsing **warning banner** prevents the user from closing the app mid-download.
6. After download or skip, the `isInitialSetupComplete` flag is persisted in DataStore — the wizard never appears again.

### Supported Models

| Model | Params | Quantization | Size | Min RAM | Best for |
|-------|--------|-------------|------|---------|----------|
| TinyLlama 1.1B | 1.1B | Q4_K_M | ~0.8 GB | 2 GB | Speed, basic Q&A |
| Phi-2 2.7B ⭐ | 2.7B | Q4_K_M | ~1.6 GB | 4 GB | Balanced — most devices |
| Llama-2 7B Chat | 7B | Q4_K_M | ~4.2 GB | 6 GB | Rich analysis |
| Mistral 7B Instruct v0.2 | 7B | Q4_K_M | ~4.1 GB | 6 GB | Power users / flagships |

Download runs in the foreground during setup (so the user stays on the progress screen). Background sync via `ModelDownloadWorker` (WorkManager) handles subsequent refreshes.

> The wizard is shown only once. To re-trigger it during development, clear app data or uninstall/reinstall.

## License

MIT
