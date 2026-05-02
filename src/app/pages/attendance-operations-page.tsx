import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Calculator, HandCoins, Target, TrendingUp } from "lucide-react";
import {
  listIncentiveReportFromBackend,
} from "@/lib/backend/calculation-api";
import type { IncentiveReportRow } from "@/types/calculations";
import {
  Card,
  ExportActions,
  KpiCard,
  PageHeader,
  StatusBadge,
  downloadCsv,
  formatCurrency,
} from "../components/ops-ui";

function formatEfficiency(value?: number | null) {
  if (typeof value !== "number") {
    return "0.00%";
  }
  return `${(value * 100).toFixed(2)}%`;
}

function defaultDateRange() {
  const today = new Date();
  const end = today.toISOString().slice(0, 10);
  const start = new Date(today);
  start.setDate(today.getDate() - 30);
  return {
    dateFrom: start.toISOString().slice(0, 10),
    dateTo: end,
  };
}

export function AttendanceOperationsPage() {
  const defaults = useMemo(() => defaultDateRange(), []);
  const [filters, setFilters] = useState({
    dateFrom: defaults.dateFrom,
    dateTo: defaults.dateTo,
    lineCode: "",
    shiftCode: "",
  });
  const [rows, setRows] = useState<IncentiveReportRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const nextRows = await listIncentiveReportFromBackend({
          dateFrom: filters.dateFrom,
          dateTo: filters.dateTo,
          lineCode: filters.lineCode || null,
          shiftCode: filters.shiftCode || null,
        });

        if (!cancelled) {
          setRows(nextRows);
        }
      } catch (nextError) {
        if (!cancelled) {
          setError(nextError instanceof Error ? nextError.message : String(nextError));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [filters.dateFrom, filters.dateTo, filters.lineCode, filters.shiftCode]);

  const totals = useMemo(() => {
    const totalIncentive = rows.reduce((sum, row) => sum + row.incentiveAmount, 0);
    const rowsWithWarnings = rows.filter((row) => row.warnings.length > 0).length;
    const averageEfficiency =
      rows.length > 0
        ? rows.reduce((sum, row) => sum + (row.actualEfficiency || 0), 0) / rows.length
        : 0;
    const lineCount = new Set(rows.map((row) => row.lineCode || row.productionLineId)).size;

    return {
      totalIncentive,
      rowsWithWarnings,
      averageEfficiency,
      lineCount,
    };
  }, [rows]);

  const exportRows = [
    [
      "Date",
      "Line Code",
      "Line",
      "Shift",
      "Actual Efficiency",
      "Basis Metric",
      "Band",
      "Incentive Amount",
      "Warnings",
    ],
    ...rows.map((row) => [
      row.productionDate || "",
      row.lineCode || "",
      row.lineName || "",
      row.shiftCode || "",
      row.actualEfficiency?.toString() || "",
      row.basisMetric,
      row.incentiveBandLabel || "",
      row.incentiveAmount.toString(),
      row.warnings.join(" | "),
    ]),
  ];

  return (
    <div className="ops-page">
      <PageHeader
        title="Incentive Calculation"
        subtitle="Rule-driven line incentive results coming from the Spring Boot calculation engine and persisted daily metric records."
        actions={
          <ExportActions
            onExportCsv={() => downloadCsv("line-incentive-report.csv", exportRows)}
            onPrint={() => window.print()}
          />
        }
      />

      <section className="ops-grid cols-2">
        <Card
          title="Filters"
          subtitle="Filter incentive results by date range, line, or shift. The backend returns persisted calculation outputs only."
        >
          <div className="ops-grid cols-2">
            <div className="ops-input-wrap">
              <input
                className="ops-input"
                type="date"
                value={filters.dateFrom}
                onChange={(event) =>
                  setFilters((current) => ({ ...current, dateFrom: event.target.value }))
                }
              />
            </div>
            <div className="ops-input-wrap">
              <input
                className="ops-input"
                type="date"
                value={filters.dateTo}
                onChange={(event) =>
                  setFilters((current) => ({ ...current, dateTo: event.target.value }))
                }
              />
            </div>
          </div>
          <div className="ops-grid cols-2" style={{ marginTop: 16 }}>
            <div className="ops-input-wrap">
              <input
                className="ops-input"
                value={filters.lineCode}
                onChange={(event) =>
                  setFilters((current) => ({ ...current, lineCode: event.target.value }))
                }
                placeholder="Optional line code"
              />
            </div>
            <div className="ops-input-wrap">
              <input
                className="ops-input"
                value={filters.shiftCode}
                onChange={(event) =>
                  setFilters((current) => ({ ...current, shiftCode: event.target.value }))
                }
                placeholder="Optional shift code"
              />
            </div>
          </div>
        </Card>

        <Card
          title="Calculation Status"
          subtitle="Warnings come from protected division, missing output, zero clock hours, or other policy-driven conditions."
        >
          {loading ? <div className="ops-row-subtitle">Loading incentive results…</div> : null}
          {error ? (
            <div className="ops-alert-banner tone-danger">{error}</div>
          ) : (
            <div className="ops-list">
              <div className="ops-list-item">
                <div className="ops-item-title">Rows loaded</div>
                <div className="ops-item-description">{rows.length} incentive rows in the active filter window.</div>
              </div>
              <div className="ops-list-item">
                <div className="ops-item-title">Rows with warnings</div>
                <div className="ops-item-description">{totals.rowsWithWarnings} rows carry quality flags or protected-divide warnings.</div>
              </div>
            </div>
          )}
        </Card>
      </section>

      <section className="ops-kpi-grid">
        <KpiCard
          label="Incentive Pool"
          value={formatCurrency(totals.totalIncentive)}
          meta="Total line incentive payout inside the active filter range."
          icon={HandCoins}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Average Efficiency"
          value={formatEfficiency(totals.averageEfficiency)}
          meta="Average actual efficiency used as the incentive basis."
          icon={TrendingUp}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Lines Covered"
          value={`${totals.lineCount}`}
          meta="Unique production lines represented in the returned incentive rows."
          icon={Target}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Rows With Warnings"
          value={`${totals.rowsWithWarnings}`}
          meta="Use this to trace zero-clock-hour or no-output conditions before payout sign-off."
          icon={AlertTriangle}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
      </section>

      <Card
        title="Line Incentive Results"
        subtitle="Each row shows the persisted basis metric, selected ladder band, and the warnings captured during calculation."
      >
        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Line</th>
                <th>Shift</th>
                <th>Actual Efficiency</th>
                <th>Basis</th>
                <th>Band</th>
                <th>Incentive</th>
                <th>Warnings</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{row.productionDate || "—"}</td>
                  <td>
                    <div className="ops-row-title">{row.lineName || row.lineCode || "Unmapped line"}</div>
                    <div className="ops-row-subtitle">{row.lineCode || row.productionLineId || "—"}</div>
                  </td>
                  <td>{row.shiftCode || "—"}</td>
                  <td>{formatEfficiency(row.actualEfficiency)}</td>
                  <td>
                    <div className="ops-row-title">{row.basisMetric}</div>
                    <div className="ops-row-subtitle">{row.basisValue ?? 0}</div>
                  </td>
                  <td>
                    <StatusBadge
                      label={row.incentiveBandLabel || "No band"}
                      tone={row.incentiveBandLabel ? "success" : "neutral"}
                    />
                  </td>
                  <td>{formatCurrency(row.incentiveAmount)}</td>
                  <td>
                    {row.warnings.length ? (
                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                        {row.warnings.map((warning) => (
                          <StatusBadge key={warning} label={warning} tone="warning" />
                        ))}
                      </div>
                    ) : (
                      <StatusBadge label="Clean" tone="success" />
                    )}
                  </td>
                </tr>
              ))}
              {!loading && !rows.length ? (
                <tr>
                  <td colSpan={8}>
                    <div className="ops-empty-state" style={{ padding: 18 }}>
                      <h3>No incentive rows found</h3>
                      <p>Persisted calculation results will appear here after line metrics are generated.</p>
                    </div>
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

export default AttendanceOperationsPage;
