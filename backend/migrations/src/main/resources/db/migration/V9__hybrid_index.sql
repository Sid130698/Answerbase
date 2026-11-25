CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE answerbase.document_chunks ADD COLUMN embedding vector(768);
ALTER TABLE answerbase.document_chunks ADD COLUMN embedding_model text;
ALTER TABLE answerbase.document_chunks ADD COLUMN search_text tsvector GENERATED ALWAYS AS (to_tsvector('english',section_path || ' ' || content)) STORED;
CREATE INDEX document_chunks_fts_idx ON answerbase.document_chunks USING gin(search_text);
CREATE INDEX document_chunks_vector_idx ON answerbase.document_chunks USING hnsw(embedding vector_cosine_ops);
ALTER TABLE answerbase.document_versions ADD COLUMN index_status varchar(20) NOT NULL DEFAULT 'pending' CHECK(index_status IN ('pending','indexing','indexed','failed'));
ALTER TABLE answerbase.document_versions ADD COLUMN indexed_at timestamptz;
CREATE TABLE answerbase.index_jobs (
 version_id uuid PRIMARY KEY REFERENCES answerbase.document_versions(id) ON DELETE CASCADE,
 state varchar(20) NOT NULL DEFAULT 'queued' CHECK(state IN ('queued','processing','done','failed')),
 attempts integer NOT NULL DEFAULT 0,
 available_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid,
 lease_until timestamptz,
 error text
);
INSERT INTO answerbase.index_jobs(version_id) SELECT id FROM answerbase.document_versions WHERE status='processed';
