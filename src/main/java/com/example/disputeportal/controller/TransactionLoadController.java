package com.example.disputeportal.controller;

import com.example.disputeportal.dto.TransactionLoadRequest;
import com.example.disputeportal.dto.TransactionLoadResult;
import com.example.disputeportal.service.TransactionLoadService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Machine-to-machine endpoint for loading transactions. See docs/api/openapi.yaml. */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionLoadController {

    private final TransactionLoadService loadService;

    public TransactionLoadController(TransactionLoadService loadService) {
        this.loadService = loadService;
    }

    /**
     * Loads a batch of transactions (see docs/api/openapi.yaml).
     *
     * The caller has already been authenticated by ApiKeyAuthenticationFilter, and its name is available as the
     * Authentication. @Valid runs Bean Validation on the body; failures become a 422 response in ApiExceptionHandler.
     */
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionLoadResult load(@Valid @RequestBody TransactionLoadRequest request,
                                      Authentication authentication) {
        return loadService.load(request.transactions(), authentication.getName());
    }
}
