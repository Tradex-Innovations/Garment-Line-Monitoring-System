package com.tradex.unionnorth.monitoring;

import com.tradex.unionnorth.common.error.BusinessException;
import com.tradex.unionnorth.employee.dto.EmployeeResponse;
import com.tradex.unionnorth.employee.linematrix.LineMatrixEmployee;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@Order(0) // Outside the transaction interceptor: success means commit returned successfully.
public class PipelineInstrumentation {
    private final PipelineStore store;

    public PipelineInstrumentation(PipelineStore store) {
        this.store = store;
    }

    @Around(
            "execution(*"
                + " com.tradex.unionnorth.employee.linematrix.LineMatrixEmployeeLookup.lookup(..))"
                + " || execution(*"
                + " com.tradex.unionnorth.employee.service.EmployeeService.createEmployee(..)) ||"
                + " execution(* com.tradex.unionnorth.setup.PayrollProfileService.saveGeneral(..))"
                + " || execution(*"
                + " com.tradex.unionnorth.setup.PayrollProfileService.saveFinancial(..)) ||"
                + " execution(* com.tradex.unionnorth.setup.PayrollProfileService.link(..)) ||"
                + " execution(* com.tradex.unionnorth.setup.PayrollProfileService.activate(..))")
    public Object observe(ProceedingJoinPoint call) throws Throwable {
        String stage =
                switch (call.getSignature().getName()) {
                    case "lookup" -> "employee-lookup";
                    case "createEmployee" -> "registration-create";
                    case "link" -> "profile-link";
                    case "activate" -> "payroll-activate";
                    default -> "profile-save";
                };
        String correlation = PipelineContract.safeId(MDC.get("correlationId"));
        if (correlation == null) correlation = UUID.randomUUID().toString();
        UUID run = UUID.fromString(correlation), startId = UUID.randomUUID();
        String previous = MDC.get("pipelineEventId");
        UUID parent = previous == null ? null : UUID.fromString(previous);
        Instant started = Instant.now();
        var span =
                GlobalOpenTelemetry.getTracer("unionnorth.integration")
                        .spanBuilder(stage)
                        .startSpan();
        span.setAttribute("pipeline.name", "employees");
        span.setAttribute("correlation.id", correlation);
        String trace =
                span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : correlation;
        String destination = stage.equals("employee-lookup") ? "supabase" : "payroll";
        store.record(
                event(
                        startId,
                        trace,
                        correlation,
                        run,
                        parent,
                        stage,
                        destination,
                        PipelineEvent.Status.RUNNING,
                        started,
                        null,
                        null,
                        null));
        MDC.put("pipelineEventId", startId.toString());
        try (var scope = span.makeCurrent()) {
            Object result = call.proceed();
            String entity =
                    result instanceof EmployeeResponse employee
                            ? employee.id().toString()
                            : result instanceof LineMatrixEmployee source
                                    ? PipelineContract.safeId(source.sourceId())
                                    : call.getArgs().length > 0
                                                    && call.getArgs()[0] instanceof UUID id
                                            ? id.toString()
                                            : null;
            store.record(
                    event(
                            UUID.randomUUID(),
                            trace,
                            correlation,
                            run,
                            startId,
                            stage,
                            destination,
                            PipelineEvent.Status.SUCCEEDED,
                            started,
                            Instant.now(),
                            entity,
                            null));
            return result;
        } catch (Throwable error) {
            String code =
                    error instanceof BusinessException business
                            ? PipelineContract.errorCode(business.getCode())
                            : "INTERNAL_ERROR";
            span.setStatus(
                    StatusCode.ERROR, code); // Never record the raw exception on exported spans.
            store.record(
                    event(
                            UUID.randomUUID(),
                            trace,
                            correlation,
                            run,
                            startId,
                            stage,
                            destination,
                            PipelineEvent.Status.FAILED,
                            started,
                            Instant.now(),
                            null,
                            code));
            throw error;
        } finally {
            span.end();
            if (previous == null) MDC.remove("pipelineEventId");
            else MDC.put("pipelineEventId", previous);
        }
    }

    private PipelineEvent event(
            UUID id,
            String trace,
            String correlation,
            UUID run,
            UUID parent,
            String stage,
            String destination,
            PipelineEvent.Status status,
            Instant start,
            Instant end,
            String entity,
            String code) {
        return new PipelineEvent(
                id,
                trace,
                correlation,
                run,
                parent,
                "employees",
                stage,
                "payroll",
                destination,
                status,
                start,
                end,
                end == null ? 0 : Math.max(0, java.time.Duration.between(start, end).toMillis()),
                1,
                1,
                status == PipelineEvent.Status.SUCCEEDED ? 1 : 0,
                status == PipelineEvent.Status.FAILED ? 1 : 0,
                entity,
                code,
                code == null ? null : PipelineContract.ERRORS.get(code),
                "LINEMATRIX_UNAVAILABLE".equals(code),
                Map.of());
    }
}
