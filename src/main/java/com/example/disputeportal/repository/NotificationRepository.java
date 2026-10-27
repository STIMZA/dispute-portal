package com.example.disputeportal.repository;

import com.example.disputeportal.entity.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for notifications. Every method is scoped to the recipient's username in the query itself.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** One user's notifications; the caller supplies paging and sorting (newest first on the page). */
    Page<Notification> findByRecipient(String recipient, Pageable pageable);

    /** How many the user has not read yet. Shown as the badge in the page header. */
    long countByRecipientAndReadAtIsNull(String recipient);

    /** One notification, but only if it belongs to the user. */
    Optional<Notification> findByIdAndRecipient(Long id, String recipient);

    /** Marks all of the user's unread notifications as read in one statement. Returns how many changed. */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipient = :recipient and n.readAt is null")
    int markAllRead(@Param("recipient") String recipient, @Param("now") LocalDateTime now);
}
