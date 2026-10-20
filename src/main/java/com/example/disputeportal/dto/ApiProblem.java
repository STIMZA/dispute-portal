package com.example.disputeportal.dto;

/** One problem with a request, pointing at the offending field, e.g. {@code transactions[2].amount}. */
public record ApiProblem(String path, String message) {
}
