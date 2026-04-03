import { useMemo, useState } from "react";
import { useAuth } from "../auth";
import { findLine, useOperations } from "../operations-context";
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
  formatTime,
  validationTone,
} from "../components/ops-ui";
import { AlertTriangle, CheckCheck, ScanFace, ShieldAlert } from "lucide-react";

export function ValidationCenterPage() {
  const { currentUser, canDo } = useAuth();
  const {
    validationRecords,
    lines,
    markValidationVerified,
    resolveValidation,
    escalateValidation,
  } = useOperations();

  const [selectedRecordId, setSelectedRecordId] = useState<string | null>(
    null
  );
  const [search, setSearch] = useState("");
  const [selectedDate, setSelectedDate] = useState("2026-04-03");
  const [selectedShift, setSelectedShift] = useState("All");
  const [selectedDepartment, setSelectedDepartment] = useState("All");
  const [selectedLine, setSelectedLine] = useState("All");
  const [selectedStatus, setSelectedStatus] = useState("All");
  const [feedback, setFeedback] = useState<string | null>(null);

  const statusCounts = useMemo(
    () =>
      new Map(
        ["Fully Validated", "Pending Validation", "Face Only", "Fingerprint Only", "Time Mismatch", "Unresolved Exception"].map(
          (status) => [
            status,
            validationRecords.filter((record) => record.status === status).length,
          ]
        )
      ),
    [validationRecords]
  );

  const departments = ["All", ...new Set(validationRecords.map((record) => record.department))];
  const shifts = ["All", ...new Set(validationRecords.map((record) => record.shift))];
  const lineOptions = [
    "All",
    ...new Set(
      validationRecords.map((record) => findLine(lines, record.lineId)?.name).filter(Boolean)
    ),
  ] as string[];

  const filteredRecords = validationRecords.filter((record) => {
    const query = search.trim().toLowerCase();
    const matchesQuery =
      !query ||
      record.workerName.toLowerCase().includes(query) ||
      record.employeeId.toLowerCase().includes(query);
    const matchesDate = !selectedDate || record.date === selectedDate;
    const matchesShift = selectedShift === "All" || record.shift === selectedShift;
    const matchesDepartment =
      selectedDepartment === "All" || record.department === selectedDepartment;
    const lineName = findLine(lines, record.lineId)?.name || "Unassigned";
    const matchesLine = selectedLine === "All" || lineName === selectedLine;
    const matchesStatus = selectedStatus === "All" || record.status === selectedStatus;
    return (
      matchesQuery &&
      matchesDate &&
      matchesShift &&
      matchesDepartment &&
      matchesLine &&
      matchesStatus
    );
  });

  const selectedRecord =
    filteredRecords.find((record) => record.id === selectedRecordId) ||
    validationRecords.find((record) => record.id === selectedRecordId) ||
    null;

  const handleResult = (result: { ok: boolean; message: string }) => {
    setFeedback(result.message);
  };

  return (
    <div className="ops-page">
      <PageHeader
        title="Validation Center"
        subtitle="Unified attendance validation queue for face recognition, fingerprint reconciliation, manual review, and escalation handling."
        actions={
          <>
            <StatusBadge label={`${filteredRecords.length} in queue`} tone="info" />
            {canDo("markValidationVerified") ? (
              <Button
                tone="primary"
                onClick={() => {
                  if (!selectedRecord) return;
                  handleResult(
                    markValidationVerified({
                      validationId: selectedRecord.id,
                      actor: currentUser.name,
                    })
                  );
                }}
              >
                <CheckCheck size={15} />
                Mark Selected Verified
              </Button>
            ) : null}
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
          label="Fully Validated"
          value={`${statusCounts.get("Fully Validated") || 0}`}
          meta="Both biometric sources reconciled."
          icon={CheckCheck}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Pending Review"
          value={`${statusCounts.get("Pending Validation") || 0}`}
          meta="Require HR intervention before attendance close."
          icon={ShieldAlert}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Partial Capture"
          value={`${(statusCounts.get("Face Only") || 0) + (statusCounts.get("Fingerprint Only") || 0)}`}
          meta="Single-method entries waiting for reconciliation."
          icon={ScanFace}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Exceptions"
          value={`${(statusCounts.get("Time Mismatch") || 0) + (statusCounts.get("Unresolved Exception") || 0)}`}
          meta="Mismatches and unresolved events in the queue."
          icon={AlertTriangle}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
      </section>

      <div className="ops-filter-bar">
        <SearchField
          value={search}
          onChange={setSearch}
          placeholder="Search by employee ID or worker name"
        />
        <input
          className="ops-input"
          style={{ flex: "0 0 170px" }}
          type="date"
          value={selectedDate}
          onChange={(event) => setSelectedDate(event.target.value)}
        />
        <select
          className="ops-select"
          style={{ flex: "0 0 150px" }}
          value={selectedShift}
          onChange={(event) => setSelectedShift(event.target.value)}
        >
          {shifts.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
        <select
          className="ops-select"
          style={{ flex: "0 0 180px" }}
          value={selectedDepartment}
          onChange={(event) => setSelectedDepartment(event.target.value)}
        >
          {departments.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
        <select
          className="ops-select"
          style={{ flex: "0 0 150px" }}
          value={selectedLine}
          onChange={(event) => setSelectedLine(event.target.value)}
        >
          {lineOptions.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
        <select
          className="ops-select"
          style={{ flex: "0 0 190px" }}
          value={selectedStatus}
          onChange={(event) => setSelectedStatus(event.target.value)}
        >
          <option value="All">All statuses</option>
          <option value="Fully Validated">Fully Validated</option>
          <option value="Pending Validation">Pending Validation</option>
          <option value="Face Only">Face Only</option>
          <option value="Fingerprint Only">Fingerprint Only</option>
          <option value="Time Mismatch">Time Mismatch</option>
          <option value="Unresolved Exception">Unresolved Exception</option>
        </select>
      </div>

      <section className="ops-table-card">
        {filteredRecords.length ? (
          <div className="ops-table-wrap">
            <table className="ops-table">
              <thead>
                <tr>
                  <th>Employee ID</th>
                  <th>Worker Name</th>
                  <th>Face Event</th>
                  <th>Fingerprint Event</th>
                  <th>Current Line</th>
                  <th>Shift</th>
                  <th>Validation Status</th>
                  <th>Confidence</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredRecords.map((record) => (
                  <tr key={record.id}>
                    <td className="ops-monospace">{record.employeeId}</td>
                    <td>
                      <div className="ops-row-title">{record.workerName}</div>
                      <div className="ops-row-subtitle">{record.department}</div>
                    </td>
                    <td>{formatTime(record.faceEventTime)}</td>
                    <td>{formatTime(record.fingerprintEventTime)}</td>
                    <td>{findLine(lines, record.lineId)?.name || "Unassigned"}</td>
                    <td>{record.shift}</td>
                    <td>
                      <StatusBadge
                        label={record.status}
                        tone={validationTone(record.status)}
                      />
                    </td>
                    <td>{record.confidenceScore}%</td>
                    <td>
                      <div className="ops-row-actions">
                        <button
                          className="ops-link-button"
                          onClick={() => setSelectedRecordId(record.id)}
                        >
                          View details
                        </button>
                        {canDo("resolveValidation") ? (
                          <button
                            className="ops-link-button"
                            onClick={() =>
                              handleResult(
                                resolveValidation({
                                  validationId: record.id,
                                  status: record.status,
                                  reason: `Manual review logged by ${currentUser.name}.`,
                                  actor: currentUser.name,
                                })
                              )
                            }
                          >
                            Resolve manually
                          </button>
                        ) : null}
                        {canDo("markValidationVerified") ? (
                          <button
                            className="ops-link-button"
                            onClick={() =>
                              handleResult(
                                markValidationVerified({
                                  validationId: record.id,
                                  actor: currentUser.name,
                                })
                              )
                            }
                          >
                            Mark verified
                          </button>
                        ) : null}
                        {canDo("escalateValidation") ? (
                          <button
                            className="ops-link-button"
                            onClick={() =>
                              handleResult(
                                escalateValidation({
                                  validationId: record.id,
                                  actor: currentUser.name,
                                })
                              )
                            }
                          >
                            Escalate
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="ops-card-body">
            <EmptyState
              title="No validation records matched"
              description="Adjust the date, queue status, or search value to inspect another set of biometric events."
            />
          </div>
        )}
      </section>

      <DetailDrawer
        open={Boolean(selectedRecord)}
        title={selectedRecord?.workerName || "Validation details"}
        subtitle={
          selectedRecord
            ? `${selectedRecord.employeeId} · ${selectedRecord.department} · ${selectedRecord.shift}`
            : undefined
        }
        onClose={() => setSelectedRecordId(null)}
        footer={
          selectedRecord ? (
            <div className="ops-toolbar">
              {canDo("markValidationVerified") ? (
                <Button
                  tone="primary"
                  onClick={() =>
                    handleResult(
                      markValidationVerified({
                        validationId: selectedRecord.id,
                        actor: currentUser.name,
                      })
                    )
                  }
                >
                  Mark Verified
                </Button>
              ) : null}
              {canDo("escalateValidation") ? (
                <Button
                  tone="secondary"
                  onClick={() =>
                    handleResult(
                      escalateValidation({
                        validationId: selectedRecord.id,
                        actor: currentUser.name,
                      })
                    )
                  }
                >
                  Escalate
                </Button>
              ) : null}
            </div>
          ) : undefined
        }
      >
        {selectedRecord ? (
          <>
            <div className="ops-meta-grid">
              <MetricTile label="Current Line" value={findLine(lines, selectedRecord.lineId)?.name || "Unassigned"} />
              <MetricTile label="Status" value={selectedRecord.status} />
              <MetricTile label="Face Event" value={formatTime(selectedRecord.faceEventTime)} />
              <MetricTile label="Fingerprint Event" value={formatTime(selectedRecord.fingerprintEventTime)} />
            </div>

            {selectedRecord.exceptionReason ? (
              <div className="ops-list-item" style={{ marginTop: 18 }}>
                <div className="ops-item-title">Exception summary</div>
                <div className="ops-item-description">{selectedRecord.exceptionReason}</div>
              </div>
            ) : null}

            <div style={{ marginTop: 18 }}>
              <h3 className="ops-card-title">Full Event Timeline</h3>
              <div className="ops-timeline" style={{ marginTop: 14 }}>
                {selectedRecord.timeline.map((event) => (
                  <div key={event.id} className="ops-timeline-item">
                    <div className="ops-timeline-dot">
                      <ShieldAlert size={12} />
                    </div>
                    <div className="ops-timeline-title">{event.label}</div>
                    <div className="ops-timeline-meta">
                      {formatDateTime(event.timestamp)}
                    </div>
                    <div className="ops-item-description">{event.detail}</div>
                  </div>
                ))}
              </div>
            </div>
          </>
        ) : null}
      </DetailDrawer>
    </div>
  );
}

export default ValidationCenterPage;
