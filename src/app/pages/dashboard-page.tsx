import { useMemo } from "react";
import { Link } from "react-router";
import { AlertTriangle, Clock3, ShieldCheck, Users } from "lucide-react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import { IeDashboardPage } from "./ie-dashboard-page";
import {
  AlertItem,
  Card,
  KpiCard,
  PageHeader,
  StatusBadge,
} from "../components/ops-ui";

function formatAttendanceDate(value: string) {
  if (!value) {
    return "No fingerprint attendance date available yet";
  }

  return new Date(value).toLocaleDateString("en-GB", {
    weekday: "long",
    day: "2-digit",
    month: "long",
    year: "numeric",
  });
}

export function DashboardPage() {
  const { currentUser } = useAuth();
  const { attendanceOverview, departmentAttendance, alerts, lines } = useOperations();

  if (currentUser.role === "ie") {
    return <IeDashboardPage />;
  }

  const latestAttendanceDateLabel = formatAttendanceDate(attendanceOverview.attendanceDate);
  const clockedInToday = attendanceOverview.presentWorkers + attendanceOverview.lateWorkers;
  const openAlerts = alerts.filter((alert) => alert.status !== "Resolved");
  const lineCoverage = useMemo(
    () =>
      [...lines]
        .sort((a, b) => {
          if (a.attendanceRate !== b.attendanceRate) {
            return a.attendanceRate - b.attendanceRate;
          }
          return a.name.localeCompare(b.name);
        })
        .slice(0, 6),
    [lines]
  );

  return (
    <div className="ops-page">
      <PageHeader
        title="Attendance Dashboard"
        subtitle="Fingerprint-first operations overview with current headcount, department attendance, and line readiness."
        actions={
          <>
            <Link to="/production-lines" className="ops-button ops-button-secondary">
              Production Lines
            </Link>
            <Link to="/reports" className="ops-button ops-button-primary">
              Open Reports
            </Link>
          </>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Total Workforce"
          value={`${attendanceOverview.totalWorkers}`}
          meta={`Live roster being tracked for ${latestAttendanceDateLabel}.`}
          icon={Users}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Clocked In Today"
          value={`${clockedInToday}/${attendanceOverview.totalWorkers}`}
          meta={`${attendanceOverview.lateWorkers} late arrivals captured through fingerprint attendance.`}
          icon={Clock3}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="On Leave"
          value={`${attendanceOverview.onLeaveWorkers}`}
          meta="Workers currently marked as leave from the fingerprint attendance source."
          icon={ShieldCheck}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
        <KpiCard
          label="Open Alerts"
          value={`${openAlerts.length}`}
          meta={`${attendanceOverview.absentWorkers} workers are still not clocked in for the latest attendance date.`}
          icon={AlertTriangle}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
      </section>

      <section className="ops-grid cols-2">
        <Card
          title="Department Attendance"
          subtitle={`Current attendance by department for ${latestAttendanceDateLabel}.`}
          actions={
            <StatusBadge
              label={`${departmentAttendance.length} departments`}
              tone="info"
            />
          }
        >
          <div className="ops-table-wrap">
            <table className="ops-table">
              <thead>
                <tr>
                  <th>Department</th>
                  <th>Total Staff</th>
                  <th>Came Today</th>
                  <th>Late</th>
                  <th>Leave</th>
                  <th>Absent</th>
                  <th>Attendance</th>
                </tr>
              </thead>
              <tbody>
                {departmentAttendance.map((department) => (
                  <tr key={department.department}>
                    <td>
                      <div className="ops-row-title">{department.department}</div>
                    </td>
                    <td>{department.totalWorkers}</td>
                    <td>{department.presentWorkers + department.lateWorkers}</td>
                    <td>{department.lateWorkers}</td>
                    <td>{department.onLeaveWorkers}</td>
                    <td>{department.absentWorkers}</td>
                    <td>
                      <StatusBadge
                        label={`${department.attendanceRate}%`}
                        tone={
                          department.attendanceRate >= 85
                            ? "success"
                            : department.attendanceRate >= 70
                              ? "warning"
                              : "danger"
                        }
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card
          title="Line Attendance Snapshot"
          subtitle="Assigned versus attended workers for the lines that need the most attention today."
          actions={
            <Link to="/production-lines" className="ops-button ops-button-ghost">
              View All Lines
            </Link>
          }
        >
          <div className="ops-list">
            {lineCoverage.map((line) => (
              <div key={line.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div>
                    <div className="ops-item-title">{line.name}</div>
                    <div className="ops-row-subtitle">
                      {line.department} · {line.shift}
                    </div>
                  </div>
                  <StatusBadge
                    label={`${line.presentWorkers + line.lateWorkers}/${line.assignedWorkers} came`}
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
                  <span>{line.supervisor}</span>
                  <span>{line.onLeaveWorkers} on leave</span>
                  <span>{line.absentWorkers} absent</span>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card
          title="Attendance Overview"
          subtitle="Quick roll-up of the latest fingerprint attendance snapshot."
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

        <Card
          title="Alerts Center"
          subtitle="Open exceptions that still need follow-up from supervisors or HR."
          actions={
            <Link to="/alerts-center" className="ops-button ops-button-ghost">
              Open Alerts
            </Link>
          }
        >
          <div className="ops-list">
            {openAlerts.slice(0, 4).map((alert) => (
              <AlertItem
                key={alert.id}
                priority={alert.priority}
                title={alert.title}
                description={alert.description}
                meta={<span>{alert.type}</span>}
              />
            ))}
          </div>
        </Card>
      </section>
    </div>
  );
}

export default DashboardPage;
