package com.tradex.unionnorth.setup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Repository
public class SetupStore {
    public record Item(
            UUID id,
            String kind,
            String code,
            String name,
            boolean active,
            long version,
            LocalDate effectiveFrom,
            Map<String, Object> data) {}

    record Org(String table, String nameColumn, Map<String, String> columns) {}

    private static final Map<String, Org> ORGS =
            Map.of(
                    "COMPANY",
                            new Org(
                                    "companies",
                                    "name",
                                    Map.of("registrationNumber", "registration_number")),
                    "LOCATION",
                            new Org(
                                    "locations",
                                    "name",
                                    Map.of(
                                            "companyId",
                                            "company_id",
                                            "addressLine",
                                            "address_line")),
                    "DEPARTMENT",
                            new Org(
                                    "departments",
                                    "name",
                                    Map.of("companyId", "company_id", "locationId", "location_id")),
                    "LINE",
                            new Org(
                                    "production_lines",
                                    "name",
                                    Map.of("departmentId", "department_id")),
                    "DESIGNATION",
                            new Org("designations", "title", Map.of("companyId", "company_id")));
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SetupStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encode setup data", e);
        }
    }

    Map<String, Object> object(String value) {
        try {
            return mapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read setup data", e);
        }
    }

    public List<Item> list(String kind) {
        Org org = ORGS.get(kind);
        if (org != null)
            return jdbc.query(
                    "SELECT * FROM " + org.table() + " ORDER BY code",
                    (r, i) -> orgItem(r, kind, org));
        return jdbc.query(
                """
SELECT i.id, i.kind, i.code, i.version, r.name, r.active, r.effective_from, r.data
FROM setup_items i JOIN LATERAL
(SELECT * FROM setup_item_revisions WHERE item_id=i.id ORDER BY effective_from DESC LIMIT 1) r ON true
WHERE i.kind=? ORDER BY i.code
""",
                (r, i) -> item(r),
                kind);
    }

    public Item get(String kind, UUID id, LocalDate at) {
        Org org = ORGS.get(kind);
        List<Item> found;
        if (org != null)
            found =
                    jdbc.query(
                            "SELECT * FROM " + org.table() + " WHERE id=?",
                            (r, i) -> orgItem(r, kind, org),
                            id);
        else if (at == null)
            found =
                    jdbc.query(
                            """
SELECT i.id, i.kind, i.code, i.version, r.name, r.active, r.effective_from, r.data
FROM setup_items i JOIN setup_item_revisions r ON r.item_id=i.id
WHERE i.kind=? AND i.id=? ORDER BY r.effective_from DESC LIMIT 1
""",
                            (r, i) -> item(r),
                            kind,
                            id);
        else
            found =
                    jdbc.query(
                            """
SELECT i.id, i.kind, i.code, i.version, r.name, r.active, r.effective_from, r.data
FROM setup_items i JOIN setup_item_revisions r ON r.item_id=i.id
WHERE i.kind=? AND i.id=? AND r.effective_from<=? ORDER BY r.effective_from DESC LIMIT 1
""",
                            (r, i) -> item(r),
                            kind,
                            id,
                            at);
        if (found.isEmpty()) throw SetupException.missing();
        return found.getFirst();
    }

    public Item write(
            String kind,
            UUID id,
            String code,
            String name,
            boolean active,
            long version,
            LocalDate effectiveFrom,
            Map<String, Object> data,
            String reason) {
        boolean create = id == null;
        if (create) id = UUID.randomUUID();
        Org org = ORGS.get(kind);
        if (org != null) {
            List<String> columns = new ArrayList<>(org.columns().values());
            List<Object> values = new ArrayList<>();
            for (var entry : org.columns().entrySet()) {
                Object v = data.get(entry.getKey());
                values.add(
                        entry.getKey().endsWith("Id") && v != null
                                ? UUID.fromString(v.toString())
                                : v);
            }
            if (create) {
                columns.addAll(
                        List.of(
                                "id",
                                "code",
                                org.nameColumn(),
                                "active",
                                "created_by",
                                "updated_by"));
                values.addAll(
                        List.of(id, code, name, active, SetupAccess.actor(), SetupAccess.actor()));
                jdbc.update(
                        "INSERT INTO "
                                + org.table()
                                + " ("
                                + String.join(",", columns)
                                + ") VALUES ("
                                + String.join(",", Collections.nCopies(columns.size(), "?"))
                                + ")",
                        values.toArray());
            } else {
                List<String> sets = new ArrayList<>(columns.stream().map(c -> c + "=?").toList());
                sets.add(org.nameColumn() + "=?");
                sets.add("active=?");
                sets.add("updated_by=?");
                sets.add("updated_at=now()");
                sets.add("version=version+1");
                values.addAll(List.of(name, active, SetupAccess.actor(), id, version));
                if (jdbc.update(
                                "UPDATE "
                                        + org.table()
                                        + " SET "
                                        + String.join(",", sets)
                                        + " WHERE id=? AND version=?",
                                values.toArray())
                        != 1) throw SetupException.conflict();
            }
        } else {
            if (create)
                jdbc.update("INSERT INTO setup_items(id,kind,code) VALUES (?,?,?)", id, kind, code);
            else if (jdbc.update(
                            "UPDATE setup_items SET version=version+1 WHERE id=? AND version=?",
                            id,
                            version)
                    != 1) throw SetupException.conflict();
            jdbc.update(
                    """
INSERT INTO setup_item_revisions(item_id,name,active,effective_from,data,created_by,reason)
VALUES (?,?,?,?,?::jsonb,?,?)
""",
                    id,
                    name,
                    active,
                    effectiveFrom,
                    json(data),
                    SetupAccess.actor(),
                    reason);
        }
        audit(null, create ? "MASTER_CREATED" : "MASTER_REVISED", kind, id, reason, data.keySet());
        return get(kind, id, null);
    }

    void audit(
            UUID employee,
            String action,
            String kind,
            UUID id,
            String reason,
            Collection<String> fields) {
        // Audit metadata deliberately excludes account numbers and salary amounts.
        jdbc.update(
                """
INSERT INTO audit_logs(user_id,employee_id,action,entity_type,entity_id,new_value,reason)
VALUES (?,?,?,?,?,?,?)
""",
                SetupAccess.actor(),
                employee,
                action,
                kind,
                id,
                json(Map.of("changedFields", fields)),
                reason);
    }

    List<Map<String, Object>> auditHistory(UUID id) {
        return jdbc.query(
                """
SELECT action,user_id,reason,timestamp,new_value FROM audit_logs WHERE entity_id=? ORDER BY timestamp DESC LIMIT 100
""",
                (r, i) ->
                        Map.of(
                                "action",
                                r.getString("action"),
                                "actor",
                                Objects.toString(r.getString("user_id"), "system"),
                                "reason",
                                Objects.toString(r.getString("reason"), ""),
                                "at",
                                r.getTimestamp("timestamp").toInstant().toString(),
                                "changes",
                                object(Objects.toString(r.getString("new_value"), "{}"))),
                id);
    }

    private Item orgItem(ResultSet r, String kind, Org org) throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();
        for (var e : org.columns().entrySet()) {
            Object v = r.getObject(e.getValue());
            if (v != null) data.put(e.getKey(), v.toString());
        }
        return new Item(
                r.getObject("id", UUID.class),
                kind,
                r.getString("code"),
                r.getString(org.nameColumn()),
                r.getBoolean("active"),
                r.getLong("version"),
                null,
                data);
    }

    private Item item(ResultSet r) throws SQLException {
        return new Item(
                r.getObject("id", UUID.class),
                r.getString("kind"),
                r.getString("code"),
                r.getString("name"),
                r.getBoolean("active"),
                r.getLong("version"),
                r.getDate("effective_from").toLocalDate(),
                object(r.getString("data")));
    }
}
