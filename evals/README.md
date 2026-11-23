# Evaluations

This directory will hold the human-written gold dataset and the retrieval/generation evaluation runner.
No quality numbers or evaluation gate exist yet. Do not substitute synthetic passing results for a real baseline.

Planned contract: cases carry question, workspace, user/groups, expected answer or abstention, and gold passage IDs.
Full runs will report retrieval, correctness, citation support, abstention, latency, and usage.
Run a bounded subset in CI once a working query pipeline exists; keep live LLM calls out of untrusted PR jobs.
Generated reports go in `reports/` and are ignored by Git.
