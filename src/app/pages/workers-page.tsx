import { useMemo, useState } from "react";
import { Link } from "react-router";
import { Download, UserPlus, Users } from "lucide-react";
import { useAuth } from "../auth";
import { useOperations, findLine } from "../operations-context";
import {
  Button,
  KpiCard,
  PageHeader,
  SearchField,
  StatusBadge,
  attendanceTone,
  downloadCsv,
  validationTone,
} from "../components/ops-ui";

export function WorkersPage() {
  const { canDo } = useAuth();
  const { workers, lines } = useOperations();
  const [search, setSearch] = useState("");
  const [department, setDepartment] = useState("All");
  const [status, setStatus] = useState("All");

  const departments = useMemo(
    () => ["All", ...new Set(workers.map((worker) => worker.department))],
    [workers]
  );

  const filteredWorkers = workers.filter((worker) => {
    const query = search.trim().toLowerCase();
    const matchesQuery =
      !query ||
      worker.fullName.toLowerCase().includes(query) ||
      worker.employeeId.toLowerCase().includes(query) ||
      worker.skills.join(" ").toLowerCase().includes(query);
    const matchesDepartment =
      department === "All" || worker.department === department;
    const matchesStatus =
      status === "All" ||
      worker.attendanceStatus === status ||
      worker.finalValidationStatus === status;
    return matchesQuery && matchesDepartment && matchesStatus;
  });

  const exportRows = [
    [
      "Employee ID",
      "Worker",
      "Department",
      "Role",
      "Current Line",
      "Shift",
      "Attendance",
      "Validation",
    ],
    ...filteredWorkers.map((worker) => [
      worker.employeeId,
      worker.fullName,
      worker.department,
      worker.roleTitle,
      findLine(lines, worker.currentLineId)?.name || "Unassigned",
      worker.shift,
      worker.attendanceStatus,
      worker.finalValidationStatus,
    ]),
  ];

  return (
    <div className="ops-page">
      <PageHeader
        title="Workers Management"
        subtitle="Operational roster view with validation state, current line assignment, and workforce readiness."
        actions={
          <>
            <Button tone="secondary" onClick={() => downloadCsv("workers.csv", exportRows)}>
              <Download size={15} />
              Export
            </Button>
            {canDo("manageWorkers") ? (
              <Button tone="primary">
                <UserPlus size={15} />
                Add Worker
              </Button>
            ) : null}
          </>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Total Workers"
          value={`${workers.length}`}
          meta="Profiles currently tracked in the operations centre."
          icon={Users}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="On Line"
          value={`${workers.filter((worker) => worker.currentStatus === "On Line").length}`}
          meta="Workers already placed on active production lines."
          icon={Users}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Pending Validation"
          value={`${workers.filter((worker) => worker.finalValidationStatus !== "Fully Validated").length}`}
          meta="Need review before attendance close and payroll finalisation."
          icon={Users}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Multi-Skill Pool"
          value={`${workers.filter((worker) => worker.skills.length >= 3).length}`}
          meta="Ready candidates for rebalancing understaffed lines."
          icon={Users}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <div className="ops-filter-bar">
        <SearchField
          value={search}
          onChange={setSearch}
          placeholder="Search by employee ID, name, or skill"
        />
        <select
          className="ops-select"
          style={{ flex: "0 0 190px" }}
          value={department}
          onChange={(event) => setDepartment(event.target.value)}
        >
          {departments.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
        <select
          className="ops-select"
          style={{ flex: "0 0 220px" }}
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        >
          <option value="All">All statuses</option>
          <option value="Present">Present</option>
          <option value="Late">Late</option>
          <option value="Absent">Absent</option>
          <option value="On Leave">On Leave</option>
          <option value="Fully Validated">Fully Validated</option>
          <option value="Pending Validation">Pending Validation</option>
          <option value="Unresolved Exception">Unresolved Exception</option>
        </select>
      </div>

      <section className="ops-table-card">
        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Worker</th>
                <th>Employee ID</th>
                <th>Department / Role</th>
                <th>Current Line</th>
                <th>Shift</th>
                <th>Attendance</th>
                <th>Validation</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredWorkers.map((worker) => (
                <tr key={worker.id}>
                  <td>
                    <div className="ops-row-title">{worker.fullName}</div>
                    <div className="ops-row-subtitle">{worker.phone}</div>
                  </td>
                  <td className="ops-monospace">{worker.employeeId}</td>
                  <td>
                    <div className="ops-row-title">{worker.department}</div>
                    <div className="ops-row-subtitle">{worker.roleTitle}</div>
                  </td>
                  <td>{findLine(lines, worker.currentLineId)?.name || "Unassigned"}</td>
                  <td>{worker.shift}</td>
                  <td>
                    <StatusBadge
                      label={worker.attendanceStatus}
                      tone={attendanceTone(worker.attendanceStatus)}
                    />
                  </td>
                  <td>
                    <StatusBadge
                      label={worker.finalValidationStatus}
                      tone={validationTone(worker.finalValidationStatus)}
                    />
                  </td>
                  <td>
                    <div className="ops-row-actions">
                      <Link to={`/workers/${worker.id}`} className="ops-link-button">
                        View Profile
                      </Link>
                      {canDo("assignLine") ? (
                        <Link to="/line-assignment" className="ops-link-button">
                          Assign Line
                        </Link>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

export default WorkersPage;
