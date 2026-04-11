# Credence AI - Tatva Ank Credit Scoring Engine

**Enterprise-grade credit scoring service for senseAI Labs**, powered by a multi-agent LangGraph pipeline with Langfuse observability.

## Architecture

```
Client ──▶ FastAPI ──▶ Celery + Redis ──▶ LangGraph Pipeline
                                              │
                          ┌───────────────────┤
                          ▼                   ▼
                    ┌───────────┐      ┌──────────────┐
                    │ Ingestion │      │   (parallel)  │
                    │   Agent   │      │              │
                    └─────┬─────┘      └──────────────┘
                          │
                   ┌──────┴──────┐
                   ▼             ▼
             ┌───────────┐ ┌───────────┐
             │   Quant   │ │ NLP Risk  │
             │   Agent   │ │   Agent   │
             └─────┬─────┘ └─────┬─────┘
                   └──────┬──────┘
                          ▼
                   ┌─────────────┐
                   │ Orchestrator│
                   │    Agent    │
                   └─────────────┘
                          │
                          ▼
                   Tatva Ank Report
```

### Agents

| Agent | Responsibility | Model Strategy |
|-------|---------------|----------------|
| **Ingestion** | Parse GST PDFs, MCA filings | Fast vision/OCR (Gemini 1.5 Flash), strict JSON mode |
| **Quant** | P&L forecasts, Debt-to-Equity, Altman Z-Score | Code-execution (Pandas/NumPy in sandbox) |
| **NLP Risk** | News sentiment, legal risk assessment | High-parameter model (Gemini 1.5 Pro) with search grounding |
| **Orchestrator** | Synthesise final Tatva Ank score | Rule-bound reasoning with contradiction checks |

## Quick Start

```bash
# Install dependencies
pip install -e ".[dev]"

# Run tests
pytest tests/ -v

# ── Streamlit UI (standalone, no Redis required) ──────────────────────────
streamlit run streamlit_app.py

# ── FastAPI + ARQ backend (requires Redis) ───────────────────────────────
# Start the API
uvicorn credence_ai.api.app:app --reload

# Start the ARQ worker
arq credence_ai.tasks.worker.WorkerSettings
```

## Streamlit UI

The built-in Streamlit UI provides a self-contained interface that imports and runs the full LangGraph pipeline directly (no Redis or FastAPI required):

```bash
streamlit run streamlit_app.py
```

### Pages

| Page | Description |
|------|-------------|
| 🔍 **New Analysis** | Applicant form + document upload → triggers the full pipeline |
| 📊 **Results** | Tatva Ank gauge chart, risk badge, per-agent result tabs |
| 💬 **Feedback** | Submit human-in-the-loop (HITL) feedback per agent |
| 📋 **Evaluation Framework** | Browse all 22 evaluation taxonomy metrics |



| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/analysis` | Submit documents for credit analysis (returns job ID) |
| `GET`  | `/api/v1/analysis/{job_id}` | Poll for analysis status and results |
| `POST` | `/api/v1/feedback` | Submit human-in-the-loop feedback for an agent |
| `GET`  | `/health` | Health check |

## Configuration

Copy `.env.example` to `.env` and fill in the required values:

```bash
cp .env.example .env
```

Key settings: Redis URL, Langfuse keys, Google API key.

## Evaluation Framework

The system includes a 22-taxonomy evaluation framework covering:

- **Factual Accuracy** (4 metrics): Context Precision, Math Verification, Hallucination Rate, Source Citation
- **Relevance** (3 metrics): Answer Relevance, Context Recall, Signal-to-Noise
- **Safety & Compliance** (4 metrics): PII Leakage, Toxicity/Bias, Prompt Injection, Regulatory Adherence
- **System Performance** (4 metrics): Latency, Token Efficiency, Execution Cost, JSON Schema Adherence
- **Reasoning Quality** (4 metrics): Logic Coherence, Contradiction Check, Confidence Calibration, Nuance Recognition
- **UX & Delivery** (3 metrics): Tone/Formality, Visual Alignment, Feedback Delta

## Project Structure

```
src/credence_ai/
├── api/            # FastAPI application and routes
├── agents/         # LangGraph agent nodes and state graph
├── config/         # Application settings
├── evaluation/     # 22-taxonomy evaluation framework
├── models/         # Pydantic data models
├── observability/  # Langfuse integration
└── tasks/          # Celery task definitions
```
