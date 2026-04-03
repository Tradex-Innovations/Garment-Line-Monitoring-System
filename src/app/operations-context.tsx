import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { seedData } from "./mock-data";
import type {
  AlertRecord,
  AlertState,
  AuditLogEntry,
  FaceEvent,
  FingerprintEvent,
  IncentiveRecord,
  LeaveRecord,
  LineAssignmentRecord,
  OvertimeRecord,
  ProductionLineRecord,
  ReportSeries,
  SmartInsight,
  SystemSettings,
  TransferLog,
  ValidationRecord,
  ValidationStatus,
  WorkerProfile,
} from "./types";

type ActionResult = { ok: boolean; message: string };

type OperationsContextValue = {
  workers: WorkerProfile[];
  lines: ProductionLineRecord[];
  faceEvents: FaceEvent[];
  fingerprintEvents: FingerprintEvent[];
  validationRecords: ValidationRecord[];
  lineAssignments: LineAssignmentRecord[];
  transferLogs: TransferLog[];
  alerts: AlertRecord[];
  attendanceSummaries: typeof seedData.attendanceSummaries;
  overtimeRecords: OvertimeRecord[];
  leaveRecords: LeaveRecord[];
  incentiveRecords: IncentiveRecord[];
  auditLogs: AuditLogEntry[];
  smartInsights: SmartInsight[];
  announcements: typeof seedData.announcements;
  settings: SystemSettings;
  reportSeries: ReportSeries;
  assignWorker: (args: { workerId: string; lineId: string; reason: string; actor: string }) => ActionResult;
  transferWorker: (args: { workerId: string; destinationLineId: string; reason: string; actor: string }) => ActionResult;
  markValidationVerified: (args: { validationId: string; actor: string }) => ActionResult;
  resolveValidation: (args: { validationId: string; status: ValidationStatus; reason: string; actor: string }) => ActionResult;
  escalateValidation: (args: { validationId: string; actor: string }) => ActionResult;
  updateAlertStatus: (args: { alertId: string; status: AlertState; actor: string }) => ActionResult;
  assignAlert: (args: { alertId: string; assignedToUserId: string; actor: string }) => ActionResult;
  addWorkerNote: (args: { workerId: string; note: string; actor: string }) => ActionResult;
  markWorkerException: (args: { workerId: string; note: string; actor: string }) => ActionResult;
  updateSetting: <K extends keyof SystemSettings>(args: { key: K; value: SystemSettings[K]; actor: string }) => ActionResult;
};

const OperationsContext = createContext<OperationsContextValue | null>(null);

const deepClone = <T,>(value: T): T => JSON.parse(JSON.stringify(value));
const createId = (prefix: string) => `${prefix}-${Math.random().toString(36).slice(2, 10)}`;

function deriveLineState(line: ProductionLineRecord) {
  if (line.actualManpower <= 0) return { status: "Idle", risk: "Watch" } as const;
  if (line.actualManpower < line.targetManpower) {
    return {
      status: "Partial",
      risk: line.actualManpower <= line.targetManpower - 2 ? "Critical" : "Watch",
    } as const;
  }
  return { status: "Active", risk: "Stable" } as const;
}

export function OperationsProvider({ children }: { children: ReactNode }) {
  const [workers, setWorkers] = useState(() => deepClone(seedData.workers));
  const [lines, setLines] = useState(() => deepClone(seedData.lines));
  const [faceEvents] = useState(() => deepClone(seedData.faceEvents));
  const [fingerprintEvents] = useState(() => deepClone(seedData.fingerprintEvents));
  const [validationRecords, setValidationRecords] = useState(() => deepClone(seedData.validationRecords));
  const [lineAssignments, setLineAssignments] = useState(() => deepClone(seedData.lineAssignments));
  const [transferLogs, setTransferLogs] = useState(() => deepClone(seedData.transferLogs));
  const [alerts, setAlerts] = useState(() => deepClone(seedData.alerts));
  const [attendanceSummaries] = useState(() => deepClone(seedData.attendanceSummaries));
  const [overtimeRecords] = useState(() => deepClone(seedData.overtimeRecords));
  const [leaveRecords] = useState(() => deepClone(seedData.leaveRecords));
  const [incentiveRecords] = useState(() => deepClone(seedData.incentiveRecords));
  const [auditLogs, setAuditLogs] = useState(() => deepClone(seedData.auditLogs));
  const [smartInsights] = useState(() => deepClone(seedData.smartInsights));
  const [announcements] = useState(() => deepClone(seedData.announcements));
  const [settings, setSettings] = useState(() => deepClone(seedData.settings));
  const [reportSeries] = useState(() => deepClone(seedData.reportSeries));

  const pushAuditLog = (entry: Omit<AuditLogEntry, "id">) => {
    setAuditLogs((current) => [{ id: createId("audit"), ...entry }, ...current]);
  };

  const assignWorker: OperationsContextValue["assignWorker"] = ({ workerId, lineId, reason, actor }) => {
    const worker = workers.find((item) => item.id === workerId);
    const destinationLine = lines.find((line) => line.id === lineId);
    if (!worker || !destinationLine) return { ok: false, message: "Worker or line could not be found." };
    if (destinationLine.actualManpower >= destinationLine.targetManpower) {
      return { ok: false, message: `${destinationLine.name} is already at full capacity.` };
    }
    if (worker.currentLineId) {
      return { ok: false, message: "Worker already has a source line. Use transfer instead of assign." };
    }

    setWorkers((current) => current.map((item) => item.id === workerId ? { ...item, currentLineId: lineId, currentStatus: "On Line" } : item));
    setLines((current) => current.map((line) => {
      if (line.id !== lineId) return line;
      const next = { ...line, actualManpower: line.actualManpower + 1 };
      return { ...next, ...deriveLineState(next) };
    }));
    setLineAssignments((current) => [
      { id: createId("assign"), workerId, lineId, assignedAt: new Date().toISOString(), assignedBy: actor, status: "Active" },
      ...current,
    ]);
    if (reason.trim()) {
      setWorkers((current) => current.map((item) => item.id === workerId ? { ...item, notes: [reason.trim(), ...item.notes].slice(0, 6) } : item));
    }
    pushAuditLog({
      actionType: "Worker assigned to line",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: worker.fullName,
      oldValue: "Unassigned",
      newValue: destinationLine.name,
    });
    return { ok: true, message: `${worker.fullName} assigned to ${destinationLine.name}.` };
  };

  const transferWorker: OperationsContextValue["transferWorker"] = ({ workerId, destinationLineId, reason, actor }) => {
    const worker = workers.find((item) => item.id === workerId);
    const destinationLine = lines.find((line) => line.id === destinationLineId);
    if (!worker || !destinationLine) return { ok: false, message: "Worker or destination line could not be found." };
    if (worker.currentLineId === destinationLineId) return { ok: false, message: "Worker is already assigned to that line." };
    if (destinationLine.actualManpower >= destinationLine.targetManpower) return { ok: false, message: `${destinationLine.name} is full. Choose another line.` };

    const sourceLine = lines.find((line) => line.id === worker.currentLineId);
    setWorkers((current) => current.map((item) => item.id === workerId ? { ...item, currentLineId: destinationLineId, currentStatus: "Transferred" } : item));
    setLines((current) => current.map((line) => {
      if (line.id === sourceLine?.id) {
        const next = { ...line, actualManpower: Math.max(0, line.actualManpower - 1) };
        return { ...next, ...deriveLineState(next) };
      }
      if (line.id === destinationLineId) {
        const next = { ...line, actualManpower: line.actualManpower + 1 };
        return { ...next, ...deriveLineState(next) };
      }
      return line;
    }));
    setLineAssignments((current) => current.map((assignment) => assignment.workerId === workerId && assignment.status === "Active" ? { ...assignment, status: "Transferred" } : assignment));
    setLineAssignments((current) => [
      { id: createId("assign"), workerId, lineId: destinationLineId, assignedAt: new Date().toISOString(), assignedBy: actor, status: "Active" },
      ...current,
    ]);
    setTransferLogs((current) => [
      { id: createId("transfer"), workerId, sourceLineId: sourceLine?.id, destinationLineId, reason, transferredAt: new Date().toISOString(), transferredBy: actor },
      ...current,
    ]);
    pushAuditLog({
      actionType: "Worker transferred",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: worker.fullName,
      oldValue: sourceLine?.name || "Unassigned",
      newValue: destinationLine.name,
    });
    return { ok: true, message: `${worker.fullName} transferred to ${destinationLine.name}.` };
  };

  const markValidationVerified: OperationsContextValue["markValidationVerified"] = ({ validationId, actor }) => {
    const record = validationRecords.find((item) => item.id === validationId);
    if (!record) return { ok: false, message: "Validation record was not found." };

    setValidationRecords((current) => current.map((item) => item.id === validationId ? {
      ...item,
      status: "Fully Validated",
      timeline: [
        ...item.timeline,
        { id: createId("timeline"), type: "validation", timestamp: new Date().toISOString(), label: "Marked verified", detail: `Manually verified by ${actor}.` },
      ],
    } : item));
    if (record.workerId) {
      setWorkers((current) => current.map((item) => item.id === record.workerId ? {
        ...item,
        faceVerificationStatus: item.faceVerificationStatus === "Missing" ? "Verified" : item.faceVerificationStatus,
        fingerprintVerificationStatus: item.fingerprintVerificationStatus === "Missing" ? "Verified" : item.fingerprintVerificationStatus,
        finalValidationStatus: "Fully Validated",
        currentStatus: item.currentLineId ? "On Line" : "Pending Assignment",
      } : item));
    }
    pushAuditLog({
      actionType: "Validation manually resolved",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: record.workerName,
      oldValue: record.status,
      newValue: "Fully Validated",
    });
    return { ok: true, message: `${record.workerName} marked as verified.` };
  };

  const resolveValidation: OperationsContextValue["resolveValidation"] = ({ validationId, status, reason, actor }) => {
    const record = validationRecords.find((item) => item.id === validationId);
    if (!record) return { ok: false, message: "Validation record was not found." };

    setValidationRecords((current) => current.map((item) => item.id === validationId ? {
      ...item,
      status,
      exceptionReason: reason || item.exceptionReason,
      timeline: [
        ...item.timeline,
        { id: createId("timeline"), type: "exception", timestamp: new Date().toISOString(), label: "Manual resolution", detail: reason || `Status changed to ${status} by ${actor}.` },
      ],
    } : item));
    if (record.workerId) {
      setWorkers((current) => current.map((item) => item.id === record.workerId ? {
        ...item,
        finalValidationStatus: status,
        currentStatus: status === "Fully Validated" ? (item.currentLineId ? "On Line" : "Pending Assignment") : "Awaiting Validation",
        flags: status === "Unresolved Exception" && reason ? [reason, ...item.flags].slice(0, 5) : item.flags,
      } : item));
    }
    pushAuditLog({
      actionType: "Validation manually resolved",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: record.workerName,
      oldValue: record.status,
      newValue: status,
    });
    return { ok: true, message: `${record.workerName} updated to ${status}.` };
  };

  const escalateValidation: OperationsContextValue["escalateValidation"] = ({ validationId, actor }) => {
    const record = validationRecords.find((item) => item.id === validationId);
    if (!record) return { ok: false, message: "Validation record was not found." };
    const existingAlert = alerts.find((alert) => alert.workerId === record.workerId && alert.type === "unverified worker" && alert.status !== "Resolved");
    if (existingAlert) return { ok: true, message: "An active escalation already exists for this worker." };

    setAlerts((current) => [{
      id: createId("alert"),
      type: "unverified worker",
      priority: "high",
      title: `Validation escalated for ${record.workerName}`,
      description: record.exceptionReason || `Validation record ${record.employeeId} was escalated by ${actor}.`,
      createdAt: new Date().toISOString(),
      status: "Open",
      workerId: record.workerId,
      lineId: record.lineId,
      history: [{ id: createId("alert-history"), timestamp: new Date().toISOString(), user: actor, action: "Escalated from validation center" }],
    }, ...current]);
    pushAuditLog({
      actionType: "Validation escalated",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: record.workerName,
      oldValue: record.status,
      newValue: "Escalated to alert queue",
    });
    return { ok: true, message: `${record.workerName} escalated to Alerts Center.` };
  };

  const updateAlertStatus: OperationsContextValue["updateAlertStatus"] = ({ alertId, status, actor }) => {
    const alert = alerts.find((item) => item.id === alertId);
    if (!alert) return { ok: false, message: "Alert could not be found." };

    setAlerts((current) => current.map((item) => item.id === alertId ? {
      ...item,
      status,
      history: [{ id: createId("alert-history"), timestamp: new Date().toISOString(), user: actor, action: `Status changed to ${status}` }, ...item.history],
    } : item));
    pushAuditLog({
      actionType: status === "Resolved" ? "Alert resolved" : "Alert updated",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: alert.title,
      oldValue: alert.status,
      newValue: status,
    });
    return { ok: true, message: `Alert updated to ${status}.` };
  };

  const assignAlert: OperationsContextValue["assignAlert"] = ({ alertId, assignedToUserId, actor }) => {
    const alert = alerts.find((item) => item.id === alertId);
    const assignee = seedData.users.find((user) => user.id === assignedToUserId);
    if (!alert || !assignee) return { ok: false, message: "Alert or assignee was not found." };

    setAlerts((current) => current.map((item) => item.id === alertId ? {
      ...item,
      assignedToUserId,
      history: [{ id: createId("alert-history"), timestamp: new Date().toISOString(), user: actor, action: `Assigned to ${assignee.name}` }, ...item.history],
    } : item));
    pushAuditLog({
      actionType: "Alert assigned",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: alert.title,
      oldValue: seedData.users.find((user) => user.id === alert.assignedToUserId)?.name || "Unassigned",
      newValue: assignee.name,
    });
    return { ok: true, message: `Alert assigned to ${assignee.name}.` };
  };

  const addWorkerNote: OperationsContextValue["addWorkerNote"] = ({ workerId, note, actor }) => {
    const worker = workers.find((item) => item.id === workerId);
    if (!worker || !note.trim()) return { ok: false, message: "Worker or note was invalid." };

    setWorkers((current) => current.map((item) => item.id === workerId ? { ...item, notes: [note.trim(), ...item.notes].slice(0, 6) } : item));
    pushAuditLog({
      actionType: "Worker note added",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: worker.fullName,
      oldValue: "No new note",
      newValue: note.trim(),
    });
    return { ok: true, message: "Note added to worker profile." };
  };

  const markWorkerException: OperationsContextValue["markWorkerException"] = ({ workerId, note, actor }) => {
    const worker = workers.find((item) => item.id === workerId);
    if (!worker || !note.trim()) return { ok: false, message: "Worker or exception note was invalid." };

    setWorkers((current) => current.map((item) => item.id === workerId ? {
      ...item,
      finalValidationStatus: "Unresolved Exception",
      currentStatus: "Awaiting Validation",
      flags: [note.trim(), ...item.flags].slice(0, 6),
    } : item));
    pushAuditLog({
      actionType: "Worker marked as exception",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: worker.fullName,
      oldValue: worker.finalValidationStatus,
      newValue: "Unresolved Exception",
    });
    return { ok: true, message: `${worker.fullName} marked with an exception.` };
  };

  const updateSetting: OperationsContextValue["updateSetting"] = ({ key, value, actor }) => {
    const oldValue = settings[key];
    setSettings((current) => ({ ...current, [key]: value }));
    pushAuditLog({
      actionType: "Settings changed",
      user: actor,
      timestamp: new Date().toISOString(),
      targetEntity: String(key),
      oldValue: String(oldValue),
      newValue: String(value),
    });
    return { ok: true, message: `${String(key)} updated.` };
  };

  const value = useMemo<OperationsContextValue>(() => ({
    workers,
    lines,
    faceEvents,
    fingerprintEvents,
    validationRecords,
    lineAssignments,
    transferLogs,
    alerts,
    attendanceSummaries,
    overtimeRecords,
    leaveRecords,
    incentiveRecords,
    auditLogs,
    smartInsights,
    announcements,
    settings,
    reportSeries,
    assignWorker,
    transferWorker,
    markValidationVerified,
    resolveValidation,
    escalateValidation,
    updateAlertStatus,
    assignAlert,
    addWorkerNote,
    markWorkerException,
    updateSetting,
  }), [workers, lines, faceEvents, fingerprintEvents, validationRecords, lineAssignments, transferLogs, alerts, attendanceSummaries, overtimeRecords, leaveRecords, incentiveRecords, auditLogs, smartInsights, announcements, settings, reportSeries]);

  return <OperationsContext.Provider value={value}>{children}</OperationsContext.Provider>;
}

export function useOperations() {
  const context = useContext(OperationsContext);
  if (!context) throw new Error("useOperations must be used inside OperationsProvider");
  return context;
}

export function findWorker(workers: WorkerProfile[], workerId?: string) {
  return workers.find((worker) => worker.id === workerId);
}

export function findLine(lines: ProductionLineRecord[], lineId?: string) {
  return lines.find((line) => line.id === lineId);
}
