package com.example.disputeportal.service;

import com.example.disputeportal.dto.ApiProblem;
import com.example.disputeportal.dto.CustomerLoadItem;
import com.example.disputeportal.dto.CustomerLoadResult;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.exception.BatchRejectedException;
import com.example.disputeportal.repository.CustomerRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates customer accounts in batches, atomically: either every new account is created, or none is.
 *
 * <p>Like the transaction loader it is idempotent. Re-sending an account that already exists with the same
 * name and password counts as a duplicate and is ignored, so a client can safely retry after a timeout. Re-using
 * a username with different details is a conflict and rejects the whole batch.
 *
 * <p>Only ordinary customers can be created here. Admins are never created through the API.
 *
 * <p>Passwords are only ever passed to the BCrypt encoder. They are never logged, never stored in plain text and
 * never included in error responses.
 */
@Service
@PreAuthorize("hasAuthority('SCOPE_customers:create')")
public class CustomerProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(CustomerProvisioningService.class);

    /** BCrypt ignores everything after the 72nd byte, so longer passwords are rejected instead of truncated. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerProvisioningService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates the accounts that don't exist yet.
     *
     * <p>Stages: (1) validate the batch as a whole, (2) compare each item with the accounts that already exist, to
     * tell new ones from retries and conflicts, (3) hash the passwords of the new accounts and save them. Hashing is
     * left to the last stage because it is slow and pointless for a batch that is going to be rejected.
     *
     * @param items      the accounts to create (already validated field by field by Bean Validation)
     * @param clientName the API client making the call, for the log
     * @return how many items were received, created and recognised as duplicates
     * @throws BatchRejectedException if anything is invalid or conflicts; nothing is saved
     */
    @Transactional
    public CustomerLoadResult create(List<CustomerLoadItem> items, String clientName) {
        // 1. Checks that only make sense across the whole batch, or beyond what annotations can express.
        List<ApiProblem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            CustomerLoadItem item = items.get(i);
            if (!seen.add(item.username())) {
                problems.add(problem(i, "username", "appears more than once in this request"));
            }
            if (item.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
                problems.add(problem(i, "password", "is too long once encoded (72 bytes at most)"));
            }
            if (item.password().equalsIgnoreCase(item.username())) {
                problems.add(problem(i, "password", "must not be the same as the username"));
            }
        }
        if (!problems.isEmpty()) {
            log.warn("Customer batch from client '{}' rejected: {} invalid item(s)", clientName, problems.size());
            throw new BatchRejectedException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The batch was rejected and nothing was saved", problems);
        }

        // 2. Separate new accounts from retries and conflicts.
        Map<String, Customer> existing = customerRepository.findByUsernameIn(seen).stream()
                .collect(Collectors.toMap(Customer::getUsername, Function.identity()));
        List<CustomerLoadItem> toCreate = new ArrayList<>();
        List<ApiProblem> conflicts = new ArrayList<>();
        int duplicates = 0;
        for (int i = 0; i < items.size(); i++) {
            CustomerLoadItem item = items.get(i);
            Customer found = existing.get(item.username());
            if (found == null) {
                toCreate.add(item);
            } else if (isSame(found, item)) {
                duplicates++;
            } else {
                conflicts.add(problem(i, "username", "already exists with different details"));
            }
        }
        if (!conflicts.isEmpty()) {
            log.warn("Customer batch from client '{}' rejected: {} conflicting username(s)",
                    clientName, conflicts.size());
            throw new BatchRejectedException(HttpStatus.CONFLICT,
                    "The batch was rejected and nothing was saved", conflicts);
        }

        // 3. Hash and save. Only counts are logged: never passwords, and not the names of the new accounts.
        List<Customer> created = toCreate.stream()
                .map(item -> new Customer(item.username(), passwordEncoder.encode(item.password()),
                        item.fullName().trim()))
                .toList();
        customerRepository.saveAll(created);
        log.info("Customer batch from client '{}': received={}, created={}, duplicates={}",
                clientName, items.size(), created.size(), duplicates);
        return new CustomerLoadResult(items.size(), created.size(), duplicates);
    }

    private static ApiProblem problem(int index, String field, String message) {
        return new ApiProblem("customers[" + index + "]." + field, message);
    }

    /**
     * True if the existing account is an ordinary customer with the same name and the same password. The password
     * is compared with BCrypt's matches() against the stored hash; it is never compared as plain text.
     */
    private boolean isSame(Customer existing, CustomerLoadItem item) {
        return existing.getRole() == Role.CUSTOMER
                && existing.getFullName().equals(item.fullName().trim())
                && passwordEncoder.matches(item.password(), existing.getPasswordHash());
    }
}
