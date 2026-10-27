package com.example.disputeportal.dto;

import org.springframework.core.io.Resource;

/** A stored file ready to be sent to the browser as a download. */
public record EvidenceFile(String filename, String contentType, long sizeBytes, Resource content) {
}
