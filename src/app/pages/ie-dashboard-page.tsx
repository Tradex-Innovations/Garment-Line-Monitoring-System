import { useMemo, useState } from "react";
import { Link } from "react-router";
import { BarChart3, CheckCircle2, Fingerprint, ScanFace, Users } from "lucide-react";
import { useOperations, findLine } from "../operations-context";
import {
  Card,
  KpiCard,
  PageHeader,
  SearchField,
  StatusBadge,
  WorkerChip,
  attendanceTone,
} from "../components/ops-ui";

function verificationLabel(verified: boolean) {
  return verified ? "Attended" : "Not attended";
}

function verificationTone(verified: boolean) {
  return verified ? "success" : "danger";
}

export function IeDashboardPage() {
  const { attendanceOverview, workers, lines } = useOperations();
  const [query, setQuery] = useState("");

  const filteredWorkers = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return workers;
    }

    return workers.filter((worker) => {
      const line = findLine(lines, worker.currentLineId);
      return [
        worker.fullName,
        worker.employeeId,
        worker.department,
        worker.roleTitle,
        line?.name,
        line?.code,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(normalized);
    });
  }, [lines, query, workers]);

  const fingerprintAttended = workers.filter(
    (worker) => worker.fingerprintVerificationStatus === "Verified"
  ).length;
  const faceAttended = workers.filter((worker) => worker.faceVerificationStatus === "Verified").length;
  const overallAttended = attendanceOverview.presentWorkers + attendanceOverview.lateWorkers;
  const lineAttendanceAverage =
    lines.length === 0
      ? 0
      : Math.round(lines.reduce((sum, line) => sum + line.attendanceRate, 0) / lines.length);

  return (
    <div className="ops-page">
      <PageHeader
        title="IE Dashboard"
        subtitle="Employee attendance verification and line readiness for industrial engineering review."
        actions={
          <>
            <Link to="/ie-line-attendance" className="ops-button ops-button-secondary">
              Line Attendance
            </Link>
            <Link to="/ie-analytics" className="ops-button ops-button-primary">
              Analytics
            </Link>
          </>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Total Employees"
          value={`${attendanceOverview.totalWorkers}`}
          meta="Active employee records available for IE review."
          icon={Users}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Overall Attendance"
          value={`${overallAttended}/${attendanceOverview.totalWorkers}`}
          meta={`${attendanceOverview.absentWorkers} absent and ${attendanceOverview.onLeaveWorkers} on leave.`}
          icon={CheckCircle2}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Fingerprint Attended"
          value={`${fingerprintAttended}`}
          meta="Workers with a verified fingerprint attendance signal."
          icon={Fingerprint}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
        <KpiCard
          label="Face Attended"
          value={`${faceAttended}`}
          meta={`Average line attendance is ${lineAttendanceAverage}%.`}
          icon={ScanFace}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
      </section>

      <Card
        title="Employee Attendance Verification"
        subtitle="Every active employee with image, department, current line, fingerprint status, face status, and overall attendance."
        actions={<SearchField value={query} onChange={setQuery} placeholder="Search employee, line, or department" />}
      >
        <div className="ops-table-wrap" style={{ maxHeight: 620, overflow: "auto" }}>
          <table className="ops-table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Department</th>
                <th>Line</th>
                <th>Fingerprint</th>
                <th>Face</th>
                <th>Overall Attendance</th>
              </tr>
            </thead>
            <tbody>
              {filteredWorkers.map((worker) => {
                const line = findLine(lines, worker.currentLineId);
                const fingerprintVerified = worker.fingerprintVerificationStatus === "Verified";
                const faceVerified = worker.faceVerificationStatus === "Verified";

                return (
                  <tr key={worker.id}>
                    <td>
                      <WorkerChip worker={worker} />
                    </td>
                    <td>{worker.department}</td>
                    <td>{line ? `${line.name} · ${line.code}` : "Unassigned"}</td>
                    <td>
                      <StatusBadge
                        label={verificationLabel(fingerprintVerified)}
                        tone={verificationTone(fingerprintVerified)}
                      />
                    </td>
                    <td>
                      <StatusBadge
                        label={verificationLabel(faceVerified)}
                        tone={verificationTone(faceVerified)}
                      />
                    </td>
                    <td>
                      <StatusBadge
                        label={worker.attendanceStatus}
                        tone={attendanceTone(worker.attendanceStatus)}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <section className="ops-grid cols-2">
        <Card
          title="Line Attention"
          subtitle="Lowest attendance production lines for IE follow-up."
          actions={
            <Link to="/ie-line-attendance" className="ops-button ops-button-ghost">
              View Lines
            </Link>
          }
        >
          <div className="ops-list">
            {[...lines]
              .sort((a, b) => a.attendanceRate - b.attendanceRate)
              .slice(0, 5)
              .map((line) => (
                <div key={line.id} className="ops-list-item">
                  <div className="ops-item-header">
                    <div>
                      <div className="ops-item-title">{line.name}</div>
                      <div className="ops-row-subtitle">
                        {line.code} · {line.department} · {line.shift}
                      </div>
                    </div>
                    <StatusBadge
                      label={`${line.attendanceRate}%`}
                      tone={
                        line.attendanceRate >= 85
                          ? "success"
                          : line.attendanceRate >= 70
                            ? "warning"
                            : "danger"
                      }
                    />
                  </div>
                  <div className="ops-item-meta">
                    <span>{line.presentWorkers + line.lateWorkers} came</span>
                    <span>{line.absentWorkers} absent</span>
                    <span>{line.onLeaveWorkers} leave</span>
                  </div>
                </div>
              ))}
          </div>
        </Card>

        <Card
          title="Analytics Shortcut"
          subtitle="Open IE analytics for line, department, and verification trends."
          actions={
            <Link to="/ie-analytics" className="ops-button ops-button-primary">
              <BarChart3 size={15} />
              Open Analytics
            </Link>
          }
        >
          <div className="ops-stat-strip">
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Present</div>
              <div className="ops-stat-value">{attendanceOverview.presentWorkers}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Late</div>
              <div className="ops-stat-value">{attendanceOverview.lateWorkers}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Leave</div>
              <div className="ops-stat-value">{attendanceOverview.onLeaveWorkers}</div>
            </div>
            <div className="ops-stat-tile">
              <div className="ops-stat-label">Absent</div>
              <div className="ops-stat-value">{attendanceOverview.absentWorkers}</div>
            </div>
          </div>
        </Card>
      </section>
    </div>
  );
}

export default IeDashboardPage;
