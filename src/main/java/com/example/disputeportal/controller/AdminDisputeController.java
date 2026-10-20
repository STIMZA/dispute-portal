package com.example.disputeportal.controller;

import com.example.disputeportal.dto.AdminDecisionForm;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.service.AdminDisputeService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Back-office pages for admins: the dispute queue, a review page for one dispute, and the action that records a
 * decision. Access is limited to ROLE_ADMIN by the URL rules in SecurityConfig, and AdminDisputeService checks
 * the role again.
 */
@Controller
@RequestMapping("/admin/disputes")
public class AdminDisputeController {

    /**
     * Disputes shown per page in the queue.
     */
    private static final int PAGE_SIZE = 15;

    private final AdminDisputeService adminDisputeService;

    public AdminDisputeController(AdminDisputeService adminDisputeService) {
        this.adminDisputeService = adminDisputeService;
    }

    /**
     * The queue: every customer's disputes, newest first, optionally filtered by status (?status=SUBMITTED).
     * An unknown status value is rejected with a 400 by Spring's type conversion.
     */
    @GetMapping
    public String queue(@RequestParam(required = false) DisputeStatus status,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        // Math.max guards against a negative page number typed into the URL.
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("disputes", adminDisputeService.list(status, pageRequest));
        model.addAttribute("statusFilter", status);
        model.addAttribute("statuses", DisputeStatus.values());
        // Keeps the filter when paging.
        model.addAttribute("pagerPath", "/admin/disputes" + (status == null ? "" : "?status=" + status.name()));
        return "admin/disputes";
    }

    /**
     * Review page for one dispute: its details, full history and the decision form.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("dispute", adminDisputeService.get(id));
        model.addAttribute("events", adminDisputeService.events(id));
        model.addAttribute("form", new AdminDecisionForm());
        return "admin/dispute";
    }

    /**
     * Records the admin's decision. Form errors and business-rule errors (a closed dispute, a missing note) re-show
     * the page with a message. Success redirects (Post/Redirect/Get) so a browser refresh can't resubmit it.
     */
    @PostMapping("/{id}/status")
    public String decide(@PathVariable Long id, @Valid @ModelAttribute("form") AdminDecisionForm form,
                         BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        // Bean Validation has already run (@Valid); only call the service if the form itself is valid.
        if (!result.hasErrors()) {
            try {
                adminDisputeService.updateStatus(id, form.getStatus(), form.getNote(), principal.getName());
                redirect.addFlashAttribute("successMessage",
                        "Dispute updated. The customer will see the new status in their history.");
                return "redirect:/admin/disputes/" + id;
            } catch (BusinessRuleException ex) {
                // A business rule was broken: show its message as a form-level error.
                result.reject("dispute.decision", ex.getMessage());
            }
        }
        model.addAttribute("dispute", adminDisputeService.get(id));
        model.addAttribute("events", adminDisputeService.events(id));
        return "admin/dispute";
    }
}
