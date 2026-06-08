import { useEffect, useMemo, useRef, useState } from "react";
import { Camera, CheckCircle2, Clock, Image, LogOut, RefreshCw, Send, UserRoundCheck } from "lucide-react";
import {
  createEmployeePortalLeaveRequestFromBackend,
  getEmployeePortalKioskRecognition,
  logoutEmployeePortal,
} from "@/lib/backend/employee-portal-api";
import { isBackendConfigured } from "@/lib/backend/env";
import type {
  EmployeePortalKioskRecognition,
  EmployeePortalSnapshot,
} from "@/types/employee-portal";
import type { HalfDaySession, LeaveCategory, LeaveType } from "@/types/leave-management";
import {
  AccessDeniedState,
  Button,
  Card,
  EmptyState,
  MetricTile,
  PageHeader,
  StatusBadge,
  formatCurrency,
  getInitials,
} from "../components/ops-ui";

const POLL_MS = 2000;
const DISPLAY_MS = 45000;

const EMPTY_PORTAL: EmployeePortalSnapshot = {
  linked: false,
  profile: { id: "" },
  employee: null,
  currentLine: null,
  attendanceHistory: [],
  leaveRequests: [],
  incentives: [],
  leaveBalance: {
    allowanceDays: 14,
    usedDays: 0,
    remainingDays: 14,
  },
};

function labelize(value?: string | null) {
  if (!value) return "Not captured";
  return value.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function statusTone(status?: string | null) {
  if (!status) return "neutral";
  const normalized = status.toLowerCase();
  if (["validated", "approved", "present", "matched"].includes(normalized)) return "success";
  if (["pending", "needs_review", "face_only", "fingerprint_only"].includes(normalized)) return "warning";
  if (["leave", "cancelled"].includes(normalized)) return "info";
  if (["absent", "rejected", "anomaly", "unmatched"].includes(normalized)) return "danger";
  return "neutral";
}

function formatDateTime(value?: string | null) {
  if (!value) return "Not captured";
  return new Date(value).toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function EmployeePortalKioskPage() {
  const [snapshot, setSnapshot] = useState<EmployeePortalSnapshot>(EMPTY_PORTAL);
  const [recognition, setRecognition] = useState<EmployeePortalKioskRecognition | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [lastEventId, setLastEventId] = useState<string | null>(null);
  const [polling, setPolling] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const displayTimerRef = useRef<number | null>(null);
  const activeTokenRef = useRef<string | null>(null);
  const [leaveForm, setLeaveForm] = useState({
    leaveType: "full_day" as LeaveType,
    leaveCategory: "casual" as LeaveCategory,
    startDate: new Date().toISOString().slice(0, 10),
    endDate: new Date().toISOString().slice(0, 10),
    startTime: "",
    endTime: "",
    halfDaySession: "first_half" as HalfDaySession,
    reason: "",
  });

  const pendingLeaveCount = useMemo(
    () => snapshot.leaveRequests.filter((item) => item.status === "pending").length,
    [snapshot.leaveRequests]
  );
  const latestAttendance = snapshot.attendanceHistory[0];
  const latestIncentive = snapshot.incentives[0];
  const isRecognized = Boolean(token && snapshot.employee);

  const clearDisplay = async () => {
    const currentToken = activeTokenRef.current;
    activeTokenRef.current = null;
    setToken(null);
    setSnapshot(EMPTY_PORTAL);
    setRecognition(null);
    setMessage(null);
    if (displayTimerRef.current) {
      window.clearTimeout(displayTimerRef.current);
      displayTimerRef.current = null;
    }
    if (currentToken) {
      try {
        await logoutEmployeePortal(currentToken);
      } catch (_error) {
        // The kiosk can return to idle even if the short-lived backend token already expired.
      }
    }
  };

  const pollRecognition = async () => {
    if (activeTokenRef.current) return;
    setPolling(true);
    setError(null);
    try {
      const response = await getEmployeePortalKioskRecognition(lastEventId);
      if (response.status === "recognized") {
        setLastEventId(response.eventId);
        setRecognition(response.recognition);
        setSnapshot(response.snapshot);
        setToken(response.token);
        activeTokenRef.current = response.token;
        setMessage(`Recognized ${response.snapshot.employee?.fullName || response.recognition.employeeName || "employee"}.`);

        if (displayTimerRef.current) {
          window.clearTimeout(displayTimerRef.current);
        }
        displayTimerRef.current = window.setTimeout(() => {
          void clearDisplay();
        }, DISPLAY_MS);
      }
    } catch (pollError) {
      setError(pollError instanceof Error ? pollError.message : String(pollError));
    } finally {
      setPolling(false);
    }
  };

  useEffect(() => {
    void pollRecognition();
    const interval = window.setInterval(() => {
      void pollRecognition();
    }, POLL_MS);
    return () => {
      window.clearInterval(interval);
      if (displayTimerRef.current) {
        window.clearTimeout(displayTimerRef.current);
      }
    };
  }, [lastEventId]);

  const applyLeave = async () => {
    if (!token) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const startDate = leaveForm.startDate;
      const endDate = leaveForm.leaveType === "full_day" ? leaveForm.endDate : startDate;
      setSnapshot(
        await createEmployeePortalLeaveRequestFromBackend(token, {
          leaveType: leaveForm.leaveType,
          leaveCategory: leaveForm.leaveCategory,
          startDate,
          endDate,
          startTime: leaveForm.leaveType === "short_leave" ? leaveForm.startTime : null,
          endTime: leaveForm.leaveType === "short_leave" ? leaveForm.endTime : null,
          halfDaySession: leaveForm.leaveType === "half_day" ? leaveForm.halfDaySession : null,
          reason: leaveForm.reason || null,
        })
      );
      setLeaveForm((current) => ({ ...current, reason: "" }));
      setMessage("Leave request submitted for HR approval.");
    } catch (leaveError) {
      setError(leaveError instanceof Error ? leaveError.message : String(leaveError));
    } finally {
      setSaving(false);
    }
  };

  if (!isBackendConfigured()) {
    return (
      <AccessDeniedState
        title="Backend configuration required"
        description="Set VITE_BACKEND_URL before opening the employee recognition display."
      />
    );
  }

  return (
    <div className="ops-page ops-self-service ops-kiosk-page">
      {isRecognized ? (
        <PageHeader
          title="Employee Portal"
          subtitle="Face recognition matched this employee profile."
          actions={
            <Button tone="secondary" onClick={() => void clearDisplay()}>
              <LogOut size={15} />
              Clear Display
            </Button>
          }
        />
      ) : (
        <div className="ops-kiosk-header">
          <div className="ops-kiosk-brand">
            <span className="ops-kiosk-live-dot" />
            Face Scan Ready
          </div>
          <Button tone="secondary" onClick={() => void pollRecognition()} disabled={polling}>
            <RefreshCw size={15} />
            Poll Now
          </Button>
        </div>
      )}

      {error ? <div className="ops-alert-banner tone-danger">{error}</div> : null}
      {message ? <div className="ops-alert-banner tone-info">{message}</div> : null}

      {!isRecognized ? (
        <section className="ops-kiosk-standby-card" aria-live="polite">
          <div className="ops-kiosk-standby-content">
            <div className="ops-kiosk-scanner" aria-hidden="true">
              <span className="ops-kiosk-ring ops-kiosk-ring-one" />
              <span className="ops-kiosk-ring ops-kiosk-ring-two" />
              <span className="ops-kiosk-ring ops-kiosk-ring-three" />
              <span className="ops-kiosk-scan-line" />
              <div className="ops-kiosk-camera">
                <Camera size={46} />
              </div>
            </div>
            <h1>Scan your face to open the portal</h1>
          </div>
        </section>
      ) : null}

      {isRecognized && snapshot.employee ? (
        <>
          <Card
            title={snapshot.employee.fullName}
            subtitle={`${snapshot.employee.employeeCode} · ${snapshot.employee.designation || "Employee"}`}
            actions={<StatusBadge label="Face recognized" tone="success" />}
          >
            <div className="ops-worker-profile-hero">
              {snapshot.employee.photoUrl || recognition?.pictureUrl ? (
                <img
                  src={snapshot.employee.photoUrl || recognition?.pictureUrl || ""}
                  alt={snapshot.employee.fullName}
                  className="ops-worker-profile-photo"
                />
              ) : (
                <div className="ops-worker-profile-photo ops-worker-profile-photo-placeholder">
                  <Image size={22} />
                  <span>{getInitials(snapshot.employee.fullName)}</span>
                </div>
              )}
              <div>
                <div className="ops-item-title">{snapshot.employee.fullName}</div>
                <div className="ops-row-subtitle">
                  {snapshot.employee.department || "Department not captured"} · {snapshot.employee.shift || "Shift not captured"}
                </div>
                <div className="ops-row-subtitle">
                  Camera {recognition?.cameraSerialNo || "not captured"} · {formatDateTime(recognition?.eventTime)}
                </div>
              </div>
            </div>
          </Card>

          <section className="ops-grid cols-4">
            <MetricTile label="Current Line" value={snapshot.currentLine?.name || "Unassigned"} />
            <MetricTile label="Latest Attendance" value={latestAttendance ? labelize(latestAttendance.status) : "No records"} />
            <MetricTile label="Leave Balance" value={`${snapshot.leaveBalance.remainingDays} days`} />
            <MetricTile label="Pending Requests" value={`${pendingLeaveCount}`} />
          </section>

          <section className="ops-grid cols-2">
            <Card title="Recognition Snapshot" subtitle="Latest matched event from the public-area camera.">
              <div className="ops-grid cols-2">
                <MetricTile label="Employee No." value={snapshot.employee.employeeCode} />
                <MetricTile label="Recognition" value={labelize(recognition?.verifyMode)} />
                <MetricTile label="Access" value={labelize(recognition?.accessDecision)} />
                <MetricTile label="Session" value="Short-lived" />
              </div>
            </Card>

            <Card title="Current Line & Incentives" subtitle="Line assignment and recent incentive visibility.">
              <div className="ops-list">
                <div className="ops-list-item">
                  <div className="ops-item-header">
                    <div>
                      <div className="ops-item-title">{snapshot.currentLine?.name || "Unassigned"}</div>
                      <div className="ops-row-subtitle">
                        {snapshot.currentLine
                          ? `${snapshot.currentLine.code} · ${snapshot.currentLine.shift || "Shift not captured"}`
                          : "No active line assignment"}
                      </div>
                    </div>
                    <StatusBadge label={snapshot.currentLine ? "Active" : "Pending"} tone={snapshot.currentLine ? "success" : "warning"} />
                  </div>
                </div>
                <div className="ops-list-item">
                  <div className="ops-item-header">
                    <div>
                      <div className="ops-item-title">{latestIncentive ? latestIncentive.monthStart : "No incentive record"}</div>
                      <div className="ops-row-subtitle">{latestIncentive?.reason || "Latest incentive visibility"}</div>
                    </div>
                    <StatusBadge label={formatCurrency(latestIncentive?.amount || 0)} tone="success" />
                  </div>
                </div>
              </div>
            </Card>
          </section>

          <section className="ops-grid cols-2">
            <Card title="Apply for Leave" subtitle="Requests are saved as pending and reviewed by HR.">
              <div className="ops-grid cols-2">
                <select
                  className="ops-select"
                  value={leaveForm.leaveType}
                  onChange={(event) =>
                    setLeaveForm((current) => ({ ...current, leaveType: event.target.value as LeaveType }))
                  }
                >
                  <option value="full_day">Full day leave</option>
                  <option value="half_day">Half day leave</option>
                  <option value="short_leave">Short leave</option>
                </select>
                <select
                  className="ops-select"
                  value={leaveForm.leaveCategory}
                  onChange={(event) =>
                    setLeaveForm((current) => ({ ...current, leaveCategory: event.target.value as LeaveCategory }))
                  }
                >
                  <option value="annual">Annual</option>
                  <option value="casual">Casual</option>
                  <option value="sick">Sick</option>
                  <option value="no_pay">No pay</option>
                  <option value="emergency">Emergency</option>
                  <option value="personal">Personal</option>
                  <option value="medical">Medical</option>
                  <option value="other">Other</option>
                </select>
                <input
                  className="ops-input"
                  type="date"
                  value={leaveForm.startDate}
                  onChange={(event) => setLeaveForm((current) => ({ ...current, startDate: event.target.value }))}
                />
                {leaveForm.leaveType === "full_day" ? (
                  <input
                    className="ops-input"
                    type="date"
                    value={leaveForm.endDate}
                    onChange={(event) => setLeaveForm((current) => ({ ...current, endDate: event.target.value }))}
                  />
                ) : null}
                {leaveForm.leaveType === "half_day" ? (
                  <select
                    className="ops-select"
                    value={leaveForm.halfDaySession}
                    onChange={(event) =>
                      setLeaveForm((current) => ({ ...current, halfDaySession: event.target.value as HalfDaySession }))
                    }
                  >
                    <option value="first_half">First half</option>
                    <option value="second_half">Second half</option>
                  </select>
                ) : null}
                {leaveForm.leaveType === "short_leave" ? (
                  <>
                    <input
                      className="ops-input"
                      type="time"
                      value={leaveForm.startTime}
                      onChange={(event) => setLeaveForm((current) => ({ ...current, startTime: event.target.value }))}
                    />
                    <input
                      className="ops-input"
                      type="time"
                      value={leaveForm.endTime}
                      onChange={(event) => setLeaveForm((current) => ({ ...current, endTime: event.target.value }))}
                    />
                  </>
                ) : null}
              </div>
              <textarea
                className="ops-textarea"
                style={{ marginTop: 14 }}
                value={leaveForm.reason}
                onChange={(event) => setLeaveForm((current) => ({ ...current, reason: event.target.value }))}
                placeholder="Leave reason"
              />
              <div className="ops-item-actions" style={{ marginTop: 14 }}>
                <Button tone="primary" disabled={saving} onClick={() => void applyLeave()}>
                  <Send size={15} />
                  Submit Request
                </Button>
              </div>
            </Card>

            <Card title="Attendance History" subtitle="Latest attendance reconciliation records.">
              <div className="ops-list">
                {snapshot.attendanceHistory.slice(0, 8).map((record) => (
                  <div key={record.id} className="ops-list-item">
                    <div className="ops-item-header">
                      <div>
                        <div className="ops-item-title">{record.date}</div>
                        <div className="ops-row-subtitle">
                          Time in {record.timeIn?.slice(0, 5) || record.faceFirstSeen?.slice(0, 5) || "N/A"} · Time out{" "}
                          {record.timeOut?.slice(0, 5) || record.faceLastSeen?.slice(0, 5) || "N/A"}
                        </div>
                      </div>
                      <StatusBadge label={labelize(record.status)} tone={statusTone(record.status)} />
                    </div>
                    <div className="ops-item-meta">
                      <span>OT {record.otHours || 0}h</span>
                      <span>Late/Early {record.lateEarlyHours || 0}h</span>
                      {record.leaveType ? <span>{record.leaveType}</span> : null}
                    </div>
                  </div>
                ))}
                {!snapshot.attendanceHistory.length ? (
                  <EmptyState title="No attendance history" description="Attendance records will appear after HR imports or device syncs data." />
                ) : null}
              </div>
            </Card>
          </section>

          <Card title="Leave Requests" subtitle="Submitted requests and HR review status.">
            <div className="ops-list">
              {snapshot.leaveRequests.slice(0, 8).map((request) => (
                <div key={request.id} className="ops-list-item">
                  <div className="ops-item-header">
                    <div>
                      <div className="ops-item-title">
                        {labelize(request.leaveType)} · {labelize(request.leaveCategory)}
                      </div>
                      <div className="ops-row-subtitle">
                        {request.startDate}
                        {request.endDate !== request.startDate ? ` to ${request.endDate}` : ""}
                        {request.startTime && request.endTime
                          ? ` · ${request.startTime.slice(0, 5)}-${request.endTime.slice(0, 5)}`
                          : ""}
                      </div>
                    </div>
                    <StatusBadge label={labelize(request.status)} tone={statusTone(request.status)} />
                  </div>
                  {request.reason ? <div className="ops-item-description">{request.reason}</div> : null}
                  <div className="ops-item-meta">
                    <span>{request.dayCount ? `${request.dayCount} day(s)` : "Short leave"}</span>
                    <span>{formatDateTime(request.requestedAt)}</span>
                  </div>
                </div>
              ))}
              {!snapshot.leaveRequests.length ? (
                <EmptyState title="No leave requests" description="Submitted leave requests will appear here." />
              ) : null}
            </div>
          </Card>
        </>
      ) : null}
    </div>
  );
}

export default EmployeePortalKioskPage;
