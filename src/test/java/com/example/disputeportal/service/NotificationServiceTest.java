package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeReason;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.entity.Notification;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.CustomerRepository;
import com.example.disputeportal.repository.NotificationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for who gets told what, and for the ownership rules on reading and marking notifications.
 * Repositories are mocked and the clock is fixed.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Captor
    private ArgumentCaptor<List<Notification>> saved;

    private NotificationService service;
    private Dispute dispute;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, customerRepository, CLOCK);
        Customer customer = new Customer("thandi", "hash", "Thandi Nkosi");
        BankTransaction tx = new BankTransaction(customer, "TXN-1", "Takealot.com", "Card purchase",
                new BigDecimal("1299.00"), "ZAR", LocalDateTime.of(2026, 10, 1, 12, 0));
        dispute = new Dispute(tx, DisputeReason.GOODS_NOT_RECEIVED, "The parcel never arrived", "thandi",
                LocalDateTime.of(2026, 10, 2, 9, 0));
    }

    @Test
    void aNewDisputeNotifiesEveryAdmin() {
        when(customerRepository.findUsernamesByRole(Role.ADMIN)).thenReturn(List.of("admin1", "admin2"));

        service.disputeRaised(dispute);

        verify(notificationRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Notification::getRecipient).containsExactly("admin1", "admin2");
        assertThat(saved.getValue()).allSatisfy(n -> {
            assertThat(n.getMessage()).contains("TXN-1").contains("waiting for review");
            assertThat(n.isRead()).isFalse();
            assertThat(n.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
        });
    }

    @Test
    void aStatusChangeNotifiesOnlyTheCustomerWhoRaisedTheDispute() {
        dispute.changeStatus(DisputeStatus.UNDER_REVIEW, null, LocalDateTime.of(2026, 10, 3, 9, 0), "agent1");

        service.statusChanged(dispute);

        verify(notificationRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().get(0).getRecipient()).isEqualTo("thandi");
        assertThat(saved.getValue().get(0).getMessage()).contains("Under review");
    }

    @Test
    void newEvidenceNotifiesEveryAdmin() {
        when(customerRepository.findUsernamesByRole(Role.ADMIN)).thenReturn(List.of("admin1"));

        service.evidenceAdded(dispute);

        verify(notificationRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Notification::getRecipient).containsExactly("admin1");
        assertThat(saved.getValue().get(0).getMessage()).contains("evidence");
    }

    @Test
    void withNoAdminsNothingBreaks() {
        when(customerRepository.findUsernamesByRole(Role.ADMIN)).thenReturn(List.of());

        service.disputeRaised(dispute);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void markingReadSetsTheTimeOnlyOnce() {
        Notification n = new Notification("thandi", 1L, "Message", LocalDateTime.of(2026, 10, 3, 9, 0));
        when(notificationRepository.findByIdAndRecipient(5L, "thandi")).thenReturn(Optional.of(n));

        service.markRead(5L, "thandi");
        LocalDateTime firstRead = n.getReadAt();
        n.markRead(LocalDateTime.of(2030, 1, 1, 0, 0));

        assertThat(n.isRead()).isTrue();
        assertThat(firstRead).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
        assertThat(n.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void someoneElsesNotificationIsNotFound() {
        when(notificationRepository.findByIdAndRecipient(5L, "sipho")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(5L, "sipho")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllReadDelegatesWithTheCurrentTime() {
        when(notificationRepository.markAllRead(any(), any())).thenReturn(3);

        assertThat(service.markAllRead("thandi")).isEqualTo(3);
        verify(notificationRepository).markAllRead("thandi", LocalDateTime.of(2026, 10, 13, 10, 0));
    }
}
