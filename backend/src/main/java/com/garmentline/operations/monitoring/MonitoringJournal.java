package com.garmentline.operations.monitoring;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MonitoringJournal {
  private final Deque<Map<String, Object>> events = new ArrayDeque<>();
  private final Map<String, Map<String, Object>> devices = new LinkedHashMap<>();
  private final AtomicLong evicted = new AtomicLong();

  public synchronized void append(Map<String, Object> event) {
    if (events.size() >= 1000) {
      events.removeFirst();
      evicted.incrementAndGet();
    }
    events.addLast(Collections.unmodifiableMap(new LinkedHashMap<>(event)));
  }

  public synchronized List<Map<String, Object>> events() {
    return List.copyOf(events);
  }

  public synchronized List<Map<String, Object>> devices() {
    return List.copyOf(devices.values());
  }

  public long evicted() {
    return evicted.get();
  }

  public Map<String, Object> event(
      String pipeline,
      String stage,
      String source,
      String destination,
      String status,
      Instant start,
      int records,
      int accepted,
      String error,
      Map<String, Object> metadata) {
    String correlation = safeUuid(MDC.get("correlationId"));
    if (correlation == null) correlation = UUID.randomUUID().toString();
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventId", UUID.randomUUID().toString());
    event.put("traceId", correlation);
    event.put("correlationId", correlation);
    event.put("runId", correlation);
    event.put("parentEventId", null);
    event.put("pipelineName", pipeline);
    event.put("stageName", stage);
    event.put("sourceSystem", source);
    event.put("destinationSystem", destination);
    event.put("status", status);
    event.put("occurredAt", start.toString());
    event.put("completedAt", status.equals("RUNNING") ? null : Instant.now().toString());
    event.put(
        "durationMs", Math.max(0, java.time.Duration.between(start, Instant.now()).toMillis()));
    event.put("attempt", 1);
    event.put("recordCount", Math.max(0, records));
    event.put("acceptedCount", Math.max(0, accepted));
    event.put("rejectedCount", status.equals("RUNNING") ? 0 : Math.max(0, records - accepted));
    event.put("safeEntityReference", null);
    event.put("errorCode", error);
    event.put("safeErrorMessage", null);
    event.put("retryable", error != null);
    event.put("metadata", metadata);
    return event;
  }

  public synchronized void worker(com.fasterxml.jackson.databind.JsonNode input) {
    String family = input.path("sourceSystem").asText();
    String stage = input.path("stageName").asText(), status = input.path("status").asText();
    String reference = input.path("deviceReference").asText();
    String correlation = safeUuid(input.path("correlationId").asText());
    if (input.path("schemaVersion").asInt() != 1
        || !Set.of("zkteco", "hikvision").contains(family)
        || !Set.of("device-read", "bridge-upload").contains(stage)
        || !Set.of("RUNNING", "SUCCEEDED", "WARNING", "FAILED").contains(status)
        || !reference.matches("device-[0-9a-f]{16}")
        || correlation == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid telemetry contract");
    }
    String previous = MDC.get("correlationId");
    MDC.put("correlationId", correlation);
    try {
      int records = Math.max(0, Math.min(1000000, input.path("recordCount").asInt()));
      int accepted = Math.min(records, Math.max(0, input.path("acceptedCount").asInt()));
      String error =
          status.equals("WARNING")
              ? "RECORDS_REJECTED"
              : status.equals("FAILED")
                  ? stage.equals("device-read") ? "DEVICE_READ_FAILED" : "UPLOAD_FAILED"
                  : null;
      Instant start = Instant.now();
      try {
        Instant supplied = Instant.parse(input.path("occurredAt").asText());
        if (supplied.isAfter(start.minusSeconds(300)) && !supplied.isAfter(start)) start = supplied;
      } catch (RuntimeException ignored) {
        /* Server receipt time is the safe fallback. */
      }
      var event =
          event(
              family + "-attendance",
              stage,
              stage.equals("device-read") ? family : "lan-bridge",
              stage.equals("device-read") ? "lan-bridge" : "linematrix",
              status,
              start,
              records,
              accepted,
              error,
              Map.of("deviceReference", reference));
      String eventId = safeUuid(input.path("eventId").asText());
      if (eventId != null) event.put("eventId", eventId);
      event.put("parentEventId", safeUuid(input.path("parentEventId").asText()));
      event.put("durationMs", Math.max(0, Math.min(86400000, input.path("durationMs").asLong())));
      append(event);
      if (!status.equals("RUNNING") && (devices.size() < 200 || devices.containsKey(reference)))
        devices.put(
            reference,
            Map.of(
                "reference",
                reference,
                "family",
                family,
                "status",
                status,
                "lastSeen",
                Instant.now().toString()));
    } finally {
      if (previous == null) MDC.remove("correlationId");
      else MDC.put("correlationId", previous);
    }
  }

  public static String safeUuid(String value) {
    try {
      return UUID.fromString(value).toString();
    } catch (Exception ignored) {
      return null;
    }
  }
}
