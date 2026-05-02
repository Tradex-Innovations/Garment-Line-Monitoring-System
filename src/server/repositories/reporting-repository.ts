import type { AppSupabaseClient } from "./base-repository";

export async function fetchValidationSummaryRows(client: AppSupabaseClient) {
  const { data, error } = await client
    .from("vw_validation_summary")
    .select("*")
    .order("attendance_date", { ascending: false });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function fetchDepartmentValidationSummaryRows(client: AppSupabaseClient) {
  const { data, error } = await client
    .from("vw_department_validation_summary")
    .select("*")
    .order("attendance_date", { ascending: false })
    .order("department_name", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function fetchReconciliationExceptions(client: AppSupabaseClient) {
  const { data, error } = await client
    .from("vw_reconciliation_exceptions")
    .select("*")
    .order("attendance_date", { ascending: false })
    .order("employee_code", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function fetchAuditLogsForEntity(
  client: AppSupabaseClient,
  entityType: string,
  entityId: string
) {
  const { data, error } = await client
    .from("audit_logs")
    .select("*")
    .eq("entity_type", entityType)
    .eq("entity_id", entityId)
    .order("created_at", { ascending: false });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}
