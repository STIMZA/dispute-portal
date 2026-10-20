package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.disputeportal.dto.DisputeCounts;
import com.example.disputeportal.dto.TransactionRow;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test that the transactions page rows carry the dispute flags and the logged/closed counts.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private DisputeService disputeService;

    @Test
    void rowsCarryDisputesLoggedAndClosedCounts() {
        Customer customer = new Customer("thandi", "hash", "Thandi Nkosi");
        BankTransaction disputed = transaction(customer, 1L, "TXN-1");
        BankTransaction untouched = transaction(customer, 2L, "TXN-2");
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(transactionRepository.findByCustomerUsername("thandi", pageRequest))
                .thenReturn(new PageImpl<>(List.of(disputed, untouched), pageRequest, 2));
        when(disputeService.findOpenDisputeTransactionIds(any())).thenReturn(Set.of(1L));
        when(disputeService.countByTransaction(any())).thenReturn(Map.of(1L, new DisputeCounts(3, 2)));
        when(disputeService.isWithinWindow(any())).thenReturn(true);

        Page<TransactionRow> rows = new TransactionService(transactionRepository, disputeService)
                .list("thandi", pageRequest);

        TransactionRow first = rows.getContent().get(0);
        assertThat(first.disputesLogged()).isEqualTo(3);
        assertThat(first.disputesClosed()).isEqualTo(2);
        assertThat(first.disputeOpen()).isTrue();
        assertThat(first.disputable()).isFalse();

        TransactionRow second = rows.getContent().get(1);
        assertThat(second.disputesLogged()).isZero();
        assertThat(second.disputesClosed()).isZero();
        assertThat(second.disputable()).isTrue();
    }

    private BankTransaction transaction(Customer customer, Long id, String reference) {
        BankTransaction tx = new BankTransaction(customer, reference, "Takealot.com", "Card purchase",
                new BigDecimal("100.00"), "ZAR", LocalDateTime.of(2026, 10, 1, 12, 0));
        ReflectionTestUtils.setField(tx, "id", id);
        return tx;
    }
}
