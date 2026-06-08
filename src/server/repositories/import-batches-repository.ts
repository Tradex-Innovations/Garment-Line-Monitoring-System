import type { Database } from "@/types/database";
import type { ImportStatus } from "@/types/pipeline";
import type { AppSupabaseClient } from "./base-repository";

type ImportBatchInsert = Database["public"]["Tables"]["import_batches"]["Insert"];
type ImportBatchUpdate = Database["public"]["Tables"]["import_batches"]["Update"];

export async function createImportBatchRecord(
  client: AppSupabaseClient,
  payload: ImportBatchInsert
) {
  const { data, error } = await client
    .from("import_batches")
    .insert(payload)
    .select("*")
    .single();

  if (error) {
    throw new Error(error.message);
  }

  return data;
}

export async function updateImportBatchRecord(
  client: AppSupabaseClient,
  batchId: string,
  payload: ImportBatchUpdate
) {
  const { data, error } = await client
    .from("import_batches")
    .update(payload)
    .eq("id", batchId)
    .select("*")
    .single();

  if (error) {
    throw new Error(error.message);
  }

  return data;
}

export async function updateImportBatchStatusRecord(
  client: AppSupabaseClient,
  batchId: string,
  importStatus: ImportStatus,
  payload: Omit<ImportBatchUpdate, "import_status"> = {}
) {
  return updateImportBatchRecord(client, batchId, {
    ...payload,
    import_status: importStatus,
  });
}

export async function fetchImportBatchRecord(
  client: AppSupabaseClient,
  batchId: string
) {
  const { data, error } = await client
    .from("import_batches")
    .select("*")
    .eq("id", batchId)
    .single();

  if (error) {
    throw new Error(error.message);
  }

  return data;
}

export async function listImportBatchRecords(client: AppSupabaseClient, limit = 50) {
  const { data, error } = await client
    .from("import_batches")
    .select("*")
    .order("created_at", { ascending: false })
    .limit(limit);

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}
