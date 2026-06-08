import type { FingerprintFileParseResult, FingerprintFileFormat } from "@/types/pipeline";

function detectFingerprintFileFormat(file: File): FingerprintFileFormat {
  const normalizedName = file.name.toLowerCase();

  if (normalizedName.endsWith(".pdf")) {
    return "pdf";
  }

  if (normalizedName.endsWith(".xlsx") || normalizedName.endsWith(".xls")) {
    return "xlsx";
  }

  if (normalizedName.endsWith(".csv")) {
    return "csv";
  }

  throw new Error(
    `Unsupported fingerprint file format for "${file.name}". Expected PDF, XLSX, XLS, or CSV.`
  );
}

export async function parseFingerprintFile(file: File): Promise<FingerprintFileParseResult> {
  const fileFormat = detectFingerprintFileFormat(file);

  if (fileFormat === "pdf") {
    const { parseFingerprintPdf } = await import("./fingerprint-pdf-parser");
    return parseFingerprintPdf(file);
  }

  const { parseFingerprintTabularFile } = await import("./fingerprint-tabular-parser");
  return parseFingerprintTabularFile(file, fileFormat);
}
