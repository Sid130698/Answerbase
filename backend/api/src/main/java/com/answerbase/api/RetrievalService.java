package com.answerbase.api;

import com.answerbase.core.models.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class RetrievalService {
    record Options(String mode, int candidates, int topK, boolean rerank) {
        Options {
            if (!Set.of("hybrid", "vector", "keyword").contains(mode)
                    || candidates < 1
                    || candidates > 100
                    || topK < 1
                    || topK > 20
                    || topK > candidates)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid retrieval configuration");
        }
    }

    record Hit(
            String id,
            UUID documentId,
            UUID versionId,
            int ordinal,
            String title,
            String section,
            Integer page,
            Integer startLine,
            Integer endLine,
            Integer startOffset,
            Integer endOffset,
            String content,
            Instant updatedAt,
            double score) {}

    record Result(
            List<Hit> passages,
            String embeddingModel,
            long embeddingMs,
            long retrievalMs,
            long rerankMs,
            Options options) {}

    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;
    private final EmbeddingModel embedding;
    private final Reranker reranker;
    private final Options defaults;

    @org.springframework.beans.factory.annotation.Autowired
    RetrievalService(
            JdbcTemplate jdbc,
            WorkspaceService workspaces,
            @Value("${OLLAMA_URL:http://localhost:11434}") String url,
            @Value("${EMBEDDING_MODEL:nomic-embed-text:latest}") String model,
            @Value("${RERANKER_URL:http://localhost:8092}") String rerankUrl,
            @Value("${RETRIEVAL_MODE:hybrid}") String mode,
            @Value("${RETRIEVAL_CANDIDATES:30}") int candidates,
            @Value("${RETRIEVAL_TOP_K:6}") int topK,
            @Value("${RETRIEVAL_RERANK:false}") boolean rerank) {
        this(
                jdbc,
                workspaces,
                new OllamaEmbedding(url, model),
                new HttpReranker(rerankUrl),
                new Options(mode, candidates, topK, rerank));
    }

    RetrievalService(
            JdbcTemplate jdbc,
            WorkspaceService workspaces,
            EmbeddingModel embedding,
            Reranker reranker,
            Options defaults) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
        this.embedding = embedding;
        this.reranker = reranker;
        this.defaults = defaults;
    }

    Options defaults() {
        return defaults;
    }

    Result search(UUID user, UUID workspace, String raw) {
        var fallback =
                new WorkspacePolicy(
                        defaults.mode(),
                        defaults.candidates(),
                        defaults.topK(),
                        defaults.rerank(),
                        "default",
                        1800,
                        true,
                        true,
                        100);
        var policy = WorkspacePolicy.read(jdbc, workspace, fallback);
        return search(
                user,
                workspace,
                raw,
                new Options(
                        policy.retrievalMode(),
                        policy.candidates(),
                        policy.topK(),
                        policy.rerank()));
    }

    Result search(UUID user, UUID workspace, String raw, Options options) {
        var access = workspaces.get(user, workspace);
        String query = raw == null ? "" : raw.strip();
        if (query.isEmpty() || query.length() > 2000)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Question must contain 1–2000 characters");
        long start = System.nanoTime();
        String vector = null, model = "keyword";
        try {
            if (!options.mode().equals("keyword")) {
                model = embedding.identity();
                vector = EmbeddingModel.vector(embedding.embed(List.of(query), true).getFirst());
            }
            long embedded = System.nanoTime();
            // MATERIALIZED guarantees permission filtering and latest indexed-version selection
            // before ranking.
            String eligible =
                    """
    WITH eligible AS MATERIALIZED (
      SELECT c.*,v.document_id,d.title,v.created_at FROM answerbase.document_chunks c
      JOIN answerbase.document_versions v ON v.id=c.version_id
      JOIN answerbase.documents d ON d.id=v.document_id
      WHERE c.organization_id=? AND c.workspace_id=? AND answerbase.can_read_document(?,d.id)
        AND v.index_status='indexed' AND v.status='processed'
        AND v.version_number=(SELECT max(v2.version_number) FROM answerbase.document_versions v2 WHERE v2.document_id=d.id AND v2.index_status='indexed' AND v2.status='processed')
    )
    """;
            var ranked = new LinkedHashMap<String, Hit>();
            var scores = new HashMap<String, Double>();
            if (!options.mode().equals("keyword")) {
                var rows =
                        jdbc.queryForList(
                                eligible
                                        + " SELECT *,embedding <=> ?::vector AS distance FROM eligible WHERE embedding_model=? AND embedding IS NOT NULL ORDER BY distance,version_id,ordinal LIMIT ?",
                                access.organizationId(),
                                workspace,
                                user,
                                vector,
                                model,
                                options.candidates());
                merge(rows, ranked, scores);
            }
            if (!options.mode().equals("vector")) {
                var rows =
                        jdbc.queryForList(
                                eligible
                                        + " SELECT * FROM eligible WHERE search_text @@ websearch_to_tsquery('english',?) ORDER BY ts_rank_cd(search_text,websearch_to_tsquery('english',?)) DESC,version_id,ordinal LIMIT ?",
                                access.organizationId(),
                                workspace,
                                user,
                                query,
                                query,
                                options.candidates());
                merge(rows, ranked, scores);
            }
            var candidates =
                    ranked.values().stream()
                            .sorted(
                                    Comparator.<Hit>comparingDouble(h -> scores.get(h.id()))
                                            .reversed()
                                            .thenComparing(Hit::id))
                            .limit(options.candidates())
                            .toList();
            long retrieved = System.nanoTime();
            if (options.rerank() && !candidates.isEmpty()) {
                var values = reranker.scores(query, candidates.stream().map(Hit::content).toList());
                var reranked = new ArrayList<Hit>();
                for (int i = 0; i < candidates.size(); i++)
                    reranked.add(withScore(candidates.get(i), values.get(i)));
                candidates =
                        reranked.stream()
                                .sorted(
                                        Comparator.comparingDouble(Hit::score)
                                                .reversed()
                                                .thenComparing(Hit::id))
                                .toList();
            } else
                candidates =
                        candidates.stream().map(h -> withScore(h, scores.get(h.id()))).toList();
            return new Result(
                    candidates.stream().limit(options.topK()).toList(),
                    model,
                    (embedded - start) / 1_000_000,
                    (retrieved - embedded) / 1_000_000,
                    (System.nanoTime() - retrieved) / 1_000_000,
                    options);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Search model service unavailable. Check local models and retry.");
        }
    }

    private Hit withScore(Hit h, double score) {
        return new Hit(
                h.id(),
                h.documentId(),
                h.versionId(),
                h.ordinal(),
                h.title(),
                h.section(),
                h.page(),
                h.startLine(),
                h.endLine(),
                h.startOffset(),
                h.endOffset(),
                h.content(),
                h.updatedAt(),
                score);
    }

    private void merge(
            List<Map<String, Object>> rows, Map<String, Hit> ranked, Map<String, Double> scores) {
        int rank = 0;
        for (var row : rows) {
            rank++;
            UUID version = (UUID) row.get("version_id");
            int ordinal = ((Number) row.get("ordinal")).intValue();
            String id = version + ":" + ordinal;
            // document_id is resolved from the version, never supplied by the client.
            UUID document = (UUID) row.get("document_id");
            ranked.putIfAbsent(
                    id,
                    new Hit(
                            id,
                            document,
                            version,
                            ordinal,
                            (String) row.get("title"),
                            (String) row.get("section_path"),
                            (Integer) row.get("page_number"),
                            (Integer) row.get("start_line"),
                            (Integer) row.get("end_line"),
                            (Integer) row.get("start_offset"),
                            (Integer) row.get("end_offset"),
                            (String) row.get("content"),
                            ((java.sql.Timestamp) row.get("created_at")).toInstant(),
                            0));
            scores.merge(id, 1.0 / (60 + rank), Double::sum);
        }
    }
}
