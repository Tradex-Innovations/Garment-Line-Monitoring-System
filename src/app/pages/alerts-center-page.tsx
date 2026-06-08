import { useMemo, useState } from "react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import type { AlertState } from "../types";
import {
  AlertItem,
  Button,
  Card,
  EmptyState,
  KpiCard,
  PageHeader,
  StatusBadge,
  formatDateTime,
  priorityTone,
} from "../components/ops-ui";
import { AlertTriangle, BellDot, CheckCheck, ShieldAlert } from "lucide-react";

export function AlertsCenterPage() {
  const { currentUser, users } = useAuth();
  const { alerts, updateAlertStatus, assignAlert } = useOperations();
  const [priorityFilter, setPriorityFilter] = useState("All");
  const [statusFilter, setStatusFilter] = useState("All");
  const [feedback, setFeedback] = useState<string | null>(null);

  const handleStatusChange = async (alertId: string, status: AlertState) => {
    const result = await updateAlertStatus({
      alertId,
      status,
      actor: currentUser.name,
    });
    setFeedback(result.message);
  };

  const handleAssign = async (alertId: string, assignedToUserId: string) => {
    const result = await assignAlert({
      alertId,
      assignedToUserId,
      actor: currentUser.name,
    });
    setFeedback(result.message);
  };

  const filteredAlerts = alerts.filter((alert) => {
    const matchesPriority = priorityFilter === "All" || alert.priority === priorityFilter;
    const matchesStatus = statusFilter === "All" || alert.status === statusFilter;
    return matchesPriority && matchesStatus;
  });

  const counts = useMemo(
    () => ({
      critical: alerts.filter((item) => item.priority === "critical").length,
      high: alerts.filter((item) => item.priority === "high").length,
      open: alerts.filter((item) => item.status === "Open").length,
      resolved: alerts.filter((item) => item.status === "Resolved").length,
    }),
    [alerts]
  );

  return (
    <div className="ops-page">
      <PageHeader
        title="Alerts & Exceptions Center"
        subtitle="Dedicated operational alert queue with assignment, read state, resolution tracking, and recent history."
        actions={
          <>
            <StatusBadge label={`${counts.open} open`} tone="danger" />
            <StatusBadge label={`${counts.resolved} resolved`} tone="success" />
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
          label="Critical"
          value={`${counts.critical}`}
          meta="Immediate operational attention required."
          icon={AlertTriangle}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
        <KpiCard
          label="High Priority"
          value={`${counts.high}`}
          meta="Monitor closely and clear inside this shift."
          icon={ShieldAlert}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Open Alerts"
          value={`${counts.open}`}
          meta="Still active in supervisor or HR workload."
          icon={BellDot}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Resolved"
          value={`${counts.resolved}`}
          meta="Closed alerts retained for history and audit."
          icon={CheckCheck}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
      </section>

      <div className="ops-filter-bar">
        <select className="ops-select" value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)}>
          <option value="All">All priorities</option>
          <option value="critical">Critical</option>
          <option value="high">High</option>
          <option value="medium">Medium</option>
          <option value="low">Low</option>
        </select>
        <select className="ops-select" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
          <option value="All">All statuses</option>
          <option value="Open">Open</option>
          <option value="Read">Read</option>
          <option value="Resolved">Resolved</option>
        </select>
      </div>

      {filteredAlerts.length ? (
        <div className="ops-list">
          {filteredAlerts.map((alert) => (
            <AlertItem
              key={alert.id}
              priority={alert.priority}
              title={alert.title}
              description={alert.description}
              meta={
                <>
                  <span>{formatDateTime(alert.createdAt)}</span>
                  <span>{alert.type}</span>
                  <span>{alert.status}</span>
                </>
              }
              actions={
                <>
                  {alert.status !== "Read" ? (
                    <Button
                      tone="secondary"
                      onClick={() => void handleStatusChange(alert.id, "Read")}
                    >
                      Mark as read
                    </Button>
                  ) : null}
                  {alert.status !== "Resolved" ? (
                    <Button
                      tone="primary"
                      onClick={() => void handleStatusChange(alert.id, "Resolved")}
                    >
                      Resolve
                    </Button>
                  ) : null}
                  <select
                    className="ops-select"
                    style={{ maxWidth: 220 }}
                    value={alert.assignedToUserId || ""}
                    onChange={(event) => void handleAssign(alert.id, event.target.value)}
                  >
                    <option value="" disabled>
                      Assign to
                    </option>
                    {users.map((user) => (
                      <option key={user.id} value={user.id}>
                        {user.name}
                      </option>
                    ))}
                  </select>
                </>
              }
            />
          ))}
        </div>
      ) : (
        <EmptyState
          title="No alerts matched the current filters"
          description="Change the priority or status filter to inspect another part of the alert queue."
        />
      )}

      <Card title="Alert History Log" subtitle="Latest alert actions and ownership changes.">
        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Alert</th>
                <th>Priority</th>
                <th>Latest action</th>
                <th>Assigned</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((alert) => (
                <tr key={alert.id}>
                  <td>
                    <div className="ops-row-title">{alert.title}</div>
                    <div className="ops-row-subtitle">{formatDateTime(alert.createdAt)}</div>
                  </td>
                  <td>
                    <StatusBadge label={alert.priority.toUpperCase()} tone={priorityTone(alert.priority)} />
                  </td>
                  <td>{alert.history[0]?.action || "No history"}</td>
                  <td>{users.find((user) => user.id === alert.assignedToUserId)?.name || "Unassigned"}</td>
                  <td>{alert.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

export default AlertsCenterPage;
