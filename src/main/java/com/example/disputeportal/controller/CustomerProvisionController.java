package com.example.disputeportal.controller;

import com.example.disputeportal.dto.CustomerLoadRequest;
import com.example.disputeportal.dto.CustomerLoadResult;
import com.example.disputeportal.service.CustomerProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-to-machine endpoint for creating customer accounts. See docs/api/openapi.yaml.
 *
 * <p>Needs an API key with the customers:create scope; a key that may only load transactions gets a 403.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerProvisionController {

    private final CustomerProvisioningService provisioningService;

    public CustomerProvisionController(CustomerProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    /**
     * Creates a batch of customer accounts. @Valid runs Bean Validation on the body; failures become a 422 response
     * in ApiExceptionHandler. The caller's identity (the API client's name) comes from the authentication that
     * ApiKeyAuthenticationFilter set up.
     */
    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CustomerLoadResult create(@Valid @RequestBody CustomerLoadRequest request,
                                     Authentication authentication) {
        return provisioningService.create(request.customers(), authentication.getName());
    }
}
