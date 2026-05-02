import type {
  AlertRecord,
  Announcement,
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
  SystemSettings,
  TransferLog,
  ValidationRecord,
  WorkerProfile,
} from "@/app/types";

export type OperationsActionResult = {
  ok: boolean;
  message: string;
};

export interface OperationsSnapshot {
  attendanceOverview: AttendanceOverview;
  departmentAttendance: DepartmentAttendanceSummary[];
  workers: WorkerProfile[];
  lines: ProductionLineRecord[];
  faceEvents: FaceEvent[];
  fingerprintEvents: FingerprintEvent[];
  validationRecords: ValidationRecord[];
  lineAssignments: LineAssignmentRecord[];
  transferLogs: TransferLog[];
  alerts: AlertRecord[];
  attendanceSummaries: AttendanceSummary[];
  overtimeRecords: OvertimeRecord[];
  leaveRecords: LeaveRecord[];
  incentiveRecords: IncentiveRecord[];
  auditLogs: AuditLogEntry[];
  smartInsights: SmartInsight[];
  announcements: Announcement[];
  settings: SystemSettings;
  reportSeries: ReportSeries;
}
