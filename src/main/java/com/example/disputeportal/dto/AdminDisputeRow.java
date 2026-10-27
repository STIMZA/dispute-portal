package com.example.disputeportal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.disputeportal.entity.DisputeStatus;

/**
 * This is a represantation of a dispute for the admin's review page.
 * nextStatuses lists the statuses the dispute may move to (empty when it is
 * closed).
 */
public record AdminDisputeRow(
        Long id,
        String customerName,
        String customerUsername,
        String transactionReference,
        String merchant,
        BigDecimal amount,
        String currency,
        String reasonLabel,
        String details,
        DisputeStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String updatedBy,
        String resolutionNote,
        List<DisputeStatus> nextStatuses) {

}
