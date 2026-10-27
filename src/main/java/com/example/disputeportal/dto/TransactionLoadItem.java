package com.example.disputeportal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** One transaction in a load request. */
public record TransactionLoadItem(
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may only contain letters, digits, '.', '_' and '-'")
        String reference,

        @NotBlank @Size(max = 50)
        String customerUsername,

        @NotBlank @Size(max = 100)
        String merchant,

        @Size(max = 255)
        String description,

        @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "must have at most 10 whole digits and 2 decimals")
        BigDecimal amount,

        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase currency code, e.g. ZAR")
        String currency,

        @NotNull
        Instant transactionDate) {
}
