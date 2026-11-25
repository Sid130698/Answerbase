package com.answerbase.core.documents;

import java.io.IOException;
import java.nio.file.*;

public final class LocalDocumentStorage implements DocumentStorage {
    private final Path root;

    public LocalDocumentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path path(String key) throws IOException {
        if (!key.matches("[a-zA-Z0-9/._-]+")) throw new IOException("Invalid storage key");
        Path result = root.resolve(key).normalize();
        if (!result.startsWith(root) || result.equals(root))
            throw new IOException("Invalid storage key");
        return result;
    }

    public void put(String key, byte[] bytes, String contentType) throws IOException {
        Path destination = path(key);
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "upload-", ".part");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public byte[] get(String key) throws IOException {
        try (var stream = Files.newInputStream(path(key))) {
            byte[] data = stream.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) throw new IOException("Stored file exceeds limit");
            return data;
        }
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }
}
