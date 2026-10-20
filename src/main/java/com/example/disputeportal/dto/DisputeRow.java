package com.example.disputeportal.dto;

import com.example.disputeportal.entity.DisputeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A dispute as the customer pages need it: the dispute together with the key details of its transaction.
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
