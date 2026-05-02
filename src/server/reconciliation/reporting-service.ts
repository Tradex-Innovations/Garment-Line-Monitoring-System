import type {
  DepartmentValidationSummaryRow,
  FaceComparisonRow,
  ValidationSummaryRow,
} from "@/types/pipeline";
import type { AppSupabaseClient } from "../repositories/base-repository";
import {
  fetchAuditLogsForEntity,
  fetchDepartmentValidationSummaryRows,
  fetchReconciliationExceptions,
  fetchValidationSummaryRows,
} from "../repositories/reporting-repository";
import { getReconciliationRows } from "./reconciliation-service";

function toValidationSummaryRow(row: any): ValidationSummaryRow {
  return {
    attendanceDate: row.attendance_date,
    totalReconciled: row.total_reconciled,
    validatedCount: row.validated_count,
    faceOnlyCount: row.face_only_count,
    fingerprintOnlyCount: row.fingerprint_only_count,
    leaveCount: row.leave_count,
    absentCount: row.absent_count,
    needsReviewCount: row.needs_review_count,
    anomalyCount: row.anomaly_count,
  };
}

function toDepartmentSummaryRow(row: any): DepartmentValidationSummaryRow {
  return {
    attendanceDate: row.attendance_date,
    departmentName: row.department_name,
    validatedCount: row.validated_count,
    faceOnlyCount: row.face_only_count,
    fingerprintOnlyCount: row.fingerprint_only_count,
    leaveCount: row.leave_count,
    absentCount: row.absent_count,
    needsReviewCount: row.needs_review_count,
    anomalyCount: row.anomaly_count,
  };
}

export async function getValidationSummary(client: AppSupabaseClient) {
  const rows = await fetchValidationSummaryRows(client);
  return rows.map(toValidationSummaryRow);
}

export async function getDepartmentValidationSummary(
  client: AppSupabaseClient
) {
  const rows = await fetchDepartmentValidationSummaryRows(client);
  return rows.map(toDepartmentSummaryRow);
}

export async function getExceptionReport(client: AppSupabaseClient) {
  return fetchReconciliationExceptions(client);
}

export async function getFaceVsFingerprintComparison(
  client: AppSupabaseClient,
  attendanceDate?: string
) {
  const rows = await getReconciliationRows(client, {
    attendanceDate: attendanceDate || null,
  });

  return rows.map<FaceComparisonRow>((row) => ({
    employeeCode: row.employeeCode,
    employeeName: row.employeeName,
    attendanceDate: row.attendanceDate,
    faceFirstSeen: row.faceFirstSeen,
    faceLastSeen: row.faceLastSeen,
    faceEventCount: row.faceEventCount,
    fingerprintTimeIn: row.fingerprintTimeIn,
    fingerprintTimeOut: row.fingerprintTimeOut,
    reconciliationStatus: row.effectiveStatus,
    confidenceLevel: row.confidenceLevel,
  }));
}

export async function getReconciliationAuditTrail(
  client: AppSupabaseClient,
  reconciliationId: string
) {
  return fetchAuditLogsForEntity(
    client,
    "attendance_reconciliation",
    reconciliationId
  );
}
