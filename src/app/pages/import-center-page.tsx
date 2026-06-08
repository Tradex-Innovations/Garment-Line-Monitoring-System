import { useMemo, useState } from "react";
import { Database, FileCog, FileWarning, RefreshCw, UploadCloud } from "lucide-react";
import { useAuth } from "../auth";
import { useImportCenter } from "../hooks/use-import-center";
import {
  Button,
  Card,
  DetailDrawer,
  EmptyState,
  KpiCard,
  MetricTile,
  PageHeader,
  StatusBadge,
  formatDateTime,
} from "../components/ops-ui";
import type { ImportBatchSummary, SourceType } from "@/types/pipeline";

function importStatusTone(status: ImportBatchSummary["importStatus"]) {
  if (status === "completed" || status === "reconciled") return "success";
  if (status === "failed") return "danger";
  if (status === "partially_completed" || status === "processing") return "warning";
  if (status === "uploaded" || status === "parsed") return "info";
  return "neutral";
}

export function ImportCenterPage() {
  const { currentUser } = useAuth();
  const {
    batches,
    faceBatches,
    fingerprintBatches,
    loading,
    busyAction,
    feedback,
    uploadBySource,
    rerunNormalization,
    reconcileBatches,
    isConfigured,
  } = useImportCenter(currentUser.id);
  const [sourceType, setSourceType] = useState<SourceType>("face");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [selectedBatch, setSelectedBatch] = useState<ImportBatchSummary | null>(null);
  const [selectedFaceBatchId, setSelectedFaceBatchId] = useState("");
  const [selectedFingerprintBatchId, setSelectedFingerprintBatchId] = useState("");

  const totals = useMemo(() => {
    const totalRawRows = batches.reduce((sum, batch) => sum + batch.totalRawRows, 0);
    const totalErrors = batches.reduce((sum, batch) => sum + batch.totalErrorRows, 0);
    const failedBatches = batches.filter((batch) => batch.importStatus === "failed").length;
    const normalizedBatches = batches.filter((batch) =>
      ["normalized", "reconciled", "completed"].includes(batch.importStatus)
    ).length;

    return { totalRawRows, totalErrors, failedBatches, normalizedBatches };
  }, [batches]);

  return (
    <div className="ops-page">
      <PageHeader
        title="Import Center"
        subtitle="Upload, normalize, reconcile, and audit face-recognition and fingerprint attendance batches backed by Supabase storage and database pipelines."
        actions={
          <>
            <StatusBadge
              label={isConfigured ? "Supabase Connected" : "Supabase Not Configured"}
              tone={isConfigured ? "success" : "warning"}
            />
            <Button
              tone="primary"
              disabled={!selectedFile || Boolean(busyAction) || !isConfigured}
              onClick={() => {
                if (!selectedFile) {
                  return;
                }

                void uploadBySource(sourceType, selectedFile).then(() => setSelectedFile(null));
              }}
            >
              <UploadCloud size={15} />
              Upload & Process
            </Button>
          </>
        }
      />

      {feedback ? (
        <div className="ops-badge tone-info" style={{ alignSelf: "flex-start" }}>
          {feedback}
        </div>
      ) : null}

      <section className="ops-kpi-grid">
        <KpiCard
          label="Batch History"
          value={`${batches.length}`}
          meta="Imported source files tracked with replayable batch metadata."
          icon={Database}
          accent="var(--ops-primary)"
          soft="var(--ops-primary-soft)"
        />
        <KpiCard
          label="Normalized Batches"
          value={`${totals.normalizedBatches}`}
          meta="Batches that have completed parsing and normalization."
          icon={FileCog}
          accent="var(--ops-success)"
          soft="var(--ops-success-soft)"
        />
        <KpiCard
          label="Total Raw Rows"
          value={`${totals.totalRawRows}`}
          meta="All face and fingerprint rows preserved for audit."
          icon={UploadCloud}
          accent="var(--ops-warning)"
          soft="var(--ops-warning-soft)"
        />
        <KpiCard
          label="Import Errors"
          value={`${totals.totalErrors}`}
          meta={`${totals.failedBatches} failed batches currently need attention.`}
          icon={FileWarning}
          accent="var(--ops-danger)"
          soft="var(--ops-danger-soft)"
        />
      </section>

      <section className="ops-grid cols-2">
        <Card
          title="Upload New Batch"
          subtitle="Choose the source type first so the correct parser and normalization flow are used."
        >
          <div className="ops-filter-bar" style={{ alignItems: "flex-end" }}>
            <div style={{ minWidth: 200 }}>
              <div className="ops-row-subtitle" style={{ marginBottom: 8 }}>
                Source type
              </div>
              <select
                className="ops-select"
                value={sourceType}
                onChange={(event) => setSourceType(event.target.value as SourceType)}
              >
                <option value="face">Face recognition workbook</option>
                <option value="fingerprint">Fingerprint attendance export</option>
              </select>
            </div>
            <div style={{ minWidth: 260, flex: 1 }}>
              <div className="ops-row-subtitle" style={{ marginBottom: 8 }}>
                Import file
              </div>
              <input
                className="ops-input"
                type="file"
                accept={sourceType === "face" ? ".xlsx,.xls" : ".pdf,.xlsx,.xls,.csv"}
                onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
              />
            </div>
          </div>

          <div className="ops-meta-grid" style={{ marginTop: 16 }}>
            <MetricTile
              label="Selected file"
              value={selectedFile?.name || "No file selected"}
              meta={selectedFile ? `${Math.round(selectedFile.size / 1024)} KB` : "Choose a file to begin processing."}
            />
            <MetricTile
              label="Pipeline behavior"
              value={sourceType === "face" ? "Excel -> raw -> face events" : "PDF/XLSX/CSV -> raw -> attendance"}
              meta="The upload action stores the source file, raw rows, normalized rows, and batch history in one workflow."
            />
          </div>
        </Card>

        <Card
          title="Run Reconciliation"
          subtitle="Pair one normalized face batch with one normalized fingerprint batch to build the validation dataset."
          actions={
            <Button
              tone="primary"
              disabled={
                !selectedFaceBatchId ||
                !selectedFingerprintBatchId ||
                Boolean(busyAction) ||
                !isConfigured
              }
              onClick={() =>
                void reconcileBatches(selectedFaceBatchId, selectedFingerprintBatchId)
              }
            >
              <RefreshCw size={15} />
              Reconcile Pair
            </Button>
          }
        >
          <div className="ops-filter-bar" style={{ alignItems: "flex-end" }}>
            <div style={{ minWidth: 240, flex: 1 }}>
              <div className="ops-row-subtitle" style={{ marginBottom: 8 }}>
                Face batch
              </div>
              <select
                className="ops-select"
                value={selectedFaceBatchId}
                onChange={(event) => setSelectedFaceBatchId(event.target.value)}
              >
                <option value="">Select face batch</option>
                {faceBatches.map((batch) => (
                  <option key={batch.id} value={batch.id}>
                    {batch.originalFilename} · {batch.importStatus}
                  </option>
                ))}
              </select>
            </div>
            <div style={{ minWidth: 240, flex: 1 }}>
              <div className="ops-row-subtitle" style={{ marginBottom: 8 }}>
                Fingerprint batch
              </div>
              <select
                className="ops-select"
                value={selectedFingerprintBatchId}
                onChange={(event) => setSelectedFingerprintBatchId(event.target.value)}
              >
                <option value="">Select fingerprint batch</option>
                {fingerprintBatches.map((batch) => (
                  <option key={batch.id} value={batch.id}>
                    {batch.originalFilename} · {batch.importStatus}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </Card>
      </section>

      <Card
        title="Import Batch History"
        subtitle="Every uploaded source file remains traceable through storage path, batch status, counts, timestamps, and notes."
      >
        {!isConfigured ? (
          <EmptyState
            title="Supabase configuration required"
            description="Set the Vite Supabase environment variables to enable live uploads, storage, normalization, and reconciliation."
          />
        ) : !batches.length && !loading ? (
          <EmptyState
            title="No import batches yet"
            description="Upload the first face or fingerprint file to initialize the pipeline history."
          />
        ) : (
          <div className="ops-table-wrap">
            <table className="ops-table">
              <thead>
                <tr>
                  <th>Source</th>
                  <th>Filename</th>
                  <th>Status</th>
                  <th>Rows</th>
                  <th>Errors</th>
                  <th>Started</th>
                  <th>Completed</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {batches.map((batch) => (
                  <tr key={batch.id}>
                    <td>
                      <StatusBadge
                        label={batch.sourceType.toUpperCase()}
                        tone={batch.sourceType === "face" ? "info" : "violet"}
                      />
                    </td>
                    <td>
                      <div className="ops-row-title">{batch.originalFilename}</div>
                      <div className="ops-row-subtitle ops-monospace">{batch.id}</div>
                    </td>
                    <td>
                      <StatusBadge
                        label={batch.importStatus}
                        tone={importStatusTone(batch.importStatus)}
                      />
                    </td>
                    <td>{batch.totalRawRows}</td>
                    <td>{batch.totalErrorRows}</td>
                    <td>{formatDateTime(batch.startedAt || batch.createdAt)}</td>
                    <td>{formatDateTime(batch.completedAt || "")}</td>
                    <td>
                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                        <Button tone="ghost" onClick={() => setSelectedBatch(batch)}>
                          View Errors
                        </Button>
                        <Button
                          tone="secondary"
                          disabled={Boolean(busyAction)}
                          onClick={() => void rerunNormalization(batch)}
                        >
                          Re-run Normalization
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <DetailDrawer
        open={Boolean(selectedBatch)}
        title={selectedBatch?.originalFilename || "Batch Detail"}
        subtitle={selectedBatch ? `${selectedBatch.sourceType} import batch` : undefined}
        onClose={() => setSelectedBatch(null)}
      >
        {selectedBatch ? (
          <div className="ops-grid cols-2">
            <MetricTile label="Batch ID" value={selectedBatch.id} />
            <MetricTile label="Storage Path" value={selectedBatch.storagePath} />
            <MetricTile label="Raw Rows" value={`${selectedBatch.totalRawRows}`} />
            <MetricTile label="Valid Rows" value={`${selectedBatch.totalValidRows}`} />
            <MetricTile label="Error Rows" value={`${selectedBatch.totalErrorRows}`} />
            <MetricTile label="Status" value={selectedBatch.importStatus} />
            <div className="ops-card" style={{ gridColumn: "1 / -1" }}>
              <div className="ops-card-body">
                <div className="ops-card-title">Batch Notes</div>
                <div className="ops-item-description" style={{ marginTop: 10 }}>
                  {selectedBatch.notes || "No parser or normalization notes were recorded."}
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </DetailDrawer>
    </div>
  );
}

export default ImportCenterPage;
