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

    /** Reconciliation is local-only; never follows symlinked files or directories. */
    public java.util.List<String> staleKeys(java.time.Duration age) throws IOException {
        if (!Files.exists(root)) return java.util.List.of();
        long cutoff = System.currentTimeMillis() - age.toMillis();
        try (var paths = Files.walk(root)) {
            return paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(
                            p -> {
                                try {
                                    return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS)
                                                    .toMillis()
                                            < cutoff;
                                } catch (IOException ignored) {
                                    return false;
                                }
                            })
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .toList();
        }
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }
}
