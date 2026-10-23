package com.example.disputeportal.controller;

import java.security.Principal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.disputeportal.dto.AdminDecisionForm;
import com.example.disputeportal.dto.AdminDisputeRow;
import com.example.disputeportal.entity.DisputeStatus;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.service.AdminDisputeService;

import jakarta.validation.Valid;

/**
 * This controller will allow the administrator to manage disputes, including
 * viewing the queue, reviewing a dispute, and making a decision. This
 * functionality is restricted to users with the "ADMIN" role, enforced by
 * Spring Security configuration.
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
     * Here we show the queue of disputes, optionally filtered by status and by the date the dispute was raised
     * (from and to are both inclusive and both optional). The queue is paginated, and the page number is passed as
     * a query parameter. An unknown status value or a malformed date is rejected with a 400 by Spring's type
     * conversion. If the start date is after the end date, the queue is shown empty with an error message.
     */
    @GetMapping
    public String queue(@RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, Model model) {
        // Math.max guards against a negative page number typed into the URL.
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminDisputeRow> disputes;
        try {
            disputes = adminDisputeService.list(status, from, to, pageRequest);
        } catch (BusinessRuleException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            disputes = new PageImpl<>(java.util.List.of(), pageRequest, 0);
        }
        model.addAttribute("disputes", disputes);
        model.addAttribute("statusFilter", status);
        model.addAttribute("fromFilter", from);
        model.addAttribute("toFilter", to);
        model.addAttribute("statuses", DisputeStatus.values());
        // Keeps every filter when paging.
        UriComponentsBuilder path = UriComponentsBuilder.fromPath("/admin/disputes");
        if (status != null) {
            path.queryParam("status", status.name());
        }
        if (from != null) {
            path.queryParam("from", from);
        }
        if (to != null) {
            path.queryParam("to", to);
        }
        model.addAttribute("pagerPath", path.build().toUriString());
        return "admin/disputes";
    }

    /**
     * Rather than a queue, this shows the details of a single dispute,
     * including its history of events and a form for the admin to make a
     * decision.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("dispute", adminDisputeService.get(id));
        model.addAttribute("events", adminDisputeService.events(id));
        model.addAttribute("form", new AdminDecisionForm());
        return "admin/dispute";
    }

    /**
     * This will manage the descision taken by the administrator, including
     * validation and error handling.
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
