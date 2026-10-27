package com.example.disputeportal.service;

import com.example.disputeportal.dto.NotificationRow;
import com.example.disputeportal.entity.BankTransaction;
import com.example.disputeportal.entity.Dispute;
import com.example.disputeportal.entity.Notification;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.exception.ResourceNotFoundException;
import com.example.disputeportal.repository.CustomerRepository;
import com.example.disputeportal.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications.
 *
 * The three event methods ({@link #disputeRaised}, {@link #statusChanged}, {@link #evidenceAdded}) are called by the
 * dispute services inside their own database transaction, so a notification exists if and only if the change it
 * describes was saved. All the wording lives here, so adding another channel later (an email or SMS sender) means
 * calling it from these same three methods.
 *
 * Reads and "mark as read" are scoped to the signed-in user's username in the query itself, like the rest of the app.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final CustomerRepository customers;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, CustomerRepository customers, Clock clock) {
        this.notifications = notifications;
        this.customers = customers;
        this.clock = clock;
    }

    /** A customer raised a dispute: every admin is told there is something to review. */
    @Transactional
    public void disputeRaised(Dispute dispute) {
        notifyAdmins(dispute, "New dispute #" + dispute.getId() + " on " + describe(dispute)
                + " is waiting for review.");
    }

    /** An admin moved a dispute to a new status: the customer who raised it is told. */
    @Transactional
    public void statusChanged(Dispute dispute) {
        send(List.of(dispute.getCreatedBy()), dispute, "Your dispute on " + describe(dispute) + " is now: "
                + dispute.getStatus().getLabel() + ".");
    }

    /** A customer added evidence: every admin is told. */
    @Transactional
    public void evidenceAdded(Dispute dispute) {
        notifyAdmins(dispute, "New evidence was added to dispute #" + dispute.getId() + " on "
                + describe(dispute) + ".");
    }

    /** How many notifications the user has not read yet. */
    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return notifications.countByRecipientAndReadAtIsNull(username);
    }

    /** The user's notifications as rows for the page. The caller supplies paging and sorting. */
    @Transactional(readOnly = true)
    public Page<NotificationRow> list(String username, Pageable pageable) {
        return notifications.findByRecipient(username, pageable).map(n ->
                new NotificationRow(n.getId(), n.getDisputeId(), n.getMessage(), n.isRead(), n.getCreatedAt()));
    }

    /**
     * Marks one of the user's notifications as read. Someone else's id is "not found".
     *
     * @throws ResourceNotFoundException if it doesn't exist or belongs to another user
     */
    @Transactional
    public void markRead(Long id, String username) {
        Notification notification = notifications.findByIdAndRecipient(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + id + " not found"));
        // A managed entity: JPA writes the change when the transaction commits.
        notification.markRead(LocalDateTime.now(clock));
    }

    /** Marks everything of the user's as read. Returns how many were unread. */
    @Transactional
    public int markAllRead(String username) {
        return notifications.markAllRead(username, LocalDateTime.now(clock));
    }

    private void notifyAdmins(Dispute dispute, String message) {
        send(customers.findUsernamesByRole(Role.ADMIN), dispute, message);
    }

    private void send(List<String> recipients, Dispute dispute, String message) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Notification> batch = recipients.stream()
                .map(recipient -> new Notification(recipient, dispute.getId(), message, now))
                .toList();
        notifications.saveAll(batch);
        log.debug("{} notification(s) created for dispute {}", batch.size(), dispute.getId());
    }

    /** "Merchant (TXN-000000)": enough for the reader to recognise the transaction. */
    private String describe(Dispute dispute) {
        BankTransaction t = dispute.getTransaction();
        return t.getMerchant() + " (" + t.getReference() + ")";
    }
}
