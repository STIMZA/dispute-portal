package com.example.disputeportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Puts a correlation ID on every request so log lines for one request can be traced together. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /**
     * Gives the request a correlation id for its whole lifetime.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        // Only trust well-formed IDs, otherwise a caller could inject arbitrary text into our logs.
        String id = (incoming != null && SAFE_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();
        // The MDC makes the id appear in every log line written while this request is handled (see logging.pattern.level).
        MDC.put(MDC_KEY, id);
        // Echo it back so a client can quote it when reporting a problem.
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        // (the finally block below always removes the id again: servlet threads are reused for other requests)
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
