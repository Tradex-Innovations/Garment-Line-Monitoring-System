import { useMemo } from "react";
import { Download, FileSpreadsheet, Printer } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
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

const pieColors = ["#2563eb", "#7c3aed", "#16a34a", "#d97706", "#94a3b8"];

export function ReportsPage() {
  const {
    reportSeries,
    attendanceSummaries,
    alerts,
    transferLogs,
    incentiveRecords,
    lines,
  } = useOperations();

  const totalPayout = attendanceSummaries.reduce((sum, item) => sum + item.finalTotal, 0);
  const averageAttendance = Math.round(
    attendanceSummaries.reduce(
      (sum, item) =>
        sum + item.daysPresent / Math.max(1, item.daysPresent + item.daysAbsent),
      0
    ) /
      attendanceSummaries.length *
      100
  );
  const unresolvedExceptions = alerts.filter((item) => item.status !== "Resolved").length;
  const totalTransfers = transferLogs.length;

  const exportRows = useMemo(
    () => [
      ["Report", "Metric", "Value"],
      ["Attendance", "Average attendance", `${averageAttendance}%`],
      ["Payroll", "Total payout", formatCurrency(totalPayout)],
      ["Exceptions", "Open alerts", `${unresolvedExceptions}`],
      ["Transfers", "Transfer logs", `${totalTransfers}`],
      ...lines.map((line) => ["Line utilization", line.name, `${line.efficiency}%`]),
    ],
    [averageAttendance, lines, totalPayout, totalTransfers, unresolvedExceptions]
  );

  const reportCards = [
    {
      title: "Attendance Reports",
      description:
        "Daily and monthly attendance summaries with validation compliance and late-arrival visibility.",
      meta: `${attendanceSummaries.length} payroll-ready worker rows`,
    },
    {
      title: "Line Utilization Reports",
      description:
        "Efficiency, manpower gap, output, and risk posture by production line.",
      meta: `${lines.length} lines monitored`,
    },
    {
      title: "Exception Reports",
      description:
        "Face-only, fingerprint-only, mismatch, anomaly, and unresolved exception coverage.",
      meta: `${unresolvedExceptions} open exceptions`,
    },
    {
      title: "Transfer History Reports",
      description:
        "Supervisor balancing decisions with reason codes, timestamps, and impact review.",
      meta: `${totalTransfers} recorded movements`,
    },
    {
      title: "OT & Incentive Reports",
      description:
        "Overtime, incentive payout, and payroll-ready totals grouped for operations and HR.",
      meta: `${incentiveRecords.length} incentive records`,
    },
  ];

  return (
    <div className="ops-page">
      <PageHeader
        title="Reports & Analytics"
        subtitle="Readable, export-ready operational reporting across attendance, utilization, exceptions, transfers, and incentive outcomes."
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
          label="Total Payout"
          value={formatCurrency(totalPayout)}
          meta="Attendance, OT, and incentives combined for current payroll view."
          icon={Download}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Average Attendance"
          value={`${averageAttendance}%`}
          meta="Across the active payroll roster for March 2026."
          icon={Printer}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Open Exceptions"
          value={`${unresolvedExceptions}`}
          meta="Exception items still affecting validation or line readiness."
          icon={Download}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
        <KpiCard
          label="Transfer Moves"
          value={`${totalTransfers}`}
          meta="Supervisor and admin workforce changes currently logged."
          icon={FileSpreadsheet}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <section className="ops-report-grid">
        {reportCards.map((card) => (
          <article key={card.title} className="ops-report-card">
            <h3>{card.title}</h3>
            <p>{card.description}</p>
            <div className="ops-item-meta">
              <span>{card.meta}</span>
            </div>
          </article>
        ))}
      </section>

      <section className="ops-grid cols-2">
        <Card title="Weekly Attendance Trend" subtitle="Present, absent, and late movement across the last six working days.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={reportSeries.weeklyAttendance}>
                <CartesianGrid stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="value" name="Present" fill="#16a34a" radius={[6, 6, 0, 0]} />
                <Bar dataKey="secondaryValue" name="Absent" fill="#dc2626" radius={[6, 6, 0, 0]} />
                <Bar dataKey="tertiaryValue" name="Late" fill="#d97706" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card title="Monthly Production vs Target" subtitle="Production output trend kept readable for management and planning reviews.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <LineChart data={reportSeries.monthlyOutput}>
                <CartesianGrid stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="value" name="Actual" stroke="#2563eb" strokeWidth={3} dot={{ r: 4 }} />
                <Line type="monotone" dataKey="secondaryValue" name="Target" stroke="#94a3b8" strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </section>

      <section className="ops-grid cols-2">
        <Card title="Line Utilization" subtitle="Efficiency share across the factory floor without overcrowding the chart.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <PieChart>
                <Pie data={reportSeries.lineUtilization} dataKey="value" nameKey="label" innerRadius={62} outerRadius={96}>
                  {reportSeries.lineUtilization.map((entry, index) => (
                    <Cell key={entry.label} fill={pieColors[index % pieColors.length]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card title="Transfer History" subtitle="Weekly transfer volume supporting staffing balance and exception recovery.">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={reportSeries.transferHistory}>
                <CartesianGrid stroke="#eef2f7" vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} />
                <Tooltip />
                <Bar dataKey="value" name="Transfers" fill="#7c3aed" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </section>
    </div>
  );
}

export default ReportsPage;
