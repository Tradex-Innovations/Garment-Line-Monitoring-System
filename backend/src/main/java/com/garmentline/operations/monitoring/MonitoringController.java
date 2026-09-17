package com.garmentline.operations.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.garmentline.operations.config.BridgeProperties;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MonitoringController {
  private final MonitoringJournal journal;
  private final SupabaseAdminClient database;
  private final BridgeProperties bridge;
  private final String token;

  public MonitoringController(
      MonitoringJournal journal,
      SupabaseAdminClient database,
      BridgeProperties bridge,
      @Value("${app.monitoring.read-token:}") String token) {
    this.journal = journal;
    this.database = database;
    this.bridge = bridge;
    this.token = token;
  }

  @GetMapping("/api/monitoring/snapshot")
  public Map<String, Object> snapshot(
      @RequestHeader(name = "X-Monitoring-Token", required = false) String supplied,
      jakarta.servlet.http.HttpServletResponse response) {
    authorize(token, supplied);
    response.setHeader("Cache-Control", "no-store");
    String health = "HEALTHY";
    try {
      var query = new LinkedMultiValueMap<String, String>();
      query.add("select", "id");
      query.add("limit", "0");
      database.select("employees", query);
    } catch (RuntimeException ignored) {
      health = "OFFLINE";
    }
    return Map.of(
        "schemaVersion",
        1,
        "events",
        journal.events(),
        "devices",
        journal.devices(),
        "databaseStatus",
        health,
        "evictedEvents",
        journal.evicted(),
        "bufferCapacity",
        1000);
  }

  @PostMapping("/api/bridge/telemetry")
  public void worker(
      @RequestHeader(name = "X-Bridge-Token", required = false) String supplied,
      @RequestBody JsonNode event) {
    authorize(bridge.sharedToken(), supplied);
    journal.worker(event);
  }

  static void authorize(String expected, String actual) {
    if (expected == null
        || expected.isBlank()
        || actual == null
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Monitoring authentication required");
  }
}
