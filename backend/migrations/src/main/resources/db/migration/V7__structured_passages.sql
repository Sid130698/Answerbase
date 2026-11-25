ALTER TABLE answerbase.document_versions ADD COLUMN extracted_text text;
ALTER TABLE answerbase.document_versions ADD COLUMN processed_at timestamptz;
ALTER TABLE answerbase.document_chunks ADD COLUMN section_path text NOT NULL DEFAULT '';
ALTER TABLE answerbase.document_chunks ADD COLUMN start_offset integer;
ALTER TABLE answerbase.document_chunks ADD COLUMN end_offset integer;
ALTER TABLE answerbase.document_chunks ADD CONSTRAINT document_chunk_offsets CHECK ((start_offset IS NULL AND end_offset IS NULL) OR (start_offset >= 0 AND end_offset > start_offset));
-- Re-extract existing versions once to populate source coordinates consistently.
UPDATE answerbase.ingestion_jobs SET state='queued',attempts=0,available_at=now(),lease_token=NULL,lease_until=NULL WHERE state='done';
UPDATE answerbase.document_versions SET status='queued' WHERE status='processed';
