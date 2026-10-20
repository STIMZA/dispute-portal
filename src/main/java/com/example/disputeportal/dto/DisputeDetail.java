package com.example.disputeportal.dto;

import java.util.List;

/**
 * One dispute with what the customer said and its full progress timeline, for the details page.
 */
public record DisputeDetail(DisputeRow dispute, String details, List<DisputeEventRow> events) {
}
