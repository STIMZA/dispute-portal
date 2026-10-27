package com.example.disputeportal.service;

import org.springframework.core.io.Resource;

/**
 * Where the bytes of uploaded evidence live. The database only keeps the metadata, so swapping the local folder for
 * object storage (S3, Azure Blob ...) means writing another implementation of this interface and nothing else.
 */
public interface EvidenceStorage {

    /** Saves the content under the key. The key must not exist yet. */
    void store(String key, byte[] content);

    /**
     * Opens a stored file.
     *
     * @throws com.example.disputeportal.exception.ResourceNotFoundException if the file is missing
     */
    Resource load(String key);

    /** Removes a file. Never throws: it is used for cleanup after a failure, and a missing file is fine. */
    void delete(String key);
}
