package com.example.disputeportal.service;

import com.example.disputeportal.config.DisputeProperties;
import com.example.disputeportal.dto.DisputeCounts;
import com.example.disputeportal.dto.DisputeDetail;
import com.example.disputeportal.dto.DisputeEventRow;
import com.example.disputeportal.dto.DisputeRow;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeEvent;
import com.example.disputeportal.entity.DisputeReason;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.BankTransactionRepository;
import com.example.disputeportal.repository.DisputeEventRepository;
import com.example.disputeportal.repository.DisputeRepository;
import com.example.disputeportal.repository.TransactionDisputeCount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-facing dispute logic.
 *
 * Raises disputes and reads them back for the signed-in customer: the dispute history list, one dispute with its
 * progress timeline, the earlier disputes on a transaction, and the counts shown on the transactions page.
 *
 * Every lookup is scoped to the customer's username inside the query itself, so one customer can never see or
 * act on another customer's data (a stranger's id simply looks like "not found").
 *
 * Used by DisputeController (web pages) and TransactionService (transactions page).
 */
@Service
public class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    /** A transaction with a dispute in one of these states cannot be disputed again. */
    static final Set<DisputeStatus> OPEN_STATUSES = EnumSet.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_REVIEW);

    /** Disputes with a final outcome. */
    static final Set<DisputeStatus> CLOSED_STATUSES =
            EnumSet.of(DisputeStatus.RESOLVED_IN_FAVOUR, DisputeStatus.REJECTED);

    private final BankTransactionRepository transactionRepository;
    private final DisputeRepository disputeRepository;
    private final DisputeEventRepository eventRepository;
    private final DisputeProperties properties;
    private final Clock clock;

    /**
     * Spring supplies these collaborators through the constructor (no field injection), which also makes the class
     * easy to unit test with mocks and a fixed clock.
     */
    public DisputeService(BankTransactionRepository transactionRepository, DisputeRepository disputeRepository,
                          DisputeEventRepository eventRepository, DisputeProperties properties, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.disputeRepository = disputeRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Raises a new dispute against one of the customer's transactions.
     *
     * Runs in one database transaction: the dispute and its first history entry are saved together or not at all.
     *
     * @param transactionId id of the transaction being disputed
     * @param username      the signed-in customer, who must own the transaction
     * @param reason        the reason the customer picked
     * @param details       the customer's own description of what happened
     * @return the saved dispute
     * @throws ResourceNotFoundException if the transaction doesn't exist or belongs to someone else
     * @throws BusinessRuleException     if the transaction is outside the dispute window or already has an open dispute
     */
    @Transactional
    public Dispute raise(Long transactionId, String username, DisputeReason reason, String details) {
        // The lookup is scoped to the signed-in customer, so another customer's id looks like "not found".
        BankTransaction transaction = transactionRepository.findWithLockByIdAndCustomerUsername(transactionId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + transactionId + " not found"));

        // Rule 1: only recent transactions can be disputed (the window is app.dispute.window-days).
        if (!isWithinWindow(transaction.getTransactionDate())) {
            throw new BusinessRuleException("This transaction is older than " + properties.windowDays()
                    + " days, so it can no longer be disputed.");
        }
        // Rule 2: only one open dispute per transaction. This check can't be raced: the lookup above locked the
        // transaction row, so a simultaneous submission waits for us to finish and then sees this dispute.
        if (disputeRepository.existsByTransactionIdAndStatusIn(transaction.getId(), OPEN_STATUSES)) {
            throw new BusinessRuleException("This transaction already has an open dispute.");
        }

        // Save the dispute and, in the same transaction, its first history entry (SUBMITTED, by the customer).
        LocalDateTime now = LocalDateTime.now(clock);
        Dispute dispute = disputeRepository.save(new Dispute(transaction, reason, details.trim(), username, now));
        eventRepository.save(new DisputeEvent(dispute, null, DisputeStatus.SUBMITTED, null, username, now));
        log.info("Dispute {} raised for transaction {}", dispute.getId(), transaction.getReference());
        return dispute;
    }

    /**
     * The customer's disputes as summary rows. The caller supplies paging and sorting (newest first on the web page).
     * The full progress timeline of a dispute is loaded separately by {@link #getDetail}.
     */
    @Transactional(readOnly = true)
    public Page<DisputeRow> history(String username, Pageable pageable) {
        return disputeRepository.findByTransactionCustomerUsername(username, pageable).map(this::toRow);
    }

    /** Earlier disputes on one of the customer's transactions, newest first. */
    @Transactional(readOnly = true)
    public List<DisputeRow> forTransaction(Long transactionId, String username) {
        return disputeRepository
                .findByTransactionIdAndTransactionCustomerUsernameOrderByCreatedAtDesc(transactionId, username)
                .stream().map(this::toRow).toList();
    }

    /** One of the customer's own disputes with its full history. Someone else's dispute is "not found". */
    @Transactional(readOnly = true)
    public DisputeDetail getDetail(Long id, String username) {
        Dispute dispute = disputeRepository.findByIdAndTransactionCustomerUsername(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute " + id + " not found"));
        List<DisputeEventRow> events = eventRepository.findByDisputeIdOrderByCreatedAtAscIdAsc(dispute.getId())
                .stream().map(e -> toEventRow(e, username)).toList();
        return new DisputeDetail(toRow(dispute), dispute.getDetails(), events);
    }

    /**
     * Which of the given transactions currently have an open (Submitted or Under review) dispute.
     * One query covers a whole page of transactions, instead of one query per row.
     */
    @Transactional(readOnly = true)
    public Set<Long> findOpenDisputeTransactionIds(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(disputeRepository.findTransactionIdsWithStatusIn(transactionIds, OPEN_STATUSES));
    }

    /** Disputes logged and closed for each of the given transactions (missing ids have none). */
    @Transactional(readOnly = true)
    public Map<Long, DisputeCounts> countByTransaction(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, DisputeCounts> counts = new HashMap<>();
        for (TransactionDisputeCount c : disputeRepository.countByTransactionIds(transactionIds, CLOSED_STATUSES)) {
            counts.put(c.getTransactionId(), new DisputeCounts(c.getLogged().intValue(), c.getClosed().intValue()));
        }
        return counts;
    }

    /**
     * True if the transaction is recent enough to be disputed: newer than now minus the configured window.
     */
    public boolean isWithinWindow(LocalDateTime transactionDate) {
        return transactionDate.isAfter(LocalDateTime.now(clock).minusDays(properties.windowDays()));
    }

    /** Customers see their own actions as "You" and every staff action as "Dispute team". */
    private DisputeEventRow toEventRow(DisputeEvent e, String username) {
        return new DisputeEventRow(e.getFromStatus(), e.getToStatus(), e.getNote(),
                e.getActor().equals(username) ? "You" : "Dispute team", e.getCreatedAt());
    }

    /**
     * Converts a Dispute entity into the flat record the templates use. It reads the lazy "transaction"
     * association, so it must run inside a transaction (all public methods above are transactional).
     */
    private DisputeRow toRow(Dispute d) {
        BankTransaction t = d.getTransaction();
        return new DisputeRow(d.getId(), t.getReference(), t.getMerchant(), t.getAmount(), t.getCurrency(),
                d.getReason().getLabel(), d.getStatus(), d.getCreatedAt(), d.getUpdatedAt(), d.getResolutionNote());
    }
}
