import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router";
import { ArrowLeft, ClipboardList, Cpu, Gauge, Users } from "lucide-react";
import { LineFloorPlan } from "../components/line-floor-plan";
import { Card, KpiCard, PageHeader, StatusBadge } from "../components/ops-ui";
import { useOperations } from "../operations-context";
import { getSkillMatrixFromBackend } from "@/lib/backend/skill-matrix-api";
import type { SkillMatrixSnapshot } from "@/types/skill-matrix";

const EMPTY_MATRIX: SkillMatrixSnapshot = {
  operations: [],
  lines: [],
  employees: [],
  lineOperations: [],
  linePositionAssignments: [],
  stylePlans: [],
  stylePlanMachines: [],
  lineStyleSchedules: [],
  employeeSkills: [],
};

export function IeLineFloorPlanPage() {
  const { lineId } = useParams();
  const { lines, workers } = useOperations();
  const [matrix, setMatrix] = useState<SkillMatrixSnapshot>(EMPTY_MATRIX);
  const [matrixError, setMatrixError] = useState<string | null>(null);
  const line = lines.find((item) => item.id === lineId);

  useEffect(() => {
    let isMounted = true;

    async function loadMachineSpots() {
      setMatrixError(null);
      try {
        const nextMatrix = await getSkillMatrixFromBackend();
        if (isMounted) {
          setMatrix(nextMatrix);
        }
      } catch (error) {
        if (isMounted) {
          setMatrixError(error instanceof Error ? error.message : String(error));
        }
      }
    }

    if (lineId) {
      void loadMachineSpots();
    }

    return () => {
      isMounted = false;
    };
  }, [lineId]);

  if (!line) {
    return <Navigate to="/ie-line-attendance" replace />;
  }

  const lineWorkers = workers
    .filter((worker) => worker.currentLineId === line.id)
    .sort((a, b) => a.fullName.localeCompare(b.fullName));
  const presentWorkers = line.presentWorkers + line.lateWorkers;
  const lineOperations = matrix.lineOperations
    .filter((operation) => operation.productionLineId === line.id)
    .sort((a, b) => a.sequenceNo - b.sequenceNo || a.positionLabel.localeCompare(b.positionLabel));
  const linePositionAssignments = matrix.linePositionAssignments.filter(
    (assignment) => assignment.productionLineId === line.id
  );

  return (
    <div className="ops-page ops-floor-page">
      <PageHeader
        title={`${line.name} Machine Spots Plan`}
        subtitle={`${line.code} · Style ${line.allocatedStyle || "Unassigned"} · ${line.department} · ${line.shift} · ${line.supervisor}`}
        actions={
          <Link to="/ie-line-attendance" className="ops-button ops-button-secondary">
            <ArrowLeft size={16} />
            Back to Lines
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
        <KpiCard
          label="Machine Spots"
          value={`${lineOperations.length}`}
          meta="Configured operation positions available for this line."
          icon={Cpu}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <Card
        title="Line Machine Spots Plan"
        subtitle="Read-only line view of assigned employees and configured machine operation spots."
      >
        {matrixError ? <div className="ops-alert-banner tone-danger">{matrixError}</div> : null}
        {!matrixError && !lineOperations.length ? (
          <div className="ops-alert-banner tone-warning">
            No machine operation mappings have been configured for this line yet.
          </div>
        ) : null}
        <div className="ops-floor-plan-summary">
          <StatusBadge label={`${lineOperations.length} configured machine(s)`} tone="info" />
          <StatusBadge label="Read only" tone="success" />
        </div>
        <LineFloorPlan
          workers={lineWorkers}
          large
          lineOperations={lineOperations}
          positionAssignments={linePositionAssignments}
          recommendations={[]}
          showRecommendations={false}
          canAssignOperations={false}
        />
      </Card>
    </div>
  );
}

export default IeLineFloorPlanPage;
