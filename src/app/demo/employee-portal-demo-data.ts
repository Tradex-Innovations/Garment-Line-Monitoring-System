import type {
  EmployeePortalAttendance,
  EmployeePortalCalendar,
  EmployeePortalLeaveRequest,
  EmployeePortalPayslip,
  EmployeePortalSnapshot,
} from "@/types/employee-portal";

export const DEMO_EMPLOYEE_CODES = ["DEMO001", "DEMO002"] as const;
export type DemoEmployeeCode = (typeof DEMO_EMPLOYEE_CODES)[number];

export function isDemoEmployeeCode(value: string): value is DemoEmployeeCode {
  return DEMO_EMPLOYEE_CODES.includes(value as DemoEmployeeCode);
}

function dateAt(year: number, monthIndex: number, day: number) {
  return new Date(Date.UTC(year, monthIndex, day, 12)).toISOString().slice(0, 10);
}

function previousMonthPeriod(today: Date) {
  const year = today.getFullYear();
  const monthIndex = today.getMonth() - 1;
  const first = dateAt(year, monthIndex, 1);
  const last = dateAt(year, monthIndex + 1, 0);
  return { first, last };
}

function demoAttendance(today: Date, code: DemoEmployeeCode): EmployeePortalAttendance[] {
  const rows: EmployeePortalAttendance[] = [];
  for (let offset = 1; offset <= 22; offset += 1) {
    const day = new Date(today.getFullYear(), today.getMonth(), today.getDate() - offset);
    if (day.getDay() === 0) continue;
    const date = dateAt(day.getFullYear(), day.getMonth(), day.getDate());
    const late = offset === 5;
    rows.push({
      id: `demo-attendance-${code}-${date}`,
      date,
      status: late ? "late" : "present",
      timeIn: late ? "08:19:00" : "07:58:00",
      timeOut: offset % 4 === 0 ? "18:05:00" : "17:02:00",
      otHours: offset % 4 === 0 ? 1 : 0,
      lateEarlyHours: late ? 0.32 : 0,
    });
  }
  return rows;
}

export function createDemoPortal(code: DemoEmployeeCode, today = new Date()): {
  snapshot: EmployeePortalSnapshot;
  payslips: EmployeePortalPayslip[];
} {
  const isExecutive = code === "DEMO002";
  const name = isExecutive ? "Demo Executive" : "Demo Operator";
  const year = today.getFullYear();
  const approvedDate = dateAt(year, today.getMonth() - 1, 12);
  const pendingDate = dateAt(year, today.getMonth(), today.getDate() + 7);
  const rejectedDate = dateAt(year, today.getMonth() - 2, 8);
  const leaveRequests: EmployeePortalLeaveRequest[] = [
    {
      id: `demo-approved-${code}`,
      leaveType: "full_day",
      leaveCategory: "annual",
      startDate: approvedDate,
      endDate: approvedDate,
      reason: "Sample approved annual leave",
      status: "approved",
      requestedAt: `${dateAt(year, today.getMonth() - 1, 3)}T09:00:00Z`,
      reviewedAt: `${dateAt(year, today.getMonth() - 1, 4)}T10:00:00Z`,
      reviewNote: "Approved in this sample scenario.",
      dayCount: 1,
    },
    {
      id: `demo-pending-${code}`,
      leaveType: "half_day",
      leaveCategory: "casual",
      startDate: pendingDate,
      endDate: pendingDate,
      halfDaySession: "first_half",
      reason: "Sample personal appointment",
      status: "pending",
      requestedAt: today.toISOString(),
      dayCount: 0.5,
    },
    {
      id: `demo-rejected-${code}`,
      leaveType: "short_leave",
      leaveCategory: "personal",
      startDate: rejectedDate,
      endDate: rejectedDate,
      startTime: "14:00",
      endTime: "15:00",
      reason: "Sample request showing a review outcome",
      status: "rejected",
      requestedAt: `${dateAt(year, today.getMonth() - 2, 2)}T08:00:00Z`,
      reviewedAt: `${dateAt(year, today.getMonth() - 2, 3)}T11:00:00Z`,
      reviewNote: "Sample only — request a different time.",
      dayCount: 0,
    },
  ];
  const period = previousMonthPeriod(today);
  const basic = isExecutive ? 150000 : 90000;
  const overtime = isExecutive ? 0 : 5000;
  const allowance = isExecutive ? 15000 : 8000;
  const deductions = isExecutive ? 18000 : 10000;
  const payslips: EmployeePortalPayslip[] = [{
    id: `demo-payslip-${code}`,
    periodId: `demo-${period.first.slice(0, 7)}`,
    periodStart: period.first,
    periodEnd: period.last,
    result: {
      basic: basic.toFixed(2),
      gross: (basic + overtime + allowance).toFixed(2),
      deductions: deductions.toFixed(2),
      net: (basic + overtime + allowance - deductions).toFixed(2),
      lines: [
        { code: "BASIC", name: "Basic salary", category: "earning", amount: basic.toFixed(2) },
        ...(overtime ? [{ code: "OT", name: "Sample overtime", category: "earning", amount: overtime.toFixed(2) }] : []),
        { code: "ALLOWANCE", name: "Sample allowance", category: "earning", amount: allowance.toFixed(2) },
        { code: "DEDUCTION", name: "Sample deduction", category: "deduction", amount: deductions.toFixed(2) },
      ],
    },
    reviewStatus: "",
    reviewNote: "",
    reviewedAt: "",
  }];
  return {
    snapshot: {
      linked: true,
      profile: { id: `demo-profile-${code}`, fullName: name, role: "demo", employeeCode: code },
      employee: {
        id: `demo-employee-${code}`,
        employeeCode: code,
        fullName: name,
        designation: isExecutive ? "Sample Executive" : "Sample Sewing Operator",
        department: isExecutive ? "Sample Administration" : "Sample Production",
        shift: "Day shift",
        phone: null,
      },
      currentLine: {
        id: `demo-line-${code}`,
        code: isExecutive ? "DEMO-ADMIN" : "DEMO-L01",
        name: isExecutive ? "Sample Administration" : "Sample Line 01",
        department: isExecutive ? "Sample Administration" : "Sample Production",
        shift: "Day shift",
        supervisor: "Demo Supervisor",
        assignedAt: `${period.first}T08:00:00Z`,
      },
      attendanceHistory: demoAttendance(today, code).filter((record) => record.date !== approvedDate),
      leaveRequests,
      incentives: [{ id: `demo-incentive-${code}`, monthStart: period.first, amount: isExecutive ? 8000 : 3500, reason: "Sample performance incentive" }],
      leaveBalance: {
        year,
        configured: true,
        allowanceDays: 20,
        usedDays: 1,
        remainingDays: 19,
        categories: [
          { category: "annual", entitlementDays: 10, usedDays: 1, remainingDays: 9 },
          { category: "casual", entitlementDays: 5, usedDays: 0, remainingDays: 5 },
          { category: "sick", entitlementDays: 5, usedDays: 0, remainingDays: 5 },
        ],
      },
    },
    payslips,
  };
}

export function demoCalendar(snapshot: EmployeePortalSnapshot, month: string): EmployeePortalCalendar {
  return {
    month,
    attendance: snapshot.attendanceHistory.filter((record) => record.date.startsWith(month)),
    approvedLeave: snapshot.leaveRequests.filter((request) =>
      request.status === "approved" && request.startDate <= `${month}-31` && request.endDate >= `${month}-01`),
  };
}
