package com.example.disputeportal.controller;

import com.example.disputeportal.service.NotificationService;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the unread-notification count in the model of every signed-in page, so the header can show the badge without
 * each controller having to remember to add it.
 *
 * Only the page controllers listed here are covered (not the JSON APIs, whose "user" is an API client). A new page
 * controller needs adding to the list to get the badge. Spring Boot ignores the default model when redirecting
 * (spring.mvc.ignore-default-model-on-redirect), so the count never ends up in a redirect URL.
 */
@ControllerAdvice(assignableTypes = {
    TransactionController.class, DisputeController.class, AdminDisputeController.class,
    NotificationController.class})
public class NotificationModelAdvice {

    private static final Logger log = LoggerFactory.getLogger(NotificationModelAdvice.class);

    private final NotificationService notificationService;

    public NotificationModelAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** The count, or null if nobody is signed in or it can't be read: a badge problem must never break a page. */
    @ModelAttribute("unreadNotifications")
    public Long unreadNotifications(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return notificationService.unreadCount(principal.getName());
        } catch (RuntimeException ex) {
            log.warn("Could not count unread notifications", ex);
            return null;
        }
    }
}
