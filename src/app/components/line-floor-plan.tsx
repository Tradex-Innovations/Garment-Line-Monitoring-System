import { Image } from "lucide-react";
import { Link } from "react-router";
import { StatusBadge, getInitials } from "./ops-ui";
import type { WorkerProfile } from "../types";

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
}: {
  worker: WorkerProfile;
  group: "mo" | "helper" | "other";
}) {
  const present = isPresentWorker(worker);
  return (
    <Link
      to={`/workers/${worker.id}`}
      className={`ops-floor-employee group-${group} ${present ? "is-present" : "is-absent"}`}
      title={`${worker.fullName} · ${worker.employeeId} · ${worker.attendanceStatus}`}
    >
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
  );
}

export function LineFloorPlan({ workers, large = false }: { workers: WorkerProfile[]; large?: boolean }) {
  const lineWorkers = [...workers].sort((a, b) => a.fullName.localeCompare(b.fullName));
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
            <h3 className="ops-detail-subtitle">Actual MOs</h3>
            <div className="ops-row-subtitle">Present in green, absent in red</div>
          </div>
          <StatusBadge label={`${actualMoWorkers.length} MO`} tone="success" />
        </div>
        {actualMoWorkers.length ? (
          <div className="ops-floor-mo-columns">
            {moFloorColumns.map((column, index) => (
              <div key={`mo-column-${index}`} className="ops-floor-mo-column">
                {column.map((worker) => (
                  <FloorEmployeeShape key={worker.id} worker={worker} group="mo" />
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
              <h3 className="ops-detail-subtitle">Helpers</h3>
              <div className="ops-row-subtitle">Present in blue, absent in red</div>
            </div>
            <StatusBadge label={`${actualHelWorkers.length} helper`} tone="info" />
          </div>
          {actualHelWorkers.length ? (
            <div className="ops-floor-horizontal-line">
              {actualHelWorkers.map((worker) => (
                <FloorEmployeeShape key={worker.id} worker={worker} group="helper" />
              ))}
            </div>
          ) : (
            <div className="ops-floor-empty">No helpers assigned</div>
          )}
        </section>

        <section className="ops-floor-zone">
          <div className="ops-floor-zone-header">
            <div>
              <h3 className="ops-detail-subtitle">Other Employees</h3>
              <div className="ops-row-subtitle">Present in violet, absent in red</div>
            </div>
            <StatusBadge label={`${otherWorkers.length} other`} tone="violet" />
          </div>
          {otherWorkers.length ? (
            <div className="ops-floor-horizontal-line">
              {otherWorkers.map((worker) => (
                <FloorEmployeeShape key={worker.id} worker={worker} group="other" />
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
