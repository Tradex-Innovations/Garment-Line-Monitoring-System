import { useState } from "react";

// ─── Data ─────────────────────────────────────────────────────────────────────
const mockWorkers = [
  { id: "1", name: "Sarah Johnson", employeeId: "EMP-2401", line: "Line A", checkInTime: "07:58", verificationMethod: "both",        status: "active" },
  { id: "2", name: "Michael Chen",  employeeId: "EMP-2402", line: "Line A", checkInTime: "08:02", verificationMethod: "face",        status: "active" },
  { id: "3", name: "Priya Patel",   employeeId: "EMP-2403", line: "Line B", checkInTime: "08:00", verificationMethod: "fingerprint", status: "break"  },
  { id: "4", name: "James Wilson",  employeeId: "EMP-2404", line: "Line B", checkInTime: "08:15", verificationMethod: "both",        status: "late"   },
  { id: "5", name: "Maria Garcia",  employeeId: "EMP-2405", line: "Line C", checkInTime: "07:55", verificationMethod: "face",        status: "active" },
  { id: "6", name: "Ahmed Hassan",  employeeId: "EMP-2406", line: "Line C", checkInTime: "08:01", verificationMethod: "both",        status: "active" },
];

// ─── Maps ─────────────────────────────────────────────────────────────────────
const STATUS = {
  active: { pillBg: "#DCFCE7", pillColor: "#14532D", label: "Active"   },
  break:  { pillBg: "#FEF3C7", pillColor: "#78350F", label: "On Break" },
  late:   { pillBg: "#FEE2E2", pillColor: "#7F1D1D", label: "Late"     },
};

const AVATAR_COLORS = [
  { bg: "#DBEAFE", color: "#1E3A8A" },
  { bg: "#DCFCE7", color: "#14532D" },
  { bg: "#EDE9FE", color: "#4C1D95" },
  { bg: "#FEF3C7", color: "#78350F" },
  { bg: "#DBEAFE", color: "#1E3A8A" },
  { bg: "#DCFCE7", color: "#14532D" },
];

// ─── Inline SVG icons ─────────────────────────────────────────────────────────
function IconFace() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13 }}>
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/><polyline points="16 11 17 13 21 11"/>
    </svg>
  );
}
function IconFingerprint() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13 }}>
      <path d="M2 12C2 6.48 6.48 2 12 2"/><path d="M5 19.5C5.5 17 6 15 9 13.5"/><path d="M22 12c0 5.52-4.48 10-10 10"/><path d="M19 4.55A10 10 0 0 1 22 12"/><path d="M12 7a5 5 0 0 1 5 5"/><path d="M12 7a5 5 0 0 0-5 5c0 2.5.5 4 2 5.5"/><path d="M12 11v1"/>
    </svg>
  );
}
function IconClock() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="#8B90A0" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 12, height: 12, flexShrink: 0 }}>
      <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
    </svg>
  );
}
function IconCheck() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
    </svg>
  );
}

function LiveDot() {
  return <span style={{ display: "inline-block", width: 7, height: 7, borderRadius: "50%", background: "#16A34A", animation: "glpulse 2s infinite", flexShrink: 0 }} />;
}

// ─── Component ────────────────────────────────────────────────────────────────
export function LiveWorkerStatus() {
  const [hovered, setHovered] = useState(null);

  const TH = ({ children }) => (
    <th style={{ fontSize: 11, fontWeight: 500, color: "#8B90A0", textTransform: "uppercase", letterSpacing: "0.5px", padding: "10px 16px", borderBottom: "1px solid #E2E4EA", textAlign: "left", background: "#F5F6F8", whiteSpace: "nowrap" }}>
      {children}
    </th>
  );

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'DM Sans', system-ui, sans-serif; background: #F5F6F8; color: #111318; }
        @keyframes glpulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }
      `}</style>

      <div style={{ background: "#FFFFFF", border: "1px solid #E2E4EA", borderRadius: 14, overflow: "hidden", fontFamily: "'DM Sans', system-ui, sans-serif" }}>

        {/* Header */}
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "18px 20px", borderBottom: "1px solid #E2E4EA" }}>
          <div>
            <div style={{ fontSize: 14, fontWeight: 600, color: "#111318" }}>Live Worker Status</div>
            <div style={{ fontSize: 12, color: "#8B90A0", marginTop: 2 }}>Currently checked-in workers</div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 6, padding: "5px 10px", borderRadius: 20, background: "#DCFCE7", fontSize: 12, fontWeight: 500, color: "#14532D" }}>
            <LiveDot /> Live
          </div>
        </div>

        {/* Table */}
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr>
                <TH>Worker</TH>
                <TH>Employee ID</TH>
                <TH>Line</TH>
                <TH>Check-in</TH>
                <TH>Verification</TH>
                <TH>Status</TH>
              </tr>
            </thead>
            <tbody>
              {mockWorkers.map((worker, i) => {
                const av   = AVATAR_COLORS[i % AVATAR_COLORS.length];
                const st   = STATUS[worker.status];
                const isH  = hovered === worker.id;
                const isLast = i === mockWorkers.length - 1;

                return (
                  <tr
                    key={worker.id}
                    onMouseEnter={() => setHovered(worker.id)}
                    onMouseLeave={() => setHovered(null)}
                    style={{ background: isH ? "#F5F6F8" : "#FFFFFF", transition: "background 0.15s", borderBottom: isLast ? "none" : "1px solid #F0F1F4" }}
                  >
                    {/* Worker */}
                    <td style={{ padding: "11px 16px", verticalAlign: "middle" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <div style={{ width: 32, height: 32, borderRadius: "50%", background: av.bg, color: av.color, fontSize: 11, fontWeight: 600, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                          {worker.name.split(" ").map((n) => n[0]).join("")}
                        </div>
                        <span style={{ fontWeight: 600, color: "#111318" }}>{worker.name}</span>
                      </div>
                    </td>

                    {/* Employee ID */}
                    <td style={{ padding: "11px 16px", verticalAlign: "middle" }}>
                      <span style={{ fontSize: 12, color: "#4B5063", fontFamily: "monospace" }}>{worker.employeeId}</span>
                    </td>

                    {/* Line */}
                    <td style={{ padding: "11px 16px", verticalAlign: "middle" }}>
                      <span style={{ display: "inline-flex", alignItems: "center", padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 500, background: "#DBEAFE", color: "#1E3A8A", whiteSpace: "nowrap" }}>
                        {worker.line}
                      </span>
                    </td>

                    {/* Check-in */}
                    <td style={{ padding: "11px 16px", verticalAlign: "middle" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: "#4B5063" }}>
                        <IconClock />
                        <span style={{ fontFamily: "monospace" }}>{worker.checkInTime}</span>
                      </div>
                    </td>

                    {/* Verification */}
                    <td style={{ padding: "11px 16px", verticalAlign: "middle" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
                        {(worker.verificationMethod === "face" || worker.verificationMethod === "both") && (
                          <div style={{ width: 26, height: 26, borderRadius: 6, background: "#DBEAFE", color: "#2563EB", display: "flex", alignItems: "center", justifyContent: "center" }}>
                            <IconFace />
                          </div>
                        )}
                        {(worker.verificationMethod === "fingerprint" || worker.verificationMethod === "both") && (
                          <div style={{ width: 26, height: 26, borderRadius: 6, background: "#EDE9FE", color: "#7C3AED", display: "flex", alignItems: "center", justifyContent: "center" }}>
                            <IconFingerprint />
                          </div>
                        )}
                      </div>
                    </td>

                    {/* Status */}
                    <td style={{ padding: "11px 16px", verticalAlign: "middle" }}>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 500, background: st.pillBg, color: st.pillColor, whiteSpace: "nowrap" }}>
                        {worker.status === "active" && <IconCheck />}
                        {worker.status === "break"  && <IconClock />}
                        {st.label}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

export default LiveWorkerStatus;