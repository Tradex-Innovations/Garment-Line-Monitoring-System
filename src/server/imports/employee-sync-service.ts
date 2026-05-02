import type { Database } from "@/types/database";
import type { FaceParsedRow, FingerprintParsedRow } from "@/types/pipeline";
import type { AppSupabaseClient } from "../repositories/base-repository";
import {
  fetchEmployeesByCodes,
  upsertDepartments,
  upsertEmployees,
} from "../repositories/employees-repository";
import { combineSourceName, isLikelyHumanName, normalizeWhitespace } from "../parsers/shared";

type EmployeeRow = Database["public"]["Tables"]["employees"]["Row"];

function pickBetterText(existing: string | null, incoming: string | null) {
  if (!incoming) {
    return existing;
  }

  if (!existing) {
    return incoming;
  }

  return incoming.length > existing.length ? incoming : existing;
}

export function mergeEmployeeDisplayData(
  existing: EmployeeRow | undefined,
  incoming: {
    employeeCode: string;
    epfNo?: string | null;
    displayName?: string | null;
    designation?: string | null;
    departmentName?: string | null;
    sourcePriorityName?: string | null;
  }
) {
  const existingPriority = existing?.source_priority_name || null;
  const incomingName = incoming.displayName || null;
  const shouldReplaceName =
    Boolean(incomingName) &&
    (
      !existing?.display_name ||
      !isLikelyHumanName(existing.display_name) ||
      (incoming.sourcePriorityName === "fingerprint" && existingPriority !== "fingerprint")
    );

  return {
    employee_code: incoming.employeeCode,
    epf_no: incoming.epfNo || existing?.epf_no || null,
    display_name: shouldReplaceName
      ? incomingName
      : existing?.display_name || incomingName || null,
    designation: pickBetterText(existing?.designation || null, incoming.designation || null),
    department_name: pickBetterText(
      existing?.department_name || null,
      incoming.departmentName || null
    ),
    source_priority_name:
      shouldReplaceName && incoming.sourcePriorityName
        ? incoming.sourcePriorityName
        : existingPriority || incoming.sourcePriorityName || null,
    is_active: existing?.is_active ?? true,
  };
}

export function resolveFingerprintEmployeeCode(row: FingerprintParsedRow) {
  return row.empNo || row.epfNo || null;
}

export async function upsertEmployeesFromFace(
  client: AppSupabaseClient,
  rows: FaceParsedRow[]
) {
  const employeeCodes = [...new Set(rows.map((row) => row.employeeId).filter(Boolean))] as string[];
  const existingRows = await fetchEmployeesByCodes(client, employeeCodes);
  const existingMap = new Map(existingRows.map((row) => [row.employee_code, row]));
  const departmentNames = rows
    .map((row) => row.department)
    .filter((department): department is string => Boolean(department));

  await upsertDepartments(client, departmentNames);

  const upsertRows = employeeCodes.map((employeeCode) => {
    const faceRow = rows.find((row) => row.employeeId === employeeCode);
    const incomingName = combineSourceName(faceRow?.firstName || null, faceRow?.lastName || null);

    return mergeEmployeeDisplayData(existingMap.get(employeeCode), {
      employeeCode,
      displayName: isLikelyHumanName(incomingName) ? incomingName : null,
      departmentName: faceRow?.department || null,
      sourcePriorityName: isLikelyHumanName(incomingName) ? "face" : null,
    });
  });

  return upsertEmployees(client, upsertRows);
}

export async function upsertEmployeesFromFingerprint(
  client: AppSupabaseClient,
  rows: FingerprintParsedRow[]
) {
  const filteredRows = rows.filter((row) => resolveFingerprintEmployeeCode(row));
  const employeeCodes = [
    ...new Set(filteredRows.map((row) => resolveFingerprintEmployeeCode(row)).filter(Boolean)),
  ] as string[];
  const existingRows = await fetchEmployeesByCodes(client, employeeCodes);
  const existingMap = new Map(existingRows.map((row) => [row.employee_code, row]));
  const departmentNames = filteredRows
    .map((row) => row.department)
    .filter((department): department is string => Boolean(department));

  await upsertDepartments(client, departmentNames);

  const upsertRows = filteredRows.map((row) => {
    const employeeCode = resolveFingerprintEmployeeCode(row)!;

    return mergeEmployeeDisplayData(existingMap.get(employeeCode), {
      employeeCode,
      epfNo: row.epfNo || null,
      displayName: row.name ? normalizeWhitespace(row.name) : null,
      designation: row.designation ? normalizeWhitespace(row.designation) : null,
      departmentName: row.department ? normalizeWhitespace(row.department) : null,
      sourcePriorityName: row.name && isLikelyHumanName(row.name) ? "fingerprint" : null,
    });
  });

  return upsertEmployees(client, upsertRows);
}
