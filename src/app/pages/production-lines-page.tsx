import { useMemo, useState } from "react";
import { Link } from "react-router";
import { ArrowRightLeft, BriefcaseBusiness, FileSearch, UserPlus, Users } from "lucide-react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import {
  Card,
  KpiCard,
  LineCard,
  PageHeader,
  StatusBadge,
  WorkerChip,
  attendanceTone,
  formatCurrency,
} from "../components/ops-ui";
import { getMetricAuditFromBackend } from "@/lib/backend/calculation-api";
import type { CalculationAuditView } from "@/types/calculations";

function formatEfficiencyRatio(value?: number) {
  if (typeof value !== "number") {
    return "0.00%";
  }
  return `${(value * 100).toFixed(2)}%`;
}

export function ProductionLinesPage() {
  const { canDo, currentUser } = useAuth();
  const { lines, workers, updateLineStyle } = useOperations();
  const [expandedLineIds, setExpandedLineIds] = useState<string[]>([]);
  const [auditByMetricId, setAuditByMetricId] = useState<Record<string, CalculationAuditView>>({});
  const [auditLoadingMetricId, setAuditLoadingMetricId] = useState<string | null>(null);
  const [auditError, setAuditError] = useState<string | null>(null);
  const [styleDraftByLineId, setStyleDraftByLineId] = useState<Record<string, string>>({});
  const [styleSavingLineId, setStyleSavingLineId] = useState<string | null>(null);
  const [styleMessageByLineId, setStyleMessageByLineId] = useState<Record<string, string>>({});

  const toggleLine = (lineId: string) => {
    setExpandedLineIds((current) =>
      current.includes(lineId)
        ? current.filter((item) => item !== lineId)
        : [...current, lineId]
    );
  };

  const loadAudit = async (metricId: string) => {
    if (auditByMetricId[metricId]) {
      setAuditByMetricId((current) => {
        const next = { ...current };
        delete next[metricId];
        return next;
      });
      return;
    }

    setAuditLoadingMetricId(metricId);
    setAuditError(null);

    try {
      const audit = await getMetricAuditFromBackend(metricId);
      setAuditByMetricId((current) => ({ ...current, [metricId]: audit }));
    } catch (error) {
      setAuditError(error instanceof Error ? error.message : String(error));
    } finally {
      setAuditLoadingMetricId(null);
    }
  };

  const saveLineStyle = async (lineId: string, currentStyle?: string) => {
    const nextStyle = (styleDraftByLineId[lineId] ?? currentStyle ?? "").trim();

    if (!nextStyle) {
      setStyleMessageByLineId((current) => ({
        ...current,
        [lineId]: "Allocated style is required.",
      }));
      return;
    }

    setStyleSavingLineId(lineId);

    try {
      const result = await updateLineStyle({
        lineId,
        allocatedStyle: nextStyle,
        actor: currentUser.name,
      });

      setStyleMessageByLineId((current) => ({
        ...current,
        [lineId]: result.message,
      }));
    } finally {
      setStyleSavingLineId(null);
    }
  };

  const assignedWorkers = lines.reduce((sum, line) => sum + line.assignedWorkers, 0);
  const presentWorkers = lines.reduce(
    (sum, line) => sum + line.presentWorkers + line.lateWorkers,
    0
  );
  const underAttendedLines = useMemo(
    () =>
      lines.filter(
        (line) =>
          line.assignedWorkers > 0 &&
          line.presentWorkers + line.lateWorkers < line.assignedWorkers
      ),
    [lines]
  );
  const linesWithMetrics = lines.filter((line) => Boolean(line.latestMetricId)).length;

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
          label="Metrics Available"
          value={`${linesWithMetrics}`}
          meta="Lines that already have a persisted daily calculation metric."
          icon={FileSearch}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <Card
        title="Line Roster"
        subtitle="Open a line to view staff, attendance state, the latest calculation metrics, and the admin-only audit snapshot."
      >
        {auditError ? (
          <div className="ops-alert-banner tone-danger" style={{ marginBottom: 16 }}>
            {auditError}
          </div>
        ) : null}

        <div className="ops-line-grid">
          {lines.map((line) => {
            const lineWorkers = workers
              .filter((worker) => worker.currentLineId === line.id)
              .sort((a, b) => a.fullName.localeCompare(b.fullName));
            const isExpanded = expandedLineIds.includes(line.id);
            const audit = line.latestMetricId ? auditByMetricId[line.latestMetricId] : undefined;

            return (
              <LineCard
                key={line.id}
                line={line}
                actions={
                  <>
                    <button
                      type="button"
                      className="ops-button ops-button-secondary"
                      onClick={() => toggleLine(line.id)}
                    >
                      {isExpanded ? "Hide Staff" : "View Staff"}
                    </button>
                    {canDo("assignLine") ? (
                      <Link to="/line-assignment" className="ops-button ops-button-ghost">
                        <UserPlus size={15} />
                        Assign
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
                {isExpanded ? (
                  <div className="ops-line-workers">
                    <div className="ops-card-divider" />

                    <div style={{ display: "grid", gap: 12 }}>
                      <div className="ops-item-header">
                        <div>
                          <div className="ops-item-title">Line Allocation</div>
                          <div className="ops-row-subtitle">
                            {line.code} · Current style {line.allocatedStyle || "Unassigned"}
                          </div>
                        </div>
                        <StatusBadge
                          label={line.allocatedStyle ? "Style Assigned" : "Needs Style"}
                          tone={line.allocatedStyle ? "success" : "warning"}
                        />
                      </div>

                      {canDo("assignLine") ? (
                        <div
                          style={{
                            display: "grid",
                            gap: 12,
                            gridTemplateColumns: "minmax(0, 1fr) auto",
                            alignItems: "end",
                          }}
                        >
                          <div className="ops-filter-group" style={{ margin: 0 }}>
                            <label className="ops-filter-label" htmlFor={`style-${line.id}`}>
                              Allocated style
                            </label>
                            <input
                              id={`style-${line.id}`}
                              className="ops-input"
                              value={styleDraftByLineId[line.id] ?? line.allocatedStyle ?? ""}
                              onChange={(event) =>
                                setStyleDraftByLineId((current) => ({
                                  ...current,
                                  [line.id]: event.target.value,
                                }))
                              }
                              placeholder="Enter style code"
                            />
                          </div>
                          <button
                            type="button"
                            className="ops-button ops-button-secondary"
                            disabled={styleSavingLineId === line.id}
                            onClick={() => void saveLineStyle(line.id, line.allocatedStyle)}
                          >
                            {styleSavingLineId === line.id ? "Saving…" : "Save Style"}
                          </button>
                        </div>
                      ) : null}

                      {styleMessageByLineId[line.id] ? (
                        <div className="ops-badge tone-info">{styleMessageByLineId[line.id]}</div>
                      ) : null}
                    </div>

                    {line.latestMetricId ? (
                      <div style={{ display: "grid", gap: 16 }}>
                        <div className="ops-item-header">
                          <div>
                            <div className="ops-item-title">Latest Calculation Metrics</div>
                            <div className="ops-row-subtitle">
                              {line.latestMetricDate || "No calculation date"} · Formula{" "}
                              {line.formulaRuleSetId || "—"} v{line.formulaRuleVersion || 0}
                            </div>
                          </div>
                          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                            <StatusBadge
                              label={line.incentiveBand || "No band"}
                              tone={line.incentiveBand ? "success" : "neutral"}
                            />
                            {currentUser.role === "admin" ? (
                              <button
                                type="button"
                                className="ops-button ops-button-ghost"
                                onClick={() => void loadAudit(line.latestMetricId!)}
                              >
                                {audit
                                  ? "Hide Audit"
                                  : auditLoadingMetricId === line.latestMetricId
                                    ? "Loading Audit…"
                                    : "View Audit"}
                              </button>
                            ) : null}
                          </div>
                        </div>

                        <div className="ops-meta-grid">
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Planned Cadre</div>
                            <div className="ops-key-value-value">{line.plannedCadreTotal ?? 0}</div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Actual Cadre</div>
                            <div className="ops-key-value-value">{line.actualCadreTotal ?? 0}</div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Clock Hours</div>
                            <div className="ops-key-value-value">{line.clockHours ?? 0}</div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Planned Efficiency</div>
                            <div className="ops-key-value-value">
                              {formatEfficiencyRatio(line.plannedEfficiencyRatio)}
                            </div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Forecast Efficiency</div>
                            <div className="ops-key-value-value">
                              {formatEfficiencyRatio(line.forecastEfficiencyRatio)}
                            </div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Actual Efficiency</div>
                            <div className="ops-key-value-value">
                              {formatEfficiencyRatio(line.actualEfficiencyRatio)}
                            </div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Piece Variance</div>
                            <div className="ops-key-value-value">{line.pieceVariance ?? 0}</div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">SAH Variance</div>
                            <div className="ops-key-value-value">{line.sahVariance ?? 0}</div>
                          </div>
                          <div className="ops-key-value">
                            <div className="ops-key-value-label">Line Incentive</div>
                            <div className="ops-key-value-value">
                              {typeof line.incentiveAmount === "number"
                                ? formatCurrency(line.incentiveAmount)
                                : "—"}
                            </div>
                          </div>
                        </div>

                        {line.metricWarnings?.length ? (
                          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                            {line.metricWarnings.map((warning) => (
                              <StatusBadge key={warning} label={warning} tone="warning" />
                            ))}
                          </div>
                        ) : null}

                        {audit ? (
                          <div className="ops-card-divider">
                            <div className="ops-item-title" style={{ marginBottom: 12 }}>
                              Audit Snapshot
                            </div>
                            <pre
                              style={{
                                margin: 0,
                                padding: 16,
                                borderRadius: 18,
                                background: "#f8fafc",
                                overflowX: "auto",
                                fontSize: "0.78rem",
                                color: "#0f172a",
                              }}
                            >
                              {JSON.stringify(audit, null, 2)}
                            </pre>
                          </div>
                        ) : null}
                      </div>
                    ) : (
                      <div className="ops-empty-state" style={{ padding: 18 }}>
                        <h3>No calculation metrics yet</h3>
                        <p>
                          This line does not yet have a persisted daily metric from the calculation
                          engine.
                        </p>
                      </div>
                    )}

                    <div className="ops-card-divider" />

                    {lineWorkers.length ? (
                      lineWorkers.map((worker) => (
                        <div key={worker.id} className="ops-line-worker-item">
                          <WorkerChip
                            worker={worker}
                            meta={
                              <div className="ops-row-subtitle">
                                {worker.department} · {worker.phone}
                              </div>
                            }
                          />
                          <div
                            style={{
                              display: "flex",
                              gap: 8,
                              flexWrap: "wrap",
                              justifyContent: "flex-end",
                            }}
                          >
                            <StatusBadge
                              label={worker.attendanceStatus}
                              tone={attendanceTone(worker.attendanceStatus)}
                            />
                            <Link to={`/workers/${worker.id}`} className="ops-link-button">
                              View Profile
                            </Link>
                          </div>
                        </div>
                      ))
                    ) : (
                      <div className="ops-empty-state" style={{ padding: 18 }}>
                        <h3>No staff assigned</h3>
                        <p>This production line does not currently have any assigned workers.</p>
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
