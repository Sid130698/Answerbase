# Local hybrid retrieval

The API and worker use an `EmbeddingModel` interface. The included Ollama adapter calls `/api/embed` with `truncate=false` and records the installed model's digest. The initial schema uses 768-dimensional vectors, matching `nomic-embed-text`. Changing dimensions requires a schema migration; changing model weights requires reindexing. Retrieval excludes vectors with a different model identity.

A separate index queue embeds extracted passages in batches of eight, publishes atomically with a lease token, retries failed jobs, and exposes index status/rebuild controls in Sources. The highest successfully indexed document version is eligible; a failed replacement cannot erase the previous working version. Extraction and indexing have independent scheduler threads. Originals and historical versions remain available to authorized users.

Retrieval materializes organization/workspace/document-authorized candidates before scoring. PostgreSQL full-text ranking and cosine similarity each return up to 30 candidates; reciprocal rank fusion combines them, then an optional cross-encoder reranks before returning six passages. The SQL uses `ts_rank_cd`, not an exact BM25 implementation. Exact authorized vector ranking favors correctness at this stage; the HNSW index is provisioned but the materialized permission-filtered query needs scale measurement before claiming ANN performance.

Configuration: `RETRIEVAL_MODE=hybrid|vector|keyword`, `RETRIEVAL_CANDIDATES=30`, `RETRIEVAL_TOP_K=6`, `RETRIEVAL_RERANK=false`, `RERANKER_URL`. Reranker failures return a clear 503 instead of silently changing quality. Chunk size is configurable through the extractor constructor (default 1,800 characters); workspace-level experiment settings follow in administration.

## Existing host Ollama

Run `ollama pull nomic-embed-text` and keep Ollama running. Compose uses `http://host.docker.internal:11434`; host-run Java uses `http://localhost:11434`. Local embeddings do not send document text to an external model provider. Ollama must have the exact configured tag installed (`nomic-embed-text:latest` by default).

For the local cross-encoder:

```sh
uv venv --python 3.12 .cache/reranker
uv pip install --python .cache/reranker/bin/python -r models/reranker/requirements.txt
.cache/reranker/bin/python models/reranker/server.py
```

Set `RETRIEVAL_RERANK=true` and `RERANKER_URL=http://host.docker.internal:8092` for Compose. The server binds loopback by default and downloads publicly available model weights on first launch. Never expose it publicly.

Alternatively use `docker compose -f compose.yml -f compose.models.yml up -d --build`, then `docker compose -f compose.yml -f compose.models.yml exec ollama ollama pull nomic-embed-text`. Model downloads need internet once and use local disk/RAM; no API subscription is required. The optional container model stack has not yet been end-to-end verified; the host Ollama path has.

## Evidence

Local smoke script: `node scripts/smoke-retrieval.mjs` with an ignored `TEST_ACCOUNT_FILE` containing a local test account. It signs in, uploads a synthetic refund policy, waits for indexing and asserts the gold passage is retrieved. It does not print credentials. On this machine one warm query measured 112 ms embedding and 21 ms retrieval; this is not a benchmark or a quality percentage. PostgreSQL tests use deterministic vectors to test authorization, rank fusion plumbing and index publication without requiring model downloads in CI. Live-model quality belongs in the evaluation harness.

API reference: [Ollama embeddings](https://docs.ollama.com/api/embed). Reranker: [Sentence Transformers usage](https://sbert.net/docs/cross_encoder/usage/usage.html), [Apache-2.0 model card](https://huggingface.co/cross-encoder/ms-marco-MiniLM-L6-v2).

The host cross-encoder path also passed a live smoke test: 100 ms embedding, 14 ms retrieval, 338 ms reranking; the gold refund passage remained in the six results. Sources displayed Indexed in browser QA. This validates the pipeline, not the PRD accuracy/latency distribution targets.
