import type { Database } from "@/types/database";
import type {
  FaceDailySummaryInput,
  FaceParsedRow,
  FaceWorkbookParseResult,
  NormalizedFaceEventRow,
} from "@/types/pipeline";
import type { AppSupabaseClient } from "../repositories/base-repository";
import {
  fetchFaceRawRows,
  replaceFaceDailySummaries,
  replaceFaceEvents,
  replaceFaceRawRows,
} from "../repositories/face-repository";
import {
  combineSourceName,
  createQualityFlagSet,
  isLikelyHumanName,
  parseFlexibleDateText,
  toDatabaseTime,
} from "../parsers/shared";

type FaceRawRow = Database["public"]["Tables"]["face_raw_rows"]["Row"];

function isGenericDepartment(value: string | null) {
  if (!value) {
    return false;
  }

  const normalized = value.trim().toLowerCase();
  return ["union north", "production", "factory", "garment"].includes(normalized);
}

function splitRecordTokens(recordsText: string | null) {
  return (recordsText || "")
    .split(";")
    .map((value) => value.trim())
    .filter(Boolean);
}

export async function parseFaceWorkbook(file: File): Promise<FaceWorkbookParseResult> {
  const { parseFaceWorkbook: parseWorkbook } = await import(
    "../parsers/face-workbook-parser"
  );
  return parseWorkbook(file);
}

export async function saveFaceRawRows(
  client: AppSupabaseClient,
  batchId: string,
  rows: FaceParsedRow[]
) {
  const inserts = rows.map((row) => ({
    import_batch_id: batchId,
    row_number: row.rowNumber,
    source_first_name: row.firstName,
    source_last_name: row.lastName,
    source_employee_id: row.employeeId,
    source_department: row.department,
    source_date_text: row.dateText,
    source_weekday: row.weekday,
    source_records_text: row.recordsText,
    raw_payload: row.rawPayload,
    parse_status: row.parseStatus,
    parse_error: row.parseError,
  }));

  await replaceFaceRawRows(client, batchId, inserts);
}

export function buildFaceDailySummary(
  batchId: string,
  rawRows: FaceRawRow[]
): {
  eventRows: Database["public"]["Tables"]["face_events"]["Insert"][];
  summaryRows: Database["public"]["Tables"]["face_daily_summary"]["Insert"][];
  validRowCount: number;
  errorRowCount: number;
} {
  const eventRows: Database["public"]["Tables"]["face_events"]["Insert"][] = [];
  const summaryMap = new Map<string, FaceDailySummaryInput>();
  let validRowCount = 0;
  let errorRowCount = 0;

  for (const rawRow of rawRows) {
    const employeeCode = rawRow.source_employee_id?.trim() || null;
    const eventDate = parseFlexibleDateText(rawRow.source_date_text);

    if (!employeeCode || !eventDate) {
      errorRowCount += 1;
      continue;
    }

    validRowCount += 1;
    const summaryKey = `${employeeCode}::${eventDate}`;
    const existingSummary = summaryMap.get(summaryKey);
    const sourceName = combineSourceName(
      rawRow.source_first_name,
      rawRow.source_last_name
    );
    const qualityFlags = createQualityFlagSet(existingSummary?.qualityFlags || []);
    const normalizedRecords = existingSummary?.normalizedRecords.slice() || [];
    const duplicateCountByTime = normalizedRecords.reduce<Map<string, number>>((map, record) => {
      map.set(record.time, (map.get(record.time) || 0) + 1);
      return map;
    }, new Map());

    if (!employeeCode) {
      qualityFlags.add("missing_employee_id");
    }
    if (isGenericDepartment(rawRow.source_department)) {
      qualityFlags.add("generic_department");
    }
    if (!isLikelyHumanName(sourceName)) {
      qualityFlags.add("nonhuman_name_source");
    }

    const timeTokens = splitRecordTokens(rawRow.source_records_text);
    const validTimes = timeTokens.filter((token) => /^\d{2}:\d{2}$/.test(token));
    const invalidTimes = timeTokens.filter((token) => !/^\d{2}:\d{2}$/.test(token));

    if (invalidTimes.length) {
      qualityFlags.add("invalid_time_token");
    }

    const sortedTimes = [...validTimes].sort();

    for (const time of sortedTimes) {
      const duplicateCount = duplicateCountByTime.get(time) || 0;
      const isDuplicate = duplicateCount > 0;
      duplicateCountByTime.set(time, duplicateCount + 1);

      if (isDuplicate) {
        qualityFlags.add("duplicate_face_time");
      }

      normalizedRecords.push({
        time,
        isDuplicate,
      });

      eventRows.push({
        import_batch_id: batchId,
        raw_row_id: rawRow.id,
        employee_code: employeeCode,
        event_date: eventDate,
        event_time: `${time}:00`,
        event_timestamp: null,
        event_sequence: normalizedRecords.length,
        source_records_text: rawRow.source_records_text,
        is_duplicate: isDuplicate,
      });
    }

    const allTimes = normalizedRecords.map((record) => record.time).sort();
    const faceFirstSeen = allTimes[0] || null;
    const faceLastSeen = allTimes[allTimes.length - 1] || null;
    const duplicateEventCount = normalizedRecords.filter((record) => record.isDuplicate).length;

    summaryMap.set(summaryKey, {
      employeeCode,
      eventDate,
      faceFirstSeen: toDatabaseTime(faceFirstSeen),
      faceLastSeen: toDatabaseTime(faceLastSeen),
      faceEventCount: normalizedRecords.length,
      duplicateEventCount,
      normalizedRecords,
      qualityFlags: [...qualityFlags],
    });
  }

  const summaryRows = [...summaryMap.values()].map((summary) => ({
    import_batch_id: batchId,
    employee_code: summary.employeeCode,
    event_date: summary.eventDate,
    face_first_seen: summary.faceFirstSeen,
    face_last_seen: summary.faceLastSeen,
    face_event_count: summary.faceEventCount,
    duplicate_event_count: summary.duplicateEventCount,
    normalized_records: summary.normalizedRecords,
    quality_flags: summary.qualityFlags,
  }));

  return {
    eventRows,
    summaryRows,
    validRowCount,
    errorRowCount,
  };
}

export async function normalizeFaceRows(
  client: AppSupabaseClient,
  batchId: string
) {
  const rawRows = await fetchFaceRawRows(client, batchId);
  const { eventRows, summaryRows, validRowCount, errorRowCount } =
    buildFaceDailySummary(batchId, rawRows);

  await replaceFaceEvents(client, batchId, eventRows);
  await replaceFaceDailySummaries(client, batchId, summaryRows);

  return {
    eventRows,
    summaryRows,
    validRowCount,
    errorRowCount,
    totalRawRows: rawRows.length,
  };
}
