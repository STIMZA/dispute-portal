package com.example.disputeportal.dto;

import com.example.disputeportal.entity.DisputeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The admin's decision form on the review page. A plain class with getters and setters (not a record) because
 * Thymeleaf's th:field binds to JavaBean properties.
 */
public class AdminDecisionForm {

    /**
     * The status the admin chose. Only the statuses offered by the dropdown are valid; the service checks the workflow again.
     */
    @NotNull(message = "Choose a decision.")
    private DisputeStatus status;

    /**
     * Message to the customer. Optional when moving to Under review; the service requires it for a final decision.
     */
    @Size(max = 500, message = "Keep the note to 500 characters or fewer.")
    private String note;

    public DisputeStatus getStatus() {
        return status;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
