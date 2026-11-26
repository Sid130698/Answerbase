package com.answerbase.api;

import com.answerbase.core.models.WorkspacePolicy;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class WorkspaceSettingsService {
    record Configuration(WorkspacePolicy policy, List<String> models, String defaultModel) {}

    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;
    private final RetrievalService retrieval;
    private final List<String> models;
    private final String defaultModel;

    WorkspaceSettingsService(
            JdbcTemplate jdbc,
            WorkspaceService workspaces,
            RetrievalService retrieval,
            @Value("${GENERATION_MODELS:}") String allowed,
            @Value("${GENERATION_MODEL:llama3.1:8b}") String defaultModel) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
        this.retrieval = retrieval;
        this.defaultModel = defaultModel;
        var names = new LinkedHashSet<String>();
        names.add("default");
        names.add(defaultModel);
        Arrays.stream(allowed.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .forEach(names::add);
        this.models = List.copyOf(names);
    }

    private void admin(UUID user, UUID workspace) {
        if (!workspaces.get(user, workspace).role().equals("admin"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Workspace admin access required");
    }

    Configuration get(UUID user, UUID workspace) {
        admin(user, workspace);
        var d = retrieval.defaults();
        return new Configuration(
                WorkspacePolicy.read(
                        jdbc,
                        workspace,
                        new WorkspacePolicy(
                                d.mode(),
                                d.candidates(),
                                d.topK(),
                                d.rerank(),
                                "default",
                                1800,
                                true,
                                true,
                                100)),
                models,
                defaultModel);
    }

    void save(UUID user, UUID workspace, WorkspacePolicy p) {
        admin(user, workspace);
        if (p == null
                || !models.contains(p.generationModel())
                || p.chunkChars() < 600
                || p.chunkChars() > 4000
                || p.dailyAttempts() < 1
                || p.dailyAttempts() > 10000
                || !Set.of("hybrid", "vector", "keyword")
                        .contains(Objects.toString(p.retrievalMode(), "")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workspace settings");
        new RetrievalService.Options(p.retrievalMode(), p.candidates(), p.topK(), p.rerank());
        jdbc.update(
                "INSERT INTO answerbase.workspace_settings(workspace_id,retrieval_mode,candidates,top_k,rerank,generation_model,chunk_chars,context_line,redact_pii,daily_attempts) VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(workspace_id) DO UPDATE SET retrieval_mode=excluded.retrieval_mode,candidates=excluded.candidates,top_k=excluded.top_k,rerank=excluded.rerank,generation_model=excluded.generation_model,chunk_chars=excluded.chunk_chars,context_line=excluded.context_line,redact_pii=excluded.redact_pii,daily_attempts=excluded.daily_attempts",
                workspace,
                p.retrievalMode(),
                p.candidates(),
                p.topK(),
                p.rerank(),
                p.generationModel(),
                p.chunkChars(),
                p.contextLine(),
                p.redactPii(),
                p.dailyAttempts());
    }
}
