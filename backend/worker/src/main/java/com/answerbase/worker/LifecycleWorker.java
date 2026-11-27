package com.answerbase.worker;

import com.answerbase.core.documents.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class LifecycleWorker {
    private final DocumentLifecycle lifecycle;
    private final ObjectDeletionProcessor deletions;

    LifecycleWorker(
            JdbcTemplate jdbc, PlatformTransactionManager manager, DocumentStorage storage) {
        var tx = new TransactionTemplate(manager);
        lifecycle = new DocumentLifecycle(jdbc, tx);
        deletions = new ObjectDeletionProcessor(jdbc, tx, storage);
    }

    @Scheduled(fixedDelayString = "${documents.delete-poll-ms:5000}")
    void delete() {
        deletions.processOne();
    }

    @Scheduled(fixedDelayString = "${documents.retention-poll-ms:3600000}")
    void retention() {
        int deleted = lifecycle.retain();
        if (deleted > 0)
            org.slf4j.LoggerFactory.getLogger(LifecycleWorker.class)
                    .info("retention_documents_deleted count={}", deleted);
    }

    @Scheduled(fixedDelayString = "${documents.orphan-poll-ms:86400000}")
    void orphans() throws java.io.IOException {
        int queued = deletions.reconcile();
        if (queued > 0)
            org.slf4j.LoggerFactory.getLogger(LifecycleWorker.class)
                    .info("orphan_objects_queued count={}", queued);
    }
}
