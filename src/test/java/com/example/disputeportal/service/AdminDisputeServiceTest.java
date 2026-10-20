package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeEvent;
import com.example.disputeportal.entity.DisputeReason;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.DisputeEventRepository;
import com.example.disputeportal.repository.DisputeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the admin workflow: which status changes are allowed, when a note is required, and that closed
 * disputes stay closed. Repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AdminDisputeServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private DisputeRepository disputeRepository;
    @Mock
    private DisputeEventRepository eventRepository;

    private AdminDisputeService service;
    private Dispute dispute;

    @BeforeEach
    void setUp() {
        service = new AdminDisputeService(disputeRepository, eventRepository, CLOCK);
        Customer customer = new Customer("thandi", "hash", "Thandi Nkosi");
        BankTransaction tx = new BankTransaction(customer, "TXN-1", "Takealot.com", "Card purchase",
                new BigDecimal("1299.00"), "ZAR", LocalDateTime.of(2026, 10, 1, 12, 0));
        dispute = new Dispute(tx, DisputeReason.GOODS_NOT_RECEIVED, "The parcel never arrived",
                "thandi", LocalDateTime.of(2026, 10, 2, 9, 0));
    }

    @Test
    void movesSubmittedDisputeToUnderReviewWithoutANote() {
        when(disputeRepository.findWithLockById(1L)).thenReturn(Optional.of(dispute));

        service.updateStatus(1L, DisputeStatus.UNDER_REVIEW, "  ", "agent1");

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(dispute.getResolutionNote()).isNull();
        assertThat(dispute.getUpdatedBy()).isEqualTo("agent1");
        assertThat(dispute.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
    }

    @Test
    void resolvingRequiresANote() {
        when(disputeRepository.findWithLockById(1L)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.updateStatus(1L, DisputeStatus.REJECTED, null, "agent1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("note");
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void resolvesWithTrimmedNote() {
        when(disputeRepository.findWithLockById(1L)).thenReturn(Optional.of(dispute));

        service.updateStatus(1L, DisputeStatus.RESOLVED_IN_FAVOUR, "  Refunded in full  ", "agent1");

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_IN_FAVOUR);
        assertThat(dispute.getResolutionNote()).isEqualTo("Refunded in full");
        verify(eventRepository).save(any(DisputeEvent.class));
    }

    @Test
    void closedDisputesCannotBeChanged() {
        dispute.changeStatus(DisputeStatus.REJECTED, "Not upheld", LocalDateTime.of(2026, 10, 5, 9, 0), "agent1");
        when(disputeRepository.findWithLockById(1L)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.updateStatus(1L, DisputeStatus.RESOLVED_IN_FAVOUR, "Changed my mind", "agent2"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.REJECTED);
    }

    @Test
    void unknownDisputeIsNotFound() {
        when(disputeRepository.findWithLockById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(9L, DisputeStatus.UNDER_REVIEW, null, "agent1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
