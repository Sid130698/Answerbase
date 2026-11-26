# Third-party component record

This is a local component record, not a complete transitive license audit or a hosted-deployment approval. Versions below were read from the installed lockfile-resolved packages or local model metadata on 2026-11-26. Preserve distributed license notices when packaging components.

| Component | Installed version/model | Declared license source |
| --- | --- | --- |
| React / React DOM | 19.3.1 | Installed package.json: MIT |
| Supabase JS | 2.119.2 | Installed package.json: MIT |
| IBM Plex Sans font package | 5.3.0 | Installed package.json: OFL-1.1 |
| Local embeddings | nomic-embed-text:latest, 137M F16 | Ollama `/api/show` includes Apache License 2.0 |
| Local generation | llama3.1:8b, Q4_K_M | Ollama `/api/show` includes Llama 3.1 Community License Agreement, release 2024-07-23 |

Llama's bundled license is not the Apache/MIT license used by some other components. Review its complete bundled terms and usage policy before distributing a hosted product or model. Local availability does not establish that every deployment is unrestricted or free to operate.

Other runtime components include Java/Spring Boot, PostgreSQL/pgvector, Supabase's local services, PDFBox, jsoup, Jackson, Ollama, and the optional Hugging Face cross-encoder with its Python dependencies. Their full resolved dependency inventories, notices and container image licenses still need a release audit. Maven POMs, npm lockfiles, reranker requirements and Compose image references are the version sources; do not infer a complete audit from this short table.

Evaluation corpus provenance is recorded in `evals/README.md`. Acorn company content is synthetic; the two NASA-derived sentences paraphrase public facts and include attribution. No logos, photos or paid document corpus is bundled.
