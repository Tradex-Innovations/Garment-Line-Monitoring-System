import { useOperations } from "../operations-context";
import { Card, PageHeader } from "../components/ops-ui";

export function AuditLogPage() {
  const { auditLogs } = useOperations();

  return (
    <div className="ops-page">
      <PageHeader
        title="Audit Log"
        subtitle="Operational change history covering worker assignments, transfers, validation actions, settings updates, and alert resolution."
      />

      <Card title="Activity History" subtitle="Every major operational change is recorded with user, timestamp, target entity, and value diff.">
        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Action Type</th>
                <th>User</th>
                <th>Timestamp</th>
                <th>Target Entity</th>
                <th>Old Value</th>
                <th>New Value</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.map((entry) => (
                <tr key={entry.id}>
                  <td>{entry.actionType}</td>
                  <td>{entry.user}</td>
                  <td>{entry.timestamp.replace("T", " ").slice(0, 16)}</td>
                  <td>{entry.targetEntity}</td>
                  <td>{entry.oldValue}</td>
                  <td>{entry.newValue}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

export default AuditLogPage;
