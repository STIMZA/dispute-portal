package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.disputeportal.dto.CustomerLoadItem;
import com.example.disputeportal.dto.CustomerLoadResult;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.exception.BatchRejectedException;
import com.example.disputeportal.repository.CustomerRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for creating customers through the API: atomic batches, idempotent replays, conflicts, and the
 * password rules. The repository is mocked; a real BCrypt encoder (with the cheapest cost, to keep the tests fast)
 * checks that passwords are hashed and compared properly.
 */
@ExtendWith(MockitoExtension.class)
class CustomerProvisioningServiceTest {

    private static final String PASSWORD = "correct horse battery";

    @Mock
    private CustomerRepository customerRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private CustomerProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new CustomerProvisioningService(customerRepository, encoder);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsNewCustomersWithHashedPasswords() {
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of());

        CustomerLoadResult result = service.create(
                List.of(item("thandi.nkosi", "Thandi Nkosi", PASSWORD), item("sipho.d", "  Sipho Dlamini ", PASSWORD)),
                "provisioner");

        assertThat(result).isEqualTo(new CustomerLoadResult(2, 2, 0));
        ArgumentCaptor<List<Customer>> saved = ArgumentCaptor.forClass(List.class);
        verify(customerRepository).saveAll(saved.capture());
        Customer first = saved.getValue().get(0);
        assertThat(first.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(first.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, first.getPasswordHash())).isTrue();
        assertThat(saved.getValue().get(1).getFullName()).isEqualTo("Sipho Dlamini");
    }

    @Test
    void resendingAnIdenticalAccountIsAnIdempotentDuplicate() {
        Customer existing = new Customer("thandi.nkosi", encoder.encode(PASSWORD), "Thandi Nkosi");
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(existing));

        CustomerLoadResult result = service.create(List.of(item("thandi.nkosi", "Thandi Nkosi", PASSWORD)), "p");

        assertThat(result).isEqualTo(new CustomerLoadResult(1, 0, 1));
    }

    @Test
    void anExistingUsernameWithADifferentPasswordIsAConflict() {
        Customer existing = new Customer("thandi.nkosi", encoder.encode(PASSWORD), "Thandi Nkosi");
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(List.of(
                        item("thandi.nkosi", "Thandi Nkosi", "a completely different one"),
                        item("new.person", "New Person", PASSWORD)), "p"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getProblems()).extracting("path").containsExactly("customers[0].username");
                });
        // The conflict rejects the whole batch, so the new person isn't created either.
        verify(customerRepository, never()).saveAll(any());
    }

    @Test
    void anExistingAdminIsNeverTreatedAsAMatchingCustomer() {
        Customer admin = new Customer("boss.person", encoder.encode(PASSWORD), "Boss", Role.ADMIN);
        when(customerRepository.findByUsernameIn(any())).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.create(List.of(item("boss.person", "Boss", PASSWORD)), "p"))
                .isInstanceOfSatisfying(BatchRejectedException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void repeatedUsernamesInOneBatchAreRejected() {
        assertThatThrownBy(() -> service.create(List.of(
                        item("thandi.nkosi", "Thandi", PASSWORD), item("thandi.nkosi", "Thandi", PASSWORD)), "p"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex ->
                        assertThat(ex.getProblems()).extracting("path").containsExactly("customers[1].username"));
    }

    @Test
    void aPasswordEqualToTheUsernameIsRejected() {
        assertThatThrownBy(() -> service.create(List.of(item("long.username-1", "Someone", "LONG.USERNAME-1")), "p"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex ->
                        assertThat(ex.getProblems()).extracting("path").containsExactly("customers[0].password"));
    }

    @Test
    void aPasswordLongerThan72BytesIsRejectedNotTruncated() {
        // 40 two-byte characters: only 40 characters, so it passes the 72-character limit, but it is 80 bytes.
        String tooManyBytes = "\u00e9".repeat(40);

        assertThatThrownBy(() -> service.create(List.of(item("thandi.nkosi", "Thandi", tooManyBytes)), "p"))
                .isInstanceOfSatisfying(BatchRejectedException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getProblems()).extracting("path").containsExactly("customers[0].password");
                });
        verify(customerRepository, never()).saveAll(any());
    }

    @Test
    void theItemDoesNotPrintItsPassword() {
        assertThat(item("thandi.nkosi", "Thandi", PASSWORD).toString())
                .doesNotContain(PASSWORD)
                .contains("thandi.nkosi");
    }

    private static CustomerLoadItem item(String username, String fullName, String password) {
        return new CustomerLoadItem(username, fullName, password);
    }
}
