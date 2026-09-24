package com.tradex.unionnorth.monitoring;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PipelineContract {
    private PipelineContract() {}

    public record Stage(String name, String label, boolean implemented) {}

    public record Pipeline(String name, String label, List<Stage> stages) {}

    private static Stage stage(String name, String label) {
        return new Stage(name, label, true);
    }

    private static Stage future(String name, String label) {
        return new Stage(name, label, false);
    }

    public static final List<Pipeline> PIPELINES =
            List.of(
                    new Pipeline(
                            "employees",
                            "Employees",
                            List.of(
                                    stage("employee-lookup", "LineMatrix lookup"),
                                    stage("registration-create", "Create employee"),
                                    stage("profile-link", "Link source"),
                                    stage("profile-save", "Save registration"),
                                    stage("payroll-activate", "Activate profile"))),
                    attendance("zkteco-attendance", "Attendance · ZKTeco"),
                    attendance("hikvision-attendance", "Attendance · Hikvision"),
                    new Pipeline(
                            "system",
                            "Payroll API errors",
                            List.of(stage("api-request", "API request"))),
                    new Pipeline(
                            "linematrix-system",
                            "LineMatrix API errors",
                            List.of(stage("api-request", "API request"))));

    private static Pipeline attendance(String name, String label) {
        return new Pipeline(
                name,
                label,
                List.of(
                        stage("device-read", "Read device"),
                        stage("bridge-upload", "LAN bridge"),
                        stage("event-ingest", "Store events"),
                        stage("attendance-reconciliation", "Reconcile attendance"),
                        future("attendance-sync", "Attendance sync"),
                        future("payroll-staging", "Payroll staging"),
                        future("approved-input", "Approved input"),
                        future("payroll-calculation", "Payroll calculation")));
    }

    public static boolean implemented(String pipeline, String stage) {
        return PIPELINES.stream()
                .filter(p -> p.name().equals(pipeline))
                .flatMap(p -> p.stages().stream())
                .anyMatch(s -> s.name().equals(stage) && s.implemented());
    }

    public static String safeId(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static final Set<String> SYSTEMS =
            Set.of("payroll", "linematrix", "supabase", "zkteco", "hikvision", "lan-bridge");
    public static final Map<String, String> ERRORS =
            Map.ofEntries(
                    Map.entry(
                            "INTERNAL_ERROR",
                            "The stage failed. Use the correlation ID to investigate on the"
                                    + " server."),
                    Map.entry("VALIDATION_FAILED", "Validation rejected this operation."),
                    Map.entry("ACCESS_DENIED", "The caller is not authorized for this operation."),
                    Map.entry(
                            "LINEMATRIX_NOT_CONFIGURED",
                            "LineMatrix lookup configuration is missing."),
                    Map.entry("LINEMATRIX_NOT_FOUND", "No employee matched the lookup."),
                    Map.entry(
                            "LINEMATRIX_AMBIGUOUS",
                            "Multiple employee records matched the lookup."),
                    Map.entry(
                            "LINEMATRIX_NOT_PAYROLL_ELIGIBLE",
                            "The employee is not a permanent LineMatrix employee."),
                    Map.entry(
                            "LINEMATRIX_UNAVAILABLE",
                            "The LineMatrix data service could not be reached."),
                    Map.entry("INVALID_EMPLOYEE_NUMBER", "The lookup identifier is invalid."),
                    Map.entry("DEVICE_READ_FAILED", "The worker could not read the device."),
                    Map.entry("UPLOAD_FAILED", "The worker could not upload its batch."),
                    Map.entry(
                            "RECORDS_REJECTED",
                            "Some records were not accepted. They may include duplicates or"
                                    + " unmatched records."),
                    Map.entry("UPSTREAM_FAILED", "The upstream stage failed."),
                    Map.entry("HTTP_REJECTED", "The API rejected this request."));

    public static String errorCode(String code) {
        return ERRORS.containsKey(code == null ? "" : code) ? code : "INTERNAL_ERROR";
    }
}
