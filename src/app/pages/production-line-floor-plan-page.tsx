import { Link, Navigate, useParams } from "react-router";
import { ArrowLeft, ClipboardList, Gauge, Users } from "lucide-react";
import { LineFloorPlan } from "../components/line-floor-plan";
import { Card, KpiCard, PageHeader } from "../components/ops-ui";
import { useOperations } from "../operations-context";

export function ProductionLineFloorPlanPage() {
  const { lineId } = useParams();
  const { lines, workers } = useOperations();
  const line = lines.find((item) => item.id === lineId);

  if (!line) {
    return <Navigate to="/production-lines" replace />;
  }

  const lineWorkers = workers
    .filter((worker) => worker.currentLineId === line.id)
    .sort((a, b) => a.fullName.localeCompare(b.fullName));
  const presentWorkers = line.presentWorkers + line.lateWorkers;

  return (
    <div className="ops-page ops-floor-page">
      <PageHeader
        title={`${line.name} Floor Plan`}
        subtitle={`${line.code} · Style ${line.allocatedStyle || "Unassigned"} · ${line.shift} · ${line.supervisor}`}
        actions={
          <Link to={`/production-lines/${line.id}`} className="ops-button ops-button-secondary">
            <ArrowLeft size={16} />
            Back to Line
          </Link>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Assigned"
          value={`${line.assignedWorkers}`}
          meta="Workers currently mapped to this production line."
          icon={Users}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Came Today"
          value={`${presentWorkers}`}
          meta="Present and late workers from attendance records."
          icon={ClipboardList}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Attendance"
          value={`${line.attendanceRate}%`}
          meta={`${presentWorkers} of ${line.assignedWorkers} assigned workers came today.`}
          icon={Gauge}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
      </section>

      <Card
        title="Line Floor Plan"
        subtitle="Large view of current assigned employees grouped by actual MO, helpers, and other assigned staff."
      >
        <LineFloorPlan workers={lineWorkers} large />
      </Card>
    </div>
  );
}

export default ProductionLineFloorPlanPage;
