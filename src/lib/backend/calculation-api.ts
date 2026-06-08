import { backendJsonRequest } from "./client";
import type {
  CalculationAuditView,
  CalculationExecutionResult,
  CalculationRuleCatalogEntry,
  EfficiencyCalculationInputPayload,
  EfficiencyRulesPreview,
  IncentiveReportRow,
  IncentiveRulesPreview,
  LineMetricReportRow,
} from "@/types/calculations";

export function listCalculationRuleSetsFromBackend() {
  return backendJsonRequest<CalculationRuleCatalogEntry[]>("/api/calculation-rules");
}

export function getEfficiencyRulesPreviewFromBackend() {
  return backendJsonRequest<EfficiencyRulesPreview>("/api/calculation-rules/efficiency");
}

export function getIncentiveRulesPreviewFromBackend() {
  return backendJsonRequest<IncentiveRulesPreview>("/api/calculation-rules/incentives");
}

export function previewCalculationFromBackend(input: EfficiencyCalculationInputPayload) {
  return backendJsonRequest<CalculationExecutionResult>("/api/calculations/preview", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function saveCalculationFromBackend(input: EfficiencyCalculationInputPayload) {
  return backendJsonRequest<CalculationExecutionResult>("/api/calculations/metrics", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function listLineMetricsFromBackend(filters: {
  dateFrom?: string | null;
  dateTo?: string | null;
  lineCode?: string | null;
  shiftCode?: string | null;
}) {
  return backendJsonRequest<LineMetricReportRow[]>("/api/calculations/metrics", {}, filters);
}

export function listIncentiveReportFromBackend(filters: {
  dateFrom?: string | null;
  dateTo?: string | null;
  lineCode?: string | null;
  shiftCode?: string | null;
}) {
  return backendJsonRequest<IncentiveReportRow[]>("/api/calculations/incentives", {}, filters);
}

export function getMetricAuditFromBackend(metricId: string) {
  return backendJsonRequest<CalculationAuditView>(`/api/calculations/metrics/${metricId}/audit`);
}

export function recalculateMetricsFromBackend(input: {
  dateFrom?: string | null;
  dateTo?: string | null;
  lineCode?: string | null;
}) {
  return backendJsonRequest<{ recalculatedCount: number; items: Array<Record<string, unknown>> }>(
    "/api/calculations/recalculate",
    {
      method: "POST",
      body: JSON.stringify(input),
    }
  );
}

