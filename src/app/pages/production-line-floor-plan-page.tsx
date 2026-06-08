import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router";
import { ArrowLeft, ClipboardList, Gauge, Users } from "lucide-react";
import { LineFloorPlan } from "../components/line-floor-plan";
import {
  deleteLinePositionAssignmentFromBackend,
  getLineAutomaticRecommendationsFromBackend,
  getSkillMatrixFromBackend,
  saveLinePositionAssignmentFromBackend,
} from "@/lib/backend/skill-matrix-api";
import type {
  LineAutomaticRecommendation,
  LinePositionAssignment,
  SkillMatrixSnapshot,
} from "@/types/skill-matrix";
import { useAuth } from "../auth";
import { Card, KpiCard, PageHeader, StatusBadge } from "../components/ops-ui";
import { useOperations } from "../operations-context";

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

export function ProductionLineFloorPlanPage() {
  const { lineId } = useParams();
  const { canDo } = useAuth();
  const { lines, workers } = useOperations();
  const [matrix, setMatrix] = useState<SkillMatrixSnapshot>(EMPTY_MATRIX);
  const [recommendations, setRecommendations] = useState<LineAutomaticRecommendation[]>([]);
  const [matrixError, setMatrixError] = useState<string | null>(null);
  const [assignmentMessage, setAssignmentMessage] = useState<string | null>(null);
  const [assigningEmployeeId, setAssigningEmployeeId] = useState<string | null>(null);
  const line = lines.find((item) => item.id === lineId);

  const loadSkillData = async (targetLineId: string) => {
    setMatrixError(null);
    try {
      const [nextMatrix, nextRecommendations] = await Promise.all([
        getSkillMatrixFromBackend(),
        getLineAutomaticRecommendationsFromBackend(targetLineId),
      ]);
      setMatrix(nextMatrix);
      setRecommendations(nextRecommendations.recommendations);
    } catch (error) {
      setMatrixError(error instanceof Error ? error.message : String(error));
    }
  };

  useEffect(() => {
    if (lineId) {
      void loadSkillData(lineId);
    }
  }, [lineId]);

  if (!line) {
    return <Navigate to="/production-lines" replace />;
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

  const assignPosition = async (
    worker: (typeof lineWorkers)[number],
    lineOperationId: string,
    assignment?: LinePositionAssignment
  ) => {
    setAssigningEmployeeId(worker.id);
    setAssignmentMessage(null);
    setMatrixError(null);
    try {
      if (!lineOperationId) {
        if (assignment) {
          const nextMatrix = await deleteLinePositionAssignmentFromBackend(assignment.id);
          setMatrix(nextMatrix);
          setAssignmentMessage(`${worker.fullName} machine mapping removed.`);
        }
      } else {
        const nextMatrix = await saveLinePositionAssignmentFromBackend({
          id: assignment?.id,
          employeeId: worker.id,
          lineOperationId,
          isActive: true,
        });
        setMatrix(nextMatrix);
        setAssignmentMessage(`${worker.fullName} floor-plan machine updated.`);
      }
      const nextRecommendations = await getLineAutomaticRecommendationsFromBackend(line.id);
      setRecommendations(nextRecommendations.recommendations);
    } catch (error) {
      setMatrixError(error instanceof Error ? error.message : String(error));
    } finally {
      setAssigningEmployeeId(null);
    }
  };

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
        subtitle="Large view of assigned employees, machine operation mappings, and automatic replacement recommendations for absent machine operators."
      >
        {matrixError ? <div className="ops-alert-banner tone-danger">{matrixError}</div> : null}
        {assignmentMessage ? <div className="ops-alert-banner tone-info">{assignmentMessage}</div> : null}
        {!lineOperations.length ? (
          <div className="ops-alert-banner tone-warning">
            Add machine operation mappings for this line from the Skill Matrix page before assigning employees to machines.
          </div>
        ) : null}
        <div className="ops-floor-plan-summary">
          <StatusBadge label={`${lineOperations.length} configured machine(s)`} tone="info" />
          <StatusBadge label={`${recommendations.length} automatic recommendation(s)`} tone={recommendations.length ? "warning" : "success"} />
        </div>
        <LineFloorPlan
          workers={lineWorkers}
          large
          lineOperations={lineOperations}
          positionAssignments={linePositionAssignments}
          recommendations={recommendations}
          canAssignOperations={canDo("assignLine")}
          assigningEmployeeId={assigningEmployeeId}
          onAssignPosition={(worker, lineOperationId, assignment) =>
            void assignPosition(worker, lineOperationId, assignment)
          }
        />
      </Card>
    </div>
  );
}

export default ProductionLineFloorPlanPage;
