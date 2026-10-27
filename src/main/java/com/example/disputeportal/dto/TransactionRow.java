package com.example.disputeportal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A transaction as the transactions page needs it.
 *
 * @param disputeOpen    a dispute on it is currently Submitted or Under review
 * @param disputable     the customer may raise a dispute now (none open, and still inside the dispute window)
 * @param disputesLogged how many disputes were ever raised on it
 * @param disputesClosed how many of those have a final outcome
 */
public record TransactionRow(
        Long id,
        String reference,
        String merchant,
        String description,
        BigDecimal amount,
        String currency,
        LocalDateTime transactionDate,
        boolean disputeOpen,
        boolean disputable,
        int disputesLogged,
        int disputesClosed) {
}
