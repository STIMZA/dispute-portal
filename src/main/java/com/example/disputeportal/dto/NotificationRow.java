package com.example.disputeportal.dto;

import java.time.LocalDateTime;

/** One notification as the list page shows it. */
public record NotificationRow(Long id, Long disputeId, String message, boolean read, LocalDateTime createdAt) {
}
