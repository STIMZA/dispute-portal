package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.disputeportal.entity.DisputeStatus;
import org.junit.jupiter.api.Test;

/**
 * Tests the dispute workflow rules that live on the DisputeStatus enum.
 */
class DisputeStatusTest {

    @Test
    void submittedCanGoToAnyOpenOrFinalStateExceptItself() {
        assertThat(DisputeStatus.SUBMITTED.nextStatuses()).containsExactly(
                DisputeStatus.UNDER_REVIEW, DisputeStatus.RESOLVED_IN_FAVOUR, DisputeStatus.REJECTED);
    }

    @Test
    void underReviewCanOnlyBeDecided() {
        assertThat(DisputeStatus.UNDER_REVIEW.nextStatuses()).containsExactly(
                DisputeStatus.RESOLVED_IN_FAVOUR, DisputeStatus.REJECTED);
        assertThat(DisputeStatus.UNDER_REVIEW.canTransitionTo(DisputeStatus.SUBMITTED)).isFalse();
    }

    @Test
    void finalStatesAreTerminal() {
        assertThat(DisputeStatus.RESOLVED_IN_FAVOUR.nextStatuses()).isEmpty();
        assertThat(DisputeStatus.REJECTED.nextStatuses()).isEmpty();
        assertThat(DisputeStatus.REJECTED.isFinal()).isTrue();
        assertThat(DisputeStatus.SUBMITTED.isOpen()).isTrue();
    }
}
