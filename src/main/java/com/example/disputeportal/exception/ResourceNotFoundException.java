package com.example.disputeportal.exception;

/**
 * Thrown when a record doesn't exist, or exists but belongs to another customer. The two cases are deliberately
 * indistinguishable, so ids can't be used to discover other people's data.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
