# AGENTS.md

## Project Snapshot
- CredenceAI is an async credit-scoring service: FastAPI enqueues jobs to ARQ/Redis, worker runs a LangGraph pipeline, returns `TatvaAnkReport`.
- Canonical runtime path is `api -> arq queue -> tasks.run_analysis -> agents.graph.analysis_graph` (`src/credence_ai/api/routes.py`, `src/credence_ai/tasks/analysis.py`, `src/credence_ai/agents/graph.py`).
- `README.md` architecture diagram still mentions Celery, but code uses ARQ (`src/credence_ai/tasks/worker.py`). Treat ARQ as source of truth.

## Core Architecture (What Talks to What)
- API app creates one Redis-backed ARQ pool in lifespan (`src/credence_ai/api/app.py`), queue name `credence_ai:analysis`.
- `POST /api/v1/analysis` stores enqueue timestamp in Redis key `credence_ai:meta:{job_id}` and enqueues `run_analysis` with `_job_id=job_id` (`src/credence_ai/api/routes.py`).
- Worker task builds full `AgentState`, invokes `analysis_graph.ainvoke(...)`, then serializes a `JobStatusResponse`-compatible dict (`src/credence_ai/tasks/analysis.py`).
- Graph shape is fixed: `ingestion -> (quant || nlp_risk) -> orchestrator -> (await_feedback | END)` (`src/credence_ai/agents/graph.py`).
- HITL feedback endpoint validates job existence then writes Langfuse score by `generation_id` (`src/credence_ai/api/routes.py`, `src/credence_ai/observability/langfuse_utils.py`).
- Ingestion error path: when `state["error"]` is set, `_route_after_ingestion` skips quant/nlp_risk fan-out and routes directly to orchestrator (`src/credence_ai/agents/graph.py`).

### Chain Workflow Builder (second subsystem)
- Standalone chain composition layer independent of the credit-analysis graph; lives in `src/credence_ai/chains/`.
- `ChainWorkflow` → single LLM call with input template, datasets, system prompt, MCP tools, output type. `ChainPipeline` → ordered multi-step composition of chains (sequential or parallel per step).
- `ChainRegistry` is a singleton (in-memory + optional JSON persistence to `Settings.chain_storage_dir`). Access via `get_registry()`; reset in tests via `reset_registry()` (`src/credence_ai/chains/registry.py`).
- `execute_chain` / `execute_pipeline` are async; `execute_chain_sync` / `execute_pipeline_sync` wrappers exist for Streamlit (`src/credence_ai/chains/engine.py`, `src/credence_ai/chains/pipeline.py`).
- LLM resolution in `_resolve_llm`: Gemini for `gemini-*` prefixes, OpenAI for `gpt-*`/`o1-*`/`o3-*` prefixes, Gemini fallback otherwise (`src/credence_ai/chains/engine.py`).
- MCP tools are stub implementations returning placeholder text; swap in `langchain-mcp-adapters` for real connectivity.
- Schemas live in `src/credence_ai/models/chain_schemas.py` (separate from the core `schemas.py`).

## State and Schema Contracts (Do Not Drift)
- `AgentState` is the shared contract across all nodes; add new cross-agent data there first (`src/credence_ai/agents/state.py`).
- API/task payload contracts are Pydantic v2 models in `src/credence_ai/models/schemas.py`; `JobStatusResponse.result` expects `TatvaAnkReport`.
- Node return payloads are partial dict updates (not full state replacement); preserve this merge style used by LangGraph nodes.
- `span_ids` stores Langfuse generation IDs for feedback routing (`ingestion_*`, `quant_compute`, `nlp_risk_sentiment`, `orchestrator_synthesis`).

## Implementation Reality (Important for Edits)
- Agent nodes are currently deterministic stubs with production-intent comments; keep outputs schema-valid while replacing logic incrementally.
- Orchestrator currently uses explicit heuristic scoring (baseline 50, Altman and sentiment adjustments, clamp to 0-100) in `src/credence_ai/agents/orchestrator.py`.
- Streamlit UI (`src/credence_ai/ui/app.py`) runs `analysis_graph` directly (no Redis/FastAPI), so graph changes affect both worker and UI immediately.
- Streamlit UI has 8 pages: New Analysis, Results, Feedback, Evaluation Framework, plus 4 chain-builder pages (Chain Designer, Chain Registry, Chain Tester, Pipeline Builder) rendered from `src/credence_ai/ui/chain_builder.py`.
- Chain engine LLM factory and MCP tools are stubs; mock `_resolve_llm` in tests to avoid real API calls (`tests/chains/test_engine.py`).

## Local Workflows
```bash
pip install -e ".[dev]"
pip install -e ".[openai]"   # optional: adds langchain-openai for gpt-* models
pip install -e ".[mcp]"      # optional: adds langchain-mcp-adapters
pytest tests/ -v
uvicorn credence_ai.api.app:app --reload
arq credence_ai.tasks.worker.WorkerSettings
streamlit run src/credence_ai/ui/app.py
 streamlit run streamlit_app.py
```
- Redis is required for API+worker path; Streamlit standalone path does not require Redis.
- Configuration is environment-driven via `Settings` (`src/credence_ai/config/settings.py`), reading `.env` with defaults.

## Testing Patterns in This Repo
- API tests patch `create_pool` and use `AsyncMock` ARQ pools (`tests/api/test_routes.py`) instead of real Redis.
- Graph tests use a helper `_make_base_state()` and run node-level + end-to-end graph smoke tests (`tests/agents/test_agents.py`).
- Chain tests: registry tests use `reset_registry()` autouse fixture (`tests/chains/test_registry.py`); engine tests mock `_resolve_llm` (`tests/chains/test_engine.py`); pipeline tests mock `execute_chain` to test step orchestration without LLM calls (`tests/chains/test_pipeline.py`).
- Taxonomy is hard-asserted as 22 metrics with sequential IDs (`tests/evaluation/test_taxonomy.py`); keep IDs stable when editing taxonomy.

## Safe Change Checklist
- If you add fields to `AgentState` or schemas, update tests in `tests/test_schemas.py` and relevant agent/API tests.
- If you change queue/job metadata behavior, update both route enqueue logic and status-fetch path (`create_analysis` + `_get_arq_job_response`).
- If you alter graph routing/nodes, verify both async worker invocation and synchronous Streamlit invocation still work.

