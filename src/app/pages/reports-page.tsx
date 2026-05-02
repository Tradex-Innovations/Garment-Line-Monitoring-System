import { useMemo } from "react";
import { FileSpreadsheet, Printer } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useOperations } from "../operations-context";
import {
  Card,
  ExportActions,
  KpiCard,
  PageHeader,
  downloadCsv,
  formatCurrency,
} from "../components/ops-ui";

export function ReportsPage() {
  const {
    attendanceOverview,
    departmentAttendance,
    reportSeries,
    attendanceSummaries,
    alerts,
    transferLogs,
  } = useOperations();

  const totalPayout = attendanceSummaries.reduce((sum, item) => sum + item.finalTotal, 0);
  const totalIncentive = attendanceSummaries.reduce((sum, item) => sum + item.incentive, 0);
  const openAlerts = alerts.filter((item) => item.status !== "Resolved").length;
  const totalTransfers = transferLogs.length;

  const exportRows = useMemo(
    () => [
      ["Report", "Metric", "Value"],
      ["Attendance", "Clocked in today", `${attendanceOverview.presentWorkers + attendanceOverview.lateWorkers}`],
      ["Attendance", "Absent today", `${attendanceOverview.absentWorkers}`],
      ["Attendance", "On leave today", `${attendanceOverview.onLeaveWorkers}`],
      ["Payroll", "Estimated payout", formatCurrency(totalPayout)],
      ["Payroll", "Estimated incentive pool", formatCurrency(totalIncentive)],
      ["Alerts", "Open alerts", `${openAlerts}`],
      ["Transfers", "Transfer logs", `${totalTransfers}`],
      ...departmentAttendance.map((department) => [
        "Department Attendance",
        department.department,
        `${department.presentWorkers + department.lateWorkers}/${department.totalWorkers}`,
      ]),
    ],
    [
      attendanceOverview.absentWorkers,
      attendanceOverview.lateWorkers,
      attendanceOverview.onLeaveWorkers,
      attendanceOverview.presentWorkers,
      departmentAttendance,
      openAlerts,
      totalIncentive,
      totalPayout,
      totalTransfers,
    ]
  );

  return (
    <div className="ops-page">
      <PageHeader
        title="Reports"
        subtitle="Attendance-led reports built from fingerprint attendance, line assignments, alerts, and audit-friendly operational data."
        actions={
          <>
            <button className="ops-button ops-button-secondary">
              <FileSpreadsheet size={15} />
              Excel-ready
            </button>
            <ExportActions
              onExportCsv={() => downloadCsv("operations-reports.csv", exportRows)}
              onPrint={() => window.print()}
            />
          </>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Clocked In Today"
          value={`${attendanceOverview.presentWorkers + attendanceOverview.lateWorkers}/${attendanceOverview.totalWorkers}`}
          meta="Latest attendance snapshot built from fingerprint attendance data."
          icon={Printer}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Estimated Payout"
          value={formatCurrency(totalPayout)}
          meta="Current monthly total across attendance, OT, and incentive values."
          icon={FileSpreadsheet}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Open Alerts"
          value={`${openAlerts}`}
          meta="Operational alerts still open across attendance, lines, and exception handling."
          icon={Printer}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
        <KpiCard
          label="Transfers Logged"
          value={`${totalTransfers}`}
          meta="Line transfer records currently available in the operations database."
          icon={FileSpreadsheet}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <section className="ops-grid cols-2">
        <Card title="Weekly Attendance Trend" subtitle="On-time arrivals, absent or leave counts, and late arrivals across recent attendance days.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={reportSeries.weeklyAttendance}>
                <CartesianGrid stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="value" name="On Time" fill="#16a34a" radius={[6, 6, 0, 0]} />
                <Bar dataKey="secondaryValue" name="Absent / Leave" fill="#dc2626" radius={[6, 6, 0, 0]} />
                <Bar dataKey="tertiaryValue" name="Late" fill="#d97706" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card title="Department Attendance" subtitle="Present headcount against total department roster from the latest snapshot.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={reportSeries.departmentAttendance}>
                <CartesianGrid stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="value" name="Came Today" fill="#2563eb" radius={[6, 6, 0, 0]} />
                <Bar dataKey="secondaryValue" name="Total Staff" fill="#94a3b8" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card title="Line Attendance" subtitle="Assigned versus attended workers across production lines.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={reportSeries.lineAttendance}>
                <CartesianGrid stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="value" name="Came Today" fill="#7c3aed" radius={[6, 6, 0, 0]} />
                <Bar dataKey="secondaryValue" name="Assigned" fill="#cbd5f5" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card title="Transfer History" subtitle="Recent worker transfer volume for line balancing and staffing support.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={reportSeries.transferHistory}>
                <CartesianGrid stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Bar dataKey="value" name="Transfers" fill="#0f766e" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </section>
    </div>
  );
}

export default ReportsPage;
