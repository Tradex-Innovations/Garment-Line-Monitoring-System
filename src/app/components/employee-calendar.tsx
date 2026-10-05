import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { EmployeePortalCalendar, EmployeePortalLeaveRequest } from "@/types/employee-portal";
import { Card } from "./ops-ui";

function moveMonth(month: string, difference: number) {
  const date = new Date(`${month}-01T12:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() + difference);
  return date.toISOString().slice(0, 7);
}

function daysFor(month: string) {
  const first = new Date(`${month}-01T12:00:00Z`);
  const days = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate();
  const offset = (first.getUTCDay() + 6) % 7;
  return Array.from({ length: Math.ceil((offset + days) / 7) * 7 }, (_, index) => {
    const day = index - offset + 1;
    return day >= 1 && day <= days ? `${month}-${String(day).padStart(2, "0")}` : null;
  });
}

function leaveOnDate(requests: EmployeePortalLeaveRequest[], date: string) {
  return requests.filter((request) => request.startDate <= date && request.endDate >= date);
}

const weekdays = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export function EmployeeCalendar({ month, onMonthChange, data, loading, error }: {
  month: string;
  onMonthChange: (value: string) => void;
  data: EmployeePortalCalendar | null;
  loading: boolean;
  error: string | null;
}) {
  const [selected, setSelected] = useState<string | null>(null);
  const cells = useMemo(() => daysFor(month), [month]);
  const attendance = useMemo(() => new Map(data?.attendance.map((row) => [row.date, row]) || []), [data]);
  const activeDate = selected?.startsWith(month) ? selected : `${month}-01`;
  const activeAttendance = attendance.get(activeDate);
  const activeLeave = leaveOnDate(data?.approvedLeave || [], activeDate);
  const monthTitle = new Date(`${month}-01T12:00:00Z`).toLocaleDateString("en-GB", {
    month: "long", year: "numeric", timeZone: "UTC",
  });

  return <Card title="Daily calendar" subtitle="Attendance, approved leave and recorded OT for the selected month.">
    <div className="ops-employee-calendar-toolbar">
      <button type="button" aria-label="Previous month" onClick={() => onMonthChange(moveMonth(month, -1))}><ChevronLeft size={18} /></button>
      <strong>{monthTitle}</strong>
      <button type="button" aria-label="Next month" onClick={() => onMonthChange(moveMonth(month, 1))}><ChevronRight size={18} /></button>
    </div>
    {loading ? <p role="status">Loading calendar…</p> : null}
    {error ? <p role="alert" className="ops-alert-banner tone-danger">{error}</p> : null}
    <div className="ops-employee-calendar-grid" aria-label={`${monthTitle} employee calendar`}>
      {weekdays.map((day) => <span key={day} className="ops-employee-calendar-weekday">{day}</span>)}
      {cells.map((date, index) => {
        if (!date) return <span key={`blank-${index}`} aria-hidden="true" className="ops-employee-calendar-empty" />;
        const record = attendance.get(date);
        const leave = leaveOnDate(data?.approvedLeave || [], date);
        return <button key={date} type="button" aria-pressed={date === activeDate}
          aria-label={`${date}${record ? `, ${record.status}` : ""}${leave.length ? ", approved leave" : ""}`}
          className={`ops-employee-calendar-day${date === activeDate ? " is-selected" : ""}`}
          onClick={() => setSelected(date)}>
          <span className="ops-employee-calendar-number">{Number(date.slice(-2))}</span>
          {record ? <span className="ops-employee-calendar-mark">{record.status.replace(/_/g, " ")}</span> : null}
          {leave.length ? <span className="ops-employee-calendar-mark is-leave">{leave.map((item) => item.leaveCategory.replace(/_/g, " ")).join(", ")}</span> : null}
          {Number(record?.otHours || 0) > 0 ? <span className="ops-employee-calendar-mark is-ot">OT {record?.otHours}h</span> : null}
        </button>;
      })}
    </div>
    <div className="ops-employee-calendar-detail" aria-live="polite">
      <strong>{activeDate}</strong>
      {activeAttendance ? <p>Attendance: {activeAttendance.status.replace(/_/g, " ")} · In {activeAttendance.timeIn?.slice(0, 5) || activeAttendance.faceFirstSeen?.slice(0, 5) || "—"} · Out {activeAttendance.timeOut?.slice(0, 5) || activeAttendance.faceLastSeen?.slice(0, 5) || "—"} · OT {activeAttendance.otHours || 0}h · Late/early {activeAttendance.lateEarlyHours || 0}h</p> : <p>No attendance record loaded for this date.</p>}
      {activeLeave.map((item) => <p key={item.id}>Approved {item.leaveCategory.replace(/_/g, " ")} leave ({item.leaveType.replace(/_/g, " ")}){item.startTime ? ` · ${item.startTime.slice(0, 5)}–${item.endTime?.slice(0, 5) || ""}` : ""}</p>)}
    </div>
  </Card>;
}
