package com.answerbase.api;

import com.answerbase.core.documents.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
class DocumentStorageConfiguration {
    @Bean
    DocumentStorage documentStorage(
            @Value("${documents.storage.provider:local}") String provider,
            @Value("${documents.storage.root:.cache/documents}") String root,
            @Value("${documents.storage.url:}") String url,
            @Value("${documents.storage.bucket:answerbase-documents}") String bucket,
            @Value("${documents.storage.secret:}") String secret) {
        return StorageFactory.create(provider, root, url, bucket, secret);
    }
}
