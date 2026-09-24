package com.tradex.unionnorth.employee.linematrix;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class LineMatrixEmployeeLookup {
    private final RestClient client;
    private final boolean configured;

    public LineMatrixEmployeeLookup(RestClient.Builder builder,
            @Value("${app.linematrix.url:}") String url,
            @Value("${app.linematrix.service-role-key:}") String key) {
        configured = !url.isBlank() && !key.isBlank();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = builder.baseUrl(url).requestFactory(factory)
                .defaultHeader("apikey", key)
                .defaultHeader("Authorization", "Bearer " + key)
                .defaultHeader("Accept-Profile", "public").build();
    }

    public LineMatrixEmployee lookup(String number) {
        if (number == null || number.isBlank() || number.trim().length() > 50) {
            throw new LineMatrixLookupException("INVALID_EMPLOYEE_NUMBER", "Enter an employee number (maximum 50 characters).", HttpStatus.BAD_REQUEST);
        }
        if (!configured) {
            throw new LineMatrixLookupException("LINEMATRIX_NOT_CONFIGURED", "LineMatrix lookup is not configured. Contact your administrator.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            // URI variables encode the input as a value, never as additional query parameters.
            JsonNode rows = client.get().uri(b -> b.path("/rest/v1/employees")
                    .queryParam("select", "id,employee_code,display_name,epf_no,department_name,designation,employment_status,is_active,employee_category,hire_date,teams(name),grades(name),employee_profiles(phone,join_date,shift_name,photo_url),employee_master_details(identity_number,first_name,last_name,full_name,initials,name_with_initials,call_name,gender,date_of_birth,residential_address,email,basic_salary,barcode_number,occupation_code,bank_name,bank_branch,bank_account_number,phone,mobile_phone,bus_route,distance_km,district,electorate,group_joined_date,direct_indirect_status,payroll_category,overtime_paid,attendance_bonus_eligible,emergency_name,emergency_phone,emergency_relationship),line_assignments(status,ended_at,production_lines(code,name))")
                    .queryParam("employee_code", "eq.{number}")
                    .queryParam("employee_category", "eq.permanent")
                    .queryParam("limit", 2).build(number.trim()))
                    .header("X-Correlation-Id", java.util.Objects.toString(org.slf4j.MDC.get("correlationId"), java.util.UUID.randomUUID().toString()))
                    .retrieve().body(JsonNode.class);
            if (rows == null || !rows.isArray()) throw new IllegalStateException("Invalid lookup response");
            if (rows.isEmpty()) {
                throw new LineMatrixLookupException("LINEMATRIX_NOT_FOUND", "No LineMatrix employee matches this number. Add the employee in LineMatrix first.", HttpStatus.NOT_FOUND);
            }
            if (rows.size() != 1) {
                throw new LineMatrixLookupException("LINEMATRIX_AMBIGUOUS", "More than one LineMatrix employee matches. Ask HR to resolve the duplicate before importing.", HttpStatus.CONFLICT);
            }
            JsonNode row = rows.get(0);
            String code = value(row, "employee_code");
            if (!number.trim().equals(code)) throw new IllegalStateException("Mismatched lookup response");
            LineMatrixEmployee employee = mapEmployee(row);
            if (!employee.isPermanent()) {
                throw new LineMatrixLookupException(
                        "LINEMATRIX_NOT_PAYROLL_ELIGIBLE",
                        "Only permanent LineMatrix employees can be loaded into Payroll.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return employee;
        } catch (RestClientException | IllegalStateException exception) {
            // Do not expose upstream bodies, personal data, URLs or credentials in errors.
            throw new LineMatrixLookupException("LINEMATRIX_UNAVAILABLE", "LineMatrix is unavailable. Retry shortly.", HttpStatus.BAD_GATEWAY);
        }
    }

    private static LineMatrixEmployee mapEmployee(JsonNode row) {
        JsonNode profile = single(row.path("employee_profiles"));
        JsonNode details = single(row.path("employee_master_details"));
        JsonNode line = activeLine(row.path("line_assignments"));
        String joinedDate = value(row, "hire_date");
        return new LineMatrixEmployee(value(row, "employee_code"), value(row, "display_name"), value(profile, "phone"),
                value(row, "epf_no"), value(row, "department_name"), value(row, "designation"),
                value(row, "employment_status"), row.path("is_active").asBoolean(false), value(row, "id"),
                joinedDate == null ? value(profile, "join_date") : joinedDate,
                value(profile, "shift_name"), value(row, "employee_category"),
                safePhotoUrl(value(profile, "photo_url")), value(line, "code"), value(line, "name"),
                value(single(row.path("teams")), "name"), value(single(row.path("grades")), "name"),
                payrollDetails(details));
    }

    private static Map<String, Object> payrollDetails(JsonNode details) {
        if (details == null || !details.isObject()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        details.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) return;
            if (value.isBoolean()) result.put(entry.getKey(), value.booleanValue());
            else if (value.isNumber()) result.put(entry.getKey(), value.numberValue());
            else if (!value.asText().isBlank()) result.put(entry.getKey(), value.asText());
        });
        return result;
    }

    private static JsonNode single(JsonNode node) {
        return node.isArray() ? (node.size() == 1 ? node.get(0) : null) : node;
    }

    private static JsonNode activeLine(JsonNode assignments) {
        if (!assignments.isArray()) return null;
        JsonNode selected = null;
        for (JsonNode assignment : assignments) {
            if (!"Active".equals(value(assignment, "status")) || !assignment.path("ended_at").isNull()) continue;
            // Duplicate active assignments must be resolved in LineMatrix, never guessed here.
            if (selected != null) return null;
            selected = assignment;
        }
        return selected == null ? null : single(selected.path("production_lines"));
    }

    private static String safePhotoUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String value(JsonNode node, String field) {
        if (node == null || !node.path(field).isTextual()) return null;
        String text = node.path(field).asText().trim();
        return text.isEmpty() ? null : text;
    }
}
