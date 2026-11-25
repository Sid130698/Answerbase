package com.answerbase.api;

import com.answerbase.core.models.JsonHttp;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class QueryAdminService {
    private final JdbcTemplate jdbc;
    private final WorkspaceService workspaces;

    QueryAdminService(JdbcTemplate jdbc, WorkspaceService workspaces) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
    }

    private WorkspaceService.Workspace admin(UUID user, UUID workspace) {
        var access = workspaces.get(user, workspace);
        if (!access.role().equals("admin"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Workspace admin access required");
        return access;
    }

    List<Map<String, Object>> list(UUID user, UUID workspace, int offset, boolean unanswered) {
        admin(user, workspace);
        if (offset < 0 || offset > 10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return jdbc.queryForList(
                """
            SELECT q.id,q.question,q.status,q.abstained,q.created_at,q.model,q.input_tokens,q.output_tokens,q.cost_usd,
            (q.trace->>'totalMs')::bigint AS latency_ms,f.vote,f.reason,
            EXISTS(SELECT 1 FROM answerbase.evaluation_candidates c WHERE c.question_id=q.id) AS corrected
            FROM answerbase.questions q LEFT JOIN answerbase.answer_feedback f ON f.question_id=q.id AND f.user_id=q.user_id
            WHERE q.workspace_id=? AND (NOT ? OR q.abstained=true OR q.status='failed')
            ORDER BY q.created_at DESC,q.id LIMIT 25 OFFSET ?
            """,
                workspace,
                unanswered,
                offset);
    }

    Map<String, Object> usage(UUID user, UUID workspace) {
        admin(user, workspace);
        return jdbc.queryForMap(
                """
            SELECT count(*) AS questions,count(*) FILTER(WHERE status='complete') AS completed,
            count(*) FILTER(WHERE abstained=true) AS abstained,count(*) FILTER(WHERE status='failed') AS failed,
            coalesce(sum(input_tokens),0) AS input_tokens,coalesce(sum(output_tokens),0) AS output_tokens,
            coalesce(sum(cost_usd),0) AS known_cost_usd,
            count(*) FILTER(WHERE status='complete' AND cost_usd IS NULL) AS unpriced_queries,
            percentile_cont(0.5) WITHIN GROUP(ORDER BY (trace->>'totalMs')::double precision) AS p50_ms,
            percentile_cont(0.95) WITHIN GROUP(ORDER BY (trace->>'totalMs')::double precision) AS p95_ms
            FROM answerbase.questions WHERE workspace_id=? AND created_at >= now()-interval '7 days'
            """,
                workspace);
    }

    Map<String, Object> detail(UUID user, UUID workspace, UUID id) {
        admin(user, workspace);
        var rows =
                jdbc.queryForList(
                        """
            SELECT q.*,f.vote,f.reason,f.note,c.corrected_answer FROM answerbase.questions q
            LEFT JOIN answerbase.answer_feedback f ON f.question_id=q.id AND f.user_id=q.user_id
            LEFT JOIN answerbase.evaluation_candidates c ON c.question_id=q.id
            WHERE q.workspace_id=? AND q.id=?
            """,
                        workspace,
                        id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        var row = rows.getFirst();
        for (String field : List.of("answer", "evidence", "trace"))
            if (row.get(field) != null)
                row.put(field, JsonHttp.JSON.readTree(row.get(field).toString()));
        row.remove("request_key");
        return row;
    }

    void correct(UUID user, UUID workspace, UUID id, String raw) {
        var access = admin(user, workspace);
        String answer = raw == null ? "" : raw.strip();
        if (answer.isEmpty() || answer.length() > 8000)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Correction must contain 1–8000 characters");
        var row = detail(user, workspace, id);
        if (!"complete".equals(row.get("status")))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only completed questions can be corrected");
        jdbc.update(
                """
            INSERT INTO answerbase.evaluation_candidates(id,organization_id,workspace_id,question_id,corrected_answer,created_by)
            VALUES(?,?,?,?,?,?) ON CONFLICT(question_id) DO UPDATE SET corrected_answer=excluded.corrected_answer,created_by=excluded.created_by,created_at=now()
            """,
                UUID.randomUUID(),
                access.organizationId(),
                workspace,
                id,
                answer,
                user);
    }

    String unanswered(UUID user, UUID workspace) {
        admin(user, workspace);
        var rows =
                jdbc.queryForList(
                        "SELECT question,status,created_at FROM answerbase.questions WHERE workspace_id=? AND (abstained=true OR status='failed') ORDER BY created_at DESC,id LIMIT 1000",
                        workspace);
        var csv = new StringBuilder("question,status,created_at\r\n");
        for (var row : rows)
            csv.append(cell(row.get("question")))
                    .append(',')
                    .append(cell(row.get("status")))
                    .append(',')
                    .append(cell(row.get("created_at")))
                    .append("\r\n");
        return csv.toString();
    }

    static String cell(Object value) {
        String text = Objects.toString(value, "");
        String trimmed = text.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
                || text.startsWith("\t")
                || text.startsWith("\r")
                || text.startsWith("\n")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
