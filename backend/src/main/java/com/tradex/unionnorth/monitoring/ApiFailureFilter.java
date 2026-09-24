package com.tradex.unionnorth.monitoring;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiFailureFilter extends OncePerRequestFilter {
    private final PipelineStore store;

    public ApiFailureFilter(PipelineStore store) {
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/")
                || request.getRequestURI().startsWith("/api/v1/developer");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant start = Instant.now();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException error) {
            failed = true;
            throw error;
        } finally {
            if (failed || response.getStatus() >= 400) {
                String correlation = PipelineContract.safeId(MDC.get("correlationId"));
                if (correlation == null) correlation = UUID.randomUUID().toString();
                String code =
                        PipelineContract.errorCode(
                                java.util.Objects.toString(
                                        request.getAttribute("safeErrorCode"),
                                        response.getStatus() >= 500 || failed
                                                ? "INTERNAL_ERROR"
                                                : "HTTP_REJECTED"));
                store.record(
                        new PipelineEvent(
                                UUID.randomUUID(),
                                correlation,
                                correlation,
                                UUID.fromString(correlation),
                                null,
                                "system",
                                "api-request",
                                "payroll",
                                "payroll",
                                PipelineEvent.Status.FAILED,
                                start,
                                Instant.now(),
                                Math.max(
                                        0,
                                        java.time.Duration.between(start, Instant.now())
                                                .toMillis()),
                                1,
                                1,
                                0,
                                1,
                                null,
                                code,
                                PipelineContract.ERRORS.get(code),
                                false,
                                Map.of("httpStatus", failed ? 500 : response.getStatus())));
            }
        }
    }
}
