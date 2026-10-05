import { useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router";
import { Image, LogOut, Send } from "lucide-react";
import type { EmployeePortalLeaveRequest, EmployeePortalPayslip } from "@/types/employee-portal";
import type { HalfDaySession, LeaveCategory, LeaveType } from "@/types/leave-management";
import { EmployeeCalendar } from "../components/employee-calendar";
import {
  Button, Card, EmptyState, MetricTile, PageHeader, StatusBadge, formatCurrency, getInitials,
} from "../components/ops-ui";
import {
  createDemoPortal, demoCalendar, isDemoEmployeeCode, DEMO_EMPLOYEE_CODES,
  type DemoEmployeeCode,
} from "../demo/employee-portal-demo-data";

type DemoData = ReturnType<typeof createDemoPortal>;

const todayDate = () => {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Colombo", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(new Date());
  const part = (name: string) => parts.find((item) => item.type === name)?.value || "";
  return `${part("year")}-${part("month")}-${part("day")}`;
};
const labelize = (value: string) => value.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
const money = (value: string) => formatCurrency(Number(value));

function dayCount(startDate: string, endDate: string) {
  const start = Date.parse(`${startDate}T12:00:00Z`);
  const end = Date.parse(`${endDate}T12:00:00Z`);
  return Math.floor((end - start) / 86400000) + 1;
}

function DemoPayslips({ items, onReview }: {
  items: EmployeePortalPayslip[];
  onReview: (id: string, action: "CONFIRMED" | "QUERY", note?: string) => void;
}) {
  const [queryText, setQueryText] = useState("");
  return <Card title="Draft payslips and self-review" subtitle="Illustrative amounts only. Confirming or querying changes this demo screen only.">
    <div className="ops-list">
      {items.map((item) => <div className="ops-list-item" key={item.id}>
        <div className="ops-item-header">
          <div>
            <div className="ops-item-title">{item.periodStart} to {item.periodEnd}</div>
            <div className="ops-row-subtitle">Sample draft payslip</div>
          </div>
          <StatusBadge label={item.reviewStatus || "Awaiting your review"} tone={item.reviewStatus === "QUERY" ? "warning" : item.reviewStatus ? "success" : "info"} />
        </div>
        <div className="ops-grid cols-4">
          <div>Basic<br /><strong>{money(item.result.basic)}</strong></div>
          <div>Gross<br /><strong>{money(item.result.gross)}</strong></div>
          <div>Deductions<br /><strong>{money(item.result.deductions)}</strong></div>
          <div>Net<br /><strong>{money(item.result.net)}</strong></div>
        </div>
        <details style={{ marginTop: 12 }}>
          <summary>View sample earnings and deductions</summary>
          <div className="ops-list">
            {item.result.lines.map((line) => <div className="ops-list-item" key={line.code}>
              <span>{line.name} ({line.category})</span>
              <strong style={{ marginLeft: 12 }}>{money(line.amount)}</strong>
            </div>)}
          </div>
        </details>
        {item.reviewStatus === "QUERY" ? <p>Your demo query: {item.reviewNote}</p> : null}
        {!item.reviewStatus ? <div className="ops-auth-form-stack" style={{ marginTop: 12 }}>
          <label className="ops-filter-group">
            <span className="ops-filter-label">Question about this sample payslip</span>
            <textarea className="ops-textarea" maxLength={1000} value={queryText}
              onChange={(event) => setQueryText(event.target.value)} placeholder="Describe a sample discrepancy" />
          </label>
          <div className="ops-item-actions">
            <Button tone="primary" onClick={() => onReview(item.id, "CONFIRMED")}>Confirm draft (demo)</Button>
            <Button tone="secondary" disabled={!queryText.trim()} onClick={() => onReview(item.id, "QUERY", queryText.trim())}>Raise query (demo)</Button>
          </div>
        </div> : null}
      </div>)}
    </div>
  </Card>;
}

export function EmployeePortalDemoPage() {
  const [inputCode, setInputCode] = useState("");
  const [employeeCode, setEmployeeCode] = useState<DemoEmployeeCode | null>(null);
  const [data, setData] = useState<DemoData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [calendarMonth, setCalendarMonth] = useState(todayDate().slice(0, 7));
  const [leaveForm, setLeaveForm] = useState({
    leaveType: "full_day" as LeaveType,
    leaveCategory: "casual" as LeaveCategory,
    startDate: todayDate(),
    endDate: todayDate(),
    startTime: "",
    endTime: "",
    halfDaySession: "first_half" as HalfDaySession,
    reason: "",
  });

  const snapshot = data?.snapshot;
  const calendar = useMemo(
    () => snapshot ? demoCalendar(snapshot, calendarMonth) : null,
    [snapshot, calendarMonth],
  );

  function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const code = inputCode.trim().toUpperCase();
    if (!isDemoEmployeeCode(code)) {
      setError(`Only ${DEMO_EMPLOYEE_CODES.join(" or ")} can be used here. Real employee numbers cannot open this demo.`);
      return;
    }
    setData(createDemoPortal(code));
    setEmployeeCode(code);
    setCalendarMonth(todayDate().slice(0, 7));
    setError(null);
    setMessage(null);
  }

  function signOut() {
    setEmployeeCode(null);
    setData(null);
    setInputCode("");
    setError(null);
    setMessage(null);
  }

  function applyLeave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!data || !employeeCode) return;
    const { leaveType, leaveCategory, startDate, reason } = leaveForm;
    const endDate = leaveType === "full_day" ? leaveForm.endDate : startDate;
    const days = leaveType === "full_day" ? dayCount(startDate, endDate) : leaveType === "half_day" ? 0.5 : 0;
    if (!startDate || !endDate || !Number.isFinite(days) || days < 0 || days > 31) {
      setError("Choose a valid date range of up to 31 days.");
      return;
    }
    if (leaveType === "short_leave" && (!leaveForm.startTime || !leaveForm.endTime || leaveForm.endTime <= leaveForm.startTime)) {
      setError("Choose a valid start and end time for short leave.");
      return;
    }
    const overlaps = data.snapshot.leaveRequests.some((request) =>
      ["pending", "approved"].includes(request.status) && request.startDate <= endDate && request.endDate >= startDate);
    if (overlaps) {
      setError("That date overlaps another pending or approved demo request.");
      return;
    }
    const request: EmployeePortalLeaveRequest = {
      id: `demo-request-${crypto.randomUUID()}`,
      leaveType, leaveCategory, startDate, endDate,
      startTime: leaveType === "short_leave" ? leaveForm.startTime : null,
      endTime: leaveType === "short_leave" ? leaveForm.endTime : null,
      halfDaySession: leaveType === "half_day" ? leaveForm.halfDaySession : null,
      reason: reason.trim() || null,
      status: "pending",
      requestedAt: new Date().toISOString(),
      dayCount: days,
    };
    setData({ ...data, snapshot: { ...data.snapshot, leaveRequests: [request, ...data.snapshot.leaveRequests] } });
    setLeaveForm((current) => ({ ...current, reason: "" }));
    setError(null);
    setMessage("Sample leave request submitted. It is pending in this browser only.");
  }

  function cancelLeave(id: string) {
    if (!data) return;
    setData({
      ...data,
      snapshot: {
        ...data.snapshot,
        leaveRequests: data.snapshot.leaveRequests.map((request) =>
          request.id === id && request.status === "pending" ? { ...request, status: "cancelled" } : request),
      },
    });
    setMessage("Sample request cancelled in this browser.");
  }

  function reviewPayslip(id: string, action: "CONFIRMED" | "QUERY", note = "") {
    if (!data) return;
    setData({
      ...data,
      payslips: data.payslips.map((item) => item.id === id ? {
        ...item, reviewStatus: action, reviewNote: note, reviewedAt: new Date().toISOString(),
      } : item),
    });
    setMessage(action === "QUERY" ? "Sample payslip query recorded in this browser." : "Sample payslip confirmed in this browser.");
  }

  return <div className="ops-page ops-self-service">
    <PageHeader title="Employee Portal Demo" subtitle="Explore the employee journey with fictional records only."
      actions={<>
        <Link className="ops-button ops-button-secondary" to="/employee-portal/manual">Real employee sign-in</Link>
        {employeeCode ? <Button tone="secondary" onClick={signOut}><LogOut size={15} /> Exit demo</Button> : null}
      </>} />
    <div className="ops-alert-banner tone-info" role="note">
      Synthetic demo: no real employee, attendance, leave, or salary records are loaded. Changes are not saved to the server and reset when you exit or refresh.
    </div>
    {error ? <div className="ops-alert-banner tone-danger" role="alert">{error}</div> : null}
    {message ? <div className="ops-alert-banner tone-info" role="status">{message}</div> : null}

    {!snapshot?.employee ? <section className="ops-grid cols-2">
      <Card title="Demo sign-in" subtitle="Enter a sample employee number. No password is needed because these records are fictional.">
        <form className="ops-auth-form-stack" onSubmit={signIn}>
          <label className="ops-filter-group">
            <span className="ops-filter-label">Demo employee number</span>
            <input className="ops-input" value={inputCode} autoComplete="off"
              onChange={(event) => setInputCode(event.target.value)} placeholder="DEMO001" required />
          </label>
          <Button tone="primary" type="submit">Open demo portal</Button>
        </form>
      </Card>
      <Card title="Available sample employees" subtitle="Both profiles use generated, non-production data.">
        <div className="ops-list">
          <div className="ops-list-item"><strong>DEMO001</strong> · Demo Operator · general workforce</div>
          <div className="ops-list-item"><strong>DEMO002</strong> · Demo Executive · executive staff</div>
        </div>
      </Card>
    </section> : <>
      <Card title={snapshot.employee.fullName} subtitle={`${snapshot.employee.employeeCode} · ${snapshot.employee.designation}`}>
        <div className="ops-worker-profile-hero">
          <div className="ops-worker-profile-photo ops-worker-profile-photo-placeholder">
            <Image size={22} /><span>{getInitials(snapshot.employee.fullName)}</span>
          </div>
          <div>
            <div className="ops-item-title">{snapshot.employee.fullName}</div>
            <div className="ops-row-subtitle">{snapshot.employee.department} · {snapshot.employee.shift}</div>
            <div className="ops-row-subtitle">Fictional employee profile</div>
          </div>
        </div>
      </Card>
      <section className="ops-grid cols-4">
        <MetricTile label="Current line" value={snapshot.currentLine?.name || "Unassigned"} />
        <MetricTile label="Latest attendance" value={labelize(snapshot.attendanceHistory[0]?.status || "No record")} />
        <MetricTile label="Sample leave balance" value={`${snapshot.leaveBalance.remainingDays} days`} />
        <MetricTile label="Pending requests" value={String(snapshot.leaveRequests.filter((item) => item.status === "pending").length)} />
      </section>
      <EmployeeCalendar month={calendarMonth} onMonthChange={setCalendarMonth} data={calendar} loading={false} error={null} />
      <section className="ops-grid cols-2">
        <Card title="Apply for leave" subtitle="Submit a sample request to see the employee-side workflow. No real approval is sent.">
          <form className="ops-auth-form-stack" onSubmit={applyLeave}>
            <div className="ops-grid cols-2">
              <label className="ops-filter-group"><span className="ops-filter-label">Leave type</span>
                <select className="ops-select" value={leaveForm.leaveType} onChange={(event) => setLeaveForm((current) => ({ ...current, leaveType: event.target.value as LeaveType }))}>
                  <option value="full_day">Full day</option><option value="half_day">Half day</option><option value="short_leave">Short leave</option>
                </select>
              </label>
              <label className="ops-filter-group"><span className="ops-filter-label">Category</span>
                <select className="ops-select" value={leaveForm.leaveCategory} onChange={(event) => setLeaveForm((current) => ({ ...current, leaveCategory: event.target.value as LeaveCategory }))}>
                  {(["annual", "casual", "sick", "no_pay", "emergency", "personal", "medical", "other"] as LeaveCategory[]).map((category) =>
                    <option value={category} key={category}>{labelize(category)}</option>)}
                </select>
              </label>
              <label className="ops-filter-group"><span className="ops-filter-label">Start date</span>
                <input className="ops-input" type="date" required value={leaveForm.startDate} onChange={(event) => setLeaveForm((current) => ({ ...current, startDate: event.target.value }))} />
              </label>
              {leaveForm.leaveType === "full_day" ? <label className="ops-filter-group"><span className="ops-filter-label">End date</span>
                <input className="ops-input" type="date" required value={leaveForm.endDate} onChange={(event) => setLeaveForm((current) => ({ ...current, endDate: event.target.value }))} />
              </label> : null}
              {leaveForm.leaveType === "half_day" ? <label className="ops-filter-group"><span className="ops-filter-label">Session</span>
                <select className="ops-select" value={leaveForm.halfDaySession} onChange={(event) => setLeaveForm((current) => ({ ...current, halfDaySession: event.target.value as HalfDaySession }))}>
                  <option value="first_half">First half</option><option value="second_half">Second half</option>
                </select>
              </label> : null}
              {leaveForm.leaveType === "short_leave" ? <>
                <label className="ops-filter-group"><span className="ops-filter-label">Start time</span>
                  <input className="ops-input" type="time" required value={leaveForm.startTime} onChange={(event) => setLeaveForm((current) => ({ ...current, startTime: event.target.value }))} />
                </label>
                <label className="ops-filter-group"><span className="ops-filter-label">End time</span>
                  <input className="ops-input" type="time" required value={leaveForm.endTime} onChange={(event) => setLeaveForm((current) => ({ ...current, endTime: event.target.value }))} />
                </label>
              </> : null}
            </div>
            <label className="ops-filter-group"><span className="ops-filter-label">Reason</span>
              <textarea className="ops-textarea" maxLength={500} value={leaveForm.reason} onChange={(event) => setLeaveForm((current) => ({ ...current, reason: event.target.value }))} placeholder="Optional sample reason" />
            </label>
            <Button tone="primary" type="submit"><Send size={15} /> Submit sample request</Button>
          </form>
        </Card>
        <Card title="Leave requests and balance" subtitle="Illustrative entitlements and review outcomes; not approved company policy.">
          <div className="ops-item-description" style={{ marginBottom: 14 }}>
            {snapshot.leaveBalance.categories.map((category) => <span key={category.category} style={{ marginRight: 18 }}>
              {labelize(category.category)}: {category.remainingDays} of {category.entitlementDays} days remaining
            </span>)}
          </div>
          <div className="ops-list">
            {snapshot.leaveRequests.map((request) => <div className="ops-list-item" key={request.id}>
              <div className="ops-item-header">
                <div>
                  <div className="ops-item-title">{labelize(request.leaveType)} · {labelize(request.leaveCategory)}</div>
                  <div className="ops-row-subtitle">{request.startDate}{request.endDate !== request.startDate ? ` to ${request.endDate}` : ""}</div>
                </div>
                <StatusBadge label={labelize(request.status)} tone={request.status === "approved" ? "success" : request.status === "pending" ? "warning" : request.status === "rejected" ? "danger" : "info"} />
              </div>
              {request.reason ? <div className="ops-item-description">{request.reason}</div> : null}
              {request.reviewNote ? <div className="ops-item-description">Sample review note: {request.reviewNote}</div> : null}
              {request.status === "pending" ? <div className="ops-item-actions"><Button tone="secondary" onClick={() => cancelLeave(request.id)}>Cancel sample request</Button></div> : null}
            </div>)}
          </div>
        </Card>
      </section>
      <DemoPayslips items={data!.payslips} onReview={reviewPayslip} />
      <section className="ops-grid cols-2">
        <Card title="Attendance history" subtitle="Sample clock events, status, overtime and late time.">
          <div className="ops-list">
            {snapshot.attendanceHistory.slice(0, 12).map((record) => <div className="ops-list-item" key={record.id}>
              <div className="ops-item-header">
                <div><div className="ops-item-title">{record.date}</div>
                  <div className="ops-row-subtitle">In {record.timeIn?.slice(0, 5)} · Out {record.timeOut?.slice(0, 5)}</div>
                </div>
                <StatusBadge label={labelize(record.status)} tone={record.status === "late" ? "warning" : "success"} />
              </div>
              <div className="ops-item-meta"><span>OT {record.otHours}h</span><span>Late/early {record.lateEarlyHours}h</span></div>
            </div>)}
            {!snapshot.attendanceHistory.length ? <EmptyState title="No sample records" description="This month has no demo attendance yet." /> : null}
          </div>
        </Card>
        <Card title="Current line and incentives" subtitle="Sample assignment and incentive visibility.">
          <div className="ops-list">
            <div className="ops-list-item">
              <div className="ops-item-title">{snapshot.currentLine?.name}</div>
              <div className="ops-row-subtitle">{snapshot.currentLine?.code} · {snapshot.currentLine?.shift}</div>
              <div className="ops-item-description">Supervisor: {snapshot.currentLine?.supervisor}</div>
            </div>
            {snapshot.incentives.map((incentive) => <div className="ops-list-item" key={incentive.id}>
              <div className="ops-item-header"><div><div className="ops-item-title">{incentive.monthStart}</div>
                <div className="ops-row-subtitle">{incentive.reason}</div></div>
                <StatusBadge label={formatCurrency(incentive.amount || 0)} tone="success" />
              </div>
            </div>)}
          </div>
        </Card>
      </section>
    </>}
  </div>;
}

export default EmployeePortalDemoPage;
