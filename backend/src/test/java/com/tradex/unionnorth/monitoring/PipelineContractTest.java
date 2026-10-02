package com.tradex.unionnorth.monitoring;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

class PipelineContractTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    ObjectNode upstream() {
        var node = mapper.createObjectNode();
        for (String id : new String[] {"eventId", "runId", "correlationId"})
            node.put(id, UUID.randomUUID().toString());
        node.put("pipelineName", "hikvision-attendance")
                .put("stageName", "event-ingest")
                .put("sourceSystem", "linematrix")
                .put("destinationSystem", "supabase")
                .put("status", "FAILED")
                .put("occurredAt", Instant.now().toString())
                .put("recordCount", 1);
        return node;
    }

    @Test
    void redactsUntrustedMessagesMetadataAndEntityPayloads() throws Exception {
        var node = upstream();
        node.put("safeErrorMessage", "password=secret")
                .put("safeEntityReference", "NIC123")
                .put("errorCode", "SQL secret")
                .put("traceId", "bank-account")
                .put("acceptedCount", -10);
        node.putObject("metadata")
                .put("employeeName", "Private Person")
                .put("deviceReference", "device-0123456789abcdef");
        var event = UpstreamEventSanitizer.parse(node);
        assertThat(event).isNotNull();
        String json = mapper.writeValueAsString(event);
        assertThat(json).doesNotContain("secret", "NIC123", "bank-account", "Private Person");
        assertThat(event.errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(event.acceptedCount()).isZero();
        assertThat(event.metadata()).containsOnlyKeys("deviceReference");
    }

    @Test
    void futureStagesCannotReportSuccess() {
        var node = upstream().put("stageName", "payroll-calculation").put("status", "SUCCEEDED");
        assertThat(UpstreamEventSanitizer.parse(node)).isNull();
        assertThat(PipelineContract.implemented("zkteco-attendance", "payroll-staging")).isFalse();
        assertThat(PipelineContract.errorCode("LINEMATRIX_NOT_PAYROLL_ELIGIBLE"))
                .isEqualTo("LINEMATRIX_NOT_PAYROLL_ELIGIBLE");
    }

    @Test
    void rejectsInvalidIdentitiesAndStaleTime() {
        assertThat(UpstreamEventSanitizer.parse(upstream().put("eventId", "not-a-uuid"))).isNull();
        assertThat(
                        UpstreamEventSanitizer.parse(
                                upstream().put("occurredAt", "2000-01-01T00:00:00Z")))
                .isNull();
    }

    @Test
    void boundedQueueDoesNotTouchDatabaseDuringBusinessRequest() {
        var jdbc = mock(JdbcTemplate.class);
        var store = new PipelineStore(jdbc, mapper, new SimpleMeterRegistry(), 30);
        var event = UpstreamEventSanitizer.parse(upstream());
        for (int i = 0; i < 5002; i++) store.record(event);
        verifyNoInteractions(jdbc);
        assertThat(store.metrics())
                .containsEntry("pendingEvents", 5000)
                .containsEntry("droppedEvents", 2L);
    }

    @Test
    void retainsQueueWhenPersistenceFails() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new IllegalStateException("DB unavailable"));
        var store = new PipelineStore(jdbc, mapper, new SimpleMeterRegistry(), 30);
        store.record(UpstreamEventSanitizer.parse(upstream()));
        store.flush();
        assertThat(store.metrics())
                .containsEntry("pendingEvents", 1)
                .containsEntry("storageAvailable", false);
    }
}
