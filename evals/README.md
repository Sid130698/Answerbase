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

## Optional local rubric judge

```sh
node evals/judge.mjs evals/reports/my-answer-run.json
node evals/check-regression.mjs BASELINE.json CURRENT.json BASELINE.judge.json CURRENT.judge.json
```

The judge uses local Ollama (`EVAL_JUDGE_MODEL`, default `llama3.1:8b`) and grades correctness, faithfulness and citation support against the question, annotated reference documents and returned evidence. It checks the source dataset hash and binds grades to the exact answer-report hash. Grades are labeled `llmJudge*`; they are not human-verified correctness. Using the same model family for generation and judging can introduce correlated bias. Each completed grading run exports a deterministic 20% review sample with blank human fields. A reviewer must inspect and grade that sample and calculate agreement before these scores can support PRD acceptance. Failed answer requests remain ungraded errors, not silently successful abstentions. The comparison command rejects incomplete/mismatched reports and >3-point drops, including model-judged correctness when both judge reports are supplied. It is available locally; no untrusted PR can trigger a live model judge in CI.

## Live stream and restricted-source probes

After an answer run completes, `node evals/stream.mjs REPORT.json` records first status, first verified claim and final-answer timing. It uses a single synthetic refund question in that evaluation workspace; it is not a latency distribution. `node evals/permissions.mjs REPORT.json` uses the local owner and confirmed viewer fixtures (`TEST_ACCOUNT_FILE`, `TEST_VIEWER_FILE`) to verify an indexed restricted canary is visible to the admin and absent from viewer retrieval, original access and adversarial generation. The latter adds a viewer membership only if absent, and removes that added membership plus its temporary document in cleanup. Existing roles are never downgraded. Run these probes serially with generation benchmarks to avoid model contention.

The judge uses Ollama's [JSON-schema structured output format](https://github.com/ollama/ollama/blob/main/docs/capabilities/structured-outputs.mdx), followed by local validation. A schema-valid grade is still a model judgment, not a human finding.

The current judge asks for a supported-citation **count** bounded by the actual number of citations, then the evaluator computes citation accuracy. This avoids trusting model arithmetic or accepting out-of-range percentages. To generate a review packet that withholds machine grades, run `node evals/review-packet.mjs ANSWERS.json JUDGE.json`. Fill the matching CSV independently before comparing with the model's grades. `EVAL_JUDGE_IDS` and `EVAL_JUDGE_REPORT` support bounded judge debugging; partial runs cannot pass the full-report regression check.

After a person completes every row in the exported sample, `node evals/human-review.mjs JUDGE.json REVIEW.csv` validates the exact sample, required reviewer/grade fields and citation-score bounds, then writes a separate agreement report. Blank grades, missing/duplicate/substituted cases and incomplete judge runs fail closed. The report distinguishes human sample correctness from full-set machine grades; it never fills in human opinions automatically. Keep reviewer identities and completed review files in the ignored report directory unless explicitly approved for publication.
