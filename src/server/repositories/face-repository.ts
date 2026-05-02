import type { Database } from "@/types/database";
import type { AppSupabaseClient } from "./base-repository";
import { insertInChunks } from "./base-repository";

type FaceRawInsert = Database["public"]["Tables"]["face_raw_rows"]["Insert"];
type FaceEventInsert = Database["public"]["Tables"]["face_events"]["Insert"];
type FaceSummaryInsert = Database["public"]["Tables"]["face_daily_summary"]["Insert"];

export async function replaceFaceRawRows(
  client: AppSupabaseClient,
  batchId: string,
  rows: FaceRawInsert[]
) {
  const deleteResult = await client
    .from("face_raw_rows")
    .delete()
    .eq("import_batch_id", batchId);

  if (deleteResult.error) {
    throw new Error(deleteResult.error.message);
  }

  await insertInChunks({ client, table: "face_raw_rows", rows });
}

export async function fetchFaceRawRows(client: AppSupabaseClient, batchId: string) {
  const { data, error } = await client
    .from("face_raw_rows")
    .select("*")
    .eq("import_batch_id", batchId)
    .order("row_number", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function replaceFaceEvents(
  client: AppSupabaseClient,
  batchId: string,
  rows: FaceEventInsert[]
) {
  const deleteResult = await client
    .from("face_events")
    .delete()
    .eq("import_batch_id", batchId);

  if (deleteResult.error) {
    throw new Error(deleteResult.error.message);
  }

  await insertInChunks({ client, table: "face_events", rows });
}

export async function replaceFaceDailySummaries(
  client: AppSupabaseClient,
  batchId: string,
  rows: FaceSummaryInsert[]
) {
  const deleteResult = await client
    .from("face_daily_summary")
    .delete()
    .eq("import_batch_id", batchId);

  if (deleteResult.error) {
    throw new Error(deleteResult.error.message);
  }

  await insertInChunks({ client, table: "face_daily_summary", rows });
}

export async function fetchFaceEventsForEmployeeDate(
  client: AppSupabaseClient,
  employeeCode: string,
  attendanceDate: string
) {
  const { data, error } = await client
    .from("face_events")
    .select("*")
    .eq("employee_code", employeeCode)
    .eq("event_date", attendanceDate)
    .order("event_time", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function fetchFaceRawRowsByEmployeeCode(
  client: AppSupabaseClient,
  employeeCode: string
) {
  const { data, error } = await client
    .from("face_raw_rows")
    .select("*")
    .eq("source_employee_id", employeeCode)
    .order("row_number", { ascending: true });

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}
