import { useEffect, useState } from "react";
import { getEmployeePortalPayslips, reviewEmployeePortalPayslip } from "@/lib/backend/employee-portal-api";
import type { EmployeePortalPayslip } from "@/types/employee-portal";
import { Button, Card, EmptyState } from "./ops-ui";

function money(value: string | undefined) {
  const number = Number(value);
  return Number.isFinite(number) ? `LKR ${number.toLocaleString("en-LK", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : "Unavailable";
}

export function EmployeePayslips({ token }: { token: string }) {
  const [items, setItems] = useState<EmployeePortalPayslip[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [queryText, setQueryText] = useState<Record<string, string>>({});

  useEffect(() => {
    let active = true;
    setLoading(true);
    getEmployeePortalPayslips(token).then((rows) => {
      if (active) { setItems(rows); setError(null); }
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : String(cause));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [token]);

  const respond = async (id: string, action: "CONFIRMED" | "QUERY") => {
    if (action === "CONFIRMED" && !window.confirm("Confirm this draft payslip? This response is recorded and cannot be changed here.")) return;
    setSaving(true);
    setError(null);
    try {
      setItems(await reviewEmployeePortalPayslip(token, id, { action, note: action === "QUERY" ? queryText[id] : undefined }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSaving(false);
    }
  };

  return <Card title="Draft payslips and self-review" subtitle="Only internally approved payroll calculations appear here. Confirm the draft or raise a query before final payment.">
    {loading ? <p role="status">Loading draft payslips…</p> : null}
    {error ? <p role="alert" className="ops-alert-banner tone-danger">{error}</p> : null}
    {!loading && !error && !items.length ? <EmptyState title="No draft payslip ready" description="A payslip appears after Payroll completes the calculation and management approves the employee's workforce group." /> : null}
    <div className="ops-list">
      {items.map((item) => <div key={item.id} className="ops-list-item">
        <div className="ops-item-header">
          <div>
            <div className="ops-item-title">{item.periodStart && item.periodEnd ? `${item.periodStart} to ${item.periodEnd}` : `Pay period ${item.periodId}`}</div>
            <div className="ops-row-subtitle">Draft payslip · {item.reviewStatus || "Awaiting your review"}</div>
          </div>
        </div>
        <div className="ops-grid cols-4">
          <div>Basic<br /><strong>{money(item.result.basic)}</strong></div>
          <div>Gross<br /><strong>{money(item.result.gross)}</strong></div>
          <div>Deductions<br /><strong>{money(item.result.deductions)}</strong></div>
          <div>Net<br /><strong>{money(item.result.net)}</strong></div>
        </div>
        <details style={{ marginTop: 12 }}>
          <summary>View earnings and deductions</summary>
          <div className="ops-list">
            {(item.result.lines || []).map((line, index) => <div className="ops-list-item" key={`${line.code}-${index}`}>
              <span>{line.name} ({line.category.replace(/_/g, " ")})</span>
              <strong style={{ marginLeft: 12 }}>{money(line.amount)}</strong>
            </div>)}
          </div>
        </details>
        {item.reviewStatus === "QUERY" ? <p>Your query: {item.reviewNote}</p> : null}
        {!item.reviewStatus ? <div className="ops-auth-form-stack" style={{ marginTop: 12 }}>
          <label className="ops-filter-group">
            <span className="ops-filter-label">Question about this payslip</span>
            <textarea className="ops-textarea" maxLength={1000} value={queryText[item.id] || ""}
              onChange={(event) => setQueryText((current) => ({ ...current, [item.id]: event.target.value }))}
              placeholder="Explain the difference you noticed" />
          </label>
          <div className="ops-item-actions">
            <Button tone="primary" disabled={saving} onClick={() => void respond(item.id, "CONFIRMED")}>Confirm draft</Button>
            <Button tone="secondary" disabled={saving || !queryText[item.id]?.trim()} onClick={() => void respond(item.id, "QUERY")}>Raise query</Button>
          </div>
        </div> : null}
      </div>)}
    </div>
  </Card>;
}
