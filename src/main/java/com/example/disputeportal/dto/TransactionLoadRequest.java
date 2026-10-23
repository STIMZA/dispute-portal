package com.example.disputeportal.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Here we define the structure for loading transactions. Each item is validated
 * individually (the {@code @Valid}).
 */
public record TransactionLoadRequest(
        @NotEmpty(message = "must contain at least one transaction")
        @Size(max = 500, message = "may contain at most 500 transactions per request")
        List<@Valid TransactionLoadItem> transactions) {

}
