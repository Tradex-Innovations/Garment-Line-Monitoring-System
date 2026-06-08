import { useMemo } from "react";
import { Link } from "react-router";
import { ArrowRight, Users } from "lucide-react";
import { useOperations } from "../operations-context";
import { Card, KpiCard, LineCard, PageHeader, StatusBadge, WorkerChip } from "../components/ops-ui";

export function IeLineAttendancePage() {
  const { lines, workers, attendanceOverview } = useOperations();

  const lineRows = useMemo(
    () =>
      [...lines].sort((a, b) => {
        if (a.attendanceRate !== b.attendanceRate) {
          return a.attendanceRate - b.attendanceRate;
        }
        return a.name.localeCompare(b.name);
      }),
    [lines]
  );

  const assignedWorkers = lineRows.reduce((sum, line) => sum + line.assignedWorkers, 0);
  const cameToday = lineRows.reduce((sum, line) => sum + line.presentWorkers + line.lateWorkers, 0);
  const lineAttendance =
    assignedWorkers === 0 ? 0 : Math.round((cameToday / assignedWorkers) * 100);
  const criticalLines = lineRows.filter((line) => line.risk === "Critical").length;

  return (
    <div className="ops-page">
      <PageHeader
        title="Line Wise Attendance"
        subtitle="Line-level assigned versus attended employees for IE balancing and operation planning."
        actions={
          <>
            <Link to="/" className="ops-button ops-button-secondary">
              IE Dashboard
            </Link>
            <Link to="/ie-analytics" className="ops-button ops-button-primary">
              Analytics
            </Link>
          </>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Lines Tracked"
          value={`${lineRows.length}`}
          meta="Active production lines in the current roster."
          icon={Users}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Assigned Workers"
          value={`${assignedWorkers}`}
          meta="Workers currently mapped to active production lines."
          icon={Users}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Came Today"
          value={`${cameToday}`}
          meta={`${attendanceOverview.absentWorkers} active workers are not currently present.`}
          icon={Users}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Line Attendance"
          value={`${lineAttendance}%`}
          meta={`${criticalLines} critical line(s) need review.`}
          icon={Users}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <Card title="Line Attendance Table" subtitle="Attendance detail by production line.">
        <div className="ops-table-wrap" style={{ maxHeight: 520, overflow: "auto" }}>
          <table className="ops-table">
            <thead>
              <tr>
                <th>Line</th>
                <th>Style</th>
                <th>Assigned</th>
                <th>Came</th>
                <th>Late</th>
                <th>Leave</th>
                <th>Absent</th>
                <th>Attendance</th>
                <th>Risk</th>
              </tr>
            </thead>
            <tbody>
              {lineRows.map((line) => (
                <tr key={line.id}>
                  <td>
                    <div className="ops-row-title">{line.name}</div>
                    <div className="ops-row-subtitle">
                      {line.code} · {line.department} · {line.shift}
                    </div>
                  </td>
                  <td>{line.allocatedStyle || "Unassigned"}</td>
                  <td>{line.assignedWorkers}</td>
                  <td>{line.presentWorkers + line.lateWorkers}</td>
                  <td>{line.lateWorkers}</td>
                  <td>{line.onLeaveWorkers}</td>
                  <td>{line.absentWorkers}</td>
                  <td>
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
                  </td>
                  <td>
                    <StatusBadge
                      label={line.risk}
                      tone={
                        line.risk === "Stable"
                          ? "success"
                          : line.risk === "Watch"
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

      <section className="ops-grid cols-3">
        {lineRows.map((line) => {
          const lineWorkers = workers.filter((worker) => worker.currentLineId === line.id);
          return (
            <Link key={line.id} to={`/ie-line-attendance/${line.id}`} className="ops-card-link">
              <LineCard
                line={line}
                actions={
                  <span className="ops-button ops-button-secondary">
                    View Machine Spots
                    <ArrowRight size={15} />
                  </span>
                }
              >
                <div className="ops-list" style={{ marginTop: 16, maxHeight: 260, overflow: "auto" }}>
                  {lineWorkers.slice(0, 8).map((worker) => (
                    <div key={worker.id} className="ops-list-item">
                      <div className="ops-item-header">
                        <WorkerChip worker={worker} />
                        <StatusBadge
                          label={worker.attendanceStatus}
                          tone={
                            worker.attendanceStatus === "Present"
                              ? "success"
                              : worker.attendanceStatus === "Late"
                                ? "warning"
                                : worker.attendanceStatus === "On Leave"
                                  ? "info"
                                  : "danger"
                          }
                        />
                      </div>
                    </div>
                  ))}
                  {lineWorkers.length === 0 ? (
                    <div className="ops-row-subtitle">No employees currently assigned.</div>
                  ) : null}
                </div>
              </LineCard>
            </Link>
          );
        })}
      </section>
    </div>
  );
}

export default IeLineAttendancePage;
