import { useCallback, useEffect, useMemo, useState } from "react";
import { isBackendConfigured } from "@/lib/backend/env";
import {
  listImportBatchesFromBackend,
  reconcileBatchPairFromBackend,
  rerunImportNormalizationFromBackend,
  uploadImportBatchFromBackend,
} from "@/lib/backend/pipeline-api";
import { requireSupabaseBrowserClient } from "@/lib/supabase/client";
import { isSupabaseConfigured } from "@/lib/supabase/env";
import type { ImportBatchSummary, SourceType } from "@/types/pipeline";

function looksLikeUuid(value: string | null | undefined) {
  return Boolean(
    value &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
  );
}

async function listImportBatchesFromSupabase() {
  const { listImportBatches } = await import("@/server/imports/import-batch-service");
  return listImportBatches(requireSupabaseBrowserClient());
}

async function runImportPipelineFromSupabase(args: {
  sourceType: SourceType;
  file: File;
  uploadedBy: string | null;
}) {
  const {
    runFaceImportPipeline,
    runFingerprintImportPipeline,
  } = await import("@/server/imports/import-pipeline-service");

  return args.sourceType === "face"
    ? runFaceImportPipeline(requireSupabaseBrowserClient(), {
        file: args.file,
        uploadedBy: args.uploadedBy,
      })
    : runFingerprintImportPipeline(requireSupabaseBrowserClient(), {
        file: args.file,
        uploadedBy: args.uploadedBy,
      });
}

async function rerunNormalizationFromSupabase(batch: ImportBatchSummary) {
  const {
    rerunFaceNormalization,
    rerunFingerprintNormalization,
  } = await import("@/server/imports/import-pipeline-service");

  return batch.sourceType === "face"
    ? rerunFaceNormalization(requireSupabaseBrowserClient(), batch.id)
    : rerunFingerprintNormalization(requireSupabaseBrowserClient(), batch.id);
}

async function reconcileBatchPairFromSupabase(args: {
  faceBatchId: string;
  fingerprintBatchId: string;
}) {
  const { reconcileForBatchPair } = await import(
    "@/server/reconciliation/reconciliation-service"
  );

  return reconcileForBatchPair(requireSupabaseBrowserClient(), args) as Promise<{
    reconciled_count?: number;
  }>;
}

export function useImportCenter(currentUserId?: string | null) {
  const [batches, setBatches] = useState<ImportBatchSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!isSupabaseConfigured()) {
      return;
    }

    setLoading(true);
    try {
      const nextBatches = isBackendConfigured()
        ? await listImportBatchesFromBackend()
        : await listImportBatchesFromSupabase();
      setBatches(nextBatches);
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : String(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const uploadBySource = useCallback(
    async (sourceType: SourceType, file: File) => {
      setBusyAction(`upload:${sourceType}`);
      setFeedback(null);

      try {
        const uploadedBy = looksLikeUuid(currentUserId) ? currentUserId : null;
        const batch = isBackendConfigured()
          ? await uploadImportBatchFromBackend({ sourceType, file })
          : await runImportPipelineFromSupabase({ sourceType, file, uploadedBy });

        setFeedback(
          `${batch.originalFilename} imported successfully and is ready with status ${batch.importStatus}.`
        );
        await refresh();
        return batch;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        setFeedback(message);
        throw error;
      } finally {
        setBusyAction(null);
      }
    },
    [currentUserId, refresh]
  );

  const rerunNormalization = useCallback(
    async (batch: ImportBatchSummary) => {
      setBusyAction(`rerun:${batch.id}`);
      setFeedback(null);

      try {
        const result = isBackendConfigured()
          ? await rerunImportNormalizationFromBackend(batch.id)
          : await rerunNormalizationFromSupabase(batch);

        setFeedback(
          `Normalization re-ran for ${result.originalFilename}. ${result.totalValidRows} valid rows were rebuilt.`
        );
        await refresh();
      } catch (error) {
        setFeedback(error instanceof Error ? error.message : String(error));
      } finally {
        setBusyAction(null);
      }
    },
    [refresh]
  );

  const reconcileBatches = useCallback(
    async (faceBatchId: string, fingerprintBatchId: string) => {
      setBusyAction(`reconcile:${faceBatchId}:${fingerprintBatchId}`);
      setFeedback(null);

      try {
        const result = isBackendConfigured()
          ? await reconcileBatchPairFromBackend({
              faceBatchId,
              fingerprintBatchId,
            })
          : await reconcileBatchPairFromSupabase({
              faceBatchId,
              fingerprintBatchId,
            });

        setFeedback(
          `Reconciliation completed for the selected batch pair. ${result?.reconciled_count || 0} rows were updated.`
        );
        await refresh();
      } catch (error) {
        setFeedback(error instanceof Error ? error.message : String(error));
      } finally {
        setBusyAction(null);
      }
    },
    [refresh]
  );

  const faceBatches = useMemo(
    () => batches.filter((batch) => batch.sourceType === "face"),
    [batches]
  );
  const fingerprintBatches = useMemo(
    () => batches.filter((batch) => batch.sourceType === "fingerprint"),
    [batches]
  );

  return {
    batches,
    faceBatches,
    fingerprintBatches,
    loading,
    busyAction,
    feedback,
    refresh,
    uploadBySource,
    rerunNormalization,
    reconcileBatches,
    isConfigured: isSupabaseConfigured(),
    backendConfigured: isBackendConfigured(),
  };
}
