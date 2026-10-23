package com.example.disputeportal.dto;

import java.util.List;

/**
 * One dispute , including the message from the customer. Full progress
 * timeline, for the details page.
 */
public record DisputeDetail(DisputeRow dispute, String details, List<DisputeEventRow> events) {

}
