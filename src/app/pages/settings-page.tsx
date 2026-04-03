import { Shield, SlidersHorizontal } from "lucide-react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import { roleLabels } from "../permissions";
import { Button, Card, KpiCard, PageHeader, StatusBadge } from "../components/ops-ui";

function SettingSwitch({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 16,
        padding: "14px 0",
        borderBottom: "1px solid #eef2f7",
      }}
    >
      <div>
        <div className="ops-item-title">{label}</div>
        <div className="ops-row-subtitle">{description}</div>
      </div>
      <button
        type="button"
        onClick={() => onChange(!checked)}
        className="ops-button"
        style={{
          minWidth: 64,
          minHeight: 34,
          borderRadius: 999,
          background: checked ? "var(--ops-primary)" : "#d6dbe6",
          color: "#fff",
        }}
      >
        {checked ? "On" : "Off"}
      </button>
    </div>
  );
}

export function SettingsPage() {
  const { currentUser } = useAuth();
  const { settings, updateSetting } = useOperations();

  const saveToggle = <K extends keyof typeof settings>(key: K, value: (typeof settings)[K]) =>
    updateSetting({ key, value, actor: currentUser.name });

  const roleAccessRows = [
    { role: "admin", access: "Full operational access, settings, audit, and all actions." },
    { role: "supervisor", access: "Lines, workers, transfers, alerts, and floor balancing." },
    { role: "hr", access: "Validation, attendance, leave, OT, incentive, and reports." },
    { role: "viewer", access: "Read-only dashboard, reports, self-service preview, and display mode." },
  ] as const;

  return (
    <div className="ops-page">
      <PageHeader
        title="Settings"
        subtitle="Security, attendance, alerts, and role access controls for the operations centre."
        actions={<Button tone="primary">Settings auto-save in demo</Button>}
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Biometric Modes"
          value={`${Number(settings.faceRecognition) + Number(settings.fingerprintVerification)}/2`}
          meta="Face and fingerprint verification currently enabled."
          icon={Shield}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Manual Fallback"
          value={settings.manualVerificationFallback ? "Enabled" : "Disabled"}
          meta="Supervisor override availability during validation exceptions."
          icon={SlidersHorizontal}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Late Threshold"
          value={`${settings.lateArrivalThreshold}m`}
          meta="Attendance anomaly limit before HR review is required."
          icon={SlidersHorizontal}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Daily Summary"
          value={settings.dailySummaryReport ? "On" : "Off"}
          meta="End-of-day management reporting automation."
          icon={Shield}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <section className="ops-grid cols-2">
        <Card title="Verification & Security" subtitle="Biometric and exception handling controls.">
          <SettingSwitch
            label="Face Recognition"
            description="Enable face recognition at gates and workstation validation points."
            checked={settings.faceRecognition}
            onChange={(value) => saveToggle("faceRecognition", value)}
          />
          <SettingSwitch
            label="Fingerprint Verification"
            description="Require fingerprint validation at time capture devices."
            checked={settings.fingerprintVerification}
            onChange={(value) => saveToggle("fingerprintVerification", value)}
          />
          <SettingSwitch
            label="Dual Validation Required"
            description="Force both biometric methods before release to sensitive areas."
            checked={settings.dualValidationRequired}
            onChange={(value) => saveToggle("dualValidationRequired", value)}
          />
          <SettingSwitch
            label="Manual Verification Fallback"
            description="Allow HR or supervisors to resolve edge cases when devices fail."
            checked={settings.manualVerificationFallback}
            onChange={(value) => saveToggle("manualVerificationFallback", value)}
          />
        </Card>

        <Card title="Attendance Controls" subtitle="Shift timing and alert behaviour for workforce operations.">
          <div className="ops-meta-grid">
            <div className="ops-key-value">
              <div className="ops-key-value-label">Shift Start</div>
              <div className="ops-key-value-value">{settings.morningShiftStart}</div>
            </div>
            <div className="ops-key-value">
              <div className="ops-key-value-label">Shift End</div>
              <div className="ops-key-value-value">{settings.morningShiftEnd}</div>
            </div>
            <div className="ops-key-value">
              <div className="ops-key-value-label">Late Threshold</div>
              <div className="ops-key-value-value">{settings.lateArrivalThreshold} minutes</div>
            </div>
            <div className="ops-key-value">
              <div className="ops-key-value-label">Grace Period</div>
              <div className="ops-key-value-value">{settings.gracePeriod} minutes</div>
            </div>
          </div>

          <div className="ops-card-divider" />

          <SettingSwitch
            label="Auto Mark Absent"
            description="Automatically flag absent workers when no biometric event is captured after the threshold."
            checked={settings.autoMarkAbsent}
            onChange={(value) => saveToggle("autoMarkAbsent", value)}
          />
          <SettingSwitch
            label="Failed Entry Alerts"
            description="Create immediate alerts for unknown faces and invalid gate attempts."
            checked={settings.failedEntryAlerts}
            onChange={(value) => saveToggle("failedEntryAlerts", value)}
          />
          <SettingSwitch
            label="Low Efficiency Warnings"
            description="Send alerts when line efficiency drops below configured operational limits."
            checked={settings.lowEfficiencyWarnings}
            onChange={(value) => saveToggle("lowEfficiencyWarnings", value)}
          />
          <SettingSwitch
            label="Daily Summary Report"
            description="Produce end-of-day rollups for management and payroll support."
            checked={settings.dailySummaryReport}
            onChange={(value) => saveToggle("dailySummaryReport", value)}
          />
        </Card>
      </section>

      <Card title="Role-Based Access Model" subtitle="Current route and action boundaries used throughout the frontend prototype.">
        <div className="ops-list">
          {roleAccessRows.map((row) => (
            <div key={row.role} className="ops-list-item">
              <div className="ops-item-header">
                <div className="ops-item-title">{roleLabels[row.role]}</div>
                <StatusBadge
                  label={row.role === currentUser.role ? "Current Session" : "Available"}
                  tone={row.role === currentUser.role ? "info" : "neutral"}
                />
              </div>
              <div className="ops-item-description">{row.access}</div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default SettingsPage;
