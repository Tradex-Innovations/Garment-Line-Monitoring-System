import type { AppSupabaseClient } from "../repositories/base-repository";
import {
  fetchFaceEventsForEmployeeDate,
  fetchFaceRawRowsByEmployeeCode,
} from "../repositories/face-repository";
import { fetchFingerprintAttendanceForEmployeeDate } from "../repositories/fingerprint-repository";
import { fetchAuditLogsForEntity } from "../repositories/reporting-repository";
import {
  fetchReconciliationNotes,
  fetchReconciliationRow,
  listReconciliationRows,
  runAddReconciliationNoteRpc,
  runOverrideReconciliationRpc,
  runReconciliationRpc,
} from "../repositories/reconciliation-repository";
import type {
  ReconciliationFilterInput,
  ReconciliationOverrideInput,
  ReconciliationRowDetail,
} from "@/types/pipeline";
import { parseFlexibleDateText } from "../parsers/shared";

function toReconciliationRowDetail(row: any): ReconciliationRowDetail {
  return {
    id: row.id,
    faceImportBatchId: row.face_import_batch_id,
    fingerprintImportBatchId: row.fingerprint_import_batch_id,
    employeeCode: row.employee_code,
    employeeName: row.employee_name,
    designation: row.designation,
    departmentName: row.department_name,
    attendanceDate: row.attendance_date,
    faceFirstSeen: row.face_first_seen,
    faceLastSeen: row.face_last_seen,
    faceEventCount: row.face_event_count,
    duplicateFaceEventCount: row.duplicate_face_event_count,
    fingerprintTimeIn: row.fingerprint_time_in,
    fingerprintTimeOut: row.fingerprint_time_out,
    lateEarlyHours: row.late_early_hours,
    otHours: row.ot_hours,
    leaveType: row.leave_type,
    reconciliationStatus: row.reconciliation_status,
    effectiveStatus: row.manual_override_status || row.reconciliation_status,
    exceptionReason: row.exception_reason,
    confidenceLevel: row.confidence_level,
    ruleFlags: Array.isArray(row.rule_flags) ? row.rule_flags : [],
    manuallyOverridden: row.manually_overridden,
    manualOverrideStatus: row.manual_override_status,
    manualOverrideReason: row.manual_override_reason,
    manualOverrideBy: row.manual_override_by,
    manualOverrideAt: row.manual_override_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

export async function reconcileForBatchPair(
  client: AppSupabaseClient,
  args: {
    faceBatchId: string;
    fingerprintBatchId: string;
  }
) {
  return runReconciliationRpc(client, args.faceBatchId, args.fingerprintBatchId);
}

export async function reconcileForDateRange(
  client: AppSupabaseClient,
  args: {
    startDate: string;
    endDate: string;
  }
) {
  const { data, error } = await client
    .from("attendance_reconciliation")
    .select("*")
    .gte("attendance_date", args.startDate)
    .lte("attendance_date", args.endDate)
    .order("attendance_date", { ascending: true })
    .order("employee_code", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return (data || []).map(toReconciliationRowDetail);
}

export async function rebuildReconciliationForEmployee(
  client: AppSupabaseClient,
  employeeCode: string
) {
  const { data, error } = await client
    .from("attendance_reconciliation")
    .select("*")
    .eq("employee_code", employeeCode)
    .order("attendance_date", { ascending: false });

  if (error) {
    throw new Error(error.message);
  }

  return (data || []).map(toReconciliationRowDetail);
}

export async function overrideReconciliationStatus(
  client: AppSupabaseClient,
  input: ReconciliationOverrideInput
) {
  return runOverrideReconciliationRpc(client, {
    reconciliationId: input.reconciliationId,
    newStatus: input.newStatus,
    reason: input.reason,
    note: input.note || null,
  });
}

export async function addReconciliationNote(
  client: AppSupabaseClient,
  args: {
    reconciliationId: string;
    note: string;
  }
) {
  return runAddReconciliationNoteRpc(client, args);
}

export async function getReconciliationRows(
  client: AppSupabaseClient,
  filters: ReconciliationFilterInput = {}
) {
  const rows = (await listReconciliationRows(client)).map(toReconciliationRowDetail);

  return rows.filter((row) => {
    const matchesDate = !filters.attendanceDate || row.attendanceDate === filters.attendanceDate;
    const matchesStatus =
      !filters.status || filters.status === "all"
        ? true
        : row.effectiveStatus === filters.status;
    const matchesDepartment =
      !filters.department ||
      row.departmentName?.toLowerCase() === filters.department.toLowerCase();
    const matchesEmployee =
      !filters.employeeCode ||
      row.employeeCode.toLowerCase().includes(filters.employeeCode.toLowerCase());
    const matchesBatch =
      !filters.importBatchId ||
      row.faceImportBatchId === filters.importBatchId ||
      row.fingerprintImportBatchId === filters.importBatchId;

    return (
      matchesDate &&
      matchesStatus &&
      matchesDepartment &&
      matchesEmployee &&
      matchesBatch
    );
  });
}

export async function getReconciliationDetail(
  client: AppSupabaseClient,
  reconciliationId: string
) {
  const row = await fetchReconciliationRow(client, reconciliationId);
  if (!row) {
    throw new Error(`Reconciliation row ${reconciliationId} was not found.`);
  }
  const notes = await fetchReconciliationNotes(client, reconciliationId);
  const record = toReconciliationRowDetail(row);
  const [faceEvents, allFaceRawRows, fingerprintRows, auditLogs] = await Promise.all([
    fetchFaceEventsForEmployeeDate(client, row.employee_code, row.attendance_date),
    fetchFaceRawRowsByEmployeeCode(client, row.employee_code),
    fetchFingerprintAttendanceForEmployeeDate(client, row.employee_code, row.attendance_date),
    fetchAuditLogsForEntity(client, "attendance_reconciliation", reconciliationId),
  ]);
  const faceRawRows = allFaceRawRows.filter(
    (rawRow) => parseFlexibleDateText(rawRow.source_date_text) === row.attendance_date
  );

  return {
    record,
    faceEvents,
    faceRawRows,
    fingerprintRows,
    auditLogs,
    notes,
  };
}
