package com.example.disputeportal.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.disputeportal.dto.DisputeForm;
import com.example.disputeportal.dto.TransactionRow;
import com.example.disputeportal.entity.DisputeReason;
import com.example.disputeportal.exception.BusinessRuleException;
import com.example.disputeportal.service.DisputeService;
import com.example.disputeportal.service.TransactionService;

import jakarta.validation.Valid;

/**
 * Here we seek to return dispute-related pages to the customer. The dispute
 * form is shown for one of their transactions, and they can submit it. While
 * also being able to see their dispute history and the details of each dispute.
 */
@Controller
public class DisputeController {

    /**
     * Disputes shown per page in the history list.
     */
    private static final int PAGE_SIZE = 10;

    private final DisputeService disputeService;
    private final TransactionService transactionService;

    public DisputeController(DisputeService disputeService, TransactionService transactionService) {
        this.disputeService = disputeService;
        this.transactionService = transactionService;
    }

    /**
     * Shows the dispute form for one of the customer's transactions. If it
     * can't be disputed right now (an open dispute, or too old) the customer is
     * sent back to the list with a message instead.
     */
    @GetMapping("/transactions/{id}/dispute")
    public String showForm(@PathVariable Long id, Principal principal, Model model, RedirectAttributes redirect) {
        TransactionRow transaction = transactionService.get(id, principal.getName());
        if (!transaction.disputable()) {
            redirect.addFlashAttribute("errorMessage", "This transaction can't be disputed right now.");
            return "redirect:/transactions";
        }
        model.addAttribute("form", new DisputeForm());
        populateFormPage(model, transaction, principal.getName());
        return "disputes/form";
    }

    /**
     * Submits the dispute form. on failure we redirect back to the history
     * page.
     */
    @PostMapping("/transactions/{id}/dispute")
    public String submit(@PathVariable Long id, @Valid @ModelAttribute("form") DisputeForm form,
            BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        String username = principal.getName();
        // Bean Validation has already run (@Valid): a reason must be chosen and the text must be 10-1000 characters.
        if (!result.hasErrors()) {
            try {
                disputeService.raise(id, username, form.getReason(), form.getDetails());
                // Post/Redirect/Get so a refresh doesn't submit the dispute twice.
                redirect.addFlashAttribute("successMessage",
                        "Dispute submitted. You can follow its progress below.");
                return "redirect:/disputes";
            } catch (BusinessRuleException ex) {
                // A rule was broken (e.g. a dispute was opened in the meantime): show it as a form-level error.
                result.reject("dispute.rule", ex.getMessage());
            }
        }
        populateFormPage(model, transactionService.get(id, username), username);
        return "disputes/form";
    }

    /**
     * The customer's dispute history: one summary card per dispute, newest
     * first, with a link to the details page.
     */
    @GetMapping("/disputes")
    public String history(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("disputes", disputeService.history(principal.getName(), pageRequest));
        return "disputes/history";
    }

    /**
     * One dispute with what the customer said and its full progress timeline.
     * Someone else's dispute ID gives a 404 (ResourceNotFoundException, handled
     * by GlobalExceptionHandler).
     */
    @GetMapping("/disputes/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("detail", disputeService.getDetail(id, principal.getName()));
        return "disputes/detail";
    }

    /**
     * Adds what the form page needs: the transaction summary, earlier disputes
     * on it, and the reason options for the dropdown.
     */
    private void populateFormPage(Model model, TransactionRow transaction, String username) {
        model.addAttribute("transaction", transaction);
        model.addAttribute("previousDisputes", disputeService.forTransaction(transaction.id(), username));
        model.addAttribute("reasons", DisputeReason.values());
    }
}
