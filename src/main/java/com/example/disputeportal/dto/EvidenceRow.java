package com.example.disputeportal.dto;

import java.time.LocalDateTime;

/**
 * One uploaded file as the pages show it. {@code uploadedBy} is the real username for admins and "You" or
 * "Dispute team" for customers (the same rule as the dispute timeline).
 */
public record EvidenceRow(
        Long id,
        String filename,
        String contentType,
        long sizeBytes,
        String uploadedBy,
        LocalDateTime uploadedAt) {

    /** Size for display, e.g. "340 KB". */
    public String sizeLabel() {
        return ByteSizes.format(sizeBytes);
    }
}
