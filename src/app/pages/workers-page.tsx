import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Download, Users } from "lucide-react";
import { useAuth } from "../auth";
import { useOperations, findLine } from "../operations-context";
import {
  Button,
  KpiCard,
  PageHeader,
  SearchField,
  StatusBadge,
  WorkerChip,
  attendanceTone,
  downloadCsv,
  validationTone,
} from "../components/ops-ui";

const WORKERS_PAGE_SIZE = 50;

export function WorkersPage() {
  const { canAccess } = useAuth();
  const { workers, lines } = useOperations();
  const [search, setSearch] = useState("");
  const [department, setDepartment] = useState("All");
  const [status, setStatus] = useState("All");
  const [page, setPage] = useState(1);

  const departments = useMemo(
    () => ["All", ...new Set(workers.map((worker) => worker.department).sort())],
    [workers]
  );

  const filteredWorkers = useMemo(
    () =>
      workers.filter((worker) => {
        const query = search.trim().toLowerCase();
        const matchesQuery =
          !query ||
          worker.fullName.toLowerCase().includes(query) ||
          worker.employeeId.toLowerCase().includes(query) ||
          worker.roleTitle.toLowerCase().includes(query);
        const matchesDepartment = department === "All" || worker.department === department;
        const matchesStatus = status === "All" || worker.attendanceStatus === status;
        return matchesQuery && matchesDepartment && matchesStatus;
      }),
    [department, search, status, workers]
  );

  const totalPages = Math.max(1, Math.ceil(filteredWorkers.length / WORKERS_PAGE_SIZE));
  const pagedWorkers = useMemo(() => {
    const start = (page - 1) * WORKERS_PAGE_SIZE;
    return filteredWorkers.slice(start, start + WORKERS_PAGE_SIZE);
  }, [filteredWorkers, page]);
  const workerStart = filteredWorkers.length === 0 ? 0 : (page - 1) * WORKERS_PAGE_SIZE + 1;
  const workerEnd = Math.min(page * WORKERS_PAGE_SIZE, filteredWorkers.length);

  useEffect(() => {
    setPage(1);
  }, [department, search, status]);

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  const exportRows = [
    [
      "Employee ID",
      "Worker",
      "Department",
      "Role",
      "Current Line",
      "Shift",
      "Attendance",
      "Security Check",
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
        title="Workers"
        subtitle="Live worker directory with fingerprint attendance, line assignment, and security-check visibility."
        actions={
          <Button tone="secondary" onClick={() => downloadCsv("workers.csv", exportRows)}>
            <Download size={15} />
            Export
          </Button>
        }
      />

      <section className="ops-kpi-grid">
        <KpiCard
          label="Total Workers"
          value={`${workers.length}`}
          meta="Active employee records currently available in the operations workspace."
          icon={Users}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Present Today"
          value={`${workers.filter((worker) => worker.attendanceStatus === "Present" || worker.attendanceStatus === "Late").length}`}
          meta="Workers who have clocked in through the fingerprint attendance source."
          icon={Users}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="On Leave"
          value={`${workers.filter((worker) => worker.attendanceStatus === "On Leave").length}`}
          meta="Workers currently tagged as leave from imported attendance data."
          icon={Users}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
        <KpiCard
          label="Unassigned"
          value={`${workers.filter((worker) => !worker.currentLineId).length}`}
          meta="Workers without an active production-line assignment."
          icon={Users}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
      </section>

      <div className="ops-filter-bar">
        <SearchField
          value={search}
          onChange={setSearch}
          placeholder="Search by employee number, name, or role"
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
          style={{ flex: "0 0 200px" }}
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        >
          <option value="All">All attendance</option>
          <option value="Present">Present</option>
          <option value="Late">Late</option>
          <option value="Absent">Absent</option>
          <option value="On Leave">On Leave</option>
        </select>
      </div>

      <section className="ops-table-card">
        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Worker</th>
                <th>Department / Role</th>
                <th>Current Line</th>
                <th>Shift</th>
                <th>Attendance</th>
                <th>Security Check</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pagedWorkers.map((worker) => (
                <tr key={worker.id}>
                  <td>
                    <WorkerChip
                      worker={worker}
                      meta={<div className="ops-row-subtitle">{worker.phone}</div>}
                    />
                  </td>
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
                      {canAccess("productionLines") ? (
                        <Link to="/production-lines" className="ops-link-button">
                          View Line
                        </Link>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="ops-pagination-bar">
          <div className="ops-row-subtitle">
            Showing {workerStart}-{workerEnd} of {filteredWorkers.length} workers
          </div>
          <div className="ops-pagination-actions">
            <button
              type="button"
              className="ops-button ops-button-secondary"
              disabled={page <= 1}
              onClick={() => setPage((current) => Math.max(1, current - 1))}
            >
              Previous
            </button>
            <span className="ops-pagination-count">
              Page {page} of {totalPages}
            </span>
            <button
              type="button"
              className="ops-button ops-button-secondary"
              disabled={page >= totalPages}
              onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
            >
              Next
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

export default WorkersPage;
