package com.tradex.unionnorth.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garmentline.operations.monitoring.MonitoringJournal;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

/** Developer view reads LineMatrix monitoring in-process in the shared backend. */
@Service
public class ControlCenterService {
    public record Health(String name, String status, Instant checkedAt, String detail) {}

    private final JdbcTemplate jdbc;
    private final PipelineStore store;
    private final MonitoringJournal lineMatrixJournal;
    private final SupabaseAdminClient lineMatrixDatabase;
    private final ObjectMapper mapper;
    private final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
    private volatile List<Health> health =
            List.of(new Health("Monitoring", "NO_DATA", null, "Waiting for first health probe."));
    private volatile List<Map<String, Object>> devices = List.of();
    private volatile boolean upstreamOnline;
    private volatile long upstreamEvictedEvents;

    public ControlCenterService(JdbcTemplate jdbc, PipelineStore store,
            MonitoringJournal lineMatrixJournal, SupabaseAdminClient lineMatrixDatabase,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.store = store;
        this.lineMatrixJournal = lineMatrixJournal;
        this.lineMatrixDatabase = lineMatrixDatabase;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.monitoring.poll-ms:10000}", initialDelay = 3000)
    public void poll() {
        Instant now = Instant.now();
        List<Health> result = new ArrayList<>();
        result.add(new Health("Shared backend", "HEALTHY", now, "Monitoring scheduler is running."));
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            result.add(new Health("Payroll database", "HEALTHY", now, "Read-only probe succeeded."));
        } catch (RuntimeException ignored) {
            result.add(new Health("Payroll database", "OFFLINE", now, "Read-only database probe failed."));
        }
        try {
            int count = 0;
            for (var raw : lineMatrixJournal.events()) {
                if (++count > 1000) break;
                var event = UpstreamEventSanitizer.parse(mapper.valueToTree(raw));
                if (event != null && seen.add(event.eventId())) store.record(event);
            }
            while (seen.size() > 10000) seen.remove(seen.iterator().next());
            List<Map<String, Object>> observed = new ArrayList<>();
            for (var raw : lineMatrixJournal.devices()) {
                if (observed.size() >= 200) break;
                String reference = String.valueOf(raw.getOrDefault("reference", ""));
                String family = String.valueOf(raw.getOrDefault("family", ""));
                if (!reference.matches("device-[0-9a-f]{16}")
                        || !Set.of("zkteco", "hikvision").contains(family)) continue;
                try {
                    Instant last = Instant.parse(String.valueOf(raw.get("lastSeen")));
                    String state = now.minusSeconds(120).isAfter(last) ? "OFFLINE"
                            : Set.of("FAILED", "WARNING").contains(raw.get("status"))
                                    ? String.valueOf(raw.get("status")) : "HEALTHY";
                    observed.add(Map.of("reference", reference, "family", family,
                            "lastSeen", last, "status", state));
                } catch (RuntimeException ignored) {
                    // An invalid device heartbeat does not prevent the next probe.
                }
            }
            devices = List.copyOf(observed);
            upstreamOnline = true;
            upstreamEvictedEvents = Math.max(0, lineMatrixJournal.evicted());
            result.add(new Health("LineMatrix module", "HEALTHY", now, "In-process monitoring is available."));
        } catch (RuntimeException ignored) {
            upstreamOnline = false;
            result.add(new Health("LineMatrix module", "FAILED", now, "Monitoring snapshot failed."));
        }
        try {
            var query = new LinkedMultiValueMap<String, String>();
            query.add("select", "id");
            query.add("limit", "0");
            lineMatrixDatabase.select("employees", query);
            result.add(new Health("LineMatrix Supabase", "HEALTHY", now, "Read-only probe succeeded."));
        } catch (RuntimeException ignored) {
            result.add(new Health("LineMatrix Supabase", "OFFLINE", now, "Read-only probe failed."));
        }
        result.add(new Health("LAN bridge / devices", deviceHealth(), now,
                "Worker heartbeats expire after 120 seconds. Only observed devices are listed."));
        result.add(new Health("Payroll frontend", "NO_DATA", null,
                "Viewed in this browser; no independent synthetic probe configured."));
        result.add(new Health("LineMatrix frontend", "NO_DATA", null,
                "No independent frontend probe configured."));
        health = List.copyOf(result);
        store.saveHealth(health);
    }

    private String deviceHealth() {
        if (!upstreamOnline || devices.isEmpty()) return "NO_DATA";
        if (devices.stream().allMatch(device -> "HEALTHY".equals(device.get("status"))))
            return "HEALTHY";
        if (devices.stream().allMatch(device -> "OFFLINE".equals(device.get("status"))))
            return "OFFLINE";
        return "WARNING";
    }

    public Map<String, Object> snapshot() {
        return Map.of(
                "serverTime", Instant.now(),
                "pipelines", PipelineContract.PIPELINES,
                "health", health,
                "devices", devices,
                "upstreamOnline", upstreamOnline,
                "upstreamEvictedEvents", upstreamEvictedEvents,
                "telemetry", store.metrics(),
                "unsupportedPipelines", List.of("Leave sync", "Shift sync", "Department sync"));
    }
}
