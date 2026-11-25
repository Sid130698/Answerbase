package com.answerbase.worker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class QuestionMaintenance {
    private final JdbcTemplate jdbc;

    QuestionMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${questions.maintenance-ms:60000}")
    void expire() {
        int expired =
                jdbc.update(
                        "UPDATE answerbase.questions SET status='failed',error='Answer attempt interrupted or expired. Retry the question.',completed_at=now(),lease_until=NULL WHERE status='running' AND lease_until<=now()");
        if (expired > 0)
            org.slf4j.LoggerFactory.getLogger(QuestionMaintenance.class)
                    .warn("query_attempts_expired count={}", expired);
    }
}
