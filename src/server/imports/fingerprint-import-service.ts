import type { Database } from "@/types/database";
import type {
  FingerprintFileParseResult,
  FingerprintParsedRow,
} from "@/types/pipeline";
import type { AppSupabaseClient } from "../repositories/base-repository";
import {
  fetchFingerprintRawRows,
  replaceFingerprintDailyAttendance,
  replaceFingerprintRawRows,
} from "../repositories/fingerprint-repository";
import { parseFingerprintFile as parseFile } from "../parsers/fingerprint-file-parser";
import {
  createQualityFlagSet,
  normalizeWhitespace,
  parseDecimalHours,
  parseFlexibleDateText,
  toDatabaseTime,
} from "../parsers/shared";

type FingerprintRawRow = Database["public"]["Tables"]["fingerprint_raw_rows"]["Row"];

function normalizeLeaveType(value: string | null) {
  return value ? normalizeWhitespace(value).toUpperCase() : null;
}

async function fetchLeaveCodeMap(client: AppSupabaseClient) {
  const { data, error } = await client.from("leave_code_map").select("*");

  if (error) {
    throw new Error(error.message);
  }

  return new Map((data || []).map((row) => [row.code.toUpperCase(), row.attendance_class]));
}

export async function parseFingerprintFile(
  file: File
): Promise<FingerprintFileParseResult> {
  return parseFile(file);
}

export async function saveFingerprintRawRows(
  client: AppSupabaseClient,
  batchId: string,
  rows: FingerprintParsedRow[]
) {
  const inserts = rows.map((row) => ({
    import_batch_id: batchId,
    row_number: row.rowNumber,
    source_emp_no: row.empNo,
    source_epf_no: row.epfNo,
    source_name: row.name,
    source_designation: row.designation,
    source_department: row.department,
    source_date_text: row.dateText,
    source_time_in_text: row.timeInText,
    source_time_out_text: row.timeOutText,
    source_late_early_text: row.lateEarlyText,
    source_day: row.dayText,
    source_ot_text: row.otText,
    source_leave_type: row.leaveType,
    source_leave_days_total_text: row.leaveDaysTotalText,
    source_nopay_days_total_text: row.nopayDaysTotalText,
    source_other_leave_days_text: row.otherLeaveDaysText,
    raw_payload: row.rawPayload,
    parse_status: row.parseStatus,
    parse_error: row.parseError,
  }));

  await replaceFingerprintRawRows(client, batchId, inserts);
}

function buildAttendanceState(args: {
  leaveType: string | null;
  leaveClass: string | null;
  zeroTimePair: boolean;
  timeIn: string | null;
  timeOut: string | null;
  leaveDaysTotal: number | null;
  nopayDaysTotal: number | null;
  otherLeaveDays: number | null;
}) {
  const hasLeaveTotals =
    (args.leaveDaysTotal || 0) > 0 ||
    (args.nopayDaysTotal || 0) > 0 ||
    (args.otherLeaveDays || 0) > 0;

  if (args.leaveType && args.leaveClass === "absent") {
    return "absent" as const;
  }

  if (args.leaveType && (hasLeaveTotals || args.leaveClass === "leave")) {
    return "leave" as const;
  }

  if (args.zeroTimePair && !args.leaveType) {
    return "review" as const;
  }

  if (args.timeIn || args.timeOut) {
    return "present" as const;
  }

  if (args.leaveType) {
    return "review" as const;
  }

  return "no_data" as const;
}

export async function normalizeFingerprintRows(
  client: AppSupabaseClient,
  batchId: string
) {
  const rawRows = await fetchFingerprintRawRows(client, batchId);
  const leaveCodeMap = await fetchLeaveCodeMap(client);
  const normalizedRows: Database["public"]["Tables"]["fingerprint_daily_attendance"]["Insert"][] =
    [];
  let validRowCount = 0;
  let errorRowCount = 0;

  for (const rawRow of rawRows) {
    const employeeCode =
      rawRow.source_emp_no?.trim() || rawRow.source_epf_no?.trim() || null;
    const attendanceDate = parseFlexibleDateText(rawRow.source_date_text);

    if (!employeeCode || !attendanceDate) {
      errorRowCount += 1;
      continue;
    }

    validRowCount += 1;

    const timeInToken = rawRow.source_time_in_text?.trim() || null;
    const timeOutToken = rawRow.source_time_out_text?.trim() || null;
    const zeroTimePair = timeInToken === "00:00" && timeOutToken === "00:00";
    const timeIn =
      timeInToken && /^\d{2}:\d{2}$/.test(timeInToken) ? toDatabaseTime(timeInToken) : null;
    const timeOut =
      timeOutToken && /^\d{2}:\d{2}$/.test(timeOutToken) ? toDatabaseTime(timeOutToken) : null;
    const lateEarlyHours = parseDecimalHours(rawRow.source_late_early_text);
    const otHours = parseDecimalHours(rawRow.source_ot_text);
    const leaveDaysTotal = parseDecimalHours(rawRow.source_leave_days_total_text);
    const nopayDaysTotal = parseDecimalHours(rawRow.source_nopay_days_total_text);
    const otherLeaveDays = parseDecimalHours(rawRow.source_other_leave_days_text);
    const leaveType = normalizeLeaveType(rawRow.source_leave_type);
    const leaveClass = leaveType ? leaveCodeMap.get(leaveType) || null : null;
    const qualityFlags = createQualityFlagSet();

    if (zeroTimePair) {
      qualityFlags.add("zero_time_pair");
    }
    if (timeInToken && !timeIn) {
      qualityFlags.add("invalid_time_in");
    }
    if (timeOutToken && !timeOut) {
      qualityFlags.add("invalid_time_out");
    }

    const numericFields = [
      [rawRow.source_late_early_text, lateEarlyHours],
      [rawRow.source_ot_text, otHours],
      [rawRow.source_leave_days_total_text, leaveDaysTotal],
      [rawRow.source_nopay_days_total_text, nopayDaysTotal],
      [rawRow.source_other_leave_days_text, otherLeaveDays],
    ] as const;

    if (
      numericFields.some(([sourceValue, parsedValue]) => {
        if (!sourceValue) {
          return false;
        }
        return parsedValue === null;
      })
    ) {
      qualityFlags.add("malformed_numeric_field");
    }

    if (leaveType && !timeIn && !timeOut) {
      qualityFlags.add("leave_without_time");
    }

    const attendanceState = buildAttendanceState({
      leaveType,
      leaveClass,
      zeroTimePair,
      timeIn,
      timeOut,
      leaveDaysTotal,
      nopayDaysTotal,
      otherLeaveDays,
    });

    normalizedRows.push({
      import_batch_id: batchId,
      raw_row_id: rawRow.id,
      employee_code: employeeCode,
      epf_no: rawRow.source_epf_no,
      employee_name: rawRow.source_name ? normalizeWhitespace(rawRow.source_name) : null,
      designation: rawRow.source_designation
        ? normalizeWhitespace(rawRow.source_designation)
        : null,
      department_name: rawRow.source_department
        ? normalizeWhitespace(rawRow.source_department)
        : null,
      attendance_date: attendanceDate,
      time_in: zeroTimePair ? toDatabaseTime("00:00") : timeIn,
      time_out: zeroTimePair ? toDatabaseTime("00:00") : timeOut,
      late_early_hours: lateEarlyHours,
      ot_hours: otHours,
      leave_type: leaveType,
      leave_days_total: leaveDaysTotal,
      nopay_days_total: nopayDaysTotal,
      other_leave_days: otherLeaveDays,
      attendance_state: attendanceState,
      quality_flags: [...qualityFlags],
    });
  }

  await replaceFingerprintDailyAttendance(client, batchId, normalizedRows);

  return {
    rows: normalizedRows,
    totalRawRows: rawRows.length,
    validRowCount,
    errorRowCount,
  };
}
