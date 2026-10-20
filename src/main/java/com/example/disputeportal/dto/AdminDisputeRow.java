package com.example.disputeportal.dto;

import com.example.disputeportal.entity.DisputeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A dispute as the admin pages need it, including the customer. Built by AdminDisputeService so templates never
 * touch lazily-loaded entities.
 *
 * nextStatuses lists the statuses the dispute may move to (empty when it is closed).
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
