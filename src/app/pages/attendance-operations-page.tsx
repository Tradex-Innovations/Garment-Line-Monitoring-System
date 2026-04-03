import { useMemo } from "react";
import { useOperations } from "../operations-context";
import {
  Card,
  ExportActions,
  KpiCard,
  PageHeader,
  StatusBadge,
  downloadCsv,
  formatCurrency,
} from "../components/ops-ui";
import { Clock3, HandCoins, TimerReset, UserRoundX } from "lucide-react";

export function AttendanceOperationsPage() {
  const { workers, attendanceSummaries } = useOperations();

  const totals = useMemo(() => {
    const totalPayout = attendanceSummaries.reduce((sum, item) => sum + item.finalTotal, 0);
    const totalOt = attendanceSummaries.reduce((sum, item) => sum + item.otHours, 0);
    const totalLeave = attendanceSummaries.reduce((sum, item) => sum + item.leaveDays, 0);
    const avgAttendance =
      Math.round(
        (attendanceSummaries.reduce(
          (sum, item) => sum + item.daysPresent / Math.max(1, item.daysPresent + item.daysAbsent),
          0
        ) /
          attendanceSummaries.length) *
          100
      ) || 0;
    return { totalPayout, totalOt, totalLeave, avgAttendance };
  }, [attendanceSummaries]);

  const exportRows = [
    [
      "Employee ID",
      "Worker",
      "Days Present",
      "Days Absent",
      "OT Hours",
      "Leave Days",
      "Incentive",
      "Final Total",
    ],
    ...attendanceSummaries.map((row) => {
      const worker = workers.find((item) => item.id === row.workerId);
      return [
        worker?.employeeId || row.workerId,
        worker?.fullName || row.workerId,
        `${row.daysPresent}`,
        `${row.daysAbsent}`,
        `${row.otHours}`,
        `${row.leaveDays}`,
        `${row.incentive}`,
        `${row.finalTotal}`,
      ];
    }),
  ];

  return (
    <div className="ops-page">
      <PageHeader
        title="Attendance, OT, Leave & Incentives"
        subtitle="Payroll-ready operational attendance view with overtime, leave, incentive totals, and export actions for HR."
        actions={
          <ExportActions
            onExportCsv={() => downloadCsv("attendance-operations.csv", exportRows)}
            onPrint={() => window.print()}
          />
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Total Payout"
          value={formatCurrency(totals.totalPayout)}
          meta="Combined final totals from the current monthly summary."
          icon={HandCoins}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Average Attendance"
          value={`${totals.avgAttendance}%`}
          meta="Average attendance rate across the tracked workforce."
          icon={Clock3}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Total OT"
          value={`${totals.totalOt}h`}
          meta="Approved overtime hours from the current monthly snapshot."
          icon={TimerReset}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Leave Count"
          value={`${totals.totalLeave}`}
          meta="Total leave days included in payroll-ready calculations."
          icon={UserRoundX}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <Card title="Payroll-Ready Attendance Table" subtitle="Attendance, OT, leave, incentive, and final total columns aligned for export.">
        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Employee ID</th>
                <th>Worker Name</th>
                <th>Days Present</th>
                <th>Days Absent</th>
                <th>OT Hours</th>
                <th>Leave Days</th>
                <th>Incentive</th>
                <th>Final Total</th>
                <th>Validation Rate</th>
              </tr>
            </thead>
            <tbody>
              {attendanceSummaries.map((row) => {
                const worker = workers.find((item) => item.id === row.workerId);
                return (
                  <tr key={row.id}>
                    <td className="ops-monospace">{worker?.employeeId || row.workerId}</td>
                    <td>
                      <div className="ops-row-title">{worker?.fullName || row.workerId}</div>
                      <div className="ops-row-subtitle">{worker?.roleTitle}</div>
                    </td>
                    <td>{row.daysPresent}</td>
                    <td>{row.daysAbsent}</td>
                    <td>{row.otHours}</td>
                    <td>{row.leaveDays}</td>
                    <td>{formatCurrency(row.incentive)}</td>
                    <td>{formatCurrency(row.finalTotal)}</td>
                    <td>
                      <StatusBadge
                        label={`${row.validationRate}%`}
                        tone={row.validationRate >= 95 ? "success" : row.validationRate >= 85 ? "warning" : "danger"}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

export default AttendanceOperationsPage;
