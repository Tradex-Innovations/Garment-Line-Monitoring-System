# Import Pipeline

## Flow

1. Upload file to Supabase storage
2. Create `import_batches` row
3. Parse source file into raw staging rows
4. Save all raw rows for audit
5. Upsert employees and departments
6. Normalize source rows into structured tables
7. Reconcile face and fingerprint batches
8. Synchronize reconciliation exceptions into `operations_alerts`
9. Read live operational snapshots in the UI from Supabase-backed services

## Face Recognition Workbook

Source characteristics:

- Excel workbook
- metadata rows appear before the real header
- header columns:
  - `First Name`
  - `Last Name`
  - `ID`
  - `Department`
  - `Date`
  - `Weekday`
  - `Records`

Pipeline behavior:

- finds the actual header row dynamically
- stores each raw row in `face_raw_rows`
- splits `Records` on `;`
- validates `HH:MM` tokens
- preserves invalid tokens through quality flags
- writes one row per timestamp into `face_events`
- builds `face_daily_summary`

## Fingerprint Attendance Export

Supported now:

- PDF export
- XLSX
- CSV

Pipeline behavior:

- stores every parsed row in `fingerprint_raw_rows`
- preserves suspicious raw fields even when conversion fails
- converts date, time, OT, leave totals, and late/early values when possible
- marks quality flags for zero-time pairs, invalid times, and malformed numeric fields
- writes normalized day-level rows to `fingerprint_daily_attendance`

## Retry / Reprocessing

Normalization services are written to be idempotent for a batch:

- previous normalized rows for the batch are deleted
- raw staging rows remain preserved
- normalized rows are rebuilt from the raw batch content

This is exposed in the Import Center through the `Re-run Normalization` action.

## Frontend Consumption

The current frontend no longer reads local seed objects.

Instead it loads a Supabase-backed operational snapshot built from:

- `employees`
- `employee_profiles`
- `employee_notes`
- `production_lines`
- `line_assignments`
- `transfer_logs`
- `attendance_reconciliation`
- `operations_alerts`
- `incentive_records`
- `system_settings`
- `announcements`

This keeps the existing screens alive while showing live imported and operational data.

## Error Handling

- raw rows remain stored even if normalization fails later
- batch status is updated to `failed` with notes when processing errors occur
- parser warnings are copied to batch notes for operator visibility
- duplicate imports do not overwrite older batches because each upload gets a new batch id

## Current PDF Parsing Assumption

The fingerprint PDF parser uses extracted text positions and header anchors to rebuild columns.

This is modular and production-oriented, but it is still heuristic until tuned against the client’s exact PDF layout. If the real export has wrapped cells, merged columns, or repeated footers, the parser may need one more refinement pass using actual sample files.
