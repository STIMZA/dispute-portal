package com.example.disputeportal.dto;

/**
 * @param received   transactions in the request
 * @param created    new transactions saved
 * @param duplicates transactions that were already loaded with identical details (safe retries)
 */
public record TransactionLoadResult(int received, int created, int duplicates) {
}
