import { Link } from "react-router";
import {
  AlertTriangle,
  ArrowRightLeft,
  Factory,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";
import { useOperations } from "../operations-context";
import {
  AlertItem,
  Button,
  Card,
  KpiCard,
  MetricTile,
  PageHeader,
  StatusBadge,
  formatDateTime,
  validationTone,
} from "../components/ops-ui";

const validationStatuses = [
  "Fully Validated",
  "Pending Validation",
  "Face Only",
  "Fingerprint Only",
  "Time Mismatch",
  "Unresolved Exception",
] as const;

export function DashboardPage() {
  const {
    workers,
    lines,
    validationRecords,
    alerts,
    smartInsights,
    auditLogs,
  } = useOperations();

  const todayValidations = validationRecords.filter((item) => item.date === "2026-04-03");
  const fullyValidated = todayValidations.filter(
    (item) => item.status === "Fully Validated"
  ).length;
  const unresolvedAlerts = alerts.filter((item) => item.status !== "Resolved");
  const activeLines = lines.filter((line) => line.status === "Active").length;
  const totalOutput = lines.reduce((sum, line) => sum + line.output, 0);
  const pendingPool = workers.filter(
    (worker) =>
      worker.currentStatus === "Pending Assignment" ||
      worker.finalValidationStatus !== "Fully Validated"
  );
  const lineWatchlist = lines
    .filter((line) => line.risk !== "Stable")
    .sort((a, b) => b.actualManpower - a.actualManpower);

  return (
    <div className="ops-page">
      <PageHeader
        title="Operations Dashboard"
        subtitle="Live factory overview for attendance validation, line readiness, workforce balancing, and escalations."
        actions={
          <>
            <Link to="/validation-center" className="ops-button ops-button-secondary">
              Validation Queue
            </Link>
            <Link to="/line-assignment" className="ops-button ops-button-primary">
              Workforce Actions
            </Link>
          </>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Workforce Present"
          value={`${workers.filter((worker) => worker.attendanceStatus === "Present" || worker.attendanceStatus === "Late").length}`}
          meta="Tracked staff available for Shift A today."
          icon={Users}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Validated Today"
          value={`${fullyValidated}/${todayValidations.length}`}
          meta="Fully reconciled face and fingerprint records."
          icon={ShieldCheck}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Active Alerts"
          value={`${unresolvedAlerts.length}`}
          meta="Open operational exceptions requiring attention."
          icon={AlertTriangle}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
        <KpiCard
          label="Factory Output"
          value={`${totalOutput}`}
          meta={`${activeLines} active lines currently contributing output.`}
          icon={Factory}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <section className="ops-split">
        <Card
          title="Validation Readiness"
          subtitle="Status mix across face recognition and fingerprint reconciliation for Friday, April 3, 2026."
          actions={
            <Link to="/validation-center" className="ops-button ops-button-ghost">
              Open Validation Center
            </Link>
          }
        >
          <div className="ops-stat-strip">
            {validationStatuses.map((status) => {
              const count = todayValidations.filter((item) => item.status === status).length;
              return <MetricTile key={status} label={status} value={`${count}`} />;
            })}
          </div>

          <div className="ops-card-divider" />

          <div className="ops-list" style={{ marginTop: 18 }}>
            {todayValidations
              .filter((item) => item.status !== "Fully Validated")
              .slice(0, 4)
              .map((item) => (
                <div key={item.id} className="ops-list-item">
                  <div className="ops-item-header">
                    <div>
                      <div className="ops-item-title">{item.workerName}</div>
                      <div className="ops-row-subtitle">
                        {item.employeeId} · {item.department}
                      </div>
                    </div>
                    <StatusBadge
                      label={item.status}
                      tone={validationTone(item.status)}
                    />
                  </div>
                  <div className="ops-item-meta">
                    <span>Face: {item.faceEventTime ? formatDateTime(item.faceEventTime) : "Missing"}</span>
                    <span>
                      Fingerprint:{" "}
                      {item.fingerprintEventTime
                        ? formatDateTime(item.fingerprintEventTime)
                        : "Missing"}
                    </span>
                    <span>Confidence {item.confidenceScore}%</span>
                  </div>
                </div>
              ))}
          </div>
        </Card>

        <Card
          title="Smart Insights"
          subtitle="Rule-based advisory prompts to keep manpower and attendance stable."
          actions={
            <StatusBadge
              label={`${smartInsights.length} active insights`}
              tone="violet"
            />
          }
        >
          <div className="ops-insight-grid">
            {smartInsights.map((insight) => (
              <article key={insight.id} className="ops-insight-card">
                <div className="ops-item-header">
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <Sparkles size={18} color="var(--ops-violet)" />
                    <div className="ops-item-title">{insight.title}</div>
                  </div>
                  <StatusBadge
                    label={insight.severity.toUpperCase()}
                    tone={
                      insight.severity === "critical"
                        ? "danger"
                        : insight.severity === "warning"
                          ? "warning"
                          : "info"
                    }
                  />
                </div>
                <div className="ops-item-description">{insight.description}</div>
                <div className="ops-item-meta">
                  <span>{insight.recommendation}</span>
                </div>
              </article>
            ))}
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card
          title="Line Health Watchlist"
          subtitle="Supervisor view of lines that need manpower balancing or issue follow-up."
          actions={
            <Link to="/production-lines" className="ops-button ops-button-ghost">
              View Floor Map
            </Link>
          }
        >
          <div className="ops-list">
            {lineWatchlist.map((line) => (
              <div key={line.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div>
                    <div className="ops-item-title">{line.name}</div>
                    <div className="ops-row-subtitle">
                      {line.department} · Supervisor {line.supervisor}
                    </div>
                  </div>
                  <StatusBadge
                    label={`${line.actualManpower}/${line.targetManpower} manpower`}
                    tone={line.risk === "Critical" ? "danger" : "warning"}
                  />
                </div>
                <div className="ops-item-meta">
                  <span>Efficiency {line.efficiency}%</span>
                  <span>
                    Output {line.output}/{line.targetOutput}
                  </span>
                  <span>{line.issue || "No major issue logged."}</span>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card
          title="Alerts Requiring Action"
          subtitle="Escalations still open for supervisors and HR."
          actions={
            <Link to="/alerts-center" className="ops-button ops-button-ghost">
              Open Alerts Center
            </Link>
          }
        >
          <div className="ops-list">
            {unresolvedAlerts.slice(0, 3).map((alert) => (
              <AlertItem
                key={alert.id}
                priority={alert.priority}
                title={alert.title}
                description={alert.description}
                meta={
                  <>
                    <span>{formatDateTime(alert.createdAt)}</span>
                    <span>{alert.type}</span>
                  </>
                }
              />
            ))}
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card
          title="Workforce Pool"
          subtitle="Validated and pending-assignment workers ready for supervisor action."
          actions={
            <Link to="/line-assignment" className="ops-button ops-button-ghost">
              <ArrowRightLeft size={15} />
              Reassign Staff
            </Link>
          }
        >
          <div className="ops-worker-grid">
            {pendingPool.slice(0, 4).map((worker) => (
              <div key={worker.id} className="ops-worker-card">
                <div className="ops-item-header">
                  <div>
                    <div className="ops-item-title">{worker.fullName}</div>
                    <div className="ops-row-subtitle">
                      {worker.employeeId} · {worker.roleTitle}
                    </div>
                  </div>
                  <StatusBadge
                    label={worker.currentStatus}
                    tone={worker.currentStatus === "Pending Assignment" ? "info" : "warning"}
                  />
                </div>
                <div className="ops-item-description">
                  Skills: {worker.skills.join(", ")}
                </div>
                <div className="ops-item-actions">
                  <Link
                    to={`/workers/${worker.id}`}
                    className="ops-button ops-button-secondary"
                  >
                    View Profile
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card
          title="Recent Activity"
          subtitle="Latest operational changes captured in the audit log."
          actions={
            <Link to="/audit-log" className="ops-button ops-button-ghost">
              Open Audit Log
            </Link>
          }
        >
          <div className="ops-list">
            {auditLogs.slice(0, 5).map((entry) => (
              <div key={entry.id} className="ops-list-item">
                <div className="ops-item-header">
                  <div>
                    <div className="ops-item-title">{entry.actionType}</div>
                    <div className="ops-row-subtitle">{entry.targetEntity}</div>
                  </div>
                  <StatusBadge label={entry.user} tone="neutral" />
                </div>
                <div className="ops-item-meta">
                  <span>{formatDateTime(entry.timestamp)}</span>
                  <span>
                    {entry.oldValue} → {entry.newValue}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </section>
    </div>
  );
}

export default DashboardPage;
