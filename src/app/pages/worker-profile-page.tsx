import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { ArrowLeft, ArrowRightLeft, FileWarning, NotebookPen, ShieldAlert, UserPlus } from "lucide-react";
import { useAuth } from "../auth";
import { findLine, useOperations } from "../operations-context";
import {
  Button,
  Card,
  EmptyState,
  PageHeader,
  StatusBadge,
  formatCurrency,
  formatDateTime,
  validationTone,
  attendanceTone,
} from "../components/ops-ui";

export function WorkerProfilePage() {
  const { workerId } = useParams();
  const { currentUser, canDo } = useAuth();
  const {
    loading,
    workers,
    lines,
    validationRecords,
    attendanceSummaries,
    transferLogs,
    overtimeRecords,
    leaveRecords,
    addWorkerNote,
    markWorkerException,
    assignWorker,
    transferWorker,
  } = useOperations();
  const worker = workers.find((item) => item.id === workerId);
  const [selectedLineId, setSelectedLineId] = useState(worker?.currentLineId || lines[0]?.id || "");
  const [note, setNote] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedLineId && lines[0]?.id) {
      setSelectedLineId(worker?.currentLineId || lines[0].id);
    }
  }, [lines, selectedLineId, worker?.currentLineId]);

  const currentLine = findLine(lines, worker?.currentLineId);
  const workerValidation = validationRecords
    .filter((item) => item.workerId === worker?.id)
    .sort((a, b) => b.date.localeCompare(a.date))[0];
  const attendanceSummary = attendanceSummaries.find((item) => item.workerId === worker?.id);
  const workerTransfers = transferLogs.filter((item) => item.workerId === worker?.id);
  const workerOt = overtimeRecords.filter((item) => item.workerId === worker?.id);
  const workerLeave = leaveRecords.filter((item) => item.workerId === worker?.id);
  const availableLines = useMemo(() => lines.filter((line) => line.status !== "Idle"), [lines]);

  if (!worker) {
    return (
      <div className="ops-page">
        <EmptyState
          title={loading ? "Loading worker profile" : "Worker not found"}
          description={
            loading
              ? "The live operational profile is still loading from Supabase."
              : "The requested worker profile is not available in the current operational dataset."
          }
        />
      </div>
    );
  }

  const timeline = workerValidation?.timeline || [];
  const transferLabel = workerTransfers.length
    ? `${workerTransfers.length} transfer records`
    : "No recorded transfers";

  const handleActionResult = async (
    resultPromise: Promise<{ ok: boolean; message: string }>
  ) => {
    const result = await resultPromise;
    setFeedback(result.message);
    if (result.ok) setNote("");
  };

  return (
    <div className="ops-page">
      <div className="ops-toolbar">
        <Link to="/workers" className="ops-button ops-button-secondary">
          <ArrowLeft size={15} />
          Back to Workers
        </Link>
      </div>

      <PageHeader
        title={worker.fullName}
        subtitle={`${worker.employeeId} · ${worker.department} · ${worker.roleTitle}`}
        actions={
          <>
            {canDo("assignLine") ? (
              <Button
                tone="secondary"
                onClick={() =>
                  void handleActionResult(
                    worker.currentLineId
                      ? transferWorker({
                          workerId: worker.id,
                          destinationLineId: selectedLineId,
                          reason: note || `Transfer action initiated by ${currentUser.name}.`,
                          actor: currentUser.name,
                        })
                      : assignWorker({
                          workerId: worker.id,
                          lineId: selectedLineId,
                          reason: note || `Assignment action initiated by ${currentUser.name}.`,
                          actor: currentUser.name,
                        })
                  )
                }
              >
                {worker.currentLineId ? <ArrowRightLeft size={15} /> : <UserPlus size={15} />}
                {worker.currentLineId ? "Transfer Worker" : "Assign Worker"}
              </Button>
            ) : null}
            {canDo("markException") ? (
              <Button
                tone="danger"
                onClick={() =>
                  void handleActionResult(
                    markWorkerException({
                      workerId: worker.id,
                      note: note || `Exception logged by ${currentUser.name}.`,
                      actor: currentUser.name,
                    })
                  )
                }
              >
                <FileWarning size={15} />
                Mark Exception
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

      <section className="ops-grid cols-2">
        <Card title="Profile Overview" subtitle="Current validation, attendance, and assignment status.">
          <div className="ops-meta-grid">
            <div className="ops-key-value">
              <div className="ops-key-value-label">Assigned Line</div>
              <div className="ops-key-value-value">{currentLine?.name || "Unassigned"}</div>
            </div>
            <div className="ops-key-value">
              <div className="ops-key-value-label">Shift</div>
              <div className="ops-key-value-value">{worker.shift}</div>
            </div>
            <div className="ops-key-value">
              <div className="ops-key-value-label">Attendance Status</div>
              <div className="ops-key-value-value">
                <StatusBadge label={worker.attendanceStatus} tone={attendanceTone(worker.attendanceStatus)} />
              </div>
            </div>
            <div className="ops-key-value">
              <div className="ops-key-value-label">Final Validation</div>
              <div className="ops-key-value-value">
                <StatusBadge label={worker.finalValidationStatus} tone={validationTone(worker.finalValidationStatus)} />
              </div>
            </div>
          </div>

          <div className="ops-card-divider" />

          <div className="ops-stat-strip" style={{ marginTop: 18 }}>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Face Verification</div>
              <div className="ops-stat-value">{worker.faceVerificationStatus}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Fingerprint Verification</div>
              <div className="ops-stat-value">{worker.fingerprintVerificationStatus}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Transfer History</div>
              <div className="ops-stat-value">{transferLabel}</div>
            </div>
          </div>
        </Card>

        <Card title="Supervisor Actions" subtitle="Line assignment, transfer, exception, and notes panel.">
          <div className="ops-filter-group">
            <label className="ops-filter-label" htmlFor="targetLine">
              Target Line
            </label>
            <select
              id="targetLine"
              className="ops-select"
              value={selectedLineId}
              onChange={(event) => setSelectedLineId(event.target.value)}
            >
              {availableLines.map((line) => (
                <option key={line.id} value={line.id}>
                  {line.name} · {line.actualManpower}/{line.targetManpower}
                </option>
              ))}
            </select>
          </div>

          <div className="ops-filter-group" style={{ marginTop: 14 }}>
            <label className="ops-filter-label" htmlFor="note">
              Note / Reason
            </label>
            <textarea
              id="note"
              className="ops-textarea"
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Add supervisor remark, transfer reason, or exception note"
            />
          </div>

          <div className="ops-toolbar" style={{ marginTop: 16 }}>
            {canDo("addWorkerNote") ? (
              <Button
                tone="secondary"
                onClick={() =>
                  void handleActionResult(
                    addWorkerNote({
                      workerId: worker.id,
                      note: note || `Note added by ${currentUser.name}.`,
                      actor: currentUser.name,
                    })
                  )
                }
              >
                <NotebookPen size={15} />
                Add Note
              </Button>
            ) : null}
            {canDo("assignLine") ? (
              <Button
                tone="primary"
                onClick={() =>
                  void handleActionResult(
                    worker.currentLineId
                      ? transferWorker({
                          workerId: worker.id,
                          destinationLineId: selectedLineId,
                          reason: note || `Transfer action by ${currentUser.name}.`,
                          actor: currentUser.name,
                        })
                      : assignWorker({
                          workerId: worker.id,
                          lineId: selectedLineId,
                          reason: note || `Assignment action by ${currentUser.name}.`,
                          actor: currentUser.name,
                        })
                  )
                }
              >
                {worker.currentLineId ? "Transfer Line" : "Assign Line"}
              </Button>
            ) : null}
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card title="Event Timeline" subtitle="Face, fingerprint, validation, assignment, and transfer history.">
          <div className="ops-timeline">
            {timeline.length ? (
              timeline.map((event) => (
                <div key={event.id} className="ops-timeline-item">
                  <div className="ops-timeline-dot">
                    <ShieldAlert size={12} />
                  </div>
                  <div className="ops-timeline-title">{event.label}</div>
                  <div className="ops-timeline-meta">{formatDateTime(event.timestamp)}</div>
                  <div className="ops-item-description">{event.detail}</div>
                </div>
              ))
            ) : (
              <EmptyState
                title="No validation timeline available"
                description="This worker does not have a recent validation timeline yet."
              />
            )}
          </div>
        </Card>

        <Card title="Attendance Summary" subtitle="Payroll-ready attendance, OT, leave, and incentive context.">
          <div className="ops-stat-strip">
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Days Present</div>
              <div className="ops-stat-value">{attendanceSummary?.daysPresent || 0}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">OT Hours</div>
              <div className="ops-stat-value">{attendanceSummary?.otHours || 0}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Leave Days</div>
              <div className="ops-stat-value">{attendanceSummary?.leaveDays || 0}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Final Total</div>
              <div className="ops-stat-value">
                {attendanceSummary ? formatCurrency(attendanceSummary.finalTotal) : "N/A"}
              </div>
            </div>
          </div>

          <div className="ops-card-divider" />

          <div className="ops-list" style={{ marginTop: 18 }}>
            {workerOt.map((record) => (
              <div key={record.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div className="ops-item-title">OT approved</div>
                  <StatusBadge label={`${record.hours}h`} tone="info" />
                </div>
                <div className="ops-item-meta">
                  <span>{record.date}</span>
                  <span>{record.approvedBy}</span>
                </div>
              </div>
            ))}
            {workerLeave.map((record) => (
              <div key={record.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div className="ops-item-title">{record.type} leave</div>
                  <StatusBadge label={record.status} tone="neutral" />
                </div>
                <div className="ops-item-meta">
                  <span>
                    {record.startDate} to {record.endDate}
                  </span>
                  <span>{record.days} day(s)</span>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card title="Transfer History" subtitle="Line movement log with source, destination, and supervisor reason.">
          <div className="ops-list">
            {workerTransfers.length ? (
              workerTransfers.map((record) => (
                <div key={record.id} className="ops-list-item">
                  <div className="ops-item-header">
                    <div className="ops-item-title">
                      {findLine(lines, record.sourceLineId)?.name || "Pool"} →{" "}
                      {findLine(lines, record.destinationLineId)?.name || "Pool"}
                    </div>
                    <StatusBadge label={record.transferredBy} tone="neutral" />
                  </div>
                  <div className="ops-item-description">{record.reason}</div>
                  <div className="ops-item-meta">
                    <span>{formatDateTime(record.transferredAt)}</span>
                  </div>
                </div>
              ))
            ) : (
              <EmptyState
                title="No transfer history"
                description="This worker has not been moved between lines in the current operational history."
              />
            )}
          </div>
        </Card>

        <Card title="Notes, Flags & Remarks" subtitle="Supervisor notes and operational flags attached to the worker profile.">
          <div className="ops-list">
            {worker.notes.map((entry) => (
              <div key={entry} className="ops-list-item">
                <div className="ops-item-title">Note</div>
                <div className="ops-item-description">{entry}</div>
              </div>
            ))}
            {worker.flags.map((entry) => (
              <div key={entry} className="ops-list-item">
                <div className="ops-item-title">Flag</div>
                <div className="ops-item-description">{entry}</div>
              </div>
            ))}
            {worker.supervisorRemarks.map((entry) => (
              <div key={entry} className="ops-list-item">
                <div className="ops-item-title">Supervisor remark</div>
                <div className="ops-item-description">{entry}</div>
              </div>
            ))}
          </div>
        </Card>
      </section>
    </div>
  );
}

export default WorkerProfilePage;
