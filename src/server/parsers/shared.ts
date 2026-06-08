import { format, isValid, parse } from "date-fns";
import type { SourceType } from "@/types/pipeline";

const DATE_PATTERNS = [
  "yyyy-MM-dd",
  "yyyy/MM/dd",
  "dd/MM/yyyy",
  "d/M/yyyy",
  "dd-MM-yyyy",
  "d-M-yyyy",
  "MMM d, yyyy",
  "d MMM yyyy",
] as const;

export function normalizeWhitespace(value: string) {
  return value.replace(/\s+/g, " ").trim();
}

export function cleanCellValue(value: unknown) {
  if (value === null || value === undefined) {
    return null;
  }

  if (value instanceof Date) {
    return value.toISOString();
  }

  const text = normalizeWhitespace(String(value));
  return text.length ? text : null;
}

export function parseFlexibleDateText(value: string | null) {
  if (!value) {
    return null;
  }

  const normalized = normalizeWhitespace(value);

  for (const pattern of DATE_PATTERNS) {
    const parsed = parse(normalized, pattern, new Date());
    if (isValid(parsed)) {
      return format(parsed, "yyyy-MM-dd");
    }
  }

  const directDate = new Date(normalized);
  if (isValid(directDate)) {
    return format(directDate, "yyyy-MM-dd");
  }

  return null;
}

export function isValidTimeToken(value: string | null) {
  return Boolean(value && /^([01]\d|2[0-3]):[0-5]\d$/.test(value));
}

export function normalizeTimeToken(value: string | null) {
  if (!value) {
    return null;
  }

  const normalized = normalizeWhitespace(value);
  return isValidTimeToken(normalized) ? normalized : null;
}

export function toDatabaseTime(value: string | null) {
  const normalized = normalizeTimeToken(value);
  return normalized ? `${normalized}:00` : null;
}

export function parseDecimalHours(value: string | null) {
  if (!value) {
    return null;
  }

  const normalized = normalizeWhitespace(value);

  if (/^-?\d+(\.\d+)?$/.test(normalized)) {
    return Number.parseFloat(normalized);
  }

  if (/^-?([01]?\d|2[0-3]):[0-5]\d$/.test(normalized)) {
    const negative = normalized.startsWith("-");
    const [hours, minutes] = normalized.replace(/^-/, "").split(":").map(Number);
    const total = hours + minutes / 60;
    return Math.round((negative ? -total : total) * 100) / 100;
  }

  return null;
}

export function uniqueStrings(values: Array<string | null | undefined>) {
  return [...new Set(values.filter((value): value is string => Boolean(value)))];
}

export function chunkArray<T>(items: T[], chunkSize = 500) {
  const chunks: T[][] = [];

  for (let index = 0; index < items.length; index += chunkSize) {
    chunks.push(items.slice(index, index + chunkSize));
  }

  return chunks;
}

export function isLikelyHumanName(value: string | null) {
  if (!value) {
    return false;
  }

  const normalized = normalizeWhitespace(value);
  if (!normalized || normalized === "--") {
    return false;
  }

  if (/^\d+$/.test(normalized.replace(/\s+/g, ""))) {
    return false;
  }

  return /[A-Za-z]/.test(normalized);
}

export function combineSourceName(firstName: string | null, lastName: string | null) {
  return normalizeWhitespace(
    [firstName, lastName]
      .filter((part) => part && part !== "--")
      .join(" ")
  ) || null;
}

export function buildImportsStoragePath(args: {
  sourceType: SourceType;
  batchId: string;
  originalFilename: string;
  createdAt?: Date;
}) {
  const date = args.createdAt ?? new Date();
  const safeFilename = args.originalFilename.replace(/[^\w.\-()]+/g, "_");
  const year = format(date, "yyyy");
  const month = format(date, "MM");

  return `imports/${args.sourceType}/${year}/${month}/${args.batchId}/${safeFilename}`;
}

export function createQualityFlagSet(initial: string[] = []) {
  return new Set(initial.filter(Boolean));
}
