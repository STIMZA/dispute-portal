package com.example.disputeportal.dto;

/** The upload rules the pages mention to customers. */
public record EvidenceLimits(long maxFileBytes, int maxFiles) {

    /** The largest allowed file for display, e.g. "5.0 MB". */
    public String maxFileLabel() {
        return ByteSizes.format(maxFileBytes);
    }
}
