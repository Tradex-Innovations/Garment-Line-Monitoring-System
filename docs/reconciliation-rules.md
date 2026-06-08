# Reconciliation Rules

## Matching Key

Reconciliation is performed by:

- `employee_code`
- `attendance_date`

## Statuses

### `validated`

Used when:

- face daily summary exists
- fingerprint daily attendance exists
- fingerprint state is `present`
- face event count is greater than zero
- no severe duplicate or malformed-time condition downgraded the row

Confidence: usually `high`

### `face_only`

Used when:

- face activity exists
- no fingerprint row exists, or fingerprint state is `no_data` / `review`
- there is no usable fingerprint time pair

Confidence: usually `medium`

### `fingerprint_only`

Used when:

- fingerprint attendance is `present`
- no face summary exists, or face event count is zero

Confidence: usually `medium`

### `leave`

Used when:

- fingerprint leave indicators are present
- leave code mapping classifies the row as leave
- no face activity exists that day

Confidence: usually `medium`

### `absent`

Used when:

- a mapped leave/absence code classifies the fingerprint row as absent
- no face activity exists that day

Confidence: usually `medium`

### `needs_review`

Used when:

- fingerprint row has `00:00 / 00:00` without leave and face exists
- face duplicate pattern is heavy
- face and fingerprint timing mismatch crosses the review threshold
- fingerprint row is still classified as `review`
- key fields exist but the data is not strong enough for a clean status

Confidence: `low`

### `anomaly`

Used when:

- leave is indicated but face activity exists
- malformed fingerprint values appear alongside face activity
- contradictory source patterns are too severe for a normal review queue

Confidence: `low`

## Rule Flags

Current flags include:

- `duplicate_face_events`
- `face_present_fingerprint_missing`
- `fingerprint_present_face_missing`
- `leave_and_face_conflict`
- `zero_times_without_leave`
- `malformed_time_values`
- `suspicious_timing_mismatch`

These are stored in `attendance_reconciliation.rule_flags` and surfaced in the Validation Center drawer.

## Alert Sync

Operational exception screens do not maintain a separate mock queue anymore.

The backend synchronizes reconciliation exceptions into `operations_alerts` through
`rpc_sync_reconciliation_alerts()`, which means:

- `face_only`
- `fingerprint_only`
- `needs_review`
- `anomaly`

can appear directly in Dashboard, Alerts Center, and Display Mode without duplicating business rules in the frontend.

## Manual Overrides

Manual overrides do not delete the original pipeline result.

Instead the system stores:

- original `reconciliation_status`
- `manual_override_status`
- `manual_override_reason`
- actor
- timestamp

All override actions also write into `audit_logs`, and operators can append `reconciliation_notes` alongside the override trail.
