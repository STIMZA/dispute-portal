package com.example.disputeportal.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CustomerLoadRequest(
        @NotEmpty(message = "must contain at least one customer")
        @Size(max = 100, message = "may contain at most 100 customers per request")
        List<@Valid CustomerLoadItem> customers) {

}
