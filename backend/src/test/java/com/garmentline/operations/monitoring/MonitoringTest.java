package com.garmentline.operations.monitoring;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.server.ResponseStatusException;

class MonitoringTest {
  @Test
  void missingOrIncorrectMonitoringCredentialIsRejected() {
    for (String actual : new String[] {null, "", "wrong"})
      assertThatThrownBy(() -> MonitoringController.authorize("configured", actual))
          .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> MonitoringController.authorize("", "anything"))
        .isInstanceOf(ResponseStatusException.class);
    MonitoringController.authorize("configured", "configured");
  }

  @Test
  void workerContractDoesNotCopyPrivateFieldsAndRingIsBounded() throws Exception {
    var journal = new MonitoringJournal();
    var mapper = new ObjectMapper();
    var event =
        mapper
            .createObjectNode()
            .put("schemaVersion", 1)
            .put("sourceSystem", "hikvision")
            .put("stageName", "device-read")
            .put("status", "FAILED")
            .put("recordCount", 1)
            .put("deviceReference", "device-0123456789abcdef")
            .put("correlationId", UUID.randomUUID().toString())
            .put("safeErrorMessage", "password=private")
            .put("employeeName", "Private Person");
    for (int i = 0; i < 1002; i++) journal.worker(event);
    assertThat(journal.events()).hasSize(1000);
    assertThat(journal.evicted()).isEqualTo(2);
    assertThat(mapper.writeValueAsString(journal.events()))
        .doesNotContain("Private Person", "password=private");
    assertThat(journal.devices()).hasSize(1);
  }

  @Test
  void futureStagesAndRawDeviceIdentifiersAreRejected() {
    var journal = new MonitoringJournal();
    var input =
        new ObjectMapper()
            .createObjectNode()
            .put("schemaVersion", 1)
            .put("sourceSystem", "hikvision")
            .put("stageName", "payroll-calculation")
            .put("status", "SUCCEEDED")
            .put("deviceReference", "192.0.2.1")
            .put("correlationId", UUID.randomUUID().toString());
    assertThatThrownBy(() -> journal.worker(input)).isInstanceOf(ResponseStatusException.class);
    assertThat(journal.events()).isEmpty();
  }

  @Test
  void runningCountsAreNotRejectionsAndClockSkewCannotReverseEventTimes() {
    var journal = new MonitoringJournal();
    var input =
        new ObjectMapper()
            .createObjectNode()
            .put("schemaVersion", 1)
            .put("sourceSystem", "hikvision")
            .put("stageName", "bridge-upload")
            .put("status", "RUNNING")
            .put("recordCount", 5)
            .put("deviceReference", "device-0123456789abcdef")
            .put("occurredAt", java.time.Instant.now().plusSeconds(30).toString())
            .put("correlationId", UUID.randomUUID().toString());
    journal.worker(input);
    assertThat(journal.events().getLast()).containsEntry("rejectedCount", 0);
    assertThat(journal.devices()).isEmpty();
    journal.worker(input.put("status", "SUCCEEDED").put("acceptedCount", 5));
    var completed = journal.events().getLast();
    assertThat(java.time.Instant.parse((String) completed.get("completedAt")))
        .isAfterOrEqualTo(java.time.Instant.parse((String) completed.get("occurredAt")));
  }

  @Test
  void storageFailureIsRecordedBeforeCallerCanSwallowIt() throws Throwable {
    var journal = new MonitoringJournal();
    var aspect = new AttendanceMonitoringAspect(journal);
    var call = mock(ProceedingJoinPoint.class);
    var signature = mock(Signature.class);
    when(call.getArgs())
        .thenReturn(new Object[] {"attendance_reconciliation", Map.of("employee_id", "private")});
    when(call.getSignature()).thenReturn(signature);
    when(signature.getName()).thenReturn("insertSingle");
    when(call.proceed()).thenThrow(new IllegalStateException("password=private"));
    MDC.put("pipelineName", "hikvision-attendance");
    MDC.put("correlationId", UUID.randomUUID().toString());
    try {
      assertThatThrownBy(() -> aspect.storage(call)).isInstanceOf(IllegalStateException.class);
      assertThat(journal.events()).hasSize(2);
      assertThat(journal.events().getLast())
          .containsEntry("status", "FAILED")
          .containsEntry("stageName", "attendance-reconciliation");
      assertThat(journal.events().toString()).doesNotContain("private");
    } finally {
      MDC.clear();
    }
  }
}
