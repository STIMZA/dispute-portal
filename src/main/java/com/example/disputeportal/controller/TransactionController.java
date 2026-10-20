package com.example.disputeportal.controller;

import com.example.disputeportal.service.TransactionService;
import java.security.Principal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The customer's transactions page.
 */
@Controller
public class TransactionController {

    /**
     * Transactions shown per page.
     */
    private static final int PAGE_SIZE = 10;

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Lists the signed-in customer's transactions, newest first, with their dispute status and counts.
     */
    @GetMapping("/transactions")
    public String list(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        // Math.max guards against a negative page number typed into the URL.
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "transactionDate"));
        model.addAttribute("transactions", transactionService.list(principal.getName(), pageRequest));
        return "transactions/list";
    }
}
