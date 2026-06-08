import type { Database } from "@/types/database";
import type { ImportBatchSummary, ImportStatus, SourceType } from "@/types/pipeline";
import type { AppSupabaseClient } from "../repositories/base-repository";
import {
  createImportBatchRecord,
  fetchImportBatchRecord,
  listImportBatchRecords,
  updateImportBatchRecord,
  updateImportBatchStatusRecord,
} from "../repositories/import-batches-repository";

function toBatchSummary(row: Database["public"]["Tables"]["import_batches"]["Row"]): ImportBatchSummary {
  return {
    id: row.id,
    sourceType: row.source_type,
    originalFilename: row.original_filename,
    importStatus: row.import_status,
    storagePath: row.storage_path,
    fileMimeType: row.file_mime_type,
    fileSizeBytes: row.file_size_bytes,
    totalRawRows: row.total_raw_rows,
    totalValidRows: row.total_valid_rows,
    totalErrorRows: row.total_error_rows,
    notes: row.notes,
    startedAt: row.started_at,
    completedAt: row.completed_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

export async function createImportBatch(
  client: AppSupabaseClient,
  args: {
    batchId?: string;
    sourceType: SourceType;
    originalFilename: string;
    storagePath: string;
    fileMimeType?: string | null;
    fileSizeBytes?: number | null;
    uploadedBy?: string | null;
    notes?: string | null;
  }
) {
  const row = await createImportBatchRecord(client, {
    id: args.batchId,
    source_type: args.sourceType,
    original_filename: args.originalFilename,
    storage_path: args.storagePath,
    file_mime_type: args.fileMimeType || null,
    file_size_bytes: args.fileSizeBytes || null,
    uploaded_by: args.uploadedBy || null,
    import_status: "uploaded",
    notes: args.notes || null,
  });

  return toBatchSummary(row);
}

export async function updateImportBatchStatus(
  client: AppSupabaseClient,
  batchId: string,
  importStatus: ImportStatus,
  extras: {
    notes?: string | null;
    startedAt?: string | null;
    completedAt?: string | null;
    totalRawRows?: number;
    totalValidRows?: number;
    totalErrorRows?: number;
  } = {}
) {
  const row = await updateImportBatchStatusRecord(client, batchId, importStatus, {
    notes: extras.notes,
    started_at: extras.startedAt,
    completed_at: extras.completedAt,
    total_raw_rows: extras.totalRawRows,
    total_valid_rows: extras.totalValidRows,
    total_error_rows: extras.totalErrorRows,
  });

  return toBatchSummary(row);
}

export async function finalizeImportBatch(
  client: AppSupabaseClient,
  batchId: string,
  args: {
    importStatus?: Extract<
      ImportStatus,
      "completed" | "failed" | "partially_completed" | "normalized" | "reconciled"
    >;
    totalRawRows: number;
    totalValidRows: number;
    totalErrorRows: number;
    notes?: string | null;
  }
) {
  const row = await updateImportBatchRecord(client, batchId, {
    import_status: args.importStatus || "completed",
    total_raw_rows: args.totalRawRows,
    total_valid_rows: args.totalValidRows,
    total_error_rows: args.totalErrorRows,
    notes: args.notes || null,
    completed_at: new Date().toISOString(),
  });

  return toBatchSummary(row);
}

export async function getImportBatchSummary(
  client: AppSupabaseClient,
  batchId: string
) {
  return toBatchSummary(await fetchImportBatchRecord(client, batchId));
}

export async function listImportBatches(client: AppSupabaseClient, limit = 50) {
  const rows = await listImportBatchRecords(client, limit);
  return rows.map(toBatchSummary);
}
