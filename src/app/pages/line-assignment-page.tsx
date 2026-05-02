import { useEffect, useMemo, useState } from "react";
import { Search } from "lucide-react";
import { useAuth } from "../auth";
import { findLine, useOperations } from "../operations-context";
import {
  Button,
  Card,
  EmptyState,
  KpiCard,
  LineCard,
  PageHeader,
  SearchField,
  StatusBadge,
  downloadCsv,
} from "../components/ops-ui";

export function LineAssignmentPage() {
  const { currentUser } = useAuth();
  const { workers, lines, transferLogs, assignWorker, transferWorker } = useOperations();
  const [search, setSearch] = useState("");
  const [skillFilter, setSkillFilter] = useState("All");
  const [departmentFilter, setDepartmentFilter] = useState("All");
  const [statusFilter, setStatusFilter] = useState("All");
  const [selectedWorkerId, setSelectedWorkerId] = useState<string>(workers[0]?.id || "");
  const [selectedLineId, setSelectedLineId] = useState<string>(lines[0]?.id || "");
  const [reason, setReason] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedWorkerId && workers[0]?.id) {
      setSelectedWorkerId(workers[0].id);
    }
  }, [selectedWorkerId, workers]);

  useEffect(() => {
    if (!selectedLineId && lines[0]?.id) {
      setSelectedLineId(lines[0].id);
    }
  }, [lines, selectedLineId]);

  const skillOptions = ["All", ...new Set(workers.flatMap((worker) => worker.skills))];
  const departmentOptions = ["All", ...new Set(workers.map((worker) => worker.department))];

  const filteredWorkers = workers.filter((worker) => {
    const query = search.trim().toLowerCase();
    const matchesQuery =
      !query ||
      worker.fullName.toLowerCase().includes(query) ||
      worker.employeeId.toLowerCase().includes(query);
    const matchesSkill =
      skillFilter === "All" || worker.skills.includes(skillFilter);
    const matchesDepartment =
      departmentFilter === "All" || worker.department === departmentFilter;
    const matchesStatus =
      statusFilter === "All" || worker.currentStatus === statusFilter;
    return matchesQuery && matchesSkill && matchesDepartment && matchesStatus;
  });

  const selectedWorker = workers.find((worker) => worker.id === selectedWorkerId);
  const selectedLine = lines.find((line) => line.id === selectedLineId);
  const destinationFull =
    selectedLine && selectedLine.actualManpower >= selectedLine.targetManpower;

  const exportRows = useMemo(
    () => [
      ["Worker", "From", "To", "Reason", "Timestamp", "User"],
      ...transferLogs.map((log) => [
        workers.find((worker) => worker.id === log.workerId)?.fullName || log.workerId,
        findLine(lines, log.sourceLineId)?.name || "Pool",
        findLine(lines, log.destinationLineId)?.name || "Pool",
        log.reason,
        log.transferredAt,
        log.transferredBy,
      ]),
    ],
    [lines, transferLogs, workers]
  );

  const handleAssign = async () => {
    if (!selectedWorker || !selectedLine) return;
    const result = selectedWorker.currentLineId
      ? await transferWorker({
          workerId: selectedWorker.id,
          destinationLineId: selectedLine.id,
          reason: reason || `Transfer initiated by ${currentUser.name}.`,
          actor: currentUser.name,
        })
      : await assignWorker({
          workerId: selectedWorker.id,
          lineId: selectedLine.id,
          reason: reason || `Assignment initiated by ${currentUser.name}.`,
          actor: currentUser.name,
        });
    setFeedback(result.message);
    if (result.ok) setReason("");
  };

  return (
    <div className="ops-page">
      <PageHeader
        title="Line Assignment & Transfers"
        subtitle="Supervisor tools for assigning workers, rebalancing lines, reviewing capacity, and tracking transfer reasons."
        actions={
          <>
            <Button tone="secondary" onClick={() => downloadCsv("transfer-log.csv", exportRows)}>
              Export Transfer Log
            </Button>
            <StatusBadge
              label={`${lines.filter((line) => line.actualManpower < line.targetManpower).length} understaffed lines`}
              tone="warning"
            />
          </>
        }
      />

      {feedback ? (
        <div className="ops-badge tone-info" style={{ alignSelf: "flex-start" }}>
          {feedback}
        </div>
      ) : null}

      <section className="ops-kpi-grid">
        <KpiCard
          label="Ready for Assignment"
          value={`${workers.filter((worker) => worker.currentStatus === "Pending Assignment").length}`}
          meta="Validated workers currently waiting for a destination line."
          icon={Search}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Understaffed"
          value={`${lines.filter((line) => line.actualManpower < line.targetManpower).length}`}
          meta="Lines below target manpower count."
          icon={Search}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="At Capacity"
          value={`${lines.filter((line) => line.actualManpower >= line.targetManpower).length}`}
          meta="Destination lines that should not accept direct transfer."
          icon={Search}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Transfers Logged"
          value={`${transferLogs.length}`}
          meta="Full history of staffing changes captured for audit."
          icon={Search}
          accent="var(--ops-violet)"
          soft="var(--ops-violet-soft)"
        />
      </section>

      <section className="ops-split">
        <Card title="Worker Finder" subtitle="Search by skill, department, or current status before assigning or transferring.">
          <div className="ops-filter-bar" style={{ padding: 0, border: 0, boxShadow: "none", background: "transparent" }}>
            <SearchField value={search} onChange={setSearch} placeholder="Search workers" />
            <select className="ops-select" value={skillFilter} onChange={(event) => setSkillFilter(event.target.value)}>
              {skillOptions.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
            <select className="ops-select" value={departmentFilter} onChange={(event) => setDepartmentFilter(event.target.value)}>
              {departmentOptions.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
            <select className="ops-select" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="All">All statuses</option>
              <option value="On Line">On Line</option>
              <option value="Pending Assignment">Pending Assignment</option>
              <option value="Awaiting Validation">Awaiting Validation</option>
              <option value="Transferred">Transferred</option>
            </select>
          </div>

          <div className="ops-list" style={{ marginTop: 18 }}>
            {filteredWorkers.length ? (
              filteredWorkers.map((worker) => (
                <button
                  key={worker.id}
                  type="button"
                  onClick={() => setSelectedWorkerId(worker.id)}
                  className="ops-list-item"
                  style={{
                    textAlign: "left",
                    borderColor:
                      worker.id === selectedWorkerId ? "rgba(37,99,235,0.45)" : undefined,
                    boxShadow:
                      worker.id === selectedWorkerId
                        ? "0 0 0 3px rgba(37,99,235,0.08)"
                        : undefined,
                  }}
                >
                  <div className="ops-item-header">
                    <div>
                      <div className="ops-item-title">{worker.fullName}</div>
                      <div className="ops-row-subtitle">
                        {worker.employeeId} · {worker.roleTitle}
                      </div>
                    </div>
                    <StatusBadge
                      label={worker.currentStatus}
                      tone={worker.currentStatus === "Pending Assignment" ? "info" : "neutral"}
                    />
                  </div>
                  <div className="ops-item-meta">
                    <span>{worker.skills.join(", ")}</span>
                    <span>{findLine(lines, worker.currentLineId)?.name || "Pool"}</span>
                  </div>
                </button>
              ))
            ) : (
              <EmptyState
                title="No workers matched"
                description="Try another skill, department, or status to locate a suitable operator."
              />
            )}
          </div>
        </Card>

        <Card title="Assignment Console" subtitle="Pick a destination line, review capacity, and log the reason for the move.">
          <div className="ops-filter-group">
            <label className="ops-filter-label" htmlFor="destinationLine">
              Destination line
            </label>
            <select
              id="destinationLine"
              className="ops-select"
              value={selectedLineId}
              onChange={(event) => setSelectedLineId(event.target.value)}
            >
              {lines.map((line) => (
                <option key={line.id} value={line.id}>
                  {line.name} · Style {line.allocatedStyle || "Unassigned"} · {line.actualManpower}/{line.targetManpower}
                </option>
              ))}
            </select>
          </div>

          {selectedWorker ? (
            <div className="ops-list-item" style={{ marginTop: 18 }}>
              <div className="ops-item-header">
                <div>
                  <div className="ops-item-title">{selectedWorker.fullName}</div>
                  <div className="ops-row-subtitle">
                    Source {findLine(lines, selectedWorker.currentLineId)?.name || "Pool"}
                  </div>
                </div>
                <StatusBadge label={selectedWorker.finalValidationStatus} tone="info" />
              </div>
              <div className="ops-item-description">
                Skills: {selectedWorker.skills.join(", ")}
              </div>
            </div>
          ) : null}

          {selectedLine ? (
            <div className="ops-list-item" style={{ marginTop: 14 }}>
              <div className="ops-item-header">
                <div>
                  <div className="ops-item-title">{selectedLine.name}</div>
                  <div className="ops-row-subtitle">
                    {selectedLine.code} · Style {selectedLine.allocatedStyle || "Unassigned"} · {selectedLine.department} · {selectedLine.supervisor}
                  </div>
                </div>
                <StatusBadge
                  label={
                    destinationFull
                      ? "Destination full"
                      : `${selectedLine.actualManpower}/${selectedLine.targetManpower}`
                  }
                  tone={destinationFull ? "danger" : "success"}
                />
              </div>
              <div className="ops-item-description">
                {destinationFull
                  ? "Choose another line or move out a worker first."
                  : "Destination has available manpower capacity."}
              </div>
            </div>
          ) : null}

          <div className="ops-filter-group" style={{ marginTop: 18 }}>
            <label className="ops-filter-label" htmlFor="transferReason">
              Reason for transfer / assignment
            </label>
            <textarea
              id="transferReason"
              className="ops-textarea"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Explain why this worker is being assigned or transferred"
            />
          </div>

          <div className="ops-toolbar" style={{ marginTop: 16 }}>
            <Button
              tone="primary"
              disabled={!selectedWorker || !selectedLine || destinationFull}
              onClick={() => void handleAssign()}
            >
              {selectedWorker?.currentLineId ? "Transfer Worker" : "Assign Worker"}
            </Button>
          </div>
        </Card>
      </section>

      <Card title="Line Staffing Board" subtitle="Compare target and current manpower before approving a movement.">
        <div className="ops-line-grid">
          {lines.map((line) => (
            <LineCard key={line.id} line={line} />
          ))}
        </div>
      </Card>

      <Card title="Transfer Log" subtitle="Timestamped line movements with actor and reason codes.">
        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Worker</th>
                <th>Source</th>
                <th>Destination</th>
                <th>Reason</th>
                <th>Timestamp</th>
                <th>User</th>
              </tr>
            </thead>
            <tbody>
              {transferLogs.map((log) => (
                <tr key={log.id}>
                  <td>{workers.find((worker) => worker.id === log.workerId)?.fullName || log.workerId}</td>
                  <td>{findLine(lines, log.sourceLineId)?.name || "Pool"}</td>
                  <td>{findLine(lines, log.destinationLineId)?.name || "Pool"}</td>
                  <td>{log.reason}</td>
                  <td>{log.transferredAt.replace("T", " ").slice(0, 16)}</td>
                  <td>{log.transferredBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

export default LineAssignmentPage;
