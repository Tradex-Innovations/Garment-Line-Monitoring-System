import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createDemoPortal, demoCalendar, isDemoEmployeeCode,
} from "./employee-portal-demo-data.ts";

test("only fictional demo employee numbers are accepted", () => {
  assert.equal(isDemoEmployeeCode("DEMO001"), true);
  assert.equal(isDemoEmployeeCode("DEMO002"), true);
  assert.equal(isDemoEmployeeCode("10387"), false);
  assert.equal(isDemoEmployeeCode("PAYTEST001"), false);
});

test("both demo employees have complete, internally consistent sample data", () => {
  for (const code of ["DEMO001", "DEMO002"]) {
    const { snapshot, payslips } = createDemoPortal(code, new Date("2026-10-15T10:00:00Z"));
    assert.equal(snapshot.employee?.employeeCode, code);
    assert.ok(snapshot.currentLine);
    assert.ok(snapshot.attendanceHistory.length > 0);
    assert.ok(snapshot.leaveRequests.some((request) => request.status === "approved"));
    assert.ok(snapshot.leaveRequests.some((request) => request.status === "pending"));
    assert.ok(snapshot.incentives.length > 0);
    assert.ok(snapshot.leaveBalance.categories.length > 0);
    const approved = snapshot.leaveRequests.find((request) => request.status === "approved");
    assert.ok(snapshot.attendanceHistory.every((record) => record.date !== approved.startDate));
    const slip = payslips[0];
    assert.ok(slip);
    assert.equal(Number(slip.result.gross) - Number(slip.result.deductions), Number(slip.result.net));
    assert.equal(
      slip.result.lines.filter((line) => line.category === "earning").reduce((total, line) => total + Number(line.amount), 0),
      Number(slip.result.gross),
    );
  }
});

test("calendar only includes requested month and approved leave", () => {
  const { snapshot } = createDemoPortal("DEMO001", new Date("2026-10-15T10:00:00Z"));
  const september = demoCalendar(snapshot, "2026-09");
  assert.ok(september.attendance.every((row) => row.date.startsWith("2026-09")));
  assert.equal(september.approvedLeave.length, 1);
  assert.ok(september.approvedLeave.every((request) => request.status === "approved"));
  assert.equal(demoCalendar(snapshot, "2026-11").approvedLeave.length, 0);
});
