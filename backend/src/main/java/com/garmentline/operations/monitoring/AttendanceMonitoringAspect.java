package com.garmentline.operations.monitoring;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import java.time.Instant;
import java.util.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AttendanceMonitoringAspect {
  private final MonitoringJournal journal;

  public AttendanceMonitoringAspect(MonitoringJournal journal) {
    this.journal = journal;
  }

  @Around("execution(* com.garmentline.operations.hikvision.HikvisionIsapiClient.postJson(..))")
  public Object nativeRead(ProceedingJoinPoint call) throws Throwable {
    if (!"hikvision-attendance".equals(MDC.get("pipelineName"))) return call.proceed();
    Instant start = Instant.now();
    var running =
        journal.event(
            "hikvision-attendance",
            "device-read",
            "hikvision",
            "linematrix",
            "RUNNING",
            start,
            0,
            0,
            null,
            Map.of());
    journal.append(running);
    try {
      Object result = call.proceed();
      var complete =
          journal.event(
              "hikvision-attendance",
              "device-read",
              "hikvision",
              "linematrix",
              "SUCCEEDED",
              start,
              0,
              0,
              null,
              Map.of());
      complete.put("parentEventId", running.get("eventId"));
      journal.append(complete);
      return result;
    } catch (Throwable error) {
      var failed =
          journal.event(
              "hikvision-attendance",
              "device-read",
              "hikvision",
              "linematrix",
              "FAILED",
              start,
              0,
              0,
              "DEVICE_READ_FAILED",
              Map.of());
      failed.put("parentEventId", running.get("eventId"));
      journal.append(failed);
      throw error;
    }
  }

  @Around(
      "execution(* com.garmentline.operations.hikvision.HikvisionService.receiveBridgeEvents(..))"
          + " || execution(* com.garmentline.operations.hikvision.HikvisionService.pollNow(..)) ||"
          + " execution(*"
          + " com.garmentline.operations.zkteco.ZktecoAdmsService.receiveBridgePunches(..)) ||"
          + " execution(* com.garmentline.operations.zkteco.ZktecoAdmsService.receiveCdata(..))")
  public Object pipeline(ProceedingJoinPoint call) throws Throwable {
    String previous = MDC.get("pipelineName"), correlation = MDC.get("correlationId");
    MDC.put(
        "pipelineName",
        call.getTarget().getClass().getSimpleName().contains("Hikvision")
            ? "hikvision-attendance"
            : "zkteco-attendance");
    if (correlation == null) MDC.put("correlationId", UUID.randomUUID().toString());
    try {
      return call.proceed();
    } finally {
      if (previous == null) MDC.remove("pipelineName");
      else MDC.put("pipelineName", previous);
      if (correlation == null) MDC.remove("correlationId");
    }
  }

  @Around(
      "execution(* com.garmentline.operations.supabase.SupabaseAdminClient.upsertMany(..)) ||"
          + " execution(* com.garmentline.operations.supabase.SupabaseAdminClient.insertSingle(..))"
          + " || execution(*"
          + " com.garmentline.operations.supabase.SupabaseAdminClient.updateSingle(..))")
  public Object storage(ProceedingJoinPoint call) throws Throwable {
    String table = Objects.toString(call.getArgs()[0], "");
    String pipeline = MDC.get("pipelineName");
    if (pipeline == null && table.equals("hikvision_face_events"))
      pipeline = "hikvision-attendance";
    if (pipeline == null && table.equals("zkteco_fingerprint_events"))
      pipeline = "zkteco-attendance";
    if (pipeline == null
        || !Set.of(
                "hikvision_face_events", "zkteco_fingerprint_events", "attendance_reconciliation")
            .contains(table)) return call.proceed();
    String stage =
        table.equals("attendance_reconciliation") ? "attendance-reconciliation" : "event-ingest";
    Object payload = call.getArgs()[call.getSignature().getName().equals("updateSingle") ? 2 : 1];
    int count = payload instanceof Collection<?> rows ? rows.size() : 1;
    Instant start = Instant.now();
    var span =
        GlobalOpenTelemetry.getTracer("linematrix.integration").spanBuilder(stage).startSpan();
    String previous = MDC.get("correlationId");
    if (previous == null) MDC.put("correlationId", UUID.randomUUID().toString());
    span.setAttribute("pipeline.name", pipeline);
    span.setAttribute("correlation.id", MDC.get("correlationId"));
    var running =
        journal.event(
            pipeline, stage, "linematrix", "supabase", "RUNNING", start, count, 0, null, Map.of());
    if (span.getSpanContext().isValid()) running.put("traceId", span.getSpanContext().getTraceId());
    journal.append(running);
    try (var scope = span.makeCurrent()) {
      Object result = call.proceed();
      var completed =
          journal.event(
              pipeline,
              stage,
              "linematrix",
              "supabase",
              "SUCCEEDED",
              start,
              count,
              count,
              null,
              Map.of());
      if (span.getSpanContext().isValid())
        completed.put("traceId", span.getSpanContext().getTraceId());
      completed.put("parentEventId", running.get("eventId"));
      journal.append(completed);
      return result;
    } catch (Throwable error) {
      span.setStatus(StatusCode.ERROR, "UPSTREAM_FAILED");
      var failed =
          journal.event(
              pipeline,
              stage,
              "linematrix",
              "supabase",
              "FAILED",
              start,
              count,
              0,
              "UPSTREAM_FAILED",
              Map.of());
      if (span.getSpanContext().isValid())
        failed.put("traceId", span.getSpanContext().getTraceId());
      failed.put("parentEventId", running.get("eventId"));
      journal.append(failed);
      throw error;
    } finally {
      span.end();
      if (previous == null) MDC.remove("correlationId");
    }
  }
}
