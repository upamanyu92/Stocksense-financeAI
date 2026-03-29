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

## Portfolio & XLSX import (new)
- `PortfolioScreen` has 3 tabs: **Holdings** (manual trades), **Imported** (XLSX), **Analysis** (AI agent).
- File picker uses `ActivityResultContracts.GetContent()` → `viewModel.importFromXlsx(ctx, uri)`.
- Two broker formats supported:
  - **Mutual Funds** (`Holdings_Statement_*.xlsx`): headers at row 20, data from row 22. Fields: Scheme Name, AMC, Category, Sub-category, Units, Invested Value, Current Value, Returns, XIRR.
  - **Stocks** (`Stocks_Holdings_Statement_*.xlsx`): headers at row 10, data from row 11. Fields: Stock Name, ISIN, Quantity, Avg buy price, Buy value, Closing price, Closing value, Unrealised P&L.
- `XlsxReader` (in `util/`) is a zero-dependency XLSX parser using `ZipInputStream` + `XmlPullParser`.
- `PortfolioXlsxParser` auto-detects format and returns `ParseResult.MutualFunds` or `ParseResult.Stocks`.
- AI analysis calls `LLMInsightEngine.analyzePortfolio(summary)` → template fallback if no model loaded.

## Bundled SmolLM2 model (zero-download device setup)
- Asset path: `app/src/main/assets/models/smollm2-135m-instruct-v0.2-q4_k_m.gguf`
- Download before building: `./gradlew downloadBundledModel` OR `./scripts/download_smollm2.sh`
- On launch, `BitNetModelDownloader.copyBundledModelIfNeeded()` copies it to `filesDir/models/` once.
- `InitialSetupViewModel.init` checks for the file and marks setup complete — no download dialog shown.

## Core execution paths to preserve
- Prediction: `PredictionViewModel.runPrediction()` → `ModelManager.ensureLoaded()` → `PredictionEngine.predict()`.
- Insight: `InsightsViewModel.generateInsight()` → LLM gate → `llmEngine.generateInsight()`.
- Chat: `ChatViewModel.sendMessage()` → load model → `llmEngine.chat()` → persist in `ChatMessageDao`.
- Background sync: `DataSyncWorker` seeds data, refreshes quotes, evaluates alerts.
- Portfolio analysis: `PortfolioViewModel.analyzePortfolio()` → `llmEngine.analyzePortfolio()` (or template fallback).

## Project-specific patterns
- Manual DI only — no Hilt. All new dependencies wired in `StockSenseApp`.
- ViewModels use `MutableStateFlow` + immutable data class; update with `.update { it.copy(...) }`.
- Graceful degradation: LLM unavailable → template; model not loaded → heuristic fallback.
- Symbol normalisation via `MarketDataRequest.normalized*` + `.NS/.BO` exchange inference.
- WorkManager jobs are `ExistingWorkPolicy.KEEP` — idempotent scheduling.
- Bottom nav: Dashboard → Watchlist → **Portfolio** → SenseAI (logo icon) → Alerts → Profile.

## Branding
- App name: **SenseQuant** (tagline: "Quantified Intelligence · Precision Wealth").
- Kotlin package stays `com.stocksense.app` — do NOT rename package dirs.
- UI strings use "SenseQuant"; class/function names retain `StockSense*` prefix for stability.
- App icon: adaptive icon using `ic_app_logo.png` (= `stock_sense_logo.png`) with 3D layer-list foreground.
- Boot screen: `IMG_0234.PNG` → `IMG_0233.PNG` futuristic morph transition + LLM particle background.

## Developer workflows
- Debug build: `./gradlew assembleDebug`
- Bundle SmolLM2: `./gradlew downloadBundledModel` (run once before build)
- Unit tests: `./gradlew test`
- Release (ABI splits + universal): `./gradlew assembleRelease`
- Override version: `./gradlew assembleRelease -Pstocksense.versionName=1.2.0 -Pstocksense.versionCode=10200`
- Enable native llama build: `./gradlew assembleRelease -Pstocksense.enableNativeLlm=true`
- CI: `.github/workflows/android.yml` — builds debug on every push, release on `main`.

## High-value files
- App composition: `StockSenseApp.kt`, `MainActivity.kt`
- Boot + setup: `ui/screens/BootSplashScreen.kt`, `ui/screens/InitialSetupScreen.kt`, `viewmodel/InitialSetupViewModel.kt`
- Navigation: `ui/navigation/Navigation.kt`, `ui/navigation/Screen.kt`
- Portfolio: `ui/screens/PortfolioScreen.kt`, `viewmodel/PortfolioViewModel.kt`
- XLSX parsing: `util/XlsxReader.kt`, `util/PortfolioXlsxParser.kt`
- Imported data models: `data/model/ImportedHolding.kt`
- ML/LLM: `engine/PredictionEngine.kt`, `engine/LLMInsightEngine.kt`, `engine/AgenticPipeline.kt`
- Model bundling: `engine/BitNetModelDownloader.kt`, `scripts/download_smollm2.sh`
- Background: `workers/*.kt`, `ingestion/DataIngestion.kt`
- DB: `data/database/AppDatabase.kt`, `app/schemas/`
