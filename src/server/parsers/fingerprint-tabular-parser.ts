import * as XLSX from "xlsx";
import type {
  FingerprintFileFormat,
  FingerprintFileParseResult,
  FingerprintParsedRow,
} from "@/types/pipeline";
import { cleanCellValue } from "./shared";

const FINGERPRINT_HEADER_ALIASES: Record<
  string,
  keyof Omit<FingerprintParsedRow, "rowNumber" | "rawPayload" | "parseStatus" | "parseError">
> = {
  empno: "empNo",
  epfno: "epfNo",
  name: "name",
  designation: "designation",
  department: "department",
  date: "dateText",
  timein: "timeInText",
  timeout: "timeOutText",
  lateearly: "lateEarlyText",
  day: "dayText",
  ot: "otText",
  leavetype: "leaveType",
  leavedaystotal: "leaveDaysTotalText",
  nopaydaystotal: "nopayDaysTotalText",
  otherleavedays: "otherLeaveDaysText",
};

const REQUIRED_FINGERPRINT_HEADERS = ["empNo", "dateText"] as const;

function canonicalizeHeader(value: string | null) {
  return value?.toLowerCase().replace(/[^a-z0-9]+/g, "") || "";
}

function detectHeaderRow(rows: unknown[][]) {
  for (let index = 0; index < rows.length; index += 1) {
    const canonicalHeaders = rows[index].map((cell) => canonicalizeHeader(cleanCellValue(cell)));

    if (["empno", "epfno", "name", "designation", "department", "date"].every((header) => canonicalHeaders.includes(header))) {
      return index;
    }
  }

  return -1;
}

function buildHeaderMap(row: unknown[]) {
  return row.reduce<Record<string, number>>((map, cell, index) => {
    const headerKey = FINGERPRINT_HEADER_ALIASES[canonicalizeHeader(cleanCellValue(cell))];
    if (headerKey) {
      map[headerKey] = index;
    }
    return map;
  }, {});
}

function readRowCell(row: unknown[], columnIndex: number | undefined) {
  if (columnIndex === undefined) {
    return null;
  }

  return cleanCellValue(row[columnIndex]);
}

export async function parseFingerprintTabularFile(
  file: File,
  fileFormat: Exclude<FingerprintFileFormat, "pdf">
): Promise<FingerprintFileParseResult> {
  const buffer = await file.arrayBuffer();
  const workbook = XLSX.read(buffer, {
    type: "array",
    cellDates: true,
    raw: false,
    dense: true,
  });
  const worksheetName = workbook.SheetNames[0];
  const worksheet = workbook.Sheets[worksheetName];
  const rows = XLSX.utils.sheet_to_json(worksheet, {
    header: 1,
    defval: "",
    blankrows: false,
    raw: false,
  }) as unknown[][];

  const headerRowIndex = detectHeaderRow(rows);
  if (headerRowIndex < 0) {
    throw new Error(
      "Could not find the fingerprint attendance header row in the workbook or CSV."
    );
  }

  const headerMap = buildHeaderMap(rows[headerRowIndex]);

  const parsedRows: FingerprintParsedRow[] = rows
    .slice(headerRowIndex + 1)
    .map((row, relativeIndex) => {
      const rowNumber = headerRowIndex + relativeIndex + 2;
      const empNo = readRowCell(row, headerMap.empNo);
      const epfNo = readRowCell(row, headerMap.epfNo);
      const name = readRowCell(row, headerMap.name);
      const designation = readRowCell(row, headerMap.designation);
      const department = readRowCell(row, headerMap.department);
      const dateText = readRowCell(row, headerMap.dateText);
      const timeInText = readRowCell(row, headerMap.timeInText);
      const timeOutText = readRowCell(row, headerMap.timeOutText);
      const lateEarlyText = readRowCell(row, headerMap.lateEarlyText);
      const dayText = readRowCell(row, headerMap.dayText);
      const otText = readRowCell(row, headerMap.otText);
      const leaveType = readRowCell(row, headerMap.leaveType);
      const leaveDaysTotalText = readRowCell(row, headerMap.leaveDaysTotalText);
      const nopayDaysTotalText = readRowCell(row, headerMap.nopayDaysTotalText);
      const otherLeaveDaysText = readRowCell(row, headerMap.otherLeaveDaysText);

      const missingFields = REQUIRED_FINGERPRINT_HEADERS.filter((key) => {
        const value = { empNo, dateText }[key];
        return !value;
      });

      return {
        rowNumber,
        empNo,
        epfNo,
        name,
        designation,
        department,
        dateText,
        timeInText,
        timeOutText,
        lateEarlyText,
        dayText,
        otText,
        leaveType,
        leaveDaysTotalText,
        nopayDaysTotalText,
        otherLeaveDaysText,
        rawPayload: {
          worksheetRowNumber: rowNumber,
          source: `fingerprint_${fileFormat}`,
          cells: row.map(cleanCellValue),
        },
        parseStatus: missingFields.length ? ("failed" as const) : ("parsed" as const),
        parseError: missingFields.length
          ? `Missing required fingerprint fields: ${missingFields.join(", ")}`
          : null,
      };
    })
    .filter((row) =>
      [
        row.empNo,
        row.epfNo,
        row.name,
        row.designation,
        row.department,
        row.dateText,
        row.timeInText,
        row.timeOutText,
        row.leaveType,
      ].some(Boolean)
    );

  return {
    sourceType: "fingerprint",
    fileFormat,
    rows: parsedRows,
    warnings: [],
  };
}
