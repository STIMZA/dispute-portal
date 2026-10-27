package com.example.disputeportal.controller;

import com.example.disputeportal.service.NotificationService;
import java.security.Principal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The notification inbox, for customers and admins alike: each person only ever sees and changes their own, because
 * the service scopes everything to the signed-in username.
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    /**
     * Notifications shown per page.
     */
    private static final int PAGE_SIZE = 20;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** The user's notifications, newest first. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        model.addAttribute("notifications", notificationService.list(principal.getName(), pageRequest));
        return "notifications/list";
    }

    /** Marks one notification as read and returns to the same page of the list. */
    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, Principal principal) {
        notificationService.markRead(id, principal.getName());
        return "redirect:/notifications?page=" + Math.max(page, 0);
    }

    /** Marks everything as read. */
    @PostMapping("/read-all")
    public String markAllRead(Principal principal, RedirectAttributes redirect) {
        int changed = notificationService.markAllRead(principal.getName());
        redirect.addFlashAttribute("successMessage", changed == 0
                ? "You have no unread notifications."
                : "Marked " + changed + (changed == 1 ? " notification" : " notifications") + " as read.");
        return "redirect:/notifications";
    }
}
