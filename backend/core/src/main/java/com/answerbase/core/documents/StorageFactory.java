package com.answerbase.core.documents;

import java.nio.file.Path;

public final class StorageFactory {
    private StorageFactory() {}

    public static DocumentStorage create(
            String provider, String root, String url, String bucket, String secret) {
        return switch (provider) {
            case "local" -> new LocalDocumentStorage(Path.of(root));
            case "supabase" -> new SupabaseDocumentStorage(url, bucket, secret);
            default -> throw new IllegalArgumentException("Unknown document storage provider");
        };
    }
}
