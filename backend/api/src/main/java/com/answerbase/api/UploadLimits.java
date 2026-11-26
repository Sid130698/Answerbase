package com.answerbase.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment quotas include retained versions, not just currently indexed documents. */
@ConfigurationProperties("documents.limits")
record UploadLimits(int maxVersions, long maxBytes) {
    UploadLimits {
        if (maxVersions < 1 || maxBytes < 1)
            throw new IllegalArgumentException("Document quotas must be positive");
    }

    boolean exceedsStorage(long used, long incoming) {
        return used > maxBytes || incoming > maxBytes - used;
    }
}
