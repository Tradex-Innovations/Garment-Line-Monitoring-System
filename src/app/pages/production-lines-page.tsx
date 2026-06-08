import { Link } from "react-router";
import { BriefcaseBusiness, Percent, Users } from "lucide-react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import {
  Card,
  KpiCard,
  LineCard,
  PageHeader,
} from "../components/ops-ui";

export function ProductionLinesPage() {
  const { canDo } = useAuth();
  const { lines } = useOperations();

  const assignedWorkers = lines.reduce((sum, line) => sum + line.assignedWorkers, 0);
  const presentWorkers = lines.reduce(
    (sum, line) => sum + line.presentWorkers + line.lateWorkers,
    0
  );
  const overallAttendancePercentage =
    assignedWorkers > 0 ? Math.round((presentWorkers / assignedWorkers) * 100) : 0;

  return (
    <div className="ops-page">
      <PageHeader
        title="Production Lines"
        subtitle="Attendance-focused line view with backend-generated efficiency and incentive metrics attached to each line."
        actions={
          canDo("assignLine") ? (
            <Link to="/line-assignment" className="ops-button ops-button-primary">
              Manage Line Assignments
            </Link>
          ) : null
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Lines Tracked"
          value={`${lines.length}`}
          meta="Production lines currently available in the roster."
          icon={BriefcaseBusiness}
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
          value={`${presentWorkers}`}
          meta="Assigned workers who clocked in through the fingerprint attendance source."
          icon={Users}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Line Attendance"
          value={`${overallAttendancePercentage}%`}
          meta="Overall attendance across all assigned production line workers."
          icon={Percent}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <Card
        title="Line Roster"
        subtitle="Click any production line to open its full staff, allocation, metric, and audit details."
      >
        <div className="ops-line-grid">
          {lines.map((line) => (
            <Link key={line.id} to={`/production-lines/${line.id}`} className="ops-card-link">
              <LineCard line={line} />
            </Link>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default ProductionLinesPage;
