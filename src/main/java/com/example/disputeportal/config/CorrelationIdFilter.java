package com.example.disputeportal.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Each request gets a correlation ID for tracing log lines.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /**
     * The correlation ID is assigned for the lifetime of the request.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        // Only trust well-formed IDs, otherwise a caller could inject arbitrary text into our logs.
        String id = (incoming != null && SAFE_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();
        // The MDC makes the id appear in every log line.
        MDC.put(MDC_KEY, id);
        // Return it back to the request header so that when a client is reporting a problem, they can include it.
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
            // Clean up the MDC so that the correlation ID doesn't leak into other requests.
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
