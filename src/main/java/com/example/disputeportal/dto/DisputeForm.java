package com.example.disputeportal.dto;

import com.example.disputeportal.entity.DisputeReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The customer's dispute form is a plain class with getters and setters because
 * Thymeleaf's th:field binds to JavaBean properties. The validation messages
 * are shown next to the fields.
 */
public class DisputeForm {

    /**
     * The reason chosen from the dropdown.
     */
    @NotNull(message = "Select a reason for the dispute.")
    private DisputeReason reason;

    /**
     * The customer's own description of what happened (10 to 1000 characters).
     */
    @NotBlank(message = "Tell us what happened.")
    @Size(min = 10, max = 1000, message = "Use between 10 and 1000 characters.")
    private String details;

    public DisputeReason getReason() {
        return reason;
    }

    public void setReason(DisputeReason reason) {
        this.reason = reason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
