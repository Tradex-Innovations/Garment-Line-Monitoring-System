import * as XLSX from "xlsx";
import type { FaceMetadataRow, FaceParsedRow, FaceWorkbookParseResult } from "@/types/pipeline";
import { cleanCellValue, normalizeWhitespace } from "./shared";

const FACE_HEADER_ALIASES: Record<string, keyof Omit<FaceParsedRow, "rowNumber" | "rawPayload" | "parseStatus" | "parseError">> = {
  firstname: "firstName",
  lastname: "lastName",
  id: "employeeId",
  department: "department",
  date: "dateText",
  weekday: "weekday",
  records: "recordsText",
};

const REQUIRED_FACE_COLUMNS = ["employeeId", "dateText", "recordsText"] as const;

function pickFaceSheet(workbook: XLSX.WorkBook) {
  const visibleSheet = workbook.Workbook?.Sheets?.find((sheet) => sheet.Hidden !== 1);
  const sheetName = visibleSheet?.name || workbook.SheetNames[0];

  if (!sheetName) {
    throw new Error("The face workbook does not contain a readable worksheet.");
  }

  return workbook.Sheets[sheetName];
}

function canonicalizeHeader(value: string | null) {
  return value?.toLowerCase().replace(/[^a-z0-9]+/g, "") || "";
}

function detectHeaderRow(rows: unknown[][]) {
  for (let index = 0; index < rows.length; index += 1) {
    const row = rows[index].map(cleanCellValue).filter(Boolean) as string[];
    const canonicalHeaders = row.map(canonicalizeHeader);

    if (["firstname", "lastname", "id", "department", "date", "weekday", "records"].every((header) => canonicalHeaders.includes(header))) {
      return index;
    }
  }

  return -1;
}

function buildHeaderMap(row: unknown[]) {
  return row.reduce<Record<string, number>>((map, cell, index) => {
    const alias = FACE_HEADER_ALIASES[canonicalizeHeader(cleanCellValue(cell))];
    if (alias) {
      map[alias] = index;
    }
    return map;
  }, {});
}

function toFaceMetadataRows(rows: unknown[][], headerRowIndex: number): FaceMetadataRow[] {
  return rows.slice(0, headerRowIndex).map((row, index) => ({
    rowNumber: index + 1,
    values: row.map(cleanCellValue).filter(Boolean) as string[],
  }));
}

function readRowCell(row: unknown[], columnIndex: number | undefined) {
  if (columnIndex === undefined) {
    return null;
  }

  return cleanCellValue(row[columnIndex]);
}

export async function parseFaceWorkbook(file: File): Promise<FaceWorkbookParseResult> {
  const buffer = await file.arrayBuffer();
  const workbook = XLSX.read(buffer, {
    type: "array",
    cellDates: true,
    raw: false,
    dense: true,
  });
  const worksheet = pickFaceSheet(workbook);
  const rows = XLSX.utils.sheet_to_json(worksheet, {
    header: 1,
    defval: "",
    blankrows: false,
    raw: false,
  }) as unknown[][];

  const headerRowIndex = detectHeaderRow(rows);
  if (headerRowIndex < 0) {
    throw new Error(
      "Could not find the face workbook header row. Expected columns: First Name, Last Name, ID, Department, Date, Weekday, Records."
    );
  }

  const headerMap = buildHeaderMap(rows[headerRowIndex]);
  const metadataRows = toFaceMetadataRows(rows, headerRowIndex);

  const parsedRows: FaceParsedRow[] = rows
    .slice(headerRowIndex + 1)
    .map((row, relativeIndex) => {
      const rowNumber = headerRowIndex + relativeIndex + 2;
      const firstName = readRowCell(row, headerMap.firstName);
      const lastName = readRowCell(row, headerMap.lastName);
      const employeeId = readRowCell(row, headerMap.employeeId);
      const department = readRowCell(row, headerMap.department);
      const dateText = readRowCell(row, headerMap.dateText);
      const weekday = readRowCell(row, headerMap.weekday);
      const recordsText = readRowCell(row, headerMap.recordsText);

      const missingFields = REQUIRED_FACE_COLUMNS.filter((key) => {
        const value = { employeeId, dateText, recordsText }[key];
        return !value;
      });

      return {
        rowNumber,
        firstName,
        lastName,
        employeeId,
        department,
        dateText,
        weekday,
        recordsText,
        rawPayload: {
          worksheetRowNumber: rowNumber,
          source: "face_workbook",
          cells: row.map(cleanCellValue),
        },
        parseStatus: missingFields.length ? ("failed" as const) : ("parsed" as const),
        parseError: missingFields.length
          ? `Missing required face fields: ${missingFields.join(", ")}`
          : null,
      };
    })
    .filter((row) =>
      [
        row.firstName,
        row.lastName,
        row.employeeId,
        row.department,
        row.dateText,
        row.weekday,
        row.recordsText,
      ].some(Boolean)
    );

  const warnings = metadataRows
    .filter((row) => row.values.length > 0)
    .map((row) => normalizeWhitespace(row.values.join(" ")))
    .filter(Boolean);

  return {
    sourceType: "face",
    headerRowIndex: headerRowIndex + 1,
    metadataRows,
    rows: parsedRows,
    warnings,
  };
}
