package com.example.disputeportal.exception;

/** Thrown when a request is valid but breaks a business rule (e.g. duplicate open dispute). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
