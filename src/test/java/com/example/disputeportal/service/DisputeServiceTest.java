package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.disputeportal.config.DisputeProperties;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeEvent;
import com.example.disputeportal.entity.DisputeReason;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.BankTransactionRepository;
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
 * Unit tests for raising a dispute. Repositories are mocked and the clock is fixed, so the tests are fast and
 * don't depend on today's date. No Spring context or database is started.
 */
@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private DisputeRepository disputeRepository;
    @Mock
    private DisputeEventRepository eventRepository;

    private DisputeService service;
    private final Customer customer = new Customer("thandi", "hash", "Thandi Nkosi");

    @BeforeEach
    void setUp() {
        service = new DisputeService(transactionRepository, disputeRepository, eventRepository,
                new DisputeProperties(120), CLOCK);
    }

    @Test
    void raisesDisputeForRecentTransaction() {
        givenTransactionDaysOld(10);
        when(disputeRepository.existsByTransactionIdAndStatusIn(any(), any())).thenReturn(false);
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        Dispute dispute = service.raise(1L, "thandi", DisputeReason.DUPLICATE_CHARGE, "  Charged twice today  ");

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(dispute.getDetails()).isEqualTo("Charged twice today");
        assertThat(dispute.getCreatedBy()).isEqualTo("thandi");
        assertThat(dispute.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
        verify(eventRepository).save(any(DisputeEvent.class));
    }

    @Test
    void rejectsSecondDisputeWhileOneIsOpen() {
        givenTransactionDaysOld(10);
        when(disputeRepository.existsByTransactionIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.raise(1L, "thandi", DisputeReason.FRAUD, "Not my purchase"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("open dispute");
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void rejectsTransactionsOutsideTheDisputeWindow() {
        givenTransactionDaysOld(121);

        assertThatThrownBy(() -> service.raise(1L, "thandi", DisputeReason.FRAUD, "Not my purchase"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("120 days");
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void treatsAnotherCustomersTransactionAsNotFound() {
        when(transactionRepository.findWithLockByIdAndCustomerUsername(eq(1L), eq("sipho"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.raise(1L, "sipho", DisputeReason.OTHER, "Something is wrong here"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenTransactionDaysOld(int days) {
        BankTransaction tx = new BankTransaction(customer, "TXN-1", "Takealot.com", "Card purchase",
                new BigDecimal("1299.00"), "ZAR", LocalDateTime.now(CLOCK).minusDays(days));
        when(transactionRepository.findWithLockByIdAndCustomerUsername(eq(1L), eq("thandi"))).thenReturn(Optional.of(tx));
    }
}
