package com.answerbase.api;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Durable provider retries reuse the original idempotency key and recheck current access. */
@Component
@ConditionalOnProperty(name = "answerbase.question-retries.enabled", matchIfMissing = true)
class QuestionRetries {
    private final JdbcTemplate jdbc;
    private final QuestionService questions;

    QuestionRetries(JdbcTemplate jdbc, QuestionService questions) {
        this.jdbc = jdbc;
        this.questions = questions;
    }

    @Scheduled(fixedDelayString = "${answerbase.question-retries.poll-ms:5000}")
    void poll() {
        processOne();
    }

    boolean processOne() {
        UUID lease = UUID.randomUUID();
        var jobs =
                jdbc.queryForList(
                        """
            WITH candidate AS (
              SELECT r.question_id FROM answerbase.question_retries r
              JOIN answerbase.questions q ON q.id=r.question_id
              WHERE NOT r.terminal AND r.attempts<3 AND r.expires_at>now()
                AND r.available_at<=now() AND (r.lease_until IS NULL OR r.lease_until<=now())
                AND (q.status='failed' OR (q.status='running' AND q.lease_until<=now()))
              ORDER BY r.available_at FOR UPDATE OF r SKIP LOCKED LIMIT 1
            )
            UPDATE answerbase.question_retries r SET lease_token=?,lease_until=now()+interval '12 minutes',attempts=attempts+1
            FROM candidate c,answerbase.questions q WHERE r.question_id=c.question_id AND q.id=r.question_id
            RETURNING q.id,q.user_id,q.workspace_id,q.request_key,q.conversation_id,q.question
            """,
                        lease);
        if (jobs.isEmpty()) return false;
        var job = jobs.getFirst();
        UUID id = (UUID) job.get("id");
        try {
            questions.ask(
                    (UUID) job.get("user_id"),
                    (UUID) job.get("workspace_id"),
                    new QuestionService.Ask(
                            (UUID) job.get("request_key"),
                            (UUID) job.get("conversation_id"),
                            (String) job.get("question")),
                    ignored -> {});
            jdbc.update(
                    "DELETE FROM answerbase.question_retries WHERE question_id=? AND lease_token=?",
                    id,
                    lease);
        } catch (ResponseStatusException e) {
            int code = e.getStatusCode().value();
            boolean capacity = code == 429 || code == 409;
            jdbc.update(
                    """
                UPDATE answerbase.question_retries SET terminal=?,lease_token=NULL,lease_until=NULL,
                  attempts=attempts-?,available_at=now()+ CASE WHEN ? THEN interval '1 minute' ELSE interval '2 minutes' END
                WHERE question_id=? AND lease_token=?
                """,
                    code != 503 && !capacity,
                    capacity ? 1 : 0,
                    capacity,
                    id,
                    lease);
        } catch (Exception e) {
            // Preserve the lease after an unexpected failure; another replica can recover it.
            org.slf4j.LoggerFactory.getLogger(QuestionRetries.class)
                    .error(
                            "question_retry_failed id={} error_type={}",
                            id,
                            e.getClass().getSimpleName());
        }
        return true;
    }
}
