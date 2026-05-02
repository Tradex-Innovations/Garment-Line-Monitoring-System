import type { Database } from "@/types/database";
import type { AppSupabaseClient } from "./base-repository";
import { insertInChunks } from "./base-repository";

type FingerprintRawInsert = Database["public"]["Tables"]["fingerprint_raw_rows"]["Insert"];
type FingerprintAttendanceInsert =
  Database["public"]["Tables"]["fingerprint_daily_attendance"]["Insert"];

export async function replaceFingerprintRawRows(
  client: AppSupabaseClient,
  batchId: string,
  rows: FingerprintRawInsert[]
) {
  const deleteResult = await client
    .from("fingerprint_raw_rows")
    .delete()
    .eq("import_batch_id", batchId);

  if (deleteResult.error) {
    throw new Error(deleteResult.error.message);
  }

  await insertInChunks({ client, table: "fingerprint_raw_rows", rows });
}

export async function fetchFingerprintRawRows(
  client: AppSupabaseClient,
  batchId: string
) {
  const { data, error } = await client
    .from("fingerprint_raw_rows")
    .select("*")
    .eq("import_batch_id", batchId)
    .order("row_number", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function replaceFingerprintDailyAttendance(
  client: AppSupabaseClient,
  batchId: string,
  rows: FingerprintAttendanceInsert[]
) {
  const deleteResult = await client
    .from("fingerprint_daily_attendance")
    .delete()
    .eq("import_batch_id", batchId);

  if (deleteResult.error) {
    throw new Error(deleteResult.error.message);
  }

  await insertInChunks({ client, table: "fingerprint_daily_attendance", rows });
}

export async function fetchFingerprintAttendanceForEmployeeDate(
  client: AppSupabaseClient,
  employeeCode: string,
  attendanceDate: string
) {
  const { data, error } = await client
    .from("fingerprint_daily_attendance")
    .select("*")
    .eq("employee_code", employeeCode)
    .eq("attendance_date", attendanceDate)
    .order("created_at", { ascending: false });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}
