package com.example.disputeportal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of a customer-creation call: 1 to 100 accounts. The limit is lower than for transactions because every
 * password is hashed with BCrypt, which is deliberately slow.
 */
public record CustomerLoadRequest(
        @NotEmpty(message = "must contain at least one customer")
        @Size(max = 100, message = "may contain at most 100 customers per request")
        List<@Valid CustomerLoadItem> customers) {
}
