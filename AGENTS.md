# AGENTS.md

## Big picture
- **SenseQuant** (formerly StockSense) is a single Android app module (Kotlin + Compose) with **manual DI** in `StockSenseApp.kt`.
- Runtime flow: UI (`ui/screens/*`) → ViewModels (`viewmodel/*`) → data (`data/repository`, `engine`) → Room (`data/database`) + WorkManager (`workers/*`).
- `StockRepository` is the stock-data source of truth: local Room first, conditional refresh via `MarketDataRouter`.
- Prediction: on-device TFLite (`PredictionEngine`). Insight/chat: local LLM via llama.cpp JNI when available, else template fallback (`LLMInsightEngine`). Portfolio analysis: `LLMInsightEngine.analyzePortfolio()`.

## Critical service boundaries
- App-wide singletons wired in `StockSenseApp.onCreate()`: DB, repository, engines, alert manager, ingestion, workers.
- Room schema v3, 12 entities, `fallbackToDestructiveMigration()`.
- External integrations:
  - Yahoo Finance HTTP (`YahooFinanceProvider`) for quotes/history.
  - HuggingFace GGUF download (`BitNetModelDownloader`) → `<filesDir>/models/`.
  - Optional JNI llama bridge (`LlamaCpp.kt` + `cpp/llama_jni.cpp`) behind runtime checks.
- First-launch: `UserPreferencesManager.isInitialSetupComplete()` gated in `MainActivity`. `InitialSetupViewModel` **auto-completes** if SmolLM2 bundled model is detected on disk.
- Auth: local PIN-based login stored via Jetpack DataStore (`UserPreferencesManager`). PINs are SHA-256 hashed before storage. `AuthViewModel` drives `LoginScreen`/`RegisterScreen`. The nav graph starts at `Screen.Login` when `!isLoggedIn`; bottom nav is hidden on auth routes. Boot flow: `BootSplashScreen` (3 s) → `InitialSetupScreen` (first launch only) → auth check → main app.

## Portfolio & XLSX import (new)
- `PortfolioScreen` has 3 tabs: **Holdings** (manual trades), **Imported** (XLSX), **Analysis** (AI agent).
- File picker uses `ActivityResultContracts.GetContent()` → `viewModel.importFromXlsx(ctx, uri)`.
- Two broker formats supported:
  - **Mutual Funds** (`Holdings_Statement_*.xlsx`): headers at row 20, data from row 22. Fields: Scheme Name, AMC, Category, Sub-category, Units, Invested Value, Current Value, Returns, XIRR.
  - **Stocks** (`Stocks_Holdings_Statement_*.xlsx`): headers at row 10, data from row 11. Fields: Stock Name, ISIN, Quantity, Avg buy price, Buy value, Closing price, Closing value, Unrealised P&L.
- `XlsxReader` (in `util/`) is a zero-dependency XLSX parser using `ZipInputStream` + `XmlPullParser`.
- `PortfolioXlsxParser` auto-detects format and returns `ParseResult.MutualFunds` or `ParseResult.Stocks`.
- AI analysis calls `LLMInsightEngine.analyzePortfolio(summary)` → template fallback if no model loaded.

## Bundled SmolLM2 model (runtime download — no longer bundled in APK)
- Models are downloaded at runtime via `InitialSetupScreen` on first launch.
- The `.gguf` file is **never committed to git** (exceeds GitHub's 100 MB limit). `*.gguf` is in `.gitignore`.
- `BitNetModelDownloader.copyBundledModelIfNeeded()` is deprecated/no-op; asset-copy path removed.
- `InitialSetupViewModel.init` auto-completes setup only if a previously-downloaded `.gguf` is found in `filesDir/models/`.
- SmolLM2 135M Q4_K_M (~80 MB) is pre-selected for low-RAM devices; user can pick a larger model from the setup list.

## Core execution paths to preserve
- Prediction: `PredictionViewModel.runPrediction()` → `ModelManager.ensureLoaded()` → `PredictionEngine.predict()`.
- Agentic prediction: `AgenticPipeline.predict()` runs 5 agents in sequence (DataEnrichment → AdaptiveLearning → Ensemble → PredictionEvaluator); `OutcomeEvaluatorAgent` is called separately via `AgenticPipeline.evaluateOutcome()`.
- Insight: `InsightsViewModel.generateInsight()` → LLM gate → `llmEngine.generateInsight()`.
- Chat: `ChatViewModel.sendMessage()` → load model → `llmEngine.chat()` → persist in `ChatMessageDao`.
- Background workers (all use `ExistingPeriodicWorkPolicy.KEEP` or `ExistingWorkPolicy.KEEP`):
  - `DataSyncWorker` (15 min periodic) — seeds DB, refreshes tracked stock quotes, evaluates alerts. Skips if battery < 15%.
  - `LearningWorker` (daily) — calls `LearningEngine.resolvePredictions()` to close pending predictions and update per-symbol adaptive weights (EMA + sigmoid mapping); prunes records > 90 days.
  - `ModelDownloadWorker` (one-time) — downloads the appropriate BitNet GGUF based on `QualityMode` auto-selected from device RAM; safe to re-enqueue (no-op if model present).
  - `PredictionWorker` (on-demand) — background ML inference for a symbol; enqueued via `PredictionWorker.schedule(ctx, symbol)`.
- Portfolio analysis: `PortfolioViewModel.analyzePortfolio()` → `llmEngine.analyzePortfolio()` (or template fallback).
- LLM quality mode: `QualityMode` (LITE / BALANCED / PRO) is auto-selected by RAM (≥8 GB → PRO, 6–8 GB → BALANCED, <6 GB → LITE) in `ModelManager`, `ModelDownloadWorker`, and `LlmSettingsViewModel`. Users can also download or import a specific GGUF; local import validates GGUF magic bytes `0x47455546` before copy.

## Project-specific patterns
- Manual DI only — no Hilt. All new dependencies wired in `StockSenseApp`.
- ViewModels use `MutableStateFlow` + immutable data class; update with `.update { it.copy(...) }`.
- Graceful degradation: LLM unavailable → template; model not loaded → heuristic fallback.
- Symbol normalisation via `MarketDataRequest.normalized*` + `.NS/.BO` exchange inference.
- WorkManager jobs are `ExistingWorkPolicy.KEEP` — idempotent scheduling.
- Bottom nav: Dashboard → Watchlist → **Portfolio** → SenseAI (logo icon) → Alerts → Profile.
- `LearningEngine` maintains per-symbol `LearningData` rows: resolves prediction errors against actual prices, computes EMA of error, maps to adaptive weight via sigmoid (0 % error → 1.2, 20 %+ → 0.6). Weight is applied by `AgenticPipeline.AdaptiveLearningAgent`.
- `FeatureEngineering` (pure-Kotlin object): computes SMA/EMA/ATR/ADX/RSI/Bollinger/MACD, data quality score, and market regime detection (`bull`/`bear`/`sideways`/`volatile`) used by `DataEnrichmentAgent`.

## Branding
- App name: **QuantSense** (tagline: "Quantified Intelligence · Precision Wealth").
- Kotlin package stays `com.stocksense.app` — do NOT rename package dirs.
- UI strings use "QuantSense"; class/function names retain `StockSense*` prefix for stability.
- App icon: adaptive icon using `ic_app_logo.png` (= `stock_sense_logo.png`) with 3D layer-list foreground.
- Boot screen: `IMG_0234.PNG` → `IMG_0233.PNG` futuristic morph transition + LLM particle background.

## Developer workflows
- Debug build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Release (ABI splits + universal): `./gradlew assembleRelease`
- Override version: `./gradlew assembleRelease -Pstocksense.versionName=1.2.0 -Pstocksense.versionCode=10200`
- Enable native llama build: `./gradlew assembleRelease -Pstocksense.enableNativeLlm=true`
- CI: `.github/workflows/android.yml` — builds debug on every push, release on `main`.
- **Dependency pinning**: Compose BOM is pinned to `2024.12.01` (Kotlin 2.1.0-compatible). Upgrading to BOM ≥ 2026.03.01 requires Kotlin 2.3+. Room is pinned to `2.6.1` to avoid a `kotlinx-serialization` schema conflict introduced in Room 2.8.x. Do not bump these without verifying full compatibility.

## High-value files
- App composition: `StockSenseApp.kt`, `MainActivity.kt`
- Boot + setup: `ui/screens/BootSplashScreen.kt`, `ui/screens/InitialSetupScreen.kt`, `viewmodel/InitialSetupViewModel.kt`
- Auth: `viewmodel/AuthViewModel.kt`, `ui/screens/LoginScreen.kt`, `ui/screens/RegisterScreen.kt`
- Navigation: `ui/navigation/Navigation.kt`, `ui/navigation/Screen.kt`
- Portfolio: `ui/screens/PortfolioScreen.kt`, `viewmodel/PortfolioViewModel.kt`
- XLSX parsing: `util/XlsxReader.kt`, `util/PortfolioXlsxParser.kt`
- Imported data models: `data/model/ImportedHolding.kt`
- ML/LLM: `engine/PredictionEngine.kt`, `engine/LLMInsightEngine.kt`, `engine/AgenticPipeline.kt`
- Feature engineering: `engine/FeatureEngineering.kt`
- Learning / adaptive weights: `engine/LearningEngine.kt`
- Model bundling: `engine/BitNetModelDownloader.kt`, `scripts/download_smollm2.sh`
- LLM settings: `ui/screens/LlmSettingsScreen.kt`, `viewmodel/LlmSettingsViewModel.kt`
- Background: `workers/*.kt`, `ingestion/DataIngestion.kt`
- DB: `data/database/AppDatabase.kt`, `app/schemas/`
