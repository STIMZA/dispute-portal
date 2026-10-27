package com.example.disputeportal.service;

import com.example.disputeportal.dto.ApiProblem;
import com.example.disputeportal.dto.TransactionLoadItem;
import com.example.disputeportal.dto.TransactionLoadResult;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.exception.BatchRejectedException;
import com.example.disputeportal.repository.BankTransactionRepository;
import com.example.disputeportal.repository.CustomerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Loads a batch of transactions atomically: either every new transaction is saved, or none is.
 *
 * <p>Loads are idempotent. Re-sending a transaction that was already loaded with identical details is
 * counted as a duplicate and ignored, so a client can safely retry after a timeout. Re-using a reference
 * with different details is a conflict and rejects the whole batch.
 */
@Service
@PreAuthorize("hasAuthority('SCOPE_transactions:load')")
public class TransactionLoadService {

    private static final Logger log = LoggerFactory.getLogger(TransactionLoadService.class);
    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(5);

    private final BankTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final Clock clock;

    public TransactionLoadService(BankTransactionRepository transactionRepository,
                                  CustomerRepository customerRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    /**
     * Loads a batch of transactions: all of them or none.
     *
     * Runs in one database transaction, in three stages:
     * 1. validate the batch as a whole (repeated references, unknown customers, dates in the future);
     * 2. compare each item with what is already stored, to tell new items from harmless retries and from conflicts;
     * 3. save the new items.
     *
     * Any problem in stage 1 or 2 throws a BatchRejectedException before anything is saved.
     *
     * @param items      the transactions to load (already validated field by field by Bean Validation)
     * @param clientName the API client making the call, for the log
     * @return how many items were received, created and recognised as duplicates
     */
    @Transactional
    public TransactionLoadResult load(List<TransactionLoadItem> items, String clientName) {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. Validate the batch as a whole.
        List<ApiProblem> problems = new ArrayList<>();
        Set<String> seenReferences = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            if (!seenReferences.add(items.get(i).reference())) {
                problems.add(problem(i, "reference", "appears more than once in this request"));
            }
        }
        Map<String, Customer> customers = customerRepository
                .findByUsernameIn(items.stream().map(TransactionLoadItem::customerUsername).collect(Collectors.toSet()))
                .stream()
                .filter(c -> c.getRole() == Role.CUSTOMER)
                .collect(Collectors.toMap(Customer::getUsername, Function.identity()));
        for (int i = 0; i < items.size(); i++) {
            TransactionLoadItem item = items.get(i);
            if (!customers.containsKey(item.customerUsername())) {
                problems.add(problem(i, "customerUsername", "is not a known customer"));
            }
            if (toUtc(item).isAfter(now.plus(ALLOWED_CLOCK_SKEW))) {
                problems.add(problem(i, "transactionDate", "must not be in the future"));
            }
        }
        if (!problems.isEmpty()) {
            log.warn("Transaction batch from client '{}' rejected: {} invalid item(s)", clientName, problems.size());
            throw new BatchRejectedException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The batch was rejected and nothing was saved", problems);
        }

        // 2. Separate new transactions from retries and conflicts.
        Map<String, BankTransaction> existing = transactionRepository
                .findByReferenceIn(seenReferences).stream()
                .collect(Collectors.toMap(BankTransaction::getReference, Function.identity()));
        List<BankTransaction> toCreate = new ArrayList<>();
        List<ApiProblem> conflicts = new ArrayList<>();
        int duplicates = 0;
        for (int i = 0; i < items.size(); i++) {
            TransactionLoadItem item = items.get(i);
            Customer customer = customers.get(item.customerUsername());
            BankTransaction found = existing.get(item.reference());
            if (found == null) {
                toCreate.add(toEntity(item, customer));
            } else if (isSame(found, item, customer)) {
                duplicates++;
            } else {
                conflicts.add(problem(i, "reference", "already exists with different details"));
            }
        }
        if (!conflicts.isEmpty()) {
            log.warn("Transaction batch from client '{}' rejected: {} conflicting reference(s)",
                    clientName, conflicts.size());
            throw new BatchRejectedException(HttpStatus.CONFLICT,
                    "The batch was rejected and nothing was saved", conflicts);
        }

        // 3. Save. Amounts and customers are never logged, only counts.
        transactionRepository.saveAll(toCreate);
        log.info("Transaction batch from client '{}': received={}, created={}, duplicates={}",
                clientName, items.size(), toCreate.size(), duplicates);
        return new TransactionLoadResult(items.size(), toCreate.size(), duplicates);
    }

    /**
     * Builds a problem that points at an item and field, e.g. transactions[2].amount.
     */
    private static ApiProblem problem(int index, String field, String message) {
        return new ApiProblem("transactions[" + index + "]." + field, message);
    }

    /**
     * The request's instant as a UTC date-time truncated to microseconds, the precision the database stores.
     */
    private static LocalDateTime toUtc(TransactionLoadItem item) {
        return LocalDateTime.ofInstant(item.transactionDate().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    /**
     * Turns a validated request item into an entity (trimmed text, amount with exactly two decimals).
     */
    private static BankTransaction toEntity(TransactionLoadItem item, Customer customer) {
        String description = StringUtils.hasText(item.description()) ? item.description().trim() : null;
        return new BankTransaction(customer, item.reference(), item.merchant().trim(), description,
                item.amount().setScale(2, RoundingMode.UNNECESSARY), item.currency(), toUtc(item));
    }

    /**
     * True if a stored transaction matches a request item in everything that matters (customer, merchant, amount,
     * currency, date). Used to tell a harmless retry from a conflicting reuse of a reference.
     */
    private static boolean isSame(BankTransaction existing, TransactionLoadItem item, Customer customer) {
        BigDecimal amount = item.amount().setScale(2, RoundingMode.UNNECESSARY);
        return existing.getCustomer().getId().equals(customer.getId())
                && existing.getMerchant().equals(item.merchant().trim())
                && existing.getAmount().compareTo(amount) == 0
                && existing.getCurrency().equals(item.currency())
                && existing.getTransactionDate().equals(toUtc(item));
    }
}
