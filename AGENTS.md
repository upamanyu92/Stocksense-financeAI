# AGENTS.md

## Big picture (read this first)
- `app` is a single Android app module (Kotlin + Compose) with **manual DI** anchored in `app/src/main/java/com/stocksense/app/StockSenseApp.kt`.
- Runtime flow is UI (`ui/screens/*`) -> ViewModels (`viewmodel/*`) -> data/engines (`data/repository`, `engine`) -> Room (`data/database`) + WorkManager (`workers/*`).
- `StockRepository` is the stock-data source of truth: local Room first, conditional provider refresh via `MarketDataRouter` (`data/remote/*`).
- Prediction is on-device TFLite (`PredictionEngine`); natural-language insight/chat is local BitNet via llama.cpp when available, else template fallback (`LLMInsightEngine`).

## Critical data + service boundaries
- App-wide singleton wiring lives in `StockSenseApp.onCreate()`: DB, repository, engines, alert manager, ingestion, and worker scheduling.
- Room schema (`AppDatabase`, version 3) includes 12 entities; migrations are currently `fallbackToDestructiveMigration()`.
- External integrations:
  - Yahoo Finance HTTP (`YahooFinanceProvider`) for quote/history/metadata.
  - HuggingFace model download (`BitNetModelDownloader`) into `<filesDir>/models`.
  - `BitNetModelDownloader.downloadWithProgress(url, callback)` — generic URL downloader that reports `(Float, Long, Long)` progress; used by `InitialSetupViewModel`.
  - Optional JNI llama bridge (`LlamaCpp.kt` + `cpp/llama_jni.cpp`) behind runtime checks.
- First-launch setup: `UserPreferencesManager.isInitialSetupComplete()` / `markInitialSetupComplete()` gated in `MainActivity` before `StockSenseNavGraph`.

## Core execution paths to preserve
- Prediction path: `PredictionViewModel.runPrediction()` -> `ModelManager.ensureLoaded()` -> `PredictionEngine.predict()` -> UI warning if model unavailable.
- Insight path: `InsightsViewModel.generateInsight()` -> LLM status gate -> `llmEngine.generateInsight()` or explicit user-facing unavailable message.
- Chat path: `ChatViewModel.sendMessage()` -> load model -> symbol extraction -> `llmEngine.chat()` -> persist in `ChatMessageDao`.
- Background sync path: `DataSyncWorker` seeds data, refreshes tracked quotes, and evaluates alerts.

## Project-specific patterns (follow these)
- Keep new dependencies manually injected via `StockSenseApp`; do not introduce Hilt patterns in one-off files.
- ViewModels use `MutableStateFlow` + immutable UI state data classes; update state with `.update { it.copy(...) }`.
- Prefer graceful degradation over hard failure (seen in `PredictionEngine` heuristic fallback and `LLMInsightEngine` template fallback).
- Keep symbol handling consistent with provider logic (`MarketDataRequest.normalized*` and `.NS/.BO` exchange inference).
- WorkManager jobs are uniquely named and mostly `Existing*Policy.KEEP`; preserve idempotent scheduling semantics.

## Developer workflows
- Debug build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Release build (ABI splits + universal): `./gradlew assembleRelease`
- Override packaged version: `./gradlew assembleRelease -Pstocksense.versionName=1.2.0 -Pstocksense.versionCode=10200`
- Enable native llama build: `./gradlew assembleRelease -Pstocksense.enableNativeLlm=true`

## High-value files for fast context
- App composition: `app/src/main/java/com/stocksense/app/StockSenseApp.kt`, `app/src/main/java/com/stocksense/app/MainActivity.kt`
- **First-launch setup**: `app/src/main/java/com/stocksense/app/ui/screens/InitialSetupScreen.kt`, `app/src/main/java/com/stocksense/app/viewmodel/InitialSetupViewModel.kt`
- Navigation/routes: `app/src/main/java/com/stocksense/app/ui/navigation/Navigation.kt`, `app/src/main/java/com/stocksense/app/ui/navigation/Screen.kt`
- Data + providers: `app/src/main/java/com/stocksense/app/data/repository/StockRepository.kt`, `app/src/main/java/com/stocksense/app/data/remote/providers/YahooFinanceProvider.kt`
- ML/LLM engines: `app/src/main/java/com/stocksense/app/engine/PredictionEngine.kt`, `app/src/main/java/com/stocksense/app/engine/LLMInsightEngine.kt`, `app/src/main/java/com/stocksense/app/engine/AgenticPipeline.kt`
- Background jobs + ingestion: `app/src/main/java/com/stocksense/app/workers/*.kt`, `app/src/main/java/com/stocksense/app/ingestion/DataIngestion.kt`
- Persistence: `app/src/main/java/com/stocksense/app/data/database/AppDatabase.kt`, `app/schemas/`
- LLM settings UI: `app/src/main/java/com/stocksense/app/ui/screens/LlmSettingsScreen.kt`, `app/src/main/java/com/stocksense/app/viewmodel/LlmSettingsViewModel.kt`

