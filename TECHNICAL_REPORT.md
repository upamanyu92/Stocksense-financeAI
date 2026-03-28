# StockSense — Detailed Technical & Functional Report
## For Android App Replication with Local LLM

---

## OVERVIEW

StockSense is an Indian equity market AI assistant. It targets NSE/BSE securities (symbols in `SYMBOL.NS` / `SYMBOL.BO` yfinance format), uses a multi-agent LLM pipeline for price prediction and chat, streams real-time prices via WebSocket, and persists all state in a local SQLite database. The web frontend is a JavaScript SPA rendered from Jinja2 templates, communicating with a Flask REST + Socket.IO backend.

---

## PART 1 — UI COMPONENTS & FUNCTIONAL DESCRIPTION

### 1.1 Login Page (`/login`) — `login.html`

**Functional Purpose:** Entry point for user authentication.

**UI Components:**

| Component | Type | Detail |
|-----------|------|--------|
| Username field | `<input type="text">` | Required, autofocus |
| Password field | `<input type="password">` | Show/hide toggle via JS |
| Login button | `<button>` | Spins on submit, disables re-click |
| Flash messages | `<div class="alert">` | Error / success from Flask flash |
| Register link | `<a href="/register">` | Navigate to register page |

**Design Language:** Dark glassmorphism, cyan (#00d4ff) primary, pink (#ff006e) accent, backdrop blur, Bootstrap 5, bounce/fade CSS animations.

**Backend Integration:**
- `POST /auth/login` — Flask-Login authenticates `username` + `password`. Bcrypt password comparison. Sets session cookie on success, redirects to `/premium`.
- `GET /auth/login` — Renders form. Flash messages carried from redirect.

---

### 1.2 Register Page (`/register`) — `register.html`

**Functional Purpose:** Create new user account.

**UI Components:** Same card style as login. Username, email, password, confirm-password fields.

**Backend Integration:**
- `POST /auth/register` — Creates user record with bcrypt-hashed password in `users` table, returns validation errors in flash.

---

### 1.3 Premium Dashboard (`/premium`) — `premium_dashboard.html` *(Main App Screen)*

This is a Single Page Application using `data-section` attributes to show/hide panels without page reloads. It is the primary surface of the application.

#### Left Sidebar Navigation

Ten menu items, each controlling a visible section:

| Menu Item | Icon | Target Section | Primary Function |
|-----------|------|----------------|-----------------|
| Dashboard | Gauge chart | `#overview-section` | Portfolio snapshot + AI feed |
| Markets | Line chart | `#markets-section` | Live index feeds |
| Watchlist | Star | `#watchlist-section` | Personal stock list |
| Predictions | Brain | `#predictions-section` | AI price predictions |
| Stock Detail | Candlestick | `#stock-detail-section` | Per-stock deep dive |
| Portfolio | Briefcase | `#portfolio-section` | Holdings + P&L |
| Risk Meter | Shield | `#risk-section` | Portfolio risk metrics |
| Alerts | Bell | `#alerts-section` | Price/prediction alerts |
| Data Hub | Satellite dish | `#data-hub-section` | System & background worker status |
| Settings | Gear | `#settings-section` | LLM and app configuration |

#### Top Header Bar
- **Search bar** — Debounced autocomplete calling `GET /api/stocks/search?q=<query>`. Opens stock detail panel on selection.
- **Notifications bell** — Badge count; click opens notification list from `GET /api/notifications/`.
- **User profile dropdown** — Displays username, level badge, logout button.

---

#### Section: Dashboard Overview (`#overview-section`)

**Widgets:**
1. **Portfolio Summary Card** — Total invested value, current value, absolute P&L, P&L %, holdings count. Colour-coded (green/red).
2. **Market Indices Feed** — Live chips for NIFTY 50, SENSEX, BANK NIFTY, NIFTY IT. Updates on poll.
3. **AI Insights Feed** — Scrollable list of LLM-generated insights: buy/sell signals, trend changes, anomalies, sentiment summaries. Each has a colour-coded type tag and confidence percentage.
4. **Predictions Summary** — Top 5 bullish / top 5 bearish AI predictions with symbol, predicted price, confidence bar.
5. **Recent Activity Timeline** — Chronological list: trade recorded, prediction made, alert triggered. Each entry has timestamp, symbol, and action type.
6. **User Level / Gamification Panel** — Level name (e.g., "Smart Investor"), XP points, predictions streak counter, badges earned.

**Backend APIs called:**
```
GET /api/dashboard/overview           → Portfolio totals
GET /api/dashboard/market-indices     → Index values & changes
GET /api/dashboard/ai-insights        → LLM insight records
GET /api/dashboard/predictions-summary → Top predictions
GET /api/dashboard/recent-activity    → Trade + prediction history
GET /api/dashboard/user-level         → XP, streak, badges
```

---

#### Section: Markets (`#markets-section`)

**Widgets:**
1. **Sector Heatmap** — Color-matrix of NSE sectors (IT, Banking, Auto, FMCG, Pharma, etc.) coloured by daily performance percentage (green=positive, red=negative). Rendered as a CSS grid.
2. **Market Sentiment Gauge** — Donut chart showing % bullish / bearish / neutral based on predictions in DB.
3. **Top Movers Table** — Gainers and losers tables. Sortable.

**Backend APIs:**
```
GET /api/dashboard/sector-heatmap     → Sector performance array
GET /api/dashboard/sentiment          → Bull/bear/neutral percentages
GET /api/av/top-gainers-losers        → yfinance-based top movers
```

**WebSocket events:** `price_update` — updates index values in real-time.

---

#### Section: Watchlist (`#watchlist-section`)

**Widgets:**
1. **Watchlist Table** — Symbol, company name, current price, change%, 52-week high/low. Add/remove buttons.
2. **Add Stock form** — Autocomplete input for adding symbol to watchlist.
3. **Quick Predict button** per row — Triggers agentic prediction for that symbol inline.

**Backend APIs:**
```
GET  /api/watchlist           → Fetch user's watchlist
POST /api/watchlist/add       → Add symbol
POST /api/watchlist/remove    → Remove symbol
GET  /api/stocks/quote/<sym>  → Real-time price for each symbol
```

**WebSocket:** `subscribe_watchlist` → `price_update` events refresh row prices live.

---

#### Section: Predictions (`#predictions-section`)

**Widgets:**
1. **Predictions Table** — Symbol, predicted price, current price, % predicted change, confidence score (0–100%), decision badge (ACCEPT / CAUTION / REJECT), market regime, last updated.
2. **Run All Predictions button** — POSTs to trigger batch. Shows real-time progress bar via WebSocket.
3. **Progress Modal** — WebSocket-driven; shows "Processing X of Y stocks…" with percentage.
4. **Filter/Sort controls** — Filter by decision, sort by confidence.

**Backend APIs:**
```
GET  /api/predictions/         → Paginated predictions list
POST /api/predictions/trigger  → Start batch prediction job
GET  /api/agentic/predict/<sym> → Single-symbol agentic prediction
GET  /api/agentic/performance  → Agentic system performance stats
```

**WebSocket events:**
- `prediction_progress` — `{current, total, symbol, status}` — progress bar update
- `prediction_update` — `{symbol, predicted_price, confidence, decision}` — per-row update

---

#### Section: Stock Detail (`#stock-detail-section`)

**Widgets:**
1. **Candlestick Chart** — OHLCV data rendered via Chart.js (financial plugin). Supports pan/zoom.
2. **Technical Indicators Overlay** — Toggleable lines: SMA 20, SMA 50, EMA 20, Bollinger Bands upper/lower. Secondary panel: RSI (0–100 with overbought/oversold lines), MACD line + signal + histogram.
3. **Stock Info Card** — Company name, industry, market cap (full + free-float), 52-week range, face value.
4. **Agent Reasoning Panel** — LLM-generated explanation of the prediction. Shows market sentiment, risk level, technical signal summary, full reasoning text.
5. **Kill Criteria Panel** — Signals for exiting an existing position: stop-loss hit, RSI overbought, trend reversal, etc.
6. **Paper Trade Form** — Simulate buy/sell without real execution. Shows simulated P&L.

**Backend APIs:**
```
GET /api/dashboard/candlestick/<sym>            → OHLCV array
GET /api/dashboard/technical-indicators/<sym>   → SMA, EMA, RSI, MACD, BB values
GET /api/stocks/details/<sym>                   → Full stock info
GET /api/dashboard/agent-reasoning/<sym>        → LLM reasoning text + metadata
GET /api/dashboard/kill-criteria/<sym>          → Exit signal evaluation
POST /api/dashboard/paper-trade                 → Simulate trade
```

---

#### Section: Portfolio (`#portfolio-section`)

**Widgets:**
1. **Holdings Table** — Symbol, company, quantity, average buy price, current price, current value, P&L (absolute + %), colour-coded.
2. **Portfolio Composition Donut** — Chart.js donut chart by sector or by stock weight.
3. **Record Trade Form** — Symbol, BUY/SELL, quantity, price fields. Submits to backend, updates `portfolio_holdings` table.

**Backend APIs:**
```
GET  /api/dashboard/portfolio       → Holdings list
POST /api/dashboard/record-trade    → Add trade, update holdings
```

---

#### Section: Risk Meter (`#risk-section`)

**Widgets:**
1. **Overall Risk Gauge** — Semicircular gauge chart, score 0–100, colour zones (green/yellow/orange/red).
2. **Risk Breakdown Cards** — VaR (Value at Risk at 95%), Max Drawdown, Sharpe Ratio, Beta.
3. **Position-level Risk Table** — Per holding risk contribution.

**Backend API:**
```
GET /api/dashboard/risk-meter   → {risk_score, var_95, max_drawdown, sharpe_ratio}
```

---

#### Section: Alerts (`#alerts-section`)

**Widgets:**
1. **Create Alert Form** — Symbol (autocomplete), Condition type dropdown (`price_above`, `price_below`, `predicted_change_above`), Value input, Minimum confidence slider.
2. **Alerts List** — All user alerts: symbol, condition, status (active/triggered), created date. Delete button.
3. **Notifications List** — All triggered notifications with read/unread state, mark-all-read button.

**Backend APIs:**
```
GET  /api/alerts/              → List alerts
POST /api/alerts/              → Create alert
POST /api/alerts/evaluate      → Trigger evaluation run
GET  /api/notifications/       → Notification list
POST /api/notifications/mark-read → Mark as read
```

**WebSocket event:** `alert_triggered` — Toast notification pushed to UI without page refresh.

---

#### Section: Data Hub / Admin (`#data-hub-section`)

**Widgets:**
1. **Background Worker Status** — Running / stopped status, last run time, next scheduled run.
2. **Start / Stop Worker buttons** — Control the background job.
3. **Disk Space Monitor** — Total disk, used, free, percentage. Model directory size.
4. **Model Cleanup button** — Remove old ML model artefacts.
5. **System Uptime** card.
6. **NSE Securities Loader** — Button to load `stk.json` into DB. Shows count of loaded securities.

**Backend APIs:**
```
GET  /api/system/status                         → Worker, disk, model stats
GET  /api/system/uptime                         → App uptime
POST /api/system/background_worker/start        → Start worker
POST /api/system/background_worker/stop         → Stop worker
POST /api/system/cleanup_models                 → Clean old models
POST /api/nse/load-securities                   → Load stk.json
GET  /api/nse/count                             → Total securities in DB
```

**WebSocket:** `request_system_status` → `system_status` event for live stats.

---

#### Section: Chat (`#chat-section`)

**Widgets:**
1. **Chat message list** — Scrollable bubble UI. User message right-aligned, AI response left-aligned. AI responses may include formatted markdown (tables, bold, lists).
2. **Message input** — Text box + Send button. Enter-to-send.
3. **Chat history** — Last 5 turns of conversation fetched on load.
4. **Suggested queries** — Quick-tap example prompts (e.g., "What is RELIANCE prediction?", "Add TCS to watchlist").

**Backend APIs:**
```
POST /api/chat/message    → Send message, receive AI response
GET  /api/chat/history    → Load recent conversation
```

---

### 1.4 Alerts Management (`/alerts`) — `alerts_mgmt.html`

Standalone lightweight page (43 lines). Minimal form for alert CRUD. Shares the same alert API endpoints as the dashboard alerts section.

### 1.5 Notifications (`/notifications`) — `notifications.html`

Full-page notifications list with read/delete controls.

### 1.6 Stocks List (`/stocks`) — `stocks_list.html`

**Purpose:** Browse all NSE securities loaded from `stk.json`.
- Sortable, filterable table with pagination.
- Search field (calls `GET /api/nse/search?q=<query>`).
- Click row → navigates to stock detail.

### 1.7 Admin System (`/admin`) — `admin_system.html`

System administration page with all Data Hub controls in a dedicated layout. Detailed system logs view.

---

## PART 2 — BACKEND CAPABILITIES (DETAILED)

---

### 2.1 Authentication & Session Management

**Stack:** Flask-Login, bcrypt password hashing, SQLite `users` table.

**Key mechanics:**
- Password hashed with `werkzeug.security.generate_password_hash` (pbkdf2:sha256).
- Session persisted in signed Flask cookie (`SECRET_KEY` env var). HttpOnly + SameSite=Lax by default; SECURE flag gated by `COOKIE_SECURE=true` env.
- `@login_required` decorator on all protected routes.
- `remember_me` support via Flask-Login's remember cookie.

**Database schema:**
```sql
users(id, username, password_hash, email, created_at, is_active)
```

---

### 2.2 Stock Data Pipeline (yfinance)

The app no longer uses Alpha Vantage. The `/api/av/*` routes are preserved for URL compatibility but internally call yfinance.

**Data flow:**
1. `yfinance_utils.get_quote_with_retry(symbol, max_retries=3)` — Fetches `yf.Ticker(symbol).info` + `fast_info` with 1s delay between retries.
2. Symbols are in `SYMBOL.NS` (NSE) or `SYMBOL.BO` (BSE) format.
3. Quote data is stored in `stock_quotes` table (53 fields including OHLC, 52-week range, market cap strings, industry, volume).
4. Historical OHLCV data: `yf.download(symbol, start='2010-01-01')` — full history up to 15 years.
5. `get_historical_data(symbol, period)` accepts period strings like `1y`, `2y`, `6mo`.

**Background download:** The background worker runs `_download_stocks()` daily, iterating all symbols in `stock_quotes` table, updating `current_value`, `change`, `p_change`, `updated_on`. Tracks `download_attempts` and `last_download_attempt` per symbol.

---

### 2.3 Feature Engineering (`app/features/feature_factory.py`)

Transforms raw OHLCV DataFrame into a rich feature matrix for LLM context and ML models.

**Feature groups:**

| Category | Features |
|----------|---------|
| Moving Averages | SMA (5, 10, 20, 50), EMA (5, 10, 20, 50) |
| Volatility | ATR(14), Bollinger Bands(20, 2σ), Vol(10/20/50), Volatility Ratio |
| Momentum | RSI(14), MACD(12,26,9), ROC(5,10,12,20), Stochastic K/D |
| Volume | OBV, Volume SMA(20), Volume Ratio |
| Price Patterns | Higher High, Lower Low |
| Fibonacci | Fib 38.2%, 50%, 61.8% retracement levels |
| Support/Resistance | Support_Level, Resistance_Level, Distance_to_Support/Resistance |
| Market Regime | ADX(14), Trend_Strength, Bull_Market, Market_Regime (trending/ranging) |
| Volatility Clustering | Squared_Returns, Volatility_Persistence (GARCH-like proxy) |
| Momentum Multi-TF | Momentum_(5,10,20,50), Momentum_Pct_(5,10,20,50), Momentum_Acceleration |
| Calendar | Day of Week, Month, Quarter |

**Adaptive period selection** based on recent volatility:
- High volatility → shorter periods `[3, 5, 10, 20]`
- Low volatility → longer periods `[10, 20, 50, 100]`
- Medium → default `[5, 10, 20, 50]`

---

### 2.4 Agentic Prediction System

This is the core intelligence layer. Five coordinated agents run in sequence for each prediction, orchestrated by `PredictionCoordinator`.

#### Agent 1: DataEnrichmentAgent (`app/agents/data_enrichment_agent.py`)

- Downloads full price history via `yf.download(symbol, start='2010-01-01')` — 1-hour in-memory cache per symbol.
- Applies `create_features()` from feature factory.
- Adds additional advanced features:
  - **Fibonacci retracement levels** (38.2%, 50%, 61.8%) over rolling 50-day high/low.
  - **Support and Resistance Levels** (rolling 20-day min/max).
  - **ROC** (Rate of Change) at 5, 10, 20 periods.
  - **ADX** (Average Directional Index) to classify trending vs. ranging.
  - **Volatility Clustering** — squared returns, volatility persistence.
  - **Multi-timeframe Momentum** — 5, 10, 20, 50 period momentum and acceleration.
  - **Stochastic Oscillator** (%K, %D).
- Computes a **data quality score** (0.0–1.0) based on: missing-value ratio, data recency (penalises if >30 days old), volume (optimal ≥1000 data points).

#### Agent 2: AdaptiveLearningAgent (`app/agents/adaptive_learning_agent.py`)

- **Market regime detection** from enriched data features:
  - `bull` — Close > SMA50, positive recent momentum.
  - `bear` — Close < SMA50, negative momentum.
  - `volatile` — High ATR relative to price.
  - `sideways` — ADX < 25, low directional movement.
- **Regime-to-strategy mapping:**

  | Regime | Model Preference | Confidence Boost |
  |--------|-----------------|-----------------|
  | bull | transformer | +0.10 |
  | bear | lstm | +0.05 |
  | sideways | ensemble | 0.00 |
  | volatile | ensemble | −0.10 |

- **Adaptive model weights** — Tracks error history per `transformer` and `lstm` pseudo-model types with a 0.95 decay rate. The model with lower historical error gets a higher weight in the ensemble.
- **State persistence** — Learning state (weights, performance history) saved to JSON on disk every 10 predictions; loaded at startup via `load_learning_state()`.

#### Agent 3: EnsembleAgent (`app/agents/ensemble_agent.py`)

- Calls **Ollama LLM twice** per prediction — once emphasising `technical` analysis, once `fundamental` analysis.
- Each call invokes `predict_with_details(symbol)` from `app/models/ollama_model.py`.
- Combines results using configurable method (default: `weighted_average`):
  - `weighted_average` — weights each prediction by its confidence score.
  - `average` — simple mean.
  - `voting` — median (robust to outliers).
- Calculates **95% prediction interval** = mean ± 1.96 × std of the two predictions.
- Final ensemble confidence = mean confidence penalised by inter-model variance:
  ```
  ensemble_confidence = mean_conf × max(0.5, 1 - std(conf) / mean_conf)
  ```

#### Agent 4: PredictionEvaluatorAgent (`app/agents/prediction_evaluator_agent.py`)

Evaluates prediction quality *before serving* to the user. Scores on a rubric:
- Confidence level vs. threshold.
- Data quality score.
- Consistency of prediction with current market regime.
- Width of prediction interval (narrow = more certain).
- Overall uncertainty (std of ensemble predictions).

**Serving actions it can recommend:**
| Action | Meaning |
|--------|---------|
| `proceed` | High quality — serve prediction |
| `proceed_with_caution` | Moderate quality — show warning |
| `shadow_only` | Poor quality — do not surface to user |
| `block_prediction` | Reject entirely — do not use |

#### Agent 5: OutcomeEvaluatorAgent (`app/agents/outcome_evaluator_agent.py`)

Called after actual next-day price is known:
- Computes absolute error, percentage error, directional accuracy.
- Scores the outcome.
- Feeds error back to AdaptiveLearningAgent (`learn_from_error`) to update model weights.

#### PredictionCoordinator — Orchestration (`app/agents/prediction_coordinator.py`)

**Trust score formula:**
```
trust_score = (confidence × 0.5) + (data_quality × 0.3) + (1/(1+uncertainty) × 0.2)
```

**Decision logic:**
- `trust_score ≥ 0.75` → `accept`
- `trust_score ≥ min_confidence` (default 0.6) → `caution`
- Below → `reject`

**Final serving_action** = stricter of `{decision-derived action, evaluator recommended action}`.

**Performance tracking per session:**
```json
{
  "total_predictions": 0,
  "high_confidence_predictions": 0,
  "validated_predictions": 0,
  "average_confidence": 0.0,
  "average_evaluation_score": 0.0,
  "average_outcome_score": 0.0,
  "blocked_predictions": 0
}
```

**Comprehensive result payload per prediction:**
```json
{
  "symbol": "RELIANCE.NS",
  "prediction": 2847.50,
  "confidence": 0.82,
  "base_confidence": 0.75,
  "confidence_adjustment": 0.07,
  "prediction_interval": [2790.0, 2905.0],
  "uncertainty": 29.5,
  "data_quality": 0.93,
  "market_regime": "bull",
  "adaptive_weights": {"transformer": 0.65, "lstm": 0.35},
  "decision": "accept",
  "serving_action": "proceed",
  "agents_used": [
    "DataEnrichmentAgent",
    "AdaptiveLearningAgent",
    "EnsembleAI",
    "PredictionEvaluatorAgent"
  ],
  "recommendation": "High confidence prediction (confidence: 0.82). Recommended for use.",
  "processing_time": 4.72,
  "timestamp": "2026-03-28T09:00:00"
}
```

---

### 2.5 LLM Integration Layer

#### Ollama (Local LLM) — Primary (`app/models/ollama_model.py`)

- **Host:** `http://localhost:11434` (configurable via `OLLAMA_HOST` env var)
- **Default model:** `phi-mini` (Microsoft Phi-3 Mini ~2.7B params, configurable via `OLLAMA_MODEL_NAME`)
- **API endpoint used:** `POST /api/generate`
- **Request parameters:**
  ```json
  {
    "model": "phi-mini",
    "prompt": "<enriched stock context + prediction request>",
    "stream": false,
    "temperature": 0.7,
    "top_p": 0.9,
    "top_k": 40,
    "num_predict": 500
  }
  ```
- **Example prompt template:**
  ```
  Analyze RELIANCE.NS stock.
  Current price: 2810. RSI: 62.3, MACD: bullish crossover.
  SMA20 above SMA50. Bollinger Bands: price in upper band.
  Volume 20% above 20-day average.
  Market regime: bull. Fibonacci 61.8%: 2790.
  Provide: predicted_price, confidence (0-1),
  decision (accept/caution/reject), reasoning.
  Return JSON only.
  ```
- **Response parsing:** Regex extracts JSON block from LLM text. Falls back to key-value line parsing if no JSON block found. Returns a safe fallback response if both fail.
- **Error handling:** 3 retries with 2-second delay. Falls back to GitHub Copilot if Ollama is unreachable.
- **Initialization:** `Config.initialize_ollama()` validates connection by hitting `/api/tags` and confirming the model name is listed. Called at app startup.

#### Gemini (Cloud) — Alternative (`app/models/gemini_model.py`)

- Enabled by setting `active_llm_agent=gemini` in `system_settings` table.
- Uses `google-genai` Python SDK (`google.genai`).
- Model: `gemini-2.0-flash` (configurable via `GEMINI_MODEL_NAME`).
- Downloads 2-year yfinance history, calculates all technical indicators, builds enriched context string.
- Calls Gemini with the `FEATURE_ANALYSIS_PROMPT` template (stored in `app/config/gemini_config.py`).
- Content safety filters all set to `BLOCK_NONE` for finance domain.
- Response parsed as JSON. 3 retries, 2-second delay.
- Returns same JSON structure as Ollama.

**Gemini configuration keys:**
```
GEMINI_API_KEY, GEMINI_MODEL_NAME, GEMINI_TEMPERATURE, GEMINI_TOP_P,
GEMINI_TOP_K, GEMINI_MAX_OUTPUT_TOKENS, GEMINI_MIN_CONFIDENCE,
GEMINI_HIGH_CONFIDENCE_THRESHOLD, GEMINI_MAX_RETRIES, GEMINI_RETRY_DELAY
```

#### GitHub Copilot (Cloud Agentic) — Fallback / Alternative (`app/api/copilot_routes.py`)

- Uses GitHub Models API (GPT-4o backbone) via `copilot_routes.py`.
- **Tool-calling agentic loop** (up to 10 iterations):
  1. `fetch_market_data(symbol)` — calls yfinance internally and returns structured JSON.
  2. `run_nlp_sentiment(symbol)` — calls news service for sentiment score.
  3. `generate_forecast_chart(data)` — builds a chart as base64.
- The LLM decides which tools to call and in what order.
- Returns a full Markdown report: step-by-step reasoning, data sources table, confidence score.
- Used as fallback when Ollama is unavailable and as a dedicated endpoint `GET /api/copilot/analyze/<symbol>`.

#### LLM Selection at Runtime

Stored in `system_settings` DB table under key `active_llm_agent`. Switchable via admin UI or `PUT /api/config/` without app restart. Supported values: `ollama`, `gemini`, `copilot`.

---

### 2.6 Chat Agent (`app/agents/chat_agent.py` + `app/services/ollama_chat_service.py`)

**System prompt structure fed to Ollama:**
```
You are a stock market assistant for Indian equities (NSE/BSE).
User's watchlist: [RELIANCE.NS, TCS.NS, ...]
Recent prices: {"RELIANCE.NS": 2810, "TCS.NS": 3450, ...}
Recent predictions: {"RELIANCE.NS": {"prediction": 2847, "confidence": 0.82, "decision": "accept"}}
Conversation history (last 5 turns):
  User: What is TCS prediction?
  AI: TCS predicted at 3490 with 78% confidence. Decision: accept.
Current query: <user message>
```

**Intent detection** (extracted from LLM response or via pattern matching):

| Intent | Action |
|--------|--------|
| `check_price` | Returns current price of detected symbol |
| `predict` | Triggers agentic prediction for symbol |
| `add_watchlist` | Adds symbol to user's watchlist |
| `remove_watchlist` | Removes symbol from watchlist |
| `run_prediction` | Triggers batch prediction run |
| `show_values` | Shows all watchlist prices |
| `list_stocks` | NSE securities search |
| `general` | Pure conversational response |

**Conversation persistence:** Stored in `chat_history` table: `(user_id, message, response, sentiment, timestamp)`. Last 5 turns fetched for context window.

**Fallback chain:** Ollama → GitHub Copilot → static error response.

---

### 2.7 Real-Time Price Streamer (`app/services/price_streamer.py`)

**Mechanism:** Background daemon thread. Wakes every N seconds (default 10s). For each symbol in the active subscription set, calls `yf.Ticker(symbol).fast_info` (lightweight, no full metadata). Emits `price_update` WebSocket event:
```json
{
  "symbol": "RELIANCE.NS",
  "price": 2812.45,
  "change": 4.50,
  "p_change": 0.16,
  "timestamp": "2026-03-28T10:05:00"
}
```

**Symbol management:** Symbols added/removed via WebSocket events `subscribe_stock_prices` / `unsubscribe_stock_prices` from the browser client. Internally managed as a thread-safe set.

**WebSocket subscription protocol:**
```javascript
// Client subscribes
socket.emit('subscribe_stock_prices', { symbols: ['RELIANCE.NS', 'TCS.NS'] });

// Server confirms
socket.on('subscription_confirmed', (data) => { /* type: 'stock_prices' */ });

// Server pushes updates
socket.on('price_update', (data) => { /* update UI */ });

// Client unsubscribes
socket.emit('unsubscribe_stock_prices', { symbols: ['RELIANCE.NS'] });
```

**Threading model:** `SocketIO(async_mode='threading')`. Price streamer = `threading.Thread` (daemon). Background worker = `threading.Thread` (daemon). Batch predictions = `ThreadPoolExecutor(max_workers=4)`.

---

### 2.8 Alert System (`app/services/alert_service.py`)

**Alert types:**

| Type | Trigger Condition |
|------|------------------|
| `price_above` | `current_value > condition_value` |
| `price_below` | `current_value < condition_value` |
| `predicted_change_above` | Predicted change % > threshold AND prediction confidence ≥ `min_confidence` |

**Evaluation flow:**
1. `alert_service.run_alerts_evaluation()` called by background worker each cycle.
2. Queries `stock_quotes` for current prices.
3. Queries `predictions` for latest predictions.
4. Compares each active alert's condition against current data.
5. On trigger: inserts into `notifications` table, sets `alerts.is_triggered=1`.
6. `websocket_manager.emit_alert_triggered(data)` pushes live notification to connected client.

**Alert schema:**
```sql
alerts(id, symbol, condition_type, condition_value, min_confidence, user_id, created_at, is_triggered)
notifications(id, user_id, alert_id, message, is_read, created_at)
```

---

### 2.9 Background Worker (`app/services/background_worker.py`)

**Execution sequence per cycle:**
1. `_download_stocks()` — Downloads all symbols in `stock_quotes`. Skips symbols with too many recent failed download attempts. Updates `current_value`, `change`, `p_change`, `updated_on`.
2. `_run_predictions()` — Runs agentic prediction for every symbol via `ThreadPoolExecutor(4 workers)`. Emits WebSocket progress events every 10 stocks:
   ```json
   {"current": 45, "total": 500, "symbol": "TCS.NS", "status": "processing"}
   ```
3. `_run_alerts()` — Evaluates all active alerts against fresh prices.
4. `_send_daily_digest()` — Builds daily summary report and emails it (if email configured).

**Lifecycle control:**
- `background_worker.start()` / `stop()` — controlled via admin UI or `POST /api/system/background_worker/start|stop`.
- Start/stop state persisted to a JSON file (not in DB) so it survives restarts.
- Uses interruptible `threading.Event.wait(timeout)` sleep for clean shutdown.

---

### 2.10 Database Layer

**Engine:** SQLite 3 with WAL (Write-Ahead Logging) mode enabled, connection pooling via `DatabaseSessionManager` singleton (`app/db/session_manager.py`).

**Access pattern:** All services use `get_session_manager().fetch_one()` / `fetch_all()` / `execute()` with parameterized queries. No raw string formatting in SQL.

**Migration system:** SQL files in `app/db/migrations/`, executed via `conn.executescript()` at app startup (`main.py` lines 38–61). All use `CREATE TABLE IF NOT EXISTS` for idempotency.

**Complete table catalogue:**

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `users` | Auth credentials | `username`, `password_hash`, `email`, `is_active` |
| `watchlists` | Per-user stock watchlists | `user_id`, `stock_symbol`, `display_order` |
| `stock_quotes` | Latest price quotes (53 fields) | `security_id`, `current_value`, `change`, `p_change`, `industry`, `market_cap_*` |
| `model_configurations` | ML hyperparameters per symbol | `model_type`, `num_heads`, `ff_dim`, `epochs`, `sequence_length` |
| `predictions` | Prediction records | `stock_symbol`, `predicted_price`, `prediction_date`, `user_id` |
| `trades` | Buy/sell trade history | `user_id`, `stock_symbol`, `trade_type`, `quantity`, `price`, `total_value` |
| `user_levels` | Gamification | `level_name`, `xp_points`, `predictions_made`, `streak_days`, `badges` (JSON) |
| `ai_insights` | LLM-generated insights | `insight_type`, `message`, `confidence`, `source`, `is_read` |
| `portfolio_holdings` | Aggregated holdings | `user_id`, `stock_symbol`, `quantity`, `current_value`, `invested_value`, `pnl` |
| `alerts` | Price/prediction alert rules | `symbol`, `condition_type`, `condition_value`, `min_confidence`, `is_triggered` |
| `notifications` | Triggered alert notifications | `user_id`, `alert_id`, `message`, `is_read` |
| `chat_history` | Conversation history | `user_id`, `message`, `response`, `sentiment`, `timestamp` |
| `system_settings` | Key-value app settings | `key`, `value` (e.g., `active_llm_agent=ollama`) |
| `nse_securities` | All NSE equities catalog | `code`, `name`, `industry` |

---

### 2.11 NSE Securities Catalog

**Data source:** `stk.json` — bundled static JSON file with all NSE/BSE listed equities. Fields: `code` (BSE scrip code), `name` (company name), `industry`, NSE symbol.

**Load mechanism:** `POST /api/nse/load-securities` reads the file and bulk-inserts into `nse_securities` table. Search: `GET /api/nse/search?q=<query>` — SQL LIKE query on name + code columns. Used for autocomplete throughout the UI.

---

### 2.12 Technical Indicator API (`/api/dashboard/technical-indicators/<symbol>`)

Returns pre-computed indicators for charting:
```json
{
  "sma_20": 2780.5,
  "sma_50": 2710.2,
  "ema_20": 2795.1,
  "rsi": 62.3,
  "macd": 15.4,
  "macd_signal": 12.1,
  "macd_histogram": 3.3,
  "bollinger_upper": 2890.0,
  "bollinger_lower": 2680.0,
  "bollinger_mid": 2785.0,
  "atr": 42.5,
  "adx": 28.7
}
```

---

### 2.13 Agent Reasoning API (`/api/dashboard/agent-reasoning/<symbol>`)

Returns the LLM's explanation for its prediction:
```json
{
  "reasoning": "RELIANCE.NS shows strong bullish momentum with RSI at 62.3...",
  "market_sentiment": "bullish",
  "risk_level": "medium",
  "technical_signals": "strong",
  "data_sources": ["yfinance 2-year history", "technical indicators"],
  "confidence": 0.82,
  "agents_used": ["DataEnrichmentAgent", "EnsembleAI"]
}
```

---

### 2.14 Kill Criteria API (`/api/dashboard/kill-criteria/<symbol>`)

Evaluates whether to exit an existing position:
```json
{
  "should_exit": false,
  "signals": ["RSI approaching overbought (62.3)", "Price near resistance (2890)"],
  "confidence": 0.45,
  "stop_loss_hit": false,
  "trend_reversal": false,
  "rsi_overbought": false
}
```

---

### 2.15 Backtester (`app/services/backtester.py`)

- Endpoint: `POST /api/backtest/<symbol>`
- Compares stored `predicted_price` values from the `predictions` table against actual historical closes from yfinance.
- Computes: directional accuracy (correct up/down), mean absolute error (MAE), mean absolute percentage error (MAPE), simulated return if traded on each signal.

---

### 2.16 Visualization Utilities (`app/utils/visualization_utils.py`)

Server-side charts generated as base64 PNG data URIs (used in daily digest email, not frontend):
- `plot_price_history(df, symbol)` — price line + volume bar, dual-panel layout.
- `plot_technical_indicators(df)` — SMA, RSI, MACD in subplots.
- `plot_candlestick(df)` — Japanese candlestick with volume.
- `plot_sector_heatmap(sectors)` — performance grid heatmap.
- `_fig_to_base64(fig)` — encodes matplotlib figure as data URI.

---

### 2.17 Security Headers & CORS

Applied to every response via `@app.after_request`:
```
X-Frame-Options: SAMEORIGIN
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
```

- CORS origins controlled by `CORS_ALLOWED_ORIGINS` env var; defaults to no cross-origin access.
- CSRF protection via Flask session cookie (SameSite=Lax).
- Proxy trust: one level via Werkzeug `ProxyFix` (for Render.com / reverse-proxy deployments).

---

### 2.18 Environment Configuration (`.env` / `.env.example`)

```bash
# LLM
OLLAMA_HOST=http://localhost:11434
OLLAMA_MODEL_NAME=phi-mini
OLLAMA_TEMPERATURE=0.7
OLLAMA_TOP_P=0.9
OLLAMA_TOP_K=40
OLLAMA_NUM_PREDICT=500
OLLAMA_MIN_CONFIDENCE=0.5
OLLAMA_HIGH_CONFIDENCE_THRESHOLD=0.8
OLLAMA_MAX_RETRIES=3
OLLAMA_RETRY_DELAY=2

# Gemini (if used)
GEMINI_API_KEY=...
GEMINI_MODEL_NAME=gemini-2.0-flash
GEMINI_TEMPERATURE=0.7

# Flask
FLASK_PORT=5005
SECRET_KEY=<strong-random-key>
COOKIE_SECURE=false        # Set true for HTTPS production
CORS_ALLOWED_ORIGINS=      # Comma-separated origins, empty = same-origin only
```

---

### 2.19 Deployment Architecture

**Local / Docker:**
- `python -m app.main` from repo root (port 5005).
- `Dockerfile` + `docker-compose.yml` provided.
- Automated DB migrations run at startup.
- Ollama runs as a separate sidecar container or local process.

**Health check:** `GET /health` returns `{"status": "healthy", "service": "stocksense"}`.

---

## PART 3 — ANDROID APP ARCHITECTURE GUIDE (Local LLM)

---

### 3.1 Local LLM on Android

The web app uses **Ollama** with `phi-mini` (Microsoft Phi-3 Mini, ~2.7B params, ~1.6GB Q4 quantised). For Android:

| Option | Library | Notes |
|--------|---------|-------|
| **llama.cpp via JNI** | `llama-android` (NDK binding) | Best performance; phi-mini GGUF runs ~8–15 tok/s on Snapdragon 8 Gen 2 |
| **MLC-LLM** | `mlc-llm` Android SDK | Pre-compiled TVM kernels; simpler integration |
| **Executorch** (Meta) | `pytorch-executorch` | Supports Llama 3.2 1B/3B quantized for mobile |
| **Ollama client** | REST client to local Ollama | Only if user installs Ollama separately |

**Recommended:** MLC-LLM or llama.cpp JNI with `phi3-mini-4k-instruct-q4_k_m.gguf`. The same plain-text prompts used in the web app can be used verbatim.

**Model download:** On first launch, download the quantized GGUF from HuggingFace into `Context.getFilesDir()`. Show a progress bar. Model size: ~1.6–2.3GB. Verify SHA256 checksum after download.

---

### 3.2 Data Layer

Use **Room** (Android Jetpack SQLite ORM). Tables map 1:1 to the web app schema. Room provides type-safe DAOs, `LiveData<T>` / `Flow<T>` for reactive UI, and schema migration support via `@Migration`.

**Key Room entities to implement:**
`User`, `Watchlist`, `StockQuote`, `Prediction`, `Alert`, `Notification`, `ChatHistory`, `PortfolioHolding`, `Trade`, `UserLevel`, `NseSecurity`, `SystemSetting`

---

### 3.3 Stock Data Fetching

Use Yahoo Finance v8 REST API via **Retrofit + OkHttp** (no API key required):

```
GET https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=2y
```

Same `SYMBOL.NS` / `SYMBOL.BO` symbol format as the web app. Parse the `chart.result[0]` JSON response for OHLCV arrays.

For live quotes (polling):
```
GET https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1m&range=1d
```

Bundle `stk.json` as an Android asset (`assets/stk.json`) and load into Room `nse_securities` table on first run.

---

### 3.4 Feature Engineering

Port `app/features/feature_factory.py` to Kotlin. Use pure math — no external library required beyond basic collections. Key algorithms to implement:

```kotlin
fun calcSma(close: DoubleArray, period: Int): DoubleArray
fun calcEma(close: DoubleArray, period: Int): DoubleArray
fun calcRsi(close: DoubleArray, period: Int = 14): DoubleArray
fun calcMacd(close: DoubleArray): Triple<DoubleArray, DoubleArray, DoubleArray>  // macd, signal, histogram
fun calcBollingerBands(close: DoubleArray, period: Int = 20): Triple<DoubleArray, DoubleArray, DoubleArray>
fun calcAtr(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): DoubleArray
fun calcAdx(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): DoubleArray
fun calcObv(close: DoubleArray, volume: DoubleArray): DoubleArray
fun calcStochastic(high: DoubleArray, low: DoubleArray, close: DoubleArray): Pair<DoubleArray, DoubleArray>
fun calcFibonacciLevels(high: DoubleArray, low: DoubleArray, window: Int = 50): Map<String, Double>
```

---

### 3.5 Agentic Prediction Pipeline

Port the 5-agent pipeline as Kotlin coroutine functions in a `PredictionRepository`:

```kotlin
suspend fun predict(symbol: String): PredictionResult {
    val enrichedData = dataEnrichmentAgent.enrich(symbol)           // Step 1
    val adaptiveResult = adaptiveLearningAgent.adapt(symbol, enrichedData)  // Step 2
    val ensembleResult = ensembleAgent.predict(symbol, enrichedData,         // Step 3
                          adaptiveResult.weights)                             //   (2x LLM calls, parallel)
    val evaluation = predictionEvaluator.evaluate(ensembleResult)            // Step 4
    val trustScore = calcTrustScore(ensembleResult.confidence,
                      enrichedData.qualityScore, ensembleResult.uncertainty)
    return compileResult(symbol, ensembleResult, adaptiveResult, evaluation, trustScore)
}
```

Run the two LLM calls in `EnsembleAgent` in parallel using `async {} + awaitAll()`:
```kotlin
val (r1, r2) = listOf(
    async { llm.predict(buildPrompt(symbol, enrichedData, "technical")) },
    async { llm.predict(buildPrompt(symbol, enrichedData, "fundamental")) }
).awaitAll()
```

Persist adaptive learning state (weights JSON) to `Context.getFilesDir()`. Save every 10 predictions.

---

### 3.6 Real-Time Price Updates

Replace WebSocket with:
- **Active foreground use:** `CoroutineScope(Dispatchers.IO)` polling Yahoo Finance every 10–30 seconds. Update Room DB → UI observes via `StateFlow<StockQuote>`.
- **Background sync:** `WorkManager` `PeriodicWorkRequest` (min 15-minute interval). Updates all watchlist symbols and stores in Room.
- **Alerts evaluation:** WorkManager periodic task. When triggered, post a `NotificationCompat` local notification.

---

### 3.7 Chat Interface

**UI:** `RecyclerView` with two `ViewHolder` types (user bubble right-aligned, AI bubble left-aligned). Markdown rendering via `Markwon` library for formatted AI responses.

**LLM call flow:**
1. User submits message.
2. Fetch last 5 turns from Room `chat_history`.
3. Build system prompt (same template as web app) with watchlist + prices + predictions context.
4. Send to local LLM with `stream=true` for token-by-token output (typing effect via `Flow<String>`).
5. Parse final response for intent + action.
6. Execute side effects (watchlist add/remove, trigger prediction) via Repository.
7. Persist exchange to Room `chat_history`.

---

### 3.8 Alert System

```kotlin
// Room entity
@Entity data class Alert(
    val symbol: String,
    val conditionType: String,  // "price_above", "price_below", "predicted_change_above"
    val conditionValue: Double,
    val minConfidence: Double,
    val isTriggered: Boolean = false
)

// WorkManager task
class AlertEvaluationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val alerts = alertDao.getActiveAlerts()
        val quotes = stockQuoteDao.getAllQuotes()
        // evaluate each alert
        // if triggered: insert notification, update alert, post NotificationCompat
        return Result.success()
    }
}
```

---

### 3.9 UI / Screen Mapping

| Web Page / Section | Android Screen |
|---|---|
| Login | `LoginFragment` |
| Register | `RegisterFragment` |
| Dashboard Overview | `DashboardFragment` (primary tab) |
| Markets / Heatmap | `MarketsFragment` (tab) |
| Watchlist | `WatchlistFragment` (tab) |
| Predictions | `PredictionsFragment` (tab) |
| Stock Detail | `StockDetailFragment` (nav graph destination, pushed from any list) |
| Portfolio | `PortfolioFragment` |
| Risk Meter | `RiskFragment` |
| Alerts | `AlertsFragment` |
| Chat | `ChatFragment` (FAB available globally) |
| Admin / Data Hub | `SettingsActivity` (gear icon in toolbar) |

**Navigation:** Jetpack Navigation Component + `BottomNavigationView` (5 primary tabs). Deep links into stock detail from search/watchlist rows.

**Charts:** **MPAndroidChart** (Apache 2.0) for candlestick, line, bar, and pie charts. Equivalent to Chart.js on web.

---

### 3.10 Key Adaptation Table

| Web App Behaviour | Android Adaptation |
|---|---|
| SQLite WAL + connection pooling | Room (handles WAL automatically) |
| `ThreadPoolExecutor(4)` for batch predictions | `Dispatchers.Default` coroutine pool |
| 15-year yfinance history download | Cache in Room; download delta on each sync |
| Ollama at localhost:11434 | Local LLM via llama.cpp JNI or MLC-LLM |
| WebSocket (Socket.IO) | WorkManager periodic + `StateFlow` for UI |
| Server-side matplotlib charts | MPAndroidChart (client-side) |
| Daily digest email | Local `NotificationCompat` notification |
| `stk.json` file | Bundled in `assets/`, loaded to Room on first run |
| Flask session cookie auth | `EncryptedSharedPreferences` for session token |
| CORS / proxy headers | Not applicable on Android |
| Background worker daemon thread | WorkManager `PeriodicWorkRequest` |
| Adaptive learning state JSON file | `Context.getFilesDir()/adaptive_state.json` |

---

### 3.11 Recommended Android Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose (preferred) or XML + View Binding |
| Architecture | MVVM — ViewModel + Repository + Room DAO |
| Navigation | Jetpack Navigation Component |
| Local LLM | MLC-LLM Android SDK or llama.cpp JNI |
| LLM Model | `phi3-mini-4k-instruct-q4_k_m.gguf` (~1.6GB) |
| Database | Room (SQLite) |
| Networking | Retrofit + OkHttp |
| Stock Data | Yahoo Finance v8 REST API (no API key) |
| Feature Engineering | Pure Kotlin (rolling window calculations) |
| Charts | MPAndroidChart |
| Background Tasks | WorkManager (data sync, alerts) |
| Active Polling | `CoroutineScope` + `Flow` (price streaming) |
| Dependency Injection | Hilt |
| Async | Kotlin Coroutines + `Flow` / `StateFlow` |
| Notifications | `NotificationCompat` (local) |
| Markdown rendering | Markwon library (for AI chat responses) |
| Model storage | `Context.getFilesDir()` (internal, ~2GB budget) |
| Security | `EncryptedSharedPreferences` for credentials |

---

## APPENDIX — Complete API Endpoint Reference

### Authentication
| Method | Path | Purpose |
|--------|------|---------|
| GET/POST | `/login` | Login form and authentication |
| GET | `/logout` | Logout |
| GET/POST | `/register` | Registration |

### Stock Data
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/stocks/quote/<identifier>` | Current quote by symbol or name |
| GET | `/api/stocks/search?q=` | Autocomplete search |
| GET | `/api/stocks/details/<identifier>` | Full stock detail |
| GET | `/api/stocks/historical/<identifier>` | OHLCV history |

### Watchlist
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/watchlist` | User's watchlist |
| POST | `/api/watchlist/add` | Add symbol |
| POST | `/api/watchlist/remove` | Remove symbol |

### Predictions
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/predictions/` | Paginated predictions list |
| POST | `/api/predictions/trigger` | Start batch prediction |
| GET | `/api/agentic/predict/<symbol>` | Single-symbol agentic prediction |
| POST | `/api/agentic/feedback` | Outcome feedback for adaptive learning |
| GET | `/api/agentic/performance` | Agentic system performance report |
| GET | `/api/agentic/health` | Health check |

### Premium Dashboard
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/premium` | Serve dashboard HTML |
| GET | `/api/dashboard/overview` | Portfolio totals |
| GET | `/api/dashboard/market-indices` | Index values |
| GET | `/api/dashboard/ai-insights` | LLM insights |
| GET | `/api/dashboard/predictions-summary` | Top predictions |
| GET | `/api/dashboard/recent-activity` | Activity timeline |
| POST | `/api/dashboard/record-trade` | Record trade |
| GET | `/api/dashboard/portfolio` | Holdings |
| GET | `/api/dashboard/user-level` | Gamification level |
| GET | `/api/dashboard/sentiment` | Market sentiment |
| GET | `/api/dashboard/sector-heatmap` | Sector performance |
| GET | `/api/dashboard/candlestick/<symbol>` | OHLCV chart data |
| GET | `/api/dashboard/technical-indicators/<symbol>` | Technical indicators |
| GET | `/api/dashboard/agent-reasoning/<symbol>` | LLM reasoning |
| GET | `/api/dashboard/risk-meter` | Portfolio risk |
| GET | `/api/dashboard/kill-criteria/<symbol>` | Exit signals |
| GET | `/api/dashboard/kill-criteria-batch` | Batch exit signals |
| GET | `/api/dashboard/alerts-feed` | Alerts feed |
| POST | `/api/dashboard/paper-trade` | Simulate trade |

### Chat
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/chat/message` | Send message to AI |
| GET | `/api/chat/history` | Conversation history |

### Alerts & Notifications
| Method | Path | Purpose |
|--------|------|---------|
| GET/POST | `/api/alerts/` | List / create alerts |
| POST | `/api/alerts/evaluate` | Trigger evaluation |
| GET | `/api/notifications/` | Notification list |
| POST | `/api/notifications/mark-read` | Mark as read |

### NSE Securities
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/nse/load-securities` | Load stk.json into DB |
| GET | `/api/nse/count` | Securities count |
| GET | `/api/nse/search?q=` | Search securities |

### System
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/system/status` | System status |
| GET | `/api/system/uptime` | Uptime |
| POST | `/api/system/cleanup_models` | Clean old models |
| POST | `/api/system/background_worker/start` | Start worker |
| POST | `/api/system/background_worker/stop` | Stop worker |

### Copilot & LLM
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/copilot/analyze/<symbol>` | GitHub Copilot analysis |
| GET/PUT | `/api/config/` | LLM configuration |
| POST | `/api/backtest/<symbol>` | Backtest predictions |
| GET | `/health` | App health check |

---

## APPENDIX — WebSocket Events Reference

| Event (client → server) | Payload | Purpose |
|--------------------------|---------|---------|
| `connect` | — | Establish connection |
| `subscribe_predictions` | — | Subscribe to prediction updates |
| `subscribe_watchlist` | `{user_id}` | Subscribe to watchlist price updates |
| `subscribe_stock_prices` | `{symbols: []}` | Subscribe to symbol price streaming |
| `unsubscribe_stock_prices` | `{symbols: []}` | Unsubscribe from symbols |
| `request_system_status` | — | Request current system status |

| Event (server → client) | Payload | Purpose |
|--------------------------|---------|---------|
| `connection_status` | `{status, message}` | Confirm connection |
| `subscription_confirmed` | `{type}` | Confirm subscription |
| `price_update` | `{symbol, price, change, p_change}` | Live price tick |
| `prediction_progress` | `{current, total, symbol, status}` | Batch prediction progress |
| `prediction_update` | `{symbol, predicted_price, confidence, decision}` | Per-symbol result |
| `alert_triggered` | `{symbol, condition, message}` | Alert notification |
| `system_status` | `{background_worker, disk_usage, model_stats}` | System metrics |
