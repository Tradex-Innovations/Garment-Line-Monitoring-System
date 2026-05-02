import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import { isSupabaseConfigured } from "@/lib/supabase/env";
import {
  addWorkerNote,
  assignAlert,
  assignWorkerToLine,
  getOperationsSnapshot,
  markWorkerException,
  transferWorkerBetweenLines,
  updateProductionLineStyle,
  updateAlertStatus,
  updateOperationalSetting,
} from "@/server/operations/operations-service";
import type { OperationsActionResult, OperationsSnapshot } from "@/types/operations";
import type {
  AlertRecord,
  AttendanceOverview,
  AlertState,
  AttendanceSummary,
  AuditLogEntry,
  DepartmentAttendanceSummary,
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
import { useAuth } from "./auth";

type OperationsContextValue = OperationsSnapshot & {
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  assignWorker: (args: {
    workerId: string;
    lineId: string;
    reason: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  transferWorker: (args: {
    workerId: string;
    destinationLineId: string;
    reason: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  markValidationVerified: (args: {
    validationId: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  resolveValidation: (args: {
    validationId: string;
    status: ValidationStatus;
    reason: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  escalateValidation: (args: {
    validationId: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  updateAlertStatus: (args: {
    alertId: string;
    status: AlertState;
    actor: string;
  }) => Promise<OperationsActionResult>;
  assignAlert: (args: {
    alertId: string;
    assignedToUserId: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  addWorkerNote: (args: {
    workerId: string;
    note: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  markWorkerException: (args: {
    workerId: string;
    note: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
  updateSetting: <K extends keyof SystemSettings>(args: {
    key: K;
    value: SystemSettings[K];
    actor: string;
  }) => Promise<OperationsActionResult>;
  updateLineStyle: (args: {
    lineId: string;
    allocatedStyle: string;
    actor: string;
  }) => Promise<OperationsActionResult>;
};

const EMPTY_SNAPSHOT: OperationsSnapshot = {
  attendanceOverview: {
    attendanceDate: "",
    totalWorkers: 0,
    presentWorkers: 0,
    lateWorkers: 0,
    onLeaveWorkers: 0,
    absentWorkers: 0,
  },
  departmentAttendance: [],
  workers: [],
  lines: [],
  faceEvents: [],
  fingerprintEvents: [],
  validationRecords: [],
  lineAssignments: [],
  transferLogs: [],
  alerts: [],
  attendanceSummaries: [],
  overtimeRecords: [],
  leaveRecords: [],
  incentiveRecords: [],
  auditLogs: [],
  smartInsights: [],
  announcements: [],
  settings: {
    faceRecognition: true,
    fingerprintVerification: true,
    dualValidationRequired: true,
    autoRejectUnknownFaces: false,
    manualVerificationFallback: true,
    autoMarkAbsent: false,
    morningShiftStart: "07:30",
    morningShiftEnd: "17:30",
    lateArrivalThreshold: 10,
    gracePeriod: 5,
    failedEntryAlerts: true,
    lowEfficiencyWarnings: true,
    workerAbsenceAlerts: true,
    dailySummaryReport: true,
  },
  reportSeries: {
    weeklyAttendance: [],
    departmentAttendance: [],
    lineAttendance: [],
    transferHistory: [],
  },
};

const OperationsContext = createContext<OperationsContextValue | null>(null);

function createUnavailableResult(message: string): OperationsActionResult {
  return { ok: false, message };
}

export function OperationsProvider({ children }: { children: ReactNode }) {
  const { currentUser, isAuthenticated, isConfigured, loading: authLoading } = useAuth();
  const [snapshot, setSnapshot] = useState<OperationsSnapshot>(EMPTY_SNAPSHOT);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSnapshot = useCallback(async () => {
    if (!isConfigured || !isAuthenticated) {
      setSnapshot(EMPTY_SNAPSHOT);
      setError(null);
      setLoading(false);
      return;
    }

    const client = getSupabaseBrowserClient();

    if (!client) {
      setSnapshot(EMPTY_SNAPSHOT);
      setError("Supabase browser client is not available.");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const nextSnapshot = await getOperationsSnapshot(client, {
        includeAuditLogs: currentUser.role === "admin",
        includeEmployeeNotes: currentUser.role !== "viewer",
        includeSystemSettings: currentUser.role === "admin",
        includeProfileDirectory: currentUser.role !== "viewer",
        syncReconciliationAlerts: ["admin", "hr", "supervisor"].includes(currentUser.role),
      });
      setSnapshot(nextSnapshot);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
      setSnapshot(EMPTY_SNAPSHOT);
    } finally {
      setLoading(false);
    }
  }, [currentUser.role, isAuthenticated, isConfigured]);

  useEffect(() => {
    if (!authLoading) {
      void loadSnapshot();
    }
  }, [authLoading, loadSnapshot]);

  const withClient = useCallback(
    async (
      action: (client: NonNullable<ReturnType<typeof getSupabaseBrowserClient>>) => Promise<OperationsActionResult>
    ) => {
      if (!isSupabaseConfigured() || !isAuthenticated) {
        return createUnavailableResult("Sign in with a configured Supabase project to continue.");
      }

      const client = getSupabaseBrowserClient();

      if (!client) {
        return createUnavailableResult("Supabase browser client is not available.");
      }

      try {
        const result = await action(client);
        if (result.ok) {
          await loadSnapshot();
        }
        return result;
      } catch (nextError) {
        return {
          ok: false,
          message: nextError instanceof Error ? nextError.message : String(nextError),
        };
      }
    },
    [isAuthenticated, loadSnapshot]
  );

  const value = useMemo<OperationsContextValue>(
    () => ({
      ...snapshot,
      loading,
      error,
      refresh: loadSnapshot,
      assignWorker: async ({ workerId, lineId, reason }) =>
        withClient((client) =>
          assignWorkerToLine(client, {
            employeeId: workerId,
            lineId,
            reason,
          })
        ),
      transferWorker: async ({ workerId, destinationLineId, reason }) =>
        withClient((client) =>
          transferWorkerBetweenLines(client, {
            employeeId: workerId,
            destinationLineId,
            reason,
          })
        ),
      markValidationVerified: async () =>
        createUnavailableResult(
          "Validation verification now runs through the live Validation Center override controls."
        ),
      resolveValidation: async () =>
        createUnavailableResult(
          "Validation resolution now runs through the live Validation Center override controls."
        ),
      escalateValidation: async () =>
        createUnavailableResult(
          "Validation escalation now runs through the live Validation Center exception workflow."
        ),
      updateAlertStatus: async ({ alertId, status }) =>
        withClient((client) =>
          updateAlertStatus(client, {
            alertId,
            status,
            actorUserId: currentUser.id,
          })
        ),
      assignAlert: async ({ alertId, assignedToUserId }) =>
        withClient((client) =>
          assignAlert(client, {
            alertId,
            assignedToUserId,
            actorUserId: currentUser.id,
          })
        ),
      addWorkerNote: async ({ workerId, note }) =>
        withClient((client) =>
          addWorkerNote(client, {
            employeeId: workerId,
            note,
          })
        ),
      markWorkerException: async ({ workerId, note }) =>
        withClient((client) =>
          markWorkerException(client, {
            employeeId: workerId,
            note,
          })
        ),
      updateSetting: async ({ key, value: nextValue }) =>
        withClient((client) =>
          updateOperationalSetting(client, {
            key,
            value: nextValue,
          })
        ),
      updateLineStyle: async ({ lineId, allocatedStyle }) =>
        withClient((client) =>
          updateProductionLineStyle(client, {
            lineId,
            allocatedStyle,
          })
        ),
    }),
    [currentUser.id, error, loadSnapshot, loading, snapshot, withClient]
  );

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

export type {
  AlertRecord,
  AttendanceOverview,
  AttendanceSummary,
  AuditLogEntry,
  DepartmentAttendanceSummary,
  FaceEvent,
  FingerprintEvent,
  IncentiveRecord,
  LeaveRecord,
  LineAssignmentRecord,
  OvertimeRecord,
  ProductionLineRecord,
  ReportSeries,
  SmartInsight,
  TransferLog,
  ValidationRecord,
};
