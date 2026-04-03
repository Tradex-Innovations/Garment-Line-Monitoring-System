import { useState } from "react";
import { Link } from "react-router";
import { AlertTriangle, ArrowRightLeft, Factory, UserPlus, Users, Waves } from "lucide-react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import {
  Button,
  Card,
  KpiCard,
  LineCard,
  PageHeader,
  StatusBadge,
  validationTone,
} from "../components/ops-ui";

export function ProductionLinesPage() {
  const { canDo } = useAuth();
  const { lines, workers } = useOperations();
  const [expandedLineIds, setExpandedLineIds] = useState<string[]>(["line-d"]);

  const toggleLine = (lineId: string) => {
    setExpandedLineIds((current) =>
      current.includes(lineId)
        ? current.filter((item) => item !== lineId)
        : [...current, lineId]
    );
  };

  const understaffedLines = lines.filter((line) => line.actualManpower < line.targetManpower);
  const idleLines = lines.filter((line) => line.status === "Idle");

  return (
    <div className="ops-page">
      <PageHeader
        title="Production Lines"
        subtitle="Floor-map style operational view with manpower gap, line health, quick actions, and expandable worker rosters."
        actions={
          <>
            <Link to="/display-mode" className="ops-button ops-button-secondary">
              Display Mode
            </Link>
            {canDo("assignLine") ? (
              <Link to="/line-assignment" className="ops-button ops-button-primary">
                Line Balancing
              </Link>
            ) : null}
          </>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Operational Lines"
          value={`${lines.filter((line) => line.status !== "Idle").length}/${lines.length}`}
          meta="Lines currently running partial or active manpower."
          icon={Factory}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Understaffed"
          value={`${understaffedLines.length}`}
          meta="Need balancing before the next output checkpoint."
          icon={Users}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Idle Lines"
          value={`${idleLines.length}`}
          meta="Standby lines with no active manpower allocation."
          icon={Waves}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
        <KpiCard
          label="Critical Risk"
          value={`${lines.filter((line) => line.risk === "Critical").length}`}
          meta="Lines with elevated output risk or unresolved manpower issue."
          icon={AlertTriangle}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
      </section>

      <Card
        title="Live Floor Map"
        subtitle="Each card shows line status, manpower, efficiency, output, shift owner, and immediate actions."
      >
        <div className="ops-line-grid">
          {lines.map((line) => {
            const lineWorkers = workers.filter((worker) => worker.currentLineId === line.id);
            const isExpanded = expandedLineIds.includes(line.id);
            const manpowerGap = Math.max(line.targetManpower - line.actualManpower, 0);

            return (
              <LineCard
                key={line.id}
                line={line}
                actions={
                  <>
                    <Button tone="secondary" onClick={() => toggleLine(line.id)}>
                      {isExpanded ? "Hide Workers" : "View Line Details"}
                    </Button>
                    {canDo("assignLine") ? (
                      <Link to="/line-assignment" className="ops-button ops-button-ghost">
                        <UserPlus size={15} />
                        Assign Worker
                      </Link>
                    ) : null}
                    {canDo("transferLine") ? (
                      <Link to="/line-assignment" className="ops-button ops-button-ghost">
                        <ArrowRightLeft size={15} />
                        Transfer
                      </Link>
                    ) : null}
                  </>
                }
              >
                <div className="ops-item-meta">
                  <span>Gap {manpowerGap}</span>
                  <span>{line.status === "Idle" ? "Changeover" : line.shift}</span>
                </div>

                {isExpanded ? (
                  <div className="ops-line-workers">
                    {lineWorkers.length ? (
                      lineWorkers.map((worker) => (
                        <div key={worker.id} className="ops-line-worker-item">
                          <div>
                            <div className="ops-row-title">{worker.fullName}</div>
                            <div className="ops-row-subtitle">
                              {worker.roleTitle} · {worker.employeeId}
                            </div>
                          </div>
                          <StatusBadge
                            label={worker.finalValidationStatus}
                            tone={validationTone(worker.finalValidationStatus)}
                          />
                        </div>
                      ))
                    ) : (
                      <div className="ops-empty-state" style={{ padding: 18 }}>
                        <h3>No active roster preview</h3>
                        <p>
                          This line currently has no tracked workers assigned in the demo roster.
                        </p>
                      </div>
                    )}
                  </div>
                ) : null}
              </LineCard>
            );
          })}
        </div>
      </Card>
    </div>
  );
}

export default ProductionLinesPage;
