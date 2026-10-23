package com.example.disputeportal.security;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.disputeportal.config.ApiProperties;
import com.example.disputeportal.service.ApiClientService;
import com.example.disputeportal.service.AuthenticatedClient;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates API calls with "Authorization: Bearer &lt;key&gt;". Not a
 * Spring bean: it is added to the API security chain only, so it doesn't also
 * run for the web pages.
 *
 * Order of checks: HTTPS, repeated-failure lockout, key, declared body size.
 * Nothing is learned by an unauthenticated caller other than "authentication
 * required".
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiClientService apiClients;
    private final LoginAttemptService attempts;
    private final ApiProperties properties;
    private final ApiErrorWriter errors;

    public ApiKeyAuthenticationFilter(ApiClientService apiClients, LoginAttemptService attempts,
            ApiProperties properties, ApiErrorWriter errors) {
        this.apiClients = apiClients;
        this.attempts = attempts;
        this.properties = properties;
        this.errors = errors;
    }

    /**
     * Runs for every /api/** request, before authorization. Each step below
     * either rejects the request or lets it continue.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Step 1: refuse plain HTTP when configured. The key is a bearer secret and must not travel unencrypted.
        if (properties.requireHttps() && !request.isSecure()) {
            errors.write(response, HttpServletResponse.SC_FORBIDDEN, "HTTPS is required for this API");
            return;
        }

        // Step 2: if this address has failed authentication too often recently, refuse without even looking at the key
        // (brute-force protection).
        String throttleKey = LoginAttemptService.key(request.getRemoteAddr(), "api-key");
        if (attempts.isBlocked(throttleKey)) {
            log.warn("API call refused for locked-out client {}", throttleKey);
            errors.write(response, 429, "Too many failed authentication attempts. Try again later.");
            return;
        }

        // Step 3: read "Authorization: Bearer <key>" and look the key up (by its hash, see ApiClientService).
        // A missing header isn't counted as a failed guess; a wrong key is.
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            unauthorized(response);
            return;
        }
        Optional<AuthenticatedClient> client = apiClients.authenticate(header.substring(BEARER_PREFIX.length()).trim());
        if (client.isEmpty()) {
            if (attempts.recordFailure(throttleKey)) {
                log.warn("Too many invalid API keys; locking out client {}", throttleKey);
            }
            unauthorized(response);
            return;
        }
        // A valid key wipes earlier failures from this address.
        attempts.recordSuccess(throttleKey);

        // Step 4: reject obviously oversized bodies up front using the declared Content-Length. Bodies with no declared
        // length are caught by LimitedBodyRequest below.
        if (request.getContentLengthLong() > properties.maxBodyBytes()) {
            errors.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body is too large");
            return;
        }

        // Step 5: mark the request as authenticated for the rest of the filter chain. Nothing is kept in a session, so
        // every API call must carry the key.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new ApiKeyAuthentication(client.get().name(), client.get().scopes()));
        SecurityContextHolder.setContext(context);
        try {
            // The wrapper also enforces the size limit on bodies with no declared length.
            chain.doFilter(new LimitedBodyRequest(request, properties.maxBodyBytes()), response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 401 with the standard JSON body and a WWW-Authenticate header telling the
     * client to send a Bearer token.
     */
    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        errors.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
    }
}
