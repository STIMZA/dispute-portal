package com.example.disputeportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for evidence uploads (prefix {@code app.evidence}).
 *
 * @param storageDir        folder the files are written to. In Docker this is a volume, so files survive restarts
 * @param maxFileBytes      largest accepted file
 * @param maxFilesPerDispute how many files one dispute may hold
 */
@ConfigurationProperties(prefix = "app.evidence")
public record EvidenceProperties(
        String storageDir,
        @DefaultValue("5242880") long maxFileBytes,
        @DefaultValue("5") int maxFilesPerDispute) {

    /** Fails at startup, with a clear message, instead of failing on the first upload. */
    public EvidenceProperties {
        if (storageDir == null || storageDir.isBlank()) {
            throw new IllegalArgumentException("app.evidence.storage-dir must be set");
        }
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("app.evidence.max-file-bytes must be greater than 0");
        }
        if (maxFilesPerDispute <= 0) {
            throw new IllegalArgumentException("app.evidence.max-files-per-dispute must be greater than 0");
        }
    }
}
