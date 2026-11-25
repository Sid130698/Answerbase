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
    private final IndexProcessor indexer;

    IngestionWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            DocumentStorage storage,
            @org.springframework.beans.factory.annotation.Value(
                            "${OLLAMA_URL:http://localhost:11434}")
                    String modelUrl,
            @org.springframework.beans.factory.annotation.Value(
                            "${EMBEDDING_MODEL:nomic-embed-text:latest}")
                    String modelName) {
        processor = new IngestionProcessor(jdbc, new TransactionTemplate(manager), storage);
        indexer =
                new IndexProcessor(
                        jdbc,
                        new TransactionTemplate(manager),
                        new com.answerbase.core.models.OllamaEmbedding(modelUrl, modelName));
    }

    @Scheduled(fixedDelayString = "${documents.index-poll-ms:3000}")
    void index() {
        try {
            indexer.processOne();
        } catch (Exception e) {
            log.warn("Embedding service unavailable; indexing remains queued");
        }
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
