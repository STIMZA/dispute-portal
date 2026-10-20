package com.example.disputeportal.entity;

import java.util.Arrays;
import java.util.List;

/**
 * Where a dispute is in its workflow.
 *
 * SUBMITTED -> UNDER_REVIEW -> RESOLVED_IN_FAVOUR or REJECTED (the review step may be skipped). The last two are
 * final. The workflow rules live in {@link #canTransitionTo}; the two values per constant are the text shown to
 * users and the suffix of the CSS class that colours the status chip.
 */
public enum DisputeStatus {
    /**
     * Raised by the customer, waiting for an admin to pick it up.
     */
    SUBMITTED("Submitted", "open"),
    /**
     * An admin is looking into it.
     */
    UNDER_REVIEW("Under review", "review"),
    /**
     * Final: the customer's dispute was upheld.
     */
    RESOLVED_IN_FAVOUR("Resolved in your favour", "won"),
    /**
     * Final: the dispute was not upheld.
     */
    REJECTED("Not upheld", "lost");

    private final String label;
    private final String cssClass;

    /**
     * @param label text shown to users
     * @param cssClass suffix for the CSS class (status-open, status-review, status-won, status-lost)
     */
    DisputeStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    /**
     * Text shown to users.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Suffix of the CSS class that colours the status chip.
     */
    public String getCssClass() {
        return cssClass;
    }

    /** Open disputes are still being worked on; the others are final. */
    public boolean isOpen() {
        return this == SUBMITTED || this == UNDER_REVIEW;
    }

    /**
     * A final dispute has an outcome and can no longer be changed.
     */
    public boolean isFinal() {
        return !isOpen();
    }

    /** Workflow: SUBMITTED -> UNDER_REVIEW -> (RESOLVED_IN_FAVOUR | REJECTED); a review step can be skipped. */
    public boolean canTransitionTo(DisputeStatus next) {
        return switch (this) {
            case SUBMITTED -> next == UNDER_REVIEW || next == RESOLVED_IN_FAVOUR || next == REJECTED;
            case UNDER_REVIEW -> next == RESOLVED_IN_FAVOUR || next == REJECTED;
            case RESOLVED_IN_FAVOUR, REJECTED -> false;
        };
    }

    /**
     * The statuses an admin may move this dispute to (empty for a final dispute). Drives the dropdown on the review page.
     */
    public List<DisputeStatus> nextStatuses() {
        return Arrays.stream(values()).filter(this::canTransitionTo).toList();
    }
}
