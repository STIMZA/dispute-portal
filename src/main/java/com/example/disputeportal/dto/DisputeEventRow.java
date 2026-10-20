package com.example.disputeportal.dto;

import com.example.disputeportal.entity.DisputeStatus;
import java.time.LocalDateTime;

/** @param fromStatus null for the "dispute raised" entry */
public record DisputeEventRow(
        DisputeStatus fromStatus,
        DisputeStatus toStatus,
        String note,
        String actor,
        LocalDateTime createdAt) {
}
