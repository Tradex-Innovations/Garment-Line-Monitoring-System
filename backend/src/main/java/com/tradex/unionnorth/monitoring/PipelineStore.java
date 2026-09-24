package com.tradex.unionnorth.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PipelineStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    private final int retentionDays;
    private final ArrayBlockingQueue<PipelineEvent> pending = new ArrayBlockingQueue<>(5000);
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean storageAvailable = true;

    public PipelineStore(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            MeterRegistry metrics,
            @Value("${app.monitoring.retention-days:30}") int retentionDays) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.metrics = metrics;
        this.retentionDays = Math.max(1, Math.min(retentionDays, 365));
        metrics.gauge("pipeline.pending", pending, Queue::size);
        metrics.gauge("pipeline.dropped", dropped);
    }

    public void record(PipelineEvent event) {
        if (!PipelineContract.implemented(event.pipelineName(), event.stageName())) return;
        if (!pending.offer(event)) dropped.incrementAndGet();
        metrics.counter(
                        "pipeline.events",
                        "pipeline",
                        event.pipelineName(),
                        "stage",
                        event.stageName(),
                        "status",
                        event.status().name())
                .increment();
        if (event.status() != PipelineEvent.Status.RUNNING) {
            metrics.counter("pipeline.records.accepted", "pipeline", event.pipelineName())
                    .increment(event.acceptedCount());
            metrics.counter("pipeline.records.rejected", "pipeline", event.pipelineName())
                    .increment(event.rejectedCount());
            metrics.timer("pipeline.stage.duration", "stage", event.stageName())
                    .record(java.time.Duration.ofMillis(event.durationMs()));
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void flush() {
        // peek/remove preserves queued evidence through a temporary database outage.
        for (int i = 0; i < 250; i++) {
            PipelineEvent event = pending.peek();
            if (event == null) return;
            try {
                jdbc.update(
                        """
INSERT INTO pipeline_stage_events(event_id,run_id,pipeline_name,stage_name,status,occurred_at,
correlation_id,trace_id,safe_entity_reference,payload) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)
ON CONFLICT(event_id) DO NOTHING
""",
                        event.eventId(),
                        event.runId(),
                        event.pipelineName(),
                        event.stageName(),
                        event.status().name(),
                        java.sql.Timestamp.from(event.occurredAt()),
                        event.correlationId(),
                        event.traceId(),
                        event.safeEntityReference(),
                        mapper.writeValueAsString(event));
                pending.remove(event);
                storageAvailable = true;
            } catch (Exception ignored) {
                storageAvailable = false;
                return;
            }
        }
    }

    public List<PipelineEvent> events(String pipeline, String search, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder("SELECT payload::text FROM pipeline_stage_events WHERE true");
        if (pipeline != null && !pipeline.isBlank()) {
            sql.append(" AND pipeline_name=?");
            args.add(pipeline);
        }
        if (search != null && !search.isBlank()) {
            sql.append(
                    " AND (correlation_id=? OR trace_id=? OR run_id::text=? OR"
                            + " safe_entity_reference=?)");
            for (int i = 0; i < 4; i++) args.add(search);
        }
        sql.append(" ORDER BY occurred_at DESC, sequence_id DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbc.query(
                sql.toString(),
                (rs, row) -> {
                    try {
                        return mapper.readValue(rs.getString(1), PipelineEvent.class);
                    } catch (Exception exception) {
                        throw new java.sql.SQLException("Invalid telemetry record");
                    }
                },
                args.toArray());
    }

    public Map<String, Object> metrics() {
        return Map.of(
                "storageAvailable",
                storageAvailable,
                "pendingEvents",
                pending.size(),
                "droppedEvents",
                dropped.get(),
                "retentionDays",
                retentionDays);
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void retention() {
        try {
            var cutoff =
                    java.sql.Timestamp.from(
                            Instant.now().minus(java.time.Duration.ofDays(retentionDays)));
            jdbc.update("DELETE FROM pipeline_stage_events WHERE occurred_at < ?", cutoff);
            jdbc.update("DELETE FROM service_health_snapshots WHERE occurred_at < ?", cutoff);
        } catch (RuntimeException ignored) {
            storageAvailable = false;
        }
    }

    public void saveHealth(Object snapshot) {
        try {
            jdbc.update(
                    "INSERT INTO service_health_snapshots(payload) VALUES (?::jsonb)",
                    mapper.writeValueAsString(snapshot));
        } catch (Exception ignored) {
            storageAvailable = false;
        }
    }
}
