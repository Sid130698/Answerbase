# Local evaluation

`cases.json` contains **60 AI-authored candidate questions and 10 adversarial cases**, not a human-written or reviewed gold set. Composition: 20 facts, 10 synthesis, 10 table, 5 comparison, 5 follow-up, 10 unanswerable, plus 10 adversarial. A reviewer must correct the questions/anchors and grade at least 20% before reporting PRD acceptance. Semantic correctness, faithfulness and citation accuracy remain null until human grading; substring matching is only a diagnostic proxy.

## Run

Start `make local` and the optional cross-encoder described in `docs/retrieval.md`. Use an organization owner/admin local test account. Put its credentials in a private, ignored JSON file shaped as `{"client":{"email":"...","password":"..."}}`; do not commit it. Set `TEST_ACCOUNT_FILE` to that path. The existing developer smoke-account file is the default. The runner only permits loopback API/auth URLs, uses ordinary authenticated routes, and creates a dedicated evaluation workspace. It never overwrites customer documents. Manifests in `.cache/` reuse that workspace and fixture uploads for the same account/dataset/variant. Each variant consumes one of the preview account's ten workspace slots.

```sh
node --test evals/*.test.mjs
node evals/run.mjs
EVAL_VARIANT=vector node evals/run.mjs
EVAL_VARIANT=hybrid node evals/run.mjs
EVAL_VARIANT=small-chunks node evals/run.mjs
EVAL_VARIANT=no-context node evals/run.mjs
EVAL_MODE=answers node evals/run.mjs
```

Default: hybrid + cross-encoder, 30 candidates, 8 passages, 1800-character chunks, embedding context line. Other variants change exactly the named setting. Chunk/context variants use separate workspaces so indexing reflects their settings. Small chunks use 600 characters. The tiny corpus does not represent a 50,000-chunk performance test, and many fixtures fit in one default chunk.

`EVAL_IDS=fact-01,unanswerable-01` selects a bounded subset. `EVAL_REPORT=evals/reports/my-run.json` sets an output filename. Reports checkpoint after every case, include actual returned passages/answers, model identifiers, usage and timings, and remain gitignored. A failed case is recorded and the process exits nonzero. There is no automatic model-provider fallback. Generation runs are paced to respect the per-user rate limit; follow-up cases execute their parent question in a fresh conversation first. Parent setup cost is included in raw reports but excluded from scored-case usage totals. API cost excludes hardware and electricity. The JSON endpoint measures complete-answer latency, not first-token latency.

## Metrics and regression checks

Retrieval: hit@5, reciprocal rank and all-gold-at-5 require the annotated document **and** exact normalized anchor text. Unanswerable cases have no invented gold passage and are excluded from retrieval denominators. Answer scoring measures abstention, false abstention, literal quote integrity, and expected-term matching. Literal quote integrity does **not** prove a claim is supported by its quote. Expected-term matching is not correctness. An empty citation set yields null, not 100%.

Each answer run exports a CSV with blank human-grade fields. Reviewers should read the JSON evidence and answer before filling it. Do not use the machine proxy as a substitute. A candidate dataset change invalidates previous baselines.

Set `EVAL_BASELINE=path/to/reviewed-report.json` to fail on a drop greater than three percentage points in comparable machine metrics (or a >3pp increase in false abstention). Dataset hash, configuration and case IDs/order must match; request failures and missing previously present metrics fail closed. CI tests the dataset contract and gate mechanics without live model calls. **A live quality-regression CI job is not enabled yet:** it needs an approved baseline and a model-equipped trusted runner. A unit test of gate mechanics is not evidence of model quality.

## Corpus provenance

The Acorn documents are fictional fixtures authored for this repository. Archived/current policy conflict and the malicious instruction are intentional; neither is production policy. `moon.md` contains two short paraphrased public facts from [NASA Moon Facts](https://science.nasa.gov/moon/facts/), accessed 2026-11-26. No NASA images, logos or third-party works are bundled. See [NASA usage guidance](https://www.nasa.gov/nasa-brand-center/images-and-media/). No endorsement is implied. This is a very small public-reference component, not a representative public-document benchmark.
