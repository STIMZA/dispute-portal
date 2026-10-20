package com.example.disputeportal.security;

import java.io.IOException;

/** Thrown while reading a request body that exceeds the configured limit. */
public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException(long maxBytes) {
        super("Request body exceeds " + maxBytes + " bytes");
    }
}
