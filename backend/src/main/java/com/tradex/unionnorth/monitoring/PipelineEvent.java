package com.tradex.unionnorth.monitoring;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Deliberately contains no free-form log, request or response payload. */
public record PipelineEvent(
        UUID eventId,
        String traceId,
        String correlationId,
        UUID runId,
        UUID parentEventId,
        String pipelineName,
        String stageName,
        String sourceSystem,
        String destinationSystem,
        Status status,
        Instant occurredAt,
        Instant completedAt,
        long durationMs,
        int attempt,
        int recordCount,
        int acceptedCount,
        int rejectedCount,
        String safeEntityReference,
        String errorCode,
        String safeErrorMessage,
        boolean retryable,
        Map<String, Object> metadata) {
    public enum Status {
        IDLE,
        QUEUED,
        RUNNING,
        SUCCEEDED,
        WARNING,
        FAILED,
        RETRYING,
        DEAD_LETTER,
        OFFLINE,
        NOT_IMPLEMENTED
    }
}
