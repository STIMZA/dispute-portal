package com.example.disputeportal.repository;

/** Per-transaction dispute totals, as returned by {@link DisputeRepository#countByTransactionIds}. */
public interface TransactionDisputeCount {

    Long getTransactionId();

    Long getLogged();

    Long getClosed();
}
