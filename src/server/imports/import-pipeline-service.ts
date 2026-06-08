import type { AppSupabaseClient } from "../repositories/base-repository";
import { createImportBatch, finalizeImportBatch, updateImportBatchStatus } from "./import-batch-service";
import { uploadImportFileToSupabaseStorage } from "./storage-service";
import {
  normalizeFaceRows,
  parseFaceWorkbook,
  saveFaceRawRows,
} from "./face-import-service";
import {
  normalizeFingerprintRows,
  parseFingerprintFile,
  saveFingerprintRawRows,
} from "./fingerprint-import-service";
import {
  upsertEmployeesFromFace,
  upsertEmployeesFromFingerprint,
} from "./employee-sync-service";
import { buildImportsStoragePath } from "../parsers/shared";

async function failBatchIfCreated(
  client: AppSupabaseClient,
  batchId: string | null,
  error: unknown
) {
  if (!batchId) {
    return;
  }

  await updateImportBatchStatus(client, batchId, "failed", {
    completedAt: new Date().toISOString(),
    notes: error instanceof Error ? error.message : String(error),
  });
}

export async function runFaceImportPipeline(
  client: AppSupabaseClient,
  args: {
    file: File;
    uploadedBy?: string | null;
  }
) {
  const batchId = crypto.randomUUID();
  const now = new Date().toISOString();

  try {
    const storagePath = buildImportsStoragePath({
      sourceType: "face",
      batchId,
      originalFilename: args.file.name,
    });
    const batch = await createImportBatch(client, {
      batchId,
      sourceType: "face",
      originalFilename: args.file.name,
      storagePath,
      fileMimeType: args.file.type || null,
      fileSizeBytes: args.file.size,
      uploadedBy: args.uploadedBy || null,
    });

    await uploadImportFileToSupabaseStorage(client, {
      batchId,
      sourceType: "face",
      file: args.file,
    });
    await updateImportBatchStatus(client, batch.id, "processing", { startedAt: now });

    const parsed = await parseFaceWorkbook(args.file);
    await saveFaceRawRows(client, batch.id, parsed.rows);
    await updateImportBatchStatus(client, batch.id, "parsed", {
      totalRawRows: parsed.rows.length,
      notes: parsed.warnings.join(" | ") || null,
    });

    await upsertEmployeesFromFace(client, parsed.rows);
    const normalized = await normalizeFaceRows(client, batch.id);

    return finalizeImportBatch(client, batch.id, {
      importStatus: "normalized",
      totalRawRows: normalized.totalRawRows,
      totalValidRows: normalized.validRowCount,
      totalErrorRows: normalized.errorRowCount,
      notes: parsed.warnings.join(" | ") || null,
    });
  } catch (error) {
    await failBatchIfCreated(client, batchId, error);
    throw error;
  }
}

export async function runFingerprintImportPipeline(
  client: AppSupabaseClient,
  args: {
    file: File;
    uploadedBy?: string | null;
  }
) {
  const batchId = crypto.randomUUID();
  const now = new Date().toISOString();

  try {
    const storagePath = buildImportsStoragePath({
      sourceType: "fingerprint",
      batchId,
      originalFilename: args.file.name,
    });
    const batch = await createImportBatch(client, {
      batchId,
      sourceType: "fingerprint",
      originalFilename: args.file.name,
      storagePath,
      fileMimeType: args.file.type || null,
      fileSizeBytes: args.file.size,
      uploadedBy: args.uploadedBy || null,
    });

    await uploadImportFileToSupabaseStorage(client, {
      batchId,
      sourceType: "fingerprint",
      file: args.file,
    });
    await updateImportBatchStatus(client, batch.id, "processing", { startedAt: now });

    const parsed = await parseFingerprintFile(args.file);
    await saveFingerprintRawRows(client, batch.id, parsed.rows);
    await updateImportBatchStatus(client, batch.id, "parsed", {
      totalRawRows: parsed.rows.length,
      notes: parsed.warnings.join(" | ") || null,
    });

    await upsertEmployeesFromFingerprint(client, parsed.rows);
    const normalized = await normalizeFingerprintRows(client, batch.id);

    return finalizeImportBatch(client, batch.id, {
      importStatus: "normalized",
      totalRawRows: normalized.totalRawRows,
      totalValidRows: normalized.validRowCount,
      totalErrorRows: normalized.errorRowCount,
      notes: parsed.warnings.join(" | ") || null,
    });
  } catch (error) {
    await failBatchIfCreated(client, batchId, error);
    throw error;
  }
}

export async function rerunFaceNormalization(
  client: AppSupabaseClient,
  batchId: string
) {
  const normalized = await normalizeFaceRows(client, batchId);
  return finalizeImportBatch(client, batchId, {
    importStatus: "normalized",
    totalRawRows: normalized.totalRawRows,
    totalValidRows: normalized.validRowCount,
    totalErrorRows: normalized.errorRowCount,
  });
}

export async function rerunFingerprintNormalization(
  client: AppSupabaseClient,
  batchId: string
) {
  const normalized = await normalizeFingerprintRows(client, batchId);
  return finalizeImportBatch(client, batchId, {
    importStatus: "normalized",
    totalRawRows: normalized.totalRawRows,
    totalValidRows: normalized.validRowCount,
    totalErrorRows: normalized.errorRowCount,
  });
}
