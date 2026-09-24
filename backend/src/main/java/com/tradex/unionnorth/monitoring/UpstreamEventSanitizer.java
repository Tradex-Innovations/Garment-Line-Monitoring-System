package com.tradex.unionnorth.monitoring;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Upstream telemetry is untrusted. Reconstruct the contract; never copy metadata or messages. */
public final class UpstreamEventSanitizer {
    private UpstreamEventSanitizer() {}

    public static PipelineEvent parse(JsonNode node) {
        try {
            String pipeline = node.path("pipelineName").asText(),
                    stage = node.path("stageName").asText();
            if ((!pipeline.endsWith("-attendance") && !pipeline.equals("linematrix-system"))
                    || !PipelineContract.implemented(pipeline, stage)) return null;
            String source = node.path("sourceSystem").asText(),
                    destination = node.path("destinationSystem").asText();
            if (!PipelineContract.SYSTEMS.contains(source)
                    || !PipelineContract.SYSTEMS.contains(destination)) return null;
            var status = PipelineEvent.Status.valueOf(node.path("status").asText());
            if (status == PipelineEvent.Status.NOT_IMPLEMENTED) return null;
            Instant occurred = Instant.parse(node.path("occurredAt").asText());
            if (occurred.isAfter(Instant.now().plusSeconds(60))
                    || occurred.isBefore(Instant.now().minusSeconds(86400 * 30))) return null;
            String correlation = UUID.fromString(node.path("correlationId").asText()).toString();
            String trace = node.path("traceId").asText();
            if (!trace.matches("[0-9a-f]{32}") && PipelineContract.safeId(trace) == null)
                trace = correlation;
            String error =
                    node.path("errorCode").isTextual()
                            ? PipelineContract.errorCode(node.path("errorCode").asText())
                            : null;
            String device = node.path("metadata").path("deviceReference").asText();
            Map<String, Object> metadata =
                    device.matches("device-[0-9a-f]{16}")
                            ? Map.of("deviceReference", device)
                            : Map.of();
            String parent = PipelineContract.safeId(node.path("parentEventId").asText());
            Instant completed =
                    node.path("completedAt").isTextual()
                            ? Instant.parse(node.path("completedAt").asText())
                            : null;
            if (completed != null
                    && (completed.isBefore(occurred)
                            || completed.isAfter(Instant.now().plusSeconds(60)))) return null;
            return new PipelineEvent(
                    UUID.fromString(node.path("eventId").asText()),
                    trace,
                    correlation,
                    UUID.fromString(node.path("runId").asText()),
                    parent == null ? null : UUID.fromString(parent),
                    pipeline,
                    stage,
                    source,
                    destination,
                    status,
                    occurred,
                    completed,
                    Math.max(0, Math.min(node.path("durationMs").asLong(), 86400000)),
                    1,
                    count(node, "recordCount"),
                    count(node, "acceptedCount"),
                    count(node, "rejectedCount"),
                    null,
                    error,
                    error == null ? null : PipelineContract.ERRORS.get(error),
                    node.path("retryable").asBoolean(false),
                    metadata);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int count(JsonNode node, String field) {
        return Math.max(0, Math.min(1000000, node.path(field).asInt()));
    }
}
