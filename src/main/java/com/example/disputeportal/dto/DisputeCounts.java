package com.example.disputeportal.dto;

/**
 * @param logged every dispute ever raised on the transaction
 * @param closed those that have a final outcome (resolved or not upheld)
 */
public record DisputeCounts(int logged, int closed) {

    public static final DisputeCounts NONE = new DisputeCounts(0, 0);
}
