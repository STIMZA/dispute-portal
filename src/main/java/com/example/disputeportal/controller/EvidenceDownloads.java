package com.example.disputeportal.controller;

import com.example.disputeportal.dto.EvidenceFile;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Builds the download response for an evidence file, shared by the customer and admin controllers.
 *
 * Files are always sent as attachments (the browser saves them rather than showing them inside our site) with the type
 * the service detected from the file's contents. Spring Security adds "X-Content-Type-Options: nosniff" and
 * "Cache-Control: no-store", so the browser neither guesses another type nor keeps a copy.
 */
final class EvidenceDownloads {

    private EvidenceDownloads() {
    }

    static ResponseEntity<Resource> attachment(EvidenceFile file) {
        // ContentDisposition encodes the file name for the header, so odd characters can't break out of it.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }
}
