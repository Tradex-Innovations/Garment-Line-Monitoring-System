import { useState, type CSSProperties, type SVGProps } from "react";

// ─── Data ─────────────────────────────────────────────────────────────────────
type AlertType = "missing" | "late" | "efficiency" | "security";
type AlertSeverity = "high" | "medium" | "low";

type AlertItem = {
  id: string;
  type: AlertType;
  title: string;
  description: string;
  timestamp: string;
  severity: AlertSeverity;
};

const mockAlerts: AlertItem[] = [
  { id: "1", type: "missing",    title: "Worker Not Checked In",   description: "Emma Thompson (EMP-2409) scheduled but not present",          timestamp: "5 min ago",  severity: "high"   },
  { id: "2", type: "efficiency", title: "Line B Efficiency Drop",  description: "Efficiency dropped to 78% — below 85% threshold",             timestamp: "12 min ago", severity: "medium" },
  { id: "3", type: "security",   title: "Failed Entry Attempt",    description: "Unrecognized individual attempted entry at Gate 2",            timestamp: "18 min ago", severity: "high"   },
];

// ─── Severity / type maps ─────────────────────────────────────────────────────
const SEVERITY: Record<AlertSeverity, { iconBg: string; iconColor: string; borderLeft: string; rowBg: string; pillBg: string; pillColor: string }> = {
  high:   { iconBg: "#FEE2E2", iconColor: "#DC2626", borderLeft: "#DC2626", rowBg: "#FFF5F5", pillBg: "#FEE2E2", pillColor: "#7F1D1D" },
  medium: { iconBg: "#FEF3C7", iconColor: "#D97706", borderLeft: "#D97706", rowBg: "#FFFBEB", pillBg: "#FEF3C7", pillColor: "#78350F" },
  low:    { iconBg: "#DBEAFE", iconColor: "#2563EB", borderLeft: "#2563EB", rowBg: "#EFF6FF", pillBg: "#DBEAFE", pillColor: "#1E3A8A" },
};

// ─── Inline SVG icons ─────────────────────────────────────────────────────────
function AlertIcon({ type, color }: { type: AlertType; color: string }) {
  const s: CSSProperties = { width: 15, height: 15 };
  const base: SVGProps<SVGSVGElement> = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: color,
    strokeWidth: 2,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    style: s,
  };
  if (type === "missing")
    return <svg {...base}><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="17" y1="8" x2="23" y2="14"/><line x1="23" y1="8" x2="17" y2="14"/></svg>;
  if (type === "late")
    return <svg {...base}><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>;
  if (type === "efficiency")
    return <svg {...base}><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg>;
  // security / default
  return <svg {...base}><path d="m10.29 3.86-8.6 14.9A2 2 0 0 0 3.41 22h17.18a2 2 0 0 0 1.72-3.01L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>;
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="#16A34A" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 22, height: 22 }}>
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
      <polyline points="22 4 12 14.01 9 11.01"/>
    </svg>
  );
}

// ─── Component ────────────────────────────────────────────────────────────────
export function AlertsSection() {
  const [hovered, setHovered] = useState<string | null>(null);

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'DM Sans', system-ui, sans-serif; background: #F5F6F8; color: #111318; }
      `}</style>

      <div style={{ background: "#FFFFFF", border: "1px solid #E2E4EA", borderRadius: 14, overflow: "hidden", fontFamily: "'DM Sans', system-ui, sans-serif" }}>

        {/* Header */}
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "18px 20px", borderBottom: "1px solid #E2E4EA" }}>
          <div>
            <div style={{ fontSize: 14, fontWeight: 600, color: "#111318" }}>Active Alerts</div>
            <div style={{ fontSize: 12, color: "#8B90A0", marginTop: 2 }}>Requires immediate attention</div>
          </div>
          <span style={{ display: "inline-flex", alignItems: "center", padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 500, background: "#FEE2E2", color: "#7F1D1D" }}>
            {mockAlerts.length} Active
          </span>
        </div>

        {/* Alert rows */}
        {mockAlerts.length > 0 ? (
          <div>
            {mockAlerts.map((alert, i) => {
              const s = SEVERITY[alert.severity] || SEVERITY.low;
              const isLast = i === mockAlerts.length - 1;
              const isHovered = hovered === alert.id;
              return (
                <div
                  key={alert.id}
                  onMouseEnter={() => setHovered(alert.id)}
                  onMouseLeave={() => setHovered(null)}
                  style={{
                    display: "flex",
                    gap: 12,
                    padding: "14px 20px",
                    borderBottom: isLast ? "none" : "1px solid #F0F1F4",
                    borderLeft: `3px solid ${s.borderLeft}`,
                    background: isHovered ? s.rowBg : "#FFFFFF",
                    transition: "background 0.15s",
                  }}
                >
                  {/* Icon */}
                  <div style={{ width: 32, height: 32, borderRadius: 8, background: s.iconBg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, marginTop: 1 }}>
                    <AlertIcon type={alert.type} color={s.iconColor} />
                  </div>

                  {/* Content */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 8, marginBottom: 3 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "#111318" }}>{alert.title}</div>
                      <span style={{ fontSize: 10, color: "#8B90A0", fontFamily: "monospace", flexShrink: 0 }}>{alert.timestamp}</span>
                    </div>
                    <div style={{ fontSize: 12, color: "#4B5063", lineHeight: 1.5, marginBottom: 8 }}>{alert.description}</div>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <span style={{ display: "inline-flex", alignItems: "center", padding: "1px 8px", borderRadius: 20, fontSize: 10, fontWeight: 600, letterSpacing: "0.5px", background: s.pillBg, color: s.pillColor }}>
                        {alert.severity.toUpperCase()}
                      </span>
                      <button style={{ fontSize: 12, fontWeight: 500, color: "#2563EB", background: "none", border: "none", cursor: "pointer", padding: 0 }}>
                        View Details
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          /* Empty state */
          <div style={{ padding: "36px 20px", textAlign: "center" }}>
            <div style={{ width: 48, height: 48, borderRadius: "50%", background: "#DCFCE7", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 12px" }}>
              <CheckIcon />
            </div>
            <div style={{ fontSize: 13, fontWeight: 500, color: "#4B5063" }}>No active alerts</div>
            <div style={{ fontSize: 12, color: "#8B90A0", marginTop: 3 }}>All systems operating normally</div>
          </div>
        )}
      </div>
    </>
  );
}

export default AlertsSection;
