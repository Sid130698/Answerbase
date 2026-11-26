\set ON_ERROR_STOP on
-- Only the isolated local test database. All generated data rolls back.
DO $$ BEGIN IF current_database()<>'answerbase_test' THEN RAISE EXCEPTION 'Use answerbase_test only'; END IF; END $$;
BEGIN;
SET LOCAL statement_timeout='240s';
SET LOCAL lock_timeout='5s';
CREATE TEMP TABLE scale_ids AS SELECT gen_random_uuid() org,gen_random_uuid() usr,gen_random_uuid() outsider,gen_random_uuid() ws;
INSERT INTO answerbase.organizations(id,name) SELECT org,'Synthetic scale fixture' FROM scale_ids;
INSERT INTO answerbase.users(id,organization_id,organization_role) SELECT usr,org,'owner' FROM scale_ids UNION ALL SELECT outsider,org,'member' FROM scale_ids;
INSERT INTO answerbase.workspaces(id,organization_id,name,created_by,creation_key) SELECT ws,org,'Synthetic scale fixture',usr,gen_random_uuid() FROM scale_ids;
INSERT INTO answerbase.workspace_members(organization_id,workspace_id,user_id,role) SELECT org,ws,usr,'admin' FROM scale_ids;
CREATE TEMP TABLE scale_docs AS SELECT n,gen_random_uuid() doc,gen_random_uuid() version FROM generate_series(1,2000) n;
INSERT INTO answerbase.documents(id,organization_id,workspace_id,title,created_by) SELECT doc,org,ws,'Synthetic document '||n,usr FROM scale_docs CROSS JOIN scale_ids;
INSERT INTO answerbase.document_versions(id,organization_id,workspace_id,document_id,version_number,filename,media_type,byte_size,checksum,storage_key,request_key,created_by,status,index_status)
 SELECT version,org,ws,doc,1,'scale.txt','text/plain',200,repeat('0',64),'synthetic-scale/'||version,gen_random_uuid(),usr,'processed','indexed' FROM scale_docs CROSS JOIN scale_ids;
-- Deterministic synthetic vectors measure SQL scale, NOT embedding or answer quality.
INSERT INTO answerbase.document_chunks(organization_id,workspace_id,version_id,ordinal,content,section_path,embedding,embedding_model)
 SELECT org,ws,version,k,'Synthetic refund policy document '||n||' paragraph '||k||' annual refunds within 30 days.','Scale',
 (SELECT array_agg(sin((n*25+k)*.01+dimension*.1)::real)::vector FROM generate_series(1,768) dimension),'synthetic-scale-v1'
 FROM scale_docs CROSS JOIN scale_ids CROSS JOIN generate_series(0,24) k;
ANALYZE answerbase.documents; ANALYZE answerbase.document_versions; ANALYZE answerbase.document_chunks;
SELECT json_build_object('documents',count(DISTINCT version_id),'chunks',count(*)) FROM answerbase.document_chunks WHERE workspace_id=(SELECT ws FROM scale_ids);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM answerbase.documents WHERE workspace_id=(SELECT ws FROM scale_ids) AND answerbase.can_read_document((SELECT outsider FROM scale_ids),id)) THEN RAISE EXCEPTION 'Nonmember gained document access'; END IF; END $$;
-- Same authorization/materialization/latest-indexed-version boundary as RetrievalService.
EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
WITH eligible AS MATERIALIZED (
 SELECT c.*,v.document_id,d.title,v.created_at FROM answerbase.document_chunks c
 JOIN answerbase.document_versions v ON v.id=c.version_id JOIN answerbase.documents d ON d.id=v.document_id
 WHERE c.organization_id=(SELECT org FROM scale_ids) AND c.workspace_id=(SELECT ws FROM scale_ids)
 AND answerbase.can_read_document((SELECT usr FROM scale_ids),d.id)
 AND v.index_status='indexed' AND v.status='processed'
 AND v.version_number=(SELECT max(v2.version_number) FROM answerbase.document_versions v2 WHERE v2.document_id=d.id AND v2.index_status='indexed' AND v2.status='processed')
)
SELECT version_id,ordinal,embedding <=> (SELECT embedding FROM answerbase.document_chunks WHERE version_id=(SELECT version FROM scale_docs WHERE n=1) AND ordinal=0) AS distance
FROM eligible WHERE embedding_model='synthetic-scale-v1' AND embedding IS NOT NULL ORDER BY distance,version_id,ordinal LIMIT 30;
EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
WITH eligible AS MATERIALIZED (
 SELECT c.*,v.document_id,d.title,v.created_at FROM answerbase.document_chunks c
 JOIN answerbase.document_versions v ON v.id=c.version_id JOIN answerbase.documents d ON d.id=v.document_id
 WHERE c.organization_id=(SELECT org FROM scale_ids) AND c.workspace_id=(SELECT ws FROM scale_ids)
 AND answerbase.can_read_document((SELECT usr FROM scale_ids),d.id)
 AND v.index_status='indexed' AND v.status='processed'
 AND v.version_number=(SELECT max(v2.version_number) FROM answerbase.document_versions v2 WHERE v2.document_id=d.id AND v2.index_status='indexed' AND v2.status='processed')
)
SELECT version_id,ordinal FROM eligible WHERE search_text @@ websearch_to_tsquery('english','annual refund')
 ORDER BY ts_rank_cd(search_text,websearch_to_tsquery('english','annual refund')) DESC,version_id,ordinal LIMIT 30;
ROLLBACK;
