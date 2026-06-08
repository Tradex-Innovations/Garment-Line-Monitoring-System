import { useCallback, useEffect, useMemo, useState } from "react";
import { isBackendConfigured } from "@/lib/backend/env";
import {
  addReconciliationNoteFromBackend,
  getReconciliationDetailFromBackend,
  getReconciliationRowsFromBackend,
  getValidationSummaryFromBackend,
  listImportBatchesFromBackend,
  overrideReconciliationFromBackend,
} from "@/lib/backend/pipeline-api";
import { requireSupabaseBrowserClient } from "@/lib/supabase/client";
import { isSupabaseConfigured } from "@/lib/supabase/env";
import type {
  ImportBatchSummary,
  ReconciliationFilterInput,
  ReconciliationOverrideInput,
  ReconciliationRowDetail,
  ValidationSummaryRow,
} from "@/types/pipeline";
import type { getReconciliationDetail as getReconciliationDetailFromSupabaseService } from "@/server/reconciliation/reconciliation-service";

type ReconciliationDetailPayload = Awaited<
  ReturnType<typeof getReconciliationDetailFromSupabaseService>
> | null;

async function loadValidationRowsFromSupabase(filters: ReconciliationFilterInput) {
  const [{ getReconciliationRows }, { getValidationSummary }, { listImportBatches }] =
    await Promise.all([
      import("@/server/reconciliation/reconciliation-service"),
      import("@/server/reconciliation/reporting-service"),
      import("@/server/imports/import-batch-service"),
    ]);

  return Promise.all([
    getReconciliationRows(requireSupabaseBrowserClient(), filters),
    getValidationSummary(requireSupabaseBrowserClient()),
    listImportBatches(requireSupabaseBrowserClient()),
  ]);
}

async function loadReconciliationDetailFromSupabase(reconciliationId: string) {
  const { getReconciliationDetail } = await import(
    "@/server/reconciliation/reconciliation-service"
  );

  return getReconciliationDetail(requireSupabaseBrowserClient(), reconciliationId);
}

async function overrideReconciliationFromSupabase(input: ReconciliationOverrideInput) {
  const { overrideReconciliationStatus } = await import(
    "@/server/reconciliation/reconciliation-service"
  );

  return overrideReconciliationStatus(requireSupabaseBrowserClient(), input);
}

async function addReconciliationNoteFromSupabase(
  reconciliationId: string,
  note: string
) {
  const { addReconciliationNote } = await import(
    "@/server/reconciliation/reconciliation-service"
  );

  return addReconciliationNote(requireSupabaseBrowserClient(), { reconciliationId, note });
}

export function useValidationCenterData(filters: ReconciliationFilterInput) {
  const [rows, setRows] = useState<ReconciliationRowDetail[]>([]);
  const [summaryRows, setSummaryRows] = useState<ValidationSummaryRow[]>([]);
  const [batches, setBatches] = useState<ImportBatchSummary[]>([]);
  const [detail, setDetail] = useState<ReconciliationDetailPayload>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!isSupabaseConfigured()) {
      return;
    }

    setLoading(true);
    try {
      const [nextRows, nextSummary, nextBatches] = isBackendConfigured()
        ? await Promise.all([
            getReconciliationRowsFromBackend(filters),
            getValidationSummaryFromBackend(),
            listImportBatchesFromBackend(),
          ])
        : await loadValidationRowsFromSupabase(filters);

      setRows(nextRows);
      setSummaryRows(nextSummary);
      setBatches(nextBatches);
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : String(error));
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const loadDetail = useCallback(async (reconciliationId: string) => {
    if (!isSupabaseConfigured()) {
      return;
    }

    setDetailLoading(true);
    try {
      const nextDetail = isBackendConfigured()
        ? await getReconciliationDetailFromBackend(reconciliationId)
        : await loadReconciliationDetailFromSupabase(reconciliationId);
      setDetail(nextDetail);
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : String(error));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const applyOverride = useCallback(
    async (input: ReconciliationOverrideInput) => {
      try {
        if (isBackendConfigured()) {
          await overrideReconciliationFromBackend(input.reconciliationId, input);
        } else {
          await overrideReconciliationFromSupabase(input);
        }
        await refresh();
        await loadDetail(input.reconciliationId);
        setFeedback("Reconciliation override saved.");
      } catch (error) {
        setFeedback(error instanceof Error ? error.message : String(error));
        throw error;
      }
    },
    [loadDetail, refresh]
  );

  const createNote = useCallback(
    async (reconciliationId: string, note: string) => {
      try {
        if (isBackendConfigured()) {
          await addReconciliationNoteFromBackend(reconciliationId, note);
        } else {
          await addReconciliationNoteFromSupabase(reconciliationId, note);
        }
        await loadDetail(reconciliationId);
        setFeedback("Note added to the reconciliation record.");
      } catch (error) {
        setFeedback(error instanceof Error ? error.message : String(error));
        throw error;
      }
    },
    [loadDetail]
  );

  const latestSummary = useMemo(() => summaryRows[0] || null, [summaryRows]);

  return {
    rows,
    summaryRows,
    latestSummary,
    batches,
    detail,
    loading,
    detailLoading,
    feedback,
    refresh,
    loadDetail,
    clearDetail: () => setDetail(null),
    applyOverride,
    createNote,
    isConfigured: isSupabaseConfigured(),
  };
}
