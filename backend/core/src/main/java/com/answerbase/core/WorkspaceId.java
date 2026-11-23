package com.answerbase.core;

import java.util.Objects;
import java.util.UUID;

/** A workspace identifier. Possessing one does not establish permission to access it. */
public record WorkspaceId(UUID value) {
    public WorkspaceId {
        Objects.requireNonNull(value, "workspace id must not be null");
    }
}
