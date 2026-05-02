import type { AppSupabaseClient } from "./base-repository";
import type { Json } from "@/types/database";

export async function runReconciliationRpc(
  client: AppSupabaseClient,
  faceBatchId: string,
  fingerprintBatchId: string
) {
  const { data, error } = await client.rpc("rpc_reconcile_attendance", {
    face_batch_id: faceBatchId,
    fingerprint_batch_id: fingerprintBatchId,
  });

  if (error) {
    throw new Error(error.message);
  }

  return data as Json;
}

export async function runOverrideReconciliationRpc(
  client: AppSupabaseClient,
  args: {
    reconciliationId: string;
    newStatus: string;
    reason: string;
    note?: string | null;
  }
) {
  const { data, error } = await client.rpc("rpc_override_reconciliation", {
    p_reconciliation_id: args.reconciliationId,
    p_new_status: args.newStatus,
    p_reason: args.reason,
    p_note: args.note || null,
  });

  if (error) {
    throw new Error(error.message);
  }

  return data as Json;
}

export async function runAddReconciliationNoteRpc(
  client: AppSupabaseClient,
  args: {
    reconciliationId: string;
    note: string;
  }
) {
  const { data, error } = await client.rpc("rpc_add_reconciliation_note", {
    p_reconciliation_id: args.reconciliationId,
    p_note: args.note,
  });

  if (error) {
    throw new Error(error.message);
  }

  return data as Json;
}

export async function listReconciliationRows(client: AppSupabaseClient) {
  const { data, error } = await client
    .from("attendance_reconciliation")
    .select("*")
    .order("attendance_date", { ascending: false })
    .order("employee_code", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function fetchReconciliationRow(
  client: AppSupabaseClient,
  reconciliationId: string
) {
  const { data, error } = await client
    .from("attendance_reconciliation")
    .select("*")
    .eq("id", reconciliationId)
    .single();

  if (error) {
    throw new Error(error.message);
  }

  return data;
}

export async function fetchReconciliationNotes(
  client: AppSupabaseClient,
  reconciliationId: string
) {
  const { data, error } = await client
    .from("reconciliation_notes")
    .select("*")
    .eq("reconciliation_id", reconciliationId)
    .order("created_at", { ascending: false });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}
