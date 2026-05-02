import { backendFormRequest, backendJsonRequest } from "./client";
import type {
  ImportBatchSummary,
  ReconciliationFilterInput,
  ReconciliationOverrideInput,
  ReconciliationRowDetail,
  ValidationSummaryRow,
} from "@/types/pipeline";
import type { AppUser } from "@/app/types";

export function listImportBatchesFromBackend() {
  return backendJsonRequest<ImportBatchSummary[]>("/api/import-batches");
}

export async function uploadImportBatchFromBackend(args: {
  sourceType: "face" | "fingerprint";
  file: File;
}) {
  const formData = new FormData();
  formData.set("sourceType", args.sourceType);
  formData.set("file", args.file);

  return backendFormRequest<ImportBatchSummary>("/api/import-batches/upload", formData, {
    method: "POST",
  });
}

export function rerunImportNormalizationFromBackend(batchId: string) {
  return backendJsonRequest<ImportBatchSummary>(`/api/import-batches/${batchId}/normalize`, {
    method: "POST",
  });
}

export function reconcileBatchPairFromBackend(args: {
  faceBatchId: string;
  fingerprintBatchId: string;
}) {
  return backendJsonRequest<{ reconciled_count?: number }>("/api/reconciliation/pairs", {
    method: "POST",
    body: JSON.stringify(args),
  });
}

export function getValidationSummaryFromBackend() {
  return backendJsonRequest<ValidationSummaryRow[]>("/api/reconciliation/summary");
}

export function getReconciliationRowsFromBackend(filters: ReconciliationFilterInput) {
  return backendJsonRequest<ReconciliationRowDetail[]>("/api/reconciliation", {}, {
    attendanceDate: filters.attendanceDate || null,
    status: filters.status || null,
    department: filters.department || null,
    employeeCode: filters.employeeCode || null,
    importBatchId: filters.importBatchId || null,
  });
}

export function getReconciliationDetailFromBackend(reconciliationId: string) {
  return backendJsonRequest<any>(`/api/reconciliation/${reconciliationId}`);
}

export function overrideReconciliationFromBackend(
  reconciliationId: string,
  input: ReconciliationOverrideInput
) {
  return backendJsonRequest(`/api/reconciliation/${reconciliationId}/override`, {
    method: "POST",
    body: JSON.stringify({
      newStatus: input.newStatus,
      reason: input.reason,
      note: input.note || null,
    }),
  });
}

export function addReconciliationNoteFromBackend(
  reconciliationId: string,
  note: string
) {
  return backendJsonRequest(`/api/reconciliation/${reconciliationId}/notes`, {
    method: "POST",
    body: JSON.stringify({ note }),
  });
}

export function listActiveAppUsersFromBackend() {
  return backendJsonRequest<AppUser[]>("/api/app-users");
}
