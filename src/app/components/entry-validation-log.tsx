import { useState } from "react";

// ─── Data ─────────────────────────────────────────────────────────────────────
const mockEntryLogs = [
  { id: "1", timestamp: "08:15:23", employeeName: "James Wilson",   employeeId: "EMP-2404", verificationType: "both",        status: "validated", reason: null },
  { id: "2", timestamp: "08:12:45", employeeName: "Unknown Person",  employeeId: "N/A",      verificationType: "face",        status: "rejected",  reason: "Face not recognized" },
  { id: "3", timestamp: "08:10:12", employeeName: "Lisa Anderson",   employeeId: "EMP-2407", verificationType: "fingerprint", status: "pending",   reason: "Manual verification required" },
  { id: "4", timestamp: "08:05:33", employeeName: "Robert Kim",      employeeId: "EMP-2408", verificationType: "both",        status: "validated", reason: null },
  { id: "5", timestamp: "08:02:18", employeeName: "Michael Chen",    employeeId: "EMP-2402", verificationType: "face",        status: "validated", reason: null },
  { id: "6", timestamp: "08:00:05", employeeName: "Priya Patel",     employeeId: "EMP-2403", verificationType: "fingerprint", status: "validated", reason: null },
];

// ─── Maps ─────────────────────────────────────────────────────────────────────
const STATUS = {
  validated: { iconBg: "#DCFCE7", iconColor: "#16A34A", pillBg: "#DCFCE7", pillColor: "#14532D", label: "Validated" },
  rejected:  { iconBg: "#FEE2E2", iconColor: "#DC2626", pillBg: "#FEE2E2", pillColor: "#7F1D1D", label: "Rejected"  },
  pending:   { iconBg: "#FEF3C7", iconColor: "#D97706", pillBg: "#FEF3C7", pillColor: "#78350F", label: "Pending"   },
};

const AVATAR_COLORS = [
  { bg: "#DBEAFE", color: "#1E3A8A" },
  { bg: "#FEE2E2", color: "#7F1D1D" },
  { bg: "#FEF3C7", color: "#78350F" },
  { bg: "#DBEAFE", color: "#1E3A8A" },
  { bg: "#DBEAFE", color: "#1E3A8A" },
  { bg: "#DCFCE7", color: "#14532D" },
];

// ─── Inline SVG icons ─────────────────────────────────────────────────────────
function IconCheck({ color }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13 }}>
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  );
}
function IconX({ color }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13 }}>
      <circle cx="12" cy="12" r="10" /><line x1="15" y1="9" x2="9" y2="15" /><line x1="9" y1="9" x2="15" y2="15" />
    </svg>
  );
}
function IconWarn({ color }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13 }}>
      <path d="m10.29 3.86-8.6 14.9A2 2 0 0 0 3.41 22h17.18a2 2 0 0 0 1.72-3.01L13.71 3.86a2 2 0 0 0-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  );
}
function IconFace() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /><polyline points="16 11 17 13 21 11" />
    </svg>
  );
}
function IconFingerprint() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" style={{ width: 11, height: 11 }}>
      <path d="M2 12C2 6.48 6.48 2 12 2" /><path d="M5 19.5C5.5 17 6 15 9 13.5" /><path d="M22 12c0 5.52-4.48 10-10 10" /><path d="M19 4.55A10 10 0 0 1 22 12" /><path d="M12 7a5 5 0 0 1 5 5" /><path d="M12 7a5 5 0 0 0-5 5c0 2.5.5 4 2 5.5" /><path d="M12 11v1" />
    </svg>
  );
}

function StatusIcon({ status }) {
  const s = STATUS[status];
  const Icon = status === "validated" ? IconCheck : status === "rejected" ? IconX : IconWarn;
  return (
    <div style={{ width: 26, height: 26, borderRadius: "50%", background: s.iconBg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
      <Icon color={s.iconColor} />
    </div>
  );
}

// ─── Component ────────────────────────────────────────────────────────────────
export function EntryValidationLog() {
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
            <div style={{ fontSize: 14, fontWeight: 600, color: "#111318" }}>Entry Validation Log</div>
            <div style={{ fontSize: 12, color: "#8B90A0", marginTop: 2 }}>Recent access attempts and verification results</div>
          </div>
          <span style={{ fontSize: 12, color: "#2563EB", fontWeight: 500, cursor: "pointer" }}>View all →</span>
        </div>

        {/* Rows */}
        <div>
          {mockEntryLogs.map((log, i) => {
            const s      = STATUS[log.status];
            const av     = AVATAR_COLORS[i % AVATAR_COLORS.length];
            const isLast = i === mockEntryLogs.length - 1;
            const initials = log.employeeId === "N/A"
              ? "??"
              : log.employeeName.split(" ").map((n) => n[0]).join("");

            return (
              <div
                key={log.id}
                onMouseEnter={() => setHovered(log.id)}
                onMouseLeave={() => setHovered(null)}
                style={{
                  display: "flex",
                  alignItems: "flex-start",
                  gap: 10,
                  padding: "12px 20px",
                  borderBottom: isLast ? "none" : "1px solid #F0F1F4",
                  background: hovered === log.id ? "#F5F6F8" : "#FFFFFF",
                  transition: "background 0.15s",
                }}
              >
                {/* Avatar */}
                <div style={{ width: 30, height: 30, borderRadius: "50%", background: av.bg, color: av.color, fontSize: 10, fontWeight: 600, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, marginTop: 1 }}>
                  {initials}
                </div>

                {/* Main content */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  {/* Name row */}
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, marginBottom: 2 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 6, minWidth: 0 }}>
                      <StatusIcon status={log.status} />
                      <span style={{ fontSize: 13, fontWeight: 600, color: "#111318", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {log.employeeName}
                      </span>
                      {log.employeeId !== "N/A" && (
                        <span style={{ fontSize: 11, color: "#8B90A0", fontFamily: "monospace", flexShrink: 0 }}>
                          {log.employeeId}
                        </span>
                      )}
                    </div>
                    {/* Status pill */}
                    <span style={{ display: "inline-flex", alignItems: "center", padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 500, background: s.pillBg, color: s.pillColor, whiteSpace: "nowrap", flexShrink: 0 }}>
                      {s.label}
                    </span>
                  </div>

                  {/* Timestamp */}
                  <div style={{ fontSize: 10, color: "#8B90A0", fontFamily: "monospace", marginBottom: log.reason || true ? 6 : 0 }}>
                    {log.timestamp}
                  </div>

                  {/* Reason */}
                  {log.reason && (
                    <div style={{ fontSize: 11, color: "#4B5063", marginBottom: 6, lineHeight: 1.4 }}>
                      {log.reason}
                    </div>
                  )}

                  {/* Verification method chips */}
                  <div style={{ display: "flex", gap: 5 }}>
                    {(log.verificationType === "face" || log.verificationType === "both") && (
                      <div style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "2px 8px", borderRadius: 6, background: "#DBEAFE", color: "#2563EB", fontSize: 11, fontWeight: 500 }}>
                        <IconFace /> Face
                      </div>
                    )}
                    {(log.verificationType === "fingerprint" || log.verificationType === "both") && (
                      <div style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "2px 8px", borderRadius: 6, background: "#EDE9FE", color: "#7C3AED", fontSize: 11, fontWeight: 500 }}>
                        <IconFingerprint /> Fingerprint
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div style={{ padding: "12px 20px", borderTop: "1px solid #E2E4EA", textAlign: "center" }}>
          <button style={{ fontSize: 12, fontWeight: 500, color: "#2563EB", background: "none", border: "none", cursor: "pointer" }}>
            View All Logs
          </button>
        </div>
      </div>
    </>
  );
}

export default EntryValidationLog;