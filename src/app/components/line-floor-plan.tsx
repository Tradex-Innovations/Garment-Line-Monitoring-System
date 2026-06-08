import { Image } from "lucide-react";
import { Link } from "react-router";
import { StatusBadge, getInitials } from "./ops-ui";
import type { WorkerProfile } from "../types";
import type {
  LineAutomaticRecommendation,
  LineOperation,
  LinePositionAssignment,
  SkillCandidate,
} from "@/types/skill-matrix";

export function isHelperWorker(worker: WorkerProfile) {
  const role = `${worker.roleTitle} ${worker.department}`.toLowerCase();
  return role.includes("helper") || role.includes("hel") || role.includes("support");
}

export function isMachineOperator(worker: WorkerProfile) {
  const role = `${worker.roleTitle} ${worker.department}`.toLowerCase();
  return (
    !isHelperWorker(worker) &&
    (role.includes("operator") ||
      role.includes("machine") ||
      role.includes("sewing") ||
      /\bmo\b/.test(role))
  );
}

export function isPresentWorker(worker: WorkerProfile) {
  return worker.attendanceStatus === "Present" || worker.attendanceStatus === "Late";
}

function FloorEmployeeShape({
  worker,
  group,
  assignment,
  lineOperations,
  recommendation,
  showRecommendations = true,
  canAssignOperations = false,
  assigning = false,
  onAssignPosition,
}: {
  worker: WorkerProfile;
  group: "mo" | "helper" | "other";
  assignment?: LinePositionAssignment;
  lineOperations: LineOperation[];
  recommendation?: LineAutomaticRecommendation;
  showRecommendations?: boolean;
  canAssignOperations?: boolean;
  assigning?: boolean;
  onAssignPosition?: (
    worker: WorkerProfile,
    lineOperationId: string,
    assignment?: LinePositionAssignment
  ) => void;
}) {
  const present = isPresentWorker(worker);
  const bestCandidate = recommendation?.bestCandidate || undefined;
  return (
    <article
      className={`ops-floor-employee group-${group} ${present ? "is-present" : "is-absent"}`}
      title={`${worker.fullName} · ${worker.employeeId} · ${worker.attendanceStatus}`}
    >
      <Link to={`/workers/${worker.id}`} className="ops-floor-employee-main">
        {worker.photoUrl ? (
          <img src={worker.photoUrl} alt={worker.fullName} className="ops-floor-employee-photo" />
        ) : (
          <span className="ops-floor-employee-photo ops-floor-employee-photo-placeholder">
            <Image size={13} />
            <span>{getInitials(worker.fullName)}</span>
          </span>
        )}
        <span className="ops-floor-employee-name">{worker.fullName}</span>
        <span className="ops-floor-employee-code">{worker.employeeId}</span>
      </Link>

      <div className="ops-floor-position-meta">
        <span className="ops-floor-position-tag">
          {assignment?.positionLabel || "No machine"}
        </span>
        <span className="ops-floor-operation-name">
          {assignment?.operationName || "No operation assigned"}
        </span>
      </div>

      {canAssignOperations ? (
        <select
          className="ops-floor-position-select"
          value={assignment?.lineOperationId || ""}
          disabled={assigning}
          onChange={(event) => onAssignPosition?.(worker, event.target.value, assignment)}
          aria-label={`Assign machine operation for ${worker.fullName}`}
        >
          <option value="">Select machine</option>
          {lineOperations.map((operation) => (
            <option key={operation.id} value={operation.id}>
              {operation.positionLabel} · {operation.operationName}
            </option>
          ))}
        </select>
      ) : null}

      {showRecommendations && !present && assignment ? (
        <FloorRecommendation bestCandidate={bestCandidate} />
      ) : null}
    </article>
  );
}

function FloorRecommendation({ bestCandidate }: { bestCandidate?: SkillCandidate }) {
  if (!bestCandidate) {
    return <div className="ops-floor-recommendation is-empty">No replacement match yet</div>;
  }

  return (
    <Link to={`/workers/${bestCandidate.employeeId}`} className="ops-floor-recommendation">
      <span className="ops-floor-recommendation-label">Recommended</span>
      <span className="ops-floor-recommendation-body">
        {bestCandidate.photoUrl ? (
          <img
            src={bestCandidate.photoUrl}
            alt={bestCandidate.fullName}
            className="ops-floor-recommendation-photo"
          />
        ) : (
          <span className="ops-floor-recommendation-photo ops-floor-recommendation-photo-placeholder">
            {getInitials(bestCandidate.fullName)}
          </span>
        )}
        <span className="ops-floor-recommendation-text">
          <strong>{bestCandidate.fullName}</strong>
          <span>{bestCandidate.employeeCode} · {Math.round(bestCandidate.skillLevelPercentage)}%</span>
        </span>
      </span>
    </Link>
  );
}

export function LineFloorPlan({
  workers,
  large = false,
  lineOperations = [],
  positionAssignments = [],
  recommendations = [],
  showRecommendations = true,
  canAssignOperations = false,
  assigningEmployeeId,
  onAssignPosition,
}: {
  workers: WorkerProfile[];
  large?: boolean;
  lineOperations?: LineOperation[];
  positionAssignments?: LinePositionAssignment[];
  recommendations?: LineAutomaticRecommendation[];
  showRecommendations?: boolean;
  canAssignOperations?: boolean;
  assigningEmployeeId?: string | null;
  onAssignPosition?: (
    worker: WorkerProfile,
    lineOperationId: string,
    assignment?: LinePositionAssignment
  ) => void;
}) {
  const lineWorkers = [...workers].sort((a, b) => a.fullName.localeCompare(b.fullName));
  const assignmentByEmployeeId = new Map(
    positionAssignments.map((assignment) => [assignment.employeeId, assignment])
  );
  const recommendationByEmployeeId = new Map(
    recommendations.map((recommendation) => [recommendation.assignment.employeeId, recommendation])
  );
  const actualMoWorkers = lineWorkers.filter(isMachineOperator);
  const actualHelWorkers = lineWorkers.filter(isHelperWorker);
  const otherWorkers = lineWorkers.filter(
    (worker) => !isMachineOperator(worker) && !isHelperWorker(worker)
  );
  const moFloorColumns = [
    actualMoWorkers.filter((_, index) => index % 2 === 0),
    actualMoWorkers.filter((_, index) => index % 2 === 1),
  ];

  return (
    <div className={`ops-floor-plan${large ? " is-large" : ""}`}>
      <section className="ops-floor-zone ops-floor-zone-mo">
        <div className="ops-floor-zone-header">
          <div>
            <h3 className="ops-detail-subtitle">MO Machines</h3>
            <div className="ops-row-subtitle">Machine cards: present operators in green, absent operators in red</div>
          </div>
          <StatusBadge label={`${actualMoWorkers.length} MO`} tone="success" />
        </div>
        {actualMoWorkers.length ? (
          <div className="ops-floor-mo-columns">
            {moFloorColumns.map((column, index) => (
              <div key={`mo-column-${index}`} className="ops-floor-mo-column">
                {column.map((worker) => (
                  <FloorEmployeeShape
                    key={worker.id}
                    worker={worker}
                    group="mo"
                    assignment={assignmentByEmployeeId.get(worker.id)}
                    lineOperations={lineOperations}
                    recommendation={recommendationByEmployeeId.get(worker.id)}
                    showRecommendations={showRecommendations}
                    canAssignOperations={canAssignOperations}
                    assigning={assigningEmployeeId === worker.id}
                    onAssignPosition={onAssignPosition}
                  />
                ))}
              </div>
            ))}
          </div>
        ) : (
          <div className="ops-floor-empty">No MO employees assigned</div>
        )}
      </section>

      <aside className="ops-floor-side">
        <section className="ops-floor-zone">
          <div className="ops-floor-zone-header">
            <div>
              <h3 className="ops-detail-subtitle">Helper Stations</h3>
              <div className="ops-row-subtitle">Present helpers in blue, absent helpers in red</div>
            </div>
            <StatusBadge label={`${actualHelWorkers.length} helper`} tone="info" />
          </div>
          {actualHelWorkers.length ? (
            <div className="ops-floor-horizontal-line">
              {actualHelWorkers.map((worker) => (
                <FloorEmployeeShape
                  key={worker.id}
                  worker={worker}
                  group="helper"
                  assignment={assignmentByEmployeeId.get(worker.id)}
                  lineOperations={lineOperations}
                  recommendation={recommendationByEmployeeId.get(worker.id)}
                  showRecommendations={showRecommendations}
                  canAssignOperations={canAssignOperations}
                  assigning={assigningEmployeeId === worker.id}
                  onAssignPosition={onAssignPosition}
                />
              ))}
            </div>
          ) : (
            <div className="ops-floor-empty">No helpers assigned</div>
          )}
        </section>

        <section className="ops-floor-zone">
          <div className="ops-floor-zone-header">
            <div>
              <h3 className="ops-detail-subtitle">Other Employee Stations</h3>
              <div className="ops-row-subtitle">Present employees in violet, absent employees in red</div>
            </div>
            <StatusBadge label={`${otherWorkers.length} other`} tone="violet" />
          </div>
          {otherWorkers.length ? (
            <div className="ops-floor-horizontal-line">
              {otherWorkers.map((worker) => (
                <FloorEmployeeShape
                  key={worker.id}
                  worker={worker}
                  group="other"
                  assignment={assignmentByEmployeeId.get(worker.id)}
                  lineOperations={lineOperations}
                  recommendation={recommendationByEmployeeId.get(worker.id)}
                  showRecommendations={showRecommendations}
                  canAssignOperations={canAssignOperations}
                  assigning={assigningEmployeeId === worker.id}
                  onAssignPosition={onAssignPosition}
                />
              ))}
            </div>
          ) : (
            <div className="ops-floor-empty">No other employees assigned</div>
          )}
        </section>
      </aside>
    </div>
  );
}
