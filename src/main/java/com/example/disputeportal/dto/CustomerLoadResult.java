package com.example.disputeportal.dto;

/**
 * @param received   customers in the request
 * @param created    new accounts created
 * @param duplicates accounts that already existed with the same name and password (safe retries)
 */
public record CustomerLoadResult(int received, int created, int duplicates) {
}
