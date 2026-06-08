import * as pdfjsLib from "pdfjs-dist/legacy/build/pdf.mjs";
import pdfWorker from "pdfjs-dist/legacy/build/pdf.worker.min.mjs?url";
import type { FingerprintFileParseResult, FingerprintParsedRow } from "@/types/pipeline";
import { cleanCellValue, normalizeWhitespace } from "./shared";

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker;

type PositionedText = {
  text: string;
  x: number;
  y: number;
};

type AnchorColumn =
  | "empNo"
  | "epfNo"
  | "name"
  | "designation"
  | "department"
  | "dateText"
  | "timeInText"
  | "timeOutText"
  | "lateEarlyText"
  | "dayText"
  | "otText"
  | "leaveType"
  | "leaveDaysTotalText"
  | "nopayDaysTotalText"
  | "otherLeaveDaysText";

type Anchor = {
  key: AnchorColumn;
  label: string;
  x: number;
};

const HEADER_COLUMNS: Array<{ key: AnchorColumn; label: string }> = [
  { key: "empNo", label: "EmpNo" },
  { key: "epfNo", label: "EpfNo" },
  { key: "name", label: "Name" },
  { key: "designation", label: "Designation" },
  { key: "department", label: "Department" },
  { key: "dateText", label: "Date" },
  { key: "timeInText", label: "Time In" },
  { key: "timeOutText", label: "Time Out" },
  { key: "lateEarlyText", label: "Late/Early" },
  { key: "dayText", label: "Day" },
  { key: "otText", label: "OT" },
  { key: "leaveType", label: "Leave Type" },
  { key: "leaveDaysTotalText", label: "Leave DaysTotal" },
  { key: "nopayDaysTotalText", label: "Nopay DaysTotal" },
  { key: "otherLeaveDaysText", label: "Other Leave Days" },
];

function canonicalize(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "");
}

function groupRows(items: PositionedText[], yTolerance = 3) {
  const rows: Array<{ y: number; items: PositionedText[] }> = [];

  const sorted = [...items].sort((left, right) => {
    if (Math.abs(right.y - left.y) > yTolerance) {
      return right.y - left.y;
    }

    return left.x - right.x;
  });

  for (const item of sorted) {
    const existingRow = rows.find((row) => Math.abs(row.y - item.y) <= yTolerance);
    if (existingRow) {
      existingRow.items.push(item);
    } else {
      rows.push({ y: item.y, items: [item] });
    }
  }

  return rows.map((row) => ({
    y: row.y,
    items: row.items.sort((left, right) => left.x - right.x),
  }));
}

function normalizeTextItems(textContent: any) {
  return textContent.items
    .map((item: any) => {
      const text = normalizeWhitespace(item.str || "");
      if (!text) {
        return null;
      }

      return {
        text,
        x: item.transform?.[4] || 0,
        y: item.transform?.[5] || 0,
      } satisfies PositionedText;
    })
    .filter(Boolean) as PositionedText[];
}

function findHeaderAnchors(headerRow: PositionedText[]) {
  const anchors: Anchor[] = [];
  const canonicalItems = headerRow.map((item) => canonicalize(item.text));

  for (const header of HEADER_COLUMNS) {
    const headerLabel = canonicalize(header.label);

    for (let start = 0; start < canonicalItems.length; start += 1) {
      let accumulator = "";

      for (let end = start; end < canonicalItems.length; end += 1) {
        accumulator += canonicalItems[end];
        if (accumulator === headerLabel) {
          anchors.push({
            key: header.key,
            label: header.label,
            x: headerRow[start].x,
          });
          start = canonicalItems.length;
          break;
        }
      }
    }
  }

  return anchors.sort((left, right) => left.x - right.x);
}

function assignToAnchors(row: PositionedText[], anchors: Anchor[]) {
  const values = HEADER_COLUMNS.reduce<Record<AnchorColumn, string[]>>((map, column) => {
    map[column.key] = [];
    return map;
  }, {} as Record<AnchorColumn, string[]>);

  if (!row.length) {
    return values;
  }

  for (const item of row) {
    let targetAnchor = anchors[anchors.length - 1];

    for (let index = 0; index < anchors.length; index += 1) {
      const anchor = anchors[index];
      const nextAnchor = anchors[index + 1];
      if (!nextAnchor || item.x < nextAnchor.x) {
        targetAnchor = anchor;
        break;
      }
    }

    values[targetAnchor.key].push(item.text);
  }

  return values;
}

function looksLikeHeaderRow(row: PositionedText[]) {
  const rowText = canonicalize(row.map((item) => item.text).join(" "));
  return rowText.includes("empno") && rowText.includes("epfno") && rowText.includes("timeout");
}

function appendContinuationRow(
  target: FingerprintParsedRow,
  columnValues: Record<AnchorColumn, string[]>
) {
  const appendableKeys: AnchorColumn[] = ["name", "designation", "department"];

  for (const key of appendableKeys) {
    const nextValue = normalizeWhitespace(columnValues[key].join(" "));
    if (!nextValue) {
      continue;
    }

    const currentValue = target[key];
    target[key] = currentValue
      ? normalizeWhitespace(`${currentValue} ${nextValue}`)
      : nextValue;
  }
}

export async function parseFingerprintPdf(file: File): Promise<FingerprintFileParseResult> {
  const buffer = await file.arrayBuffer();
  const pdf = await pdfjsLib.getDocument({ data: buffer }).promise;
  const rows: FingerprintParsedRow[] = [];
  const warnings: string[] = [];
  let nextRowNumber = 1;

  for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
    const page = await pdf.getPage(pageNumber);
    const textContent = await page.getTextContent();
    const groupedRows = groupRows(normalizeTextItems(textContent));
    const headerRowIndex = groupedRows.findIndex((row) => looksLikeHeaderRow(row.items));

    if (headerRowIndex < 0) {
      warnings.push(`Page ${pageNumber}: header row was not detected and the page was skipped.`);
      continue;
    }

    const anchors = findHeaderAnchors(groupedRows[headerRowIndex].items);
    if (anchors.length < 10) {
      warnings.push(
        `Page ${pageNumber}: only ${anchors.length} fingerprint header anchors were detected. Parsing may be incomplete.`
      );
    }

    for (const row of groupedRows.slice(headerRowIndex + 1)) {
      if (looksLikeHeaderRow(row.items)) {
        continue;
      }

      const rowText = normalizeWhitespace(row.items.map((item) => item.text).join(" "));
      if (!rowText || /^page\s+\d+/i.test(rowText)) {
        continue;
      }

      const assignedValues = assignToAnchors(row.items, anchors);
      const empNo = cleanCellValue(assignedValues.empNo.join(" "));
      const epfNo = cleanCellValue(assignedValues.epfNo.join(" "));
      const name = cleanCellValue(assignedValues.name.join(" "));
      const designation = cleanCellValue(assignedValues.designation.join(" "));
      const department = cleanCellValue(assignedValues.department.join(" "));
      const dateText = cleanCellValue(assignedValues.dateText.join(" "));
      const timeInText = cleanCellValue(assignedValues.timeInText.join(" "));
      const timeOutText = cleanCellValue(assignedValues.timeOutText.join(" "));
      const lateEarlyText = cleanCellValue(assignedValues.lateEarlyText.join(" "));
      const dayText = cleanCellValue(assignedValues.dayText.join(" "));
      const otText = cleanCellValue(assignedValues.otText.join(" "));
      const leaveType = cleanCellValue(assignedValues.leaveType.join(" "));
      const leaveDaysTotalText = cleanCellValue(assignedValues.leaveDaysTotalText.join(" "));
      const nopayDaysTotalText = cleanCellValue(assignedValues.nopayDaysTotalText.join(" "));
      const otherLeaveDaysText = cleanCellValue(assignedValues.otherLeaveDaysText.join(" "));

      if (!empNo && !dateText && rows.length) {
        appendContinuationRow(rows[rows.length - 1], assignedValues);
        warnings.push(`Page ${pageNumber}: merged a continuation row into row ${rows[rows.length - 1].rowNumber}.`);
        continue;
      }

      const missingFields = ["empNo", "dateText"].filter((key) => {
        const value = key === "empNo" ? empNo : dateText;
        return !value;
      });

      rows.push({
        rowNumber: nextRowNumber,
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
          source: "fingerprint_pdf",
          pageNumber,
          yPosition: row.y,
          rowText,
          columns: Object.fromEntries(
            Object.entries(assignedValues).map(([key, value]) => [
              key,
              normalizeWhitespace(value.join(" ")),
            ])
          ),
        },
        parseStatus: missingFields.length ? "failed" : "parsed",
        parseError: missingFields.length
          ? `Missing required fingerprint fields: ${missingFields.join(", ")}`
          : null,
      });

      nextRowNumber += 1;
    }
  }

  return {
    sourceType: "fingerprint",
    fileFormat: "pdf",
    rows,
    warnings,
  };
}
