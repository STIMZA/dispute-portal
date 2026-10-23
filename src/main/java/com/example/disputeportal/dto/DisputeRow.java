package com.example.disputeportal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.disputeportal.entity.DisputeStatus;

/**
 * This is the structure used for the customer to view the dispute together with
 * the key details of its transaction.
 */
public record DisputeRow(
        Long id,
        String transactionReference,
        String merchant,
        BigDecimal amount,
        String currency,
        String reasonLabel,
        DisputeStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String resolutionNote) {

}
