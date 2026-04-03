import { useMemo, useState } from "react";
import { useOperations, findLine } from "../operations-context";
import { Card, MetricTile, PageHeader, StatusBadge, formatCurrency, validationTone } from "../components/ops-ui";

export function SelfServicePage() {
  const {
    workers,
    lines,
    attendanceSummaries,
    overtimeRecords,
    leaveRecords,
    incentiveRecords,
  } = useOperations();
  const [selectedWorkerId, setSelectedWorkerId] = useState(workers[0]?.id || "");

  const worker = workers.find((item) => item.id === selectedWorkerId) || workers[0];
  const attendance = attendanceSummaries.find((item) => item.workerId === worker?.id);
  const otRows = overtimeRecords.filter((item) => item.workerId === worker?.id);
  const leaveRows = leaveRecords.filter((item) => item.workerId === worker?.id);
  const incentiveRows = incentiveRecords.filter((item) => item.workerId === worker?.id);
  const leaveBalance = useMemo(
    () => 12 - leaveRows.reduce((sum, item) => sum + item.days, 0),
    [leaveRows]
  );

  if (!worker) return null;

  return (
    <div className="ops-page ops-self-service">
      <PageHeader
        title="Staff Self-Service Portal"
        subtitle="Simple mobile-friendly view for attendance, OT, leave balance, incentives, current line, and validation status."
        actions={
          <select
            className="ops-select"
            value={selectedWorkerId}
            onChange={(event) => setSelectedWorkerId(event.target.value)}
          >
            {workers.map((item) => (
              <option key={item.id} value={item.id}>
                {item.fullName}
              </option>
            ))}
          </select>
        }
      />

      <Card title={worker.fullName} subtitle={`${worker.employeeId} · ${worker.roleTitle}`}>
        <div className="ops-meta-grid">
          <MetricTile label="Current Line" value={findLine(lines, worker.currentLineId)?.name || "Pending assignment"} />
          <MetricTile label="Attendance" value={worker.attendanceStatus} />
          <MetricTile label="Leave Balance" value={`${leaveBalance} days`} />
          <MetricTile label="Validation" value={worker.finalValidationStatus} />
        </div>
      </Card>

      <section className="ops-grid cols-2">
        <Card title="Monthly Summary" subtitle="Attendance, OT, and payroll summary for the selected worker.">
          <div className="ops-stat-strip">
            <MetricTile label="Days Present" value={`${attendance?.daysPresent || 0}`} />
            <MetricTile label="Days Absent" value={`${attendance?.daysAbsent || 0}`} />
            <MetricTile label="OT Hours" value={`${attendance?.otHours || 0}`} />
            <MetricTile label="Final Total" value={attendance ? formatCurrency(attendance.finalTotal) : "N/A"} />
          </div>
        </Card>

        <Card title="Validation Status" subtitle="Biometric readiness for attendance and line release.">
          <div className="ops-list">
            <div className="ops-list-item">
              <div className="ops-item-header">
                <div className="ops-item-title">Face verification</div>
                <StatusBadge label={worker.faceVerificationStatus} tone="info" />
              </div>
            </div>
            <div className="ops-list-item">
              <div className="ops-item-header">
                <div className="ops-item-title">Fingerprint verification</div>
                <StatusBadge label={worker.fingerprintVerificationStatus} tone="info" />
              </div>
            </div>
            <div className="ops-list-item">
              <div className="ops-item-header">
                <div className="ops-item-title">Final validation</div>
                <StatusBadge label={worker.finalValidationStatus} tone={validationTone(worker.finalValidationStatus)} />
              </div>
            </div>
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card title="OT Log" subtitle="Approved overtime items.">
          <div className="ops-list">
            {otRows.map((row) => (
              <div key={row.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div className="ops-item-title">{row.date}</div>
                  <StatusBadge label={`${row.hours}h`} tone="warning" />
                </div>
                <div className="ops-row-subtitle">Approved by {row.approvedBy}</div>
              </div>
            ))}
          </div>
        </Card>

        <Card title="Leave & Incentives" subtitle="Latest leave approvals and incentive summary.">
          <div className="ops-list">
            {leaveRows.map((row) => (
              <div key={row.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div className="ops-item-title">{row.type} leave</div>
                  <StatusBadge label={row.status} tone="neutral" />
                </div>
                <div className="ops-row-subtitle">
                  {row.startDate} to {row.endDate} · {row.days} day(s)
                </div>
              </div>
            ))}
            {incentiveRows.map((row) => (
              <div key={row.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div className="ops-item-title">{row.reason}</div>
                  <StatusBadge label={formatCurrency(row.amount)} tone="success" />
                </div>
                <div className="ops-row-subtitle">{row.month}</div>
              </div>
            ))}
          </div>
        </Card>
      </section>
    </div>
  );
}

export default SelfServicePage;
