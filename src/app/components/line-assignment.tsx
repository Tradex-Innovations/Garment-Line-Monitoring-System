import { useState } from "react";

// ─── Data ─────────────────────────────────────────────────────────────────────
const mockLines = [
  { id: "1", name: "Line A — Shirts",   target: 500, current: 342, assignedWorkers: 12, requiredWorkers: 12, efficiency: 94, status: "optimal"  },
  { id: "2", name: "Line B — Trousers", target: 400, current: 267, assignedWorkers: 10, requiredWorkers: 12, efficiency: 78, status: "warning"  },
  { id: "3", name: "Line C — Dresses",  target: 300, current: 198, assignedWorkers:  8, requiredWorkers: 10, efficiency: 82, status: "warning"  },
  { id: "4", name: "Line D — Jackets",  target: 200, current: 145, assignedWorkers:  7, requiredWorkers:  8, efficiency: 89, status: "optimal"  },
];

// ─── Maps ─────────────────────────────────────────────────────────────────────
const STATUS = {
  optimal:  { pillBg: "#DCFCE7", pillColor: "#14532D", accent: "#16A34A", label: "Optimal"  },
  warning:  { pillBg: "#FEF3C7", pillColor: "#78350F", accent: "#D97706", label: "Warning"  },
  critical: { pillBg: "#FEE2E2", pillColor: "#7F1D1D", accent: "#DC2626", label: "Critical" },
};

function effColor(v) {
  if (v >= 90) return "#16A34A";
  if (v >= 80) return "#D97706";
  return "#DC2626";
}
function progressColor(pct) {
  if (pct >= 70) return "#16A34A";
  if (pct >= 50) return "#D97706";
  return "#DC2626";
}

// ─── Inline SVG icons ─────────────────────────────────────────────────────────
function IconUsers() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="#8B90A0" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13, flexShrink: 0 }}>
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
      <path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
    </svg>
  );
}
function IconTrend() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="#8B90A0" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13, flexShrink: 0 }}>
      <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/>
    </svg>
  );
}
function IconWarn() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
      <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
    </svg>
  );
}

// ─── Component ────────────────────────────────────────────────────────────────
export function LineAssignment() {
  const [hovered, setHovered] = useState(null);

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
            <div style={{ fontSize: 14, fontWeight: 600, color: "#111318" }}>Production Line Assignment</div>
            <div style={{ fontSize: 12, color: "#8B90A0", marginTop: 2 }}>Real-time worker allocation and output tracking</div>
          </div>
          <span style={{ fontSize: 12, color: "#2563EB", fontWeight: 500, cursor: "pointer" }}>View all →</span>
        </div>

        {/* Grid */}
        <div style={{ padding: 16, display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
          {mockLines.map((line) => {
            const pct = Math.round((line.current / line.target) * 100);
            const s   = STATUS[line.status] || STATUS.optimal;
            const isH = hovered === line.id;

            return (
              <div
                key={line.id}
                onMouseEnter={() => setHovered(line.id)}
                onMouseLeave={() => setHovered(null)}
                style={{
                  border: "1px solid #E2E4EA",
                  borderRadius: 12,
                  padding: 14,
                  background: isH ? "#F5F6F8" : "#FFFFFF",
                  transition: "background 0.15s",
                  position: "relative",
                  overflow: "hidden",
                }}
              >
                {/* Left accent stripe */}
                <div style={{ position: "absolute", top: 0, left: 0, width: 3, height: "100%", background: s.accent, borderRadius: "3px 0 0 3px" }} />

                {/* Card header */}
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 12 }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "#111318" }}>{line.name}</div>
                    <div style={{ fontSize: 11, color: "#8B90A0", marginTop: 2 }}>Target: {line.target} units/day</div>
                  </div>
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 500, background: s.pillBg, color: s.pillColor, whiteSpace: "nowrap" }}>
                    {line.status === "warning" && <IconWarn />}
                    {s.label}
                  </span>
                </div>

                {/* Progress */}
                <div style={{ marginBottom: 12 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#4B5063", marginBottom: 6 }}>
                    <span>Current Output</span>
                    <span style={{ fontWeight: 600, color: "#111318" }}>{line.current} / {line.target}</span>
                  </div>
                  <div style={{ height: 6, borderRadius: 3, background: "#F0F1F4", overflow: "hidden" }}>
                    <div style={{ width: `${pct}%`, height: "100%", borderRadius: 3, background: progressColor(pct), transition: "width 0.3s" }} />
                  </div>
                </div>

                {/* Stats */}
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
                  {/* Workers */}
                  <div style={{ background: "#F5F6F8", borderRadius: 10, padding: "10px 12px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 4 }}>
                      <IconUsers />
                      <span style={{ fontSize: 10, fontWeight: 500, color: "#8B90A0", textTransform: "uppercase", letterSpacing: "0.5px" }}>Workers</span>
                    </div>
                    <div style={{ fontSize: 18, fontWeight: 600, color: line.assignedWorkers < line.requiredWorkers ? "#D97706" : "#111318" }}>
                      {line.assignedWorkers}/{line.requiredWorkers}
                    </div>
                  </div>

                  {/* Efficiency */}
                  <div style={{ background: "#F5F6F8", borderRadius: 10, padding: "10px 12px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 4 }}>
                      <IconTrend />
                      <span style={{ fontSize: 10, fontWeight: 500, color: "#8B90A0", textTransform: "uppercase", letterSpacing: "0.5px" }}>Efficiency</span>
                    </div>
                    <div style={{ fontSize: 18, fontWeight: 600, color: effColor(line.efficiency) }}>
                      {line.efficiency}%
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </>
  );
}

export default LineAssignment;