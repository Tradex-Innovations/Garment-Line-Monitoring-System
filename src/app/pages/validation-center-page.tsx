import { useMemo, useState } from "react";
import { AlertTriangle, CheckCheck, Fingerprint, ScanFace, ShieldAlert } from "lucide-react";
import { useAuth } from "../auth";
import { useValidationCenterData } from "../hooks/use-validation-center-data";
import {
  Button,
  Card,
  DetailDrawer,
  EmptyState,
  KpiCard,
  MetricTile,
  PageHeader,
  SearchField,
  StatusBadge,
  formatDateTime,
} from "../components/ops-ui";
import type { ReconciliationStatus } from "@/types/pipeline";

function reconciliationTone(status: ReconciliationStatus) {
  if (status === "validated") return "success";
  if (status === "leave") return "info";
  if (status === "face_only" || status === "fingerprint_only") return "warning";
  return "danger";
}

function confidenceTone(confidence: string | null) {
  if (confidence === "high") return "success";
  if (confidence === "medium") return "warning";
  return "danger";
}

export function ValidationCenterPage() {
  const { canDo } = useAuth();
  const [search, setSearch] = useState("");
  const [selectedDate, setSelectedDate] = useState("");
  const [selectedStatus, setSelectedStatus] = useState<ReconciliationStatus | "all">("all");
  const [selectedDepartment, setSelectedDepartment] = useState("");
  const [selectedBatchId, setSelectedBatchId] = useState("");
  const [noteDraft, setNoteDraft] = useState("");
  const [overrideStatus, setOverrideStatus] = useState<ReconciliationStatus>("validated");
  const [overrideReason, setOverrideReason] = useState("");

  const {
    rows,
    summaryRows,
    latestSummary,
    batches,
    detail,
    loading,
    detailLoading,
    feedback,
    loadDetail,
    clearDetail,
    applyOverride,
    createNote,
    isConfigured,
  } = useValidationCenterData({
    attendanceDate: selectedDate || null,
    status: selectedStatus,
    department: selectedDepartment || null,
    employeeCode: search || null,
    importBatchId: selectedBatchId || null,
  });

  const visibleSummary =
    summaryRows.find((row) => row.attendanceDate === selectedDate) || latestSummary;

  const departments = useMemo(
    () =>
      ["", ...new Set(rows.map((row) => row.departmentName).filter(Boolean) as string[])],
    [rows]
  );

  if (!isConfigured) {
    return (
      <div className="ops-page">
        <PageHeader
          title="Validation Center"
          subtitle="Live reconciliation, override, and note workflows are available once Supabase is configured."
        />
        <Card
          title="Supabase Enablement"
          subtitle="This screen depends on the imported face and fingerprint batches, normalized attendance tables, and reconciliation RPCs."
        >
          <EmptyState
            title="Supabase configuration required"
            description="Add the Vite Supabase environment variables, run the migrations, and import source files to populate the validation queue."
          />
        </Card>
      </div>
    );
  }

  return (
    <div className="ops-page">
      <PageHeader
        title="Validation Center"
        subtitle="Reconciled employee-day records with batch-aware filtering, explainable rule flags, note capture, and override controls."
        actions={
          <>
            <StatusBadge
              label={selectedDate || visibleSummary?.attendanceDate || "Latest"}
              tone="info"
            />
            <StatusBadge label={`${rows.length} rows`} tone="neutral" />
          </>
        }
      />

      {feedback ? (
        <div className="ops-badge tone-info" style={{ alignSelf: "flex-start" }}>
          {feedback}
        </div>
      ) : null}

      <section className="ops-kpi-grid">
        <KpiCard
          label="Total Reconciled"
          value={`${visibleSummary?.totalReconciled || 0}`}
          meta="Employee-day rows currently materialized for the selected date."
          icon={CheckCheck}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Validated"
          value={`${visibleSummary?.validatedCount || 0}`}
          meta="Face and fingerprint data matched cleanly."
          icon={CheckCheck}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Needs Review"
          value={`${visibleSummary?.needsReviewCount || 0}`}
          meta="Zero-time pairs, duplicates, or suspicious timing mismatches."
          icon={ShieldAlert}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Anomalies"
          value={`${visibleSummary?.anomalyCount || 0}`}
          meta="Contradictory source patterns like leave plus face activity."
          icon={AlertTriangle}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
      </section>

      <div className="ops-filter-bar">
        <SearchField
          value={search}
          onChange={setSearch}
          placeholder="Search employee code"
        />
        <input
          className="ops-input"
          type="date"
          value={selectedDate}
          onChange={(event) => setSelectedDate(event.target.value)}
          style={{ flex: "0 0 170px" }}
        />
        <select
          className="ops-select"
          value={selectedStatus}
          onChange={(event) => setSelectedStatus(event.target.value as ReconciliationStatus | "all")}
          style={{ flex: "0 0 180px" }}
        >
          <option value="all">All statuses</option>
          <option value="validated">Validated</option>
          <option value="face_only">Face Only</option>
          <option value="fingerprint_only">Fingerprint Only</option>
          <option value="leave">Leave</option>
          <option value="absent">Absent</option>
          <option value="needs_review">Needs Review</option>
          <option value="anomaly">Anomaly</option>
        </select>
        <select
          className="ops-select"
          value={selectedDepartment}
          onChange={(event) => setSelectedDepartment(event.target.value)}
          style={{ flex: "0 0 180px" }}
        >
          <option value="">All departments</option>
          {departments.map((department) =>
            department ? (
              <option key={department} value={department}>
                {department}
              </option>
            ) : null
          )}
        </select>
        <select
          className="ops-select"
          value={selectedBatchId}
          onChange={(event) => setSelectedBatchId(event.target.value)}
          style={{ flex: "0 0 220px" }}
        >
          <option value="">All import batches</option>
          {batches.map((batch) => (
            <option key={batch.id} value={batch.id}>
              {batch.originalFilename}
            </option>
          ))}
        </select>
      </div>

      <Card
        title="Reconciliation Queue"
        subtitle="Each row explains what the pipeline concluded for that employee-day and what still needs human attention."
      >
        {!rows.length && !loading ? (
          <EmptyState
            title="No reconciled rows yet"
            description="Import and reconcile a face batch and a fingerprint batch to populate the Validation Center."
          />
        ) : (
          <div className="ops-table-wrap">
            <table className="ops-table">
              <thead>
                <tr>
                  <th>Employee Code</th>
                  <th>Employee</th>
                  <th>Date</th>
                  <th>Face First Seen</th>
                  <th>Face Events</th>
                  <th>Fingerprint In</th>
                  <th>Fingerprint Out</th>
                  <th>Leave Type</th>
                  <th>Status</th>
                  <th>Confidence</th>
                  <th>Exception Reason</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td className="ops-monospace">{row.employeeCode}</td>
                    <td>
                      <div className="ops-row-title">{row.employeeName || "Unknown worker"}</div>
                      <div className="ops-row-subtitle">{row.departmentName || "Unknown department"}</div>
                    </td>
                    <td>{row.attendanceDate}</td>
                    <td>{row.faceFirstSeen || "Not captured"}</td>
                    <td>{row.faceEventCount ?? 0}</td>
                    <td>{row.fingerprintTimeIn || "Not captured"}</td>
                    <td>{row.fingerprintTimeOut || "Not captured"}</td>
                    <td>{row.leaveType || "None"}</td>
                    <td>
                      <StatusBadge
                        label={row.effectiveStatus}
                        tone={reconciliationTone(row.effectiveStatus)}
                      />
                    </td>
                    <td>
                      <StatusBadge
                        label={row.confidenceLevel || "low"}
                        tone={confidenceTone(row.confidenceLevel)}
                      />
                    </td>
                    <td>{row.exceptionReason || "Clean match"}</td>
                    <td>
                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                        <Button tone="ghost" onClick={() => void loadDetail(row.id)}>
                          View Details
                        </Button>
                        {canDo("resolveValidation") ? (
                          <Button
                            tone="secondary"
                            onClick={() =>
                              void applyOverride({
                                reconciliationId: row.id,
                                newStatus: "validated",
                                reason: "Resolved from the validation queue.",
                              })
                            }
                          >
                            Mark Resolved
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <DetailDrawer
        open={Boolean(detail)}
        title={detail?.record.employeeName || detail?.record.employeeCode || "Validation Detail"}
        subtitle={detail ? `${detail.record.employeeCode} · ${detail.record.attendanceDate}` : undefined}
        onClose={() => {
          clearDetail();
          setNoteDraft("");
          setOverrideReason("");
          setOverrideStatus("validated");
        }}
      >
        {detailLoading ? (
          <EmptyState
            title="Loading detail"
            description="The drawer is collecting face, fingerprint, note, and audit data for this reconciliation record."
          />
        ) : detail ? (
          <div className="ops-grid cols-2">
            <MetricTile label="Department" value={detail.record.departmentName || "Unknown"} />
            <MetricTile label="Designation" value={detail.record.designation || "Unknown"} />
            <MetricTile label="Face First Seen" value={detail.record.faceFirstSeen || "Not captured"} />
            <MetricTile label="Face Last Seen" value={detail.record.faceLastSeen || "Not captured"} />
            <MetricTile label="Fingerprint In" value={detail.record.fingerprintTimeIn || "Not captured"} />
            <MetricTile label="Fingerprint Out" value={detail.record.fingerprintTimeOut || "Not captured"} />
            <MetricTile label="Rule Flags" value={detail.record.ruleFlags.join(", ") || "None"} />
            <MetricTile label="Override" value={detail.record.manualOverrideStatus || "Not overridden"} />

            <Card title="Raw Face Rows" subtitle="Original row-level record strings preserved for audit.">
              {detail.faceRawRows.length ? (
                detail.faceRawRows.map((row: any) => (
                  <div key={row.id} className="ops-item-description" style={{ marginBottom: 8 }}>
                    Row {row.row_number}: {row.source_records_text || "No records text"}
                  </div>
                ))
              ) : (
                <EmptyState
                  title="No raw face rows"
                  description="There were no raw face rows matched to this employee-day."
                />
              )}
            </Card>

            <Card title="Normalized Face Events" subtitle="Sorted event tokens generated from the face workbook.">
              {detail.faceEvents.length ? (
                detail.faceEvents.map((row: any) => (
                  <div key={row.id} className="ops-item-description" style={{ marginBottom: 8 }}>
                    {row.event_time} {row.is_duplicate ? "· duplicate" : ""}
                  </div>
                ))
              ) : (
                <EmptyState
                  title="No face events"
                  description="No normalized face events were stored for this employee-day."
                />
              )}
            </Card>

            <Card title="Fingerprint Attendance" subtitle="Processed attendance summary row(s) linked to this reconciliation.">
              {detail.fingerprintRows.length ? (
                detail.fingerprintRows.map((row: any) => (
                  <div key={row.id} className="ops-item-description" style={{ marginBottom: 8 }}>
                    {row.attendance_state} · In {row.time_in || "N/A"} · Out {row.time_out || "N/A"} · Leave {row.leave_type || "None"}
                  </div>
                ))
              ) : (
                <EmptyState
                  title="No fingerprint row"
                  description="No normalized fingerprint attendance row was stored for this employee-day."
                />
              )}
            </Card>

            <Card title="Notes" subtitle="Operational notes remain visible alongside the underlying pipeline result.">
              {detail.notes.length ? (
                detail.notes.map((note: any) => (
                  <div key={note.id} className="ops-item-description" style={{ marginBottom: 8 }}>
                    {note.note} · {formatDateTime(note.created_at)}
                  </div>
                ))
              ) : (
                <EmptyState
                  title="No notes yet"
                  description="Add a supervisor or HR note to keep the review trail clear."
                />
              )}

              {canDo("addWorkerNote") ? (
                <div style={{ display: "grid", gap: 10, marginTop: 16 }}>
                  <textarea
                    className="ops-input"
                    rows={3}
                    placeholder="Add a reconciliation note"
                    value={noteDraft}
                    onChange={(event) => setNoteDraft(event.target.value)}
                  />
                  <div>
                    <Button
                      tone="secondary"
                      disabled={!noteDraft.trim()}
                      onClick={() => {
                        void createNote(detail.record.id, noteDraft.trim()).then(() =>
                          setNoteDraft("")
                        );
                      }}
                    >
                      Add Note
                    </Button>
                  </div>
                </div>
              ) : null}
            </Card>

            <Card title="Audit History" subtitle="Every override and note action is preserved for operational review.">
              {detail.auditLogs.length ? (
                detail.auditLogs.map((log: any) => (
                  <div key={log.id} className="ops-item-description" style={{ marginBottom: 8 }}>
                    {log.action_type} · {formatDateTime(log.created_at)}
                  </div>
                ))
              ) : (
                <EmptyState
                  title="No audit history yet"
                  description="Audit events will appear here once notes or overrides are recorded."
                />
              )}
            </Card>

            {canDo("resolveValidation") ? (
              <Card
                title="Override Status"
                subtitle="Only admin and HR can override the pipeline outcome. The original reconciliation status is preserved."
              >
                <div style={{ display: "grid", gap: 10 }}>
                  <select
                    className="ops-select"
                    value={overrideStatus}
                    onChange={(event) => setOverrideStatus(event.target.value as ReconciliationStatus)}
                  >
                    <option value="validated">validated</option>
                    <option value="face_only">face_only</option>
                    <option value="fingerprint_only">fingerprint_only</option>
                    <option value="leave">leave</option>
                    <option value="absent">absent</option>
                    <option value="needs_review">needs_review</option>
                    <option value="anomaly">anomaly</option>
                  </select>
                  <textarea
                    className="ops-input"
                    rows={3}
                    placeholder="Override reason"
                    value={overrideReason}
                    onChange={(event) => setOverrideReason(event.target.value)}
                  />
                  <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                    <Button
                      tone="primary"
                      disabled={!overrideReason.trim()}
                      onClick={() =>
                        void applyOverride({
                          reconciliationId: detail.record.id,
                          newStatus: overrideStatus,
                          reason: overrideReason.trim(),
                          note: noteDraft.trim() || null,
                        }).then(() => {
                          setOverrideReason("");
                        })
                      }
                    >
                      Save Override
                    </Button>
                    <Button
                      tone="ghost"
                      onClick={() =>
                        void applyOverride({
                          reconciliationId: detail.record.id,
                          newStatus: "validated",
                          reason: "Marked resolved from the validation drawer.",
                        })
                      }
                    >
                      Mark Resolved
                    </Button>
                  </div>
                </div>
              </Card>
            ) : null}
          </div>
        ) : null}
      </DetailDrawer>
    </div>
  );
}

export default ValidationCenterPage;
