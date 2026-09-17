package com.garmentline.operations.monitoring;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Records status only: no path, query, headers, tokens, request/response body or exception text.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiFailureMonitoringFilter extends OncePerRequestFilter {
  private final MonitoringJournal journal;

  public ApiFailureMonitoringFilter(MonitoringJournal journal) {
    this.journal = journal;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !(request.getRequestURI().startsWith("/api/")
            || request.getRequestURI().startsWith("/iclock/"))
        || request.getRequestURI().startsWith("/api/monitoring/")
        || request.getRequestURI().equals("/api/bridge/telemetry");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Instant start = Instant.now();
    boolean failed = false;
    try {
      chain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException error) {
      failed = true;
      throw error;
    } finally {
      if (failed || response.getStatus() >= 400)
        journal.append(
            journal.event(
                "linematrix-system",
                "api-request",
                "linematrix",
                "linematrix",
                "FAILED",
                start,
                1,
                0,
                failed || response.getStatus() >= 500 ? "INTERNAL_ERROR" : "HTTP_REJECTED",
                Map.of()));
    }
  }
}
