package com.example.disputeportal.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.disputeportal.dto.AdminDisputeRow;
import com.example.disputeportal.dto.DisputeEventRow;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.DisputeEvent;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.DisputeEventRepository;
import com.example.disputeportal.repository.DisputeRepository;

/**
 * Back-office operations on disputes. Admin-only, checked here as well as in
 * the URL rules.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeService {

    private static final Logger log = LoggerFactory.getLogger(AdminDisputeService.class);

    private final DisputeRepository disputeRepository;
    private final DisputeEventRepository eventRepository;
    private final Clock clock;

    /**
     * Dependencies are injected through the constructor, as in the other
     * services.
     */
    public AdminDisputeService(DisputeRepository disputeRepository, DisputeEventRepository eventRepository,
            Clock clock) {
        this.disputeRepository = disputeRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /**
     * The admin queue. Every filter is optional and they combine (AND).
     *
     * @param status optional status filter; null means any status
     * @param from optional first day to include, by the day the dispute was
     * raised; null means no lower bound
     * @param to optional last day to include (the whole day counts); null means
     * no upper bound
     * @throws BusinessRuleException if {@code from} is after {@code to}
     */
    @Transactional(readOnly = true)
    public Page<AdminDisputeRow> list(DisputeStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The start date must not be after the end date.");
        }
        Specification<Dispute> filter = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            // createdAt is stored in UTC; the range starts at midnight of the first day...
            LocalDateTime start = from.atStartOfDay();
            filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), start));
        }
        if (to != null) {
            // ...and ends before midnight of the day after the last day, so the whole last day is included.
            LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
            filter = filter.and((root, query, cb) -> cb.lessThan(root.<LocalDateTime>get("createdAt"), endExclusive));
        }
        return disputeRepository.findAll(filter, pageable).map(this::toRow);
    }

    /**
     * One dispute with the customer and transaction details, for the review
     * page.
     *
     * @throws ResourceNotFoundException if there is no dispute with that id
     */
    @Transactional(readOnly = true)
    public AdminDisputeRow get(Long id) {
        return disputeRepository.findById(id).map(this::toRow)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute " + id + " not found"));
    }

    /**
     * Full history of a dispute, oldest first, with the real actor names.
     */
    @Transactional(readOnly = true)
    public List<DisputeEventRow> events(Long disputeId) {
        return eventRepository.findByDisputeIdOrderByCreatedAtAscIdAsc(disputeId).stream()
                .map(e -> new DisputeEventRow(e.getFromStatus(), e.getToStatus(), e.getNote(),
                        e.getActor(), e.getCreatedAt()))
                .toList();
    }

    /**
     * Records an admin's decision on a dispute by moving it to a new status,
     * stores the note for the customer and appends an entry to the dispute's
     * history.
     *
     * The dispute row is locked while this runs, so two admins can't decide the
     * same dispute at once. The second waits, then sees the new status and is
     * refused by the workflow check.
     *
     * @param id dispute to update
     * @param next the status to move to
     * @param note message for the customer; required when the new status is a
     * final decision
     * @param adminUsername who is making the change (stored for the audit
     * trail)
     * @throws BusinessRuleException if the move isn't allowed by the workflow
     * or a final decision has no note
     */
    @Transactional
    public void updateStatus(Long id, DisputeStatus next, String note, String adminUsername) {
        Dispute dispute = disputeRepository.findWithLockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute " + id + " not found"));

        DisputeStatus current = dispute.getStatus();
        // Workflow check: only forward moves are allowed (see DisputeStatus.canTransitionTo).
        if (!current.canTransitionTo(next)) {
            throw new BusinessRuleException("A dispute that is \"" + current.getLabel()
                    + "\" can't be moved to \"" + next.getLabel() + "\".");
        }
        // Trim the note (blank means none). A final decision must always explain itself, because the customer
        // reads this note in their dispute history.
        String cleanNote = StringUtils.hasText(note) ? note.trim() : null;
        if (next.isFinal() && cleanNote == null) {
            throw new BusinessRuleException(
                    "Add a note explaining the decision so the customer understands the outcome.");
        }

        // Update the dispute and append the history entry. No save() is needed for the dispute: it is a managed
        // entity, so JPA writes the change when the transaction commits.
        LocalDateTime now = LocalDateTime.now(clock);
        dispute.changeStatus(next, cleanNote, now, adminUsername);
        eventRepository.save(new DisputeEvent(dispute, current, next, cleanNote, adminUsername, now));
        log.info("Dispute {} moved from {} to {} by {}", id, current, next, adminUsername);
    }

    /**
     * Flattens a dispute plus its transaction and customer for the admin
     * templates. Runs inside the service's transaction because the transaction
     * and customer are lazily loaded.
     */
    private AdminDisputeRow toRow(Dispute d) {
        BankTransaction t = d.getTransaction();
        Customer c = t.getCustomer();
        return new AdminDisputeRow(d.getId(), c.getFullName(), c.getUsername(), t.getReference(), t.getMerchant(),
                t.getAmount(), t.getCurrency(), d.getReason().getLabel(), d.getDetails(), d.getStatus(),
                d.getCreatedAt(), d.getUpdatedAt(), d.getUpdatedBy(), d.getResolutionNote(),
                d.getStatus().nextStatuses());
    }
}
