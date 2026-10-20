package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.disputeportal.dto.TransactionLoadItem;
import com.example.disputeportal.dto.TransactionLoadResult;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.exception.BatchRejectedException;
import com.example.disputeportal.repository.BankTransactionRepository;
import com.example.disputeportal.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for loading transactions: atomic batches, idempotent replays, conflicts, unknown customers and
 * validation of the batch as a whole. Repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class TransactionLoadServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
    private static final Instant WHEN = Instant.parse("2026-10-01T08:30:00Z");

    @Mock
    private BankTransactionRepository transactionRepository;
    @Mock
    private CustomerRepository customerRepository;

    private TransactionLoadService service;
    private Customer thandi;

    @BeforeEach
    void setUp() {
        service = new TransactionLoadService(transactionRepository, customerRepository, CLOCK);
        thandi = new Customer("thandi", "hash", "Thandi Nkosi");
        ReflectionTestUtils.setField(thandi, "id", 7L);
    }

    @Test
    void savesNewTransactions() {
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(thandi));
        when(transactionRepository.findByReferenceIn(any())).thenReturn(List.of());

        TransactionLoadResult result = service.load(
                List.of(item("A-1", "thandi", "10.50"), item("A-2", "thandi", "20.00")), "loader");

        assertThat(result).isEqualTo(new TransactionLoadResult(2, 2, 0));
        verify(transactionRepository).saveAll(any());
    }

    @Test
    void resendingIdenticalTransactionsIsAnIdempotentDuplicate() {
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(thandi));
        when(transactionRepository.findByReferenceIn(any()))
                .thenReturn(List.of(stored("A-1", thandi, "10.50")));

        TransactionLoadResult result = service.load(List.of(item("A-1", "thandi", "10.5")), "loader");

        assertThat(result).isEqualTo(new TransactionLoadResult(1, 0, 1));
    }

    @Test
    void reusingAReferenceWithDifferentDetailsRejectsTheWholeBatch() {
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(thandi));
        when(transactionRepository.findByReferenceIn(any()))
                .thenReturn(List.of(stored("A-1", thandi, "10.50")));

        assertThatThrownBy(() -> service.load(
                List.of(item("A-1", "thandi", "99.99"), item("A-2", "thandi", "5.00")), "loader"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getProblems()).extracting("path").containsExactly("transactions[0].reference");
                });
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void unknownCustomersAreRejected() {
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(thandi));

        assertThatThrownBy(() -> service.load(List.of(item("A-1", "nobody", "10.00")), "loader"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getProblems()).extracting("path").containsExactly("transactions[0].customerUsername");
                });
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void adminsCannotOwnTransactions() {
        Customer admin = new Customer("boss", "hash", "Boss", Role.ADMIN);
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.load(List.of(item("A-1", "boss", "10.00")), "loader"))
                .isInstanceOf(BatchRejectedException.class);
    }

    @Test
    void repeatedReferencesInOneBatchAreRejected() {
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(thandi));

        assertThatThrownBy(() -> service.load(
                List.of(item("A-1", "thandi", "10.00"), item("A-1", "thandi", "10.00")), "loader"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex ->
                        assertThat(ex.getProblems()).extracting("path").containsExactly("transactions[1].reference"));
    }

    @Test
    void futureDatedTransactionsAreRejected() {
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(thandi));
        TransactionLoadItem future = new TransactionLoadItem("A-1", "thandi", "Shop", null,
                new BigDecimal("1.00"), "ZAR", Instant.parse("2026-10-14T10:00:00Z"));

        assertThatThrownBy(() -> service.load(List.of(future), "loader"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex ->
                        assertThat(ex.getProblems()).extracting("path").containsExactly("transactions[0].transactionDate"));
    }

    private static TransactionLoadItem item(String reference, String username, String amount) {
        return new TransactionLoadItem(reference, username, "Takealot.com", "Card purchase",
                new BigDecimal(amount), "ZAR", WHEN);
    }

    private BankTransaction stored(String reference, Customer owner, String amount) {
        return new BankTransaction(owner, reference, "Takealot.com", "Card purchase", new BigDecimal(amount), "ZAR",
                LocalDateTime.ofInstant(WHEN, ZoneOffset.UTC));
    }
}
