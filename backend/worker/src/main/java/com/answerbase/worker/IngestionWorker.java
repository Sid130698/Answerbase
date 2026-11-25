package com.answerbase.worker;

import com.answerbase.core.documents.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
class IngestionWorker {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(IngestionWorker.class);
    private final IngestionProcessor processor;

    IngestionWorker(
            JdbcTemplate jdbc, PlatformTransactionManager manager, DocumentStorage storage) {
        processor = new IngestionProcessor(jdbc, new TransactionTemplate(manager), storage);
    }

    @Scheduled(fixedDelayString = "${documents.poll-ms:2000}")
    void poll() {
        try {
            processor.processOne();
        } catch (Exception e) {
            log.error("Ingestion queue poll failed; pending jobs will be retried", e);
        }
    }
}
