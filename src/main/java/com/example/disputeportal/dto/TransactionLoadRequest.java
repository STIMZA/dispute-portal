package com.example.disputeportal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of a loading API call: 1 to 500 transactions. Each item is validated individually (the {@code @Valid}).
 */
public record TransactionLoadRequest(
        @NotEmpty(message = "must contain at least one transaction")
        @Size(max = 500, message = "may contain at most 500 transactions per request")
        List<@Valid TransactionLoadItem> transactions) {
}
