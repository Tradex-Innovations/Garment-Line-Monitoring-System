-- Dedicated telemetry tables in the Flyway-owned Payroll schema only.
-- Runtime event ingestion is append-only; only the retention job deletes old telemetry.
CREATE TABLE pipeline_stage_events (
  sequence_id BIGSERIAL PRIMARY KEY,
  event_id UUID NOT NULL UNIQUE,
  run_id UUID NOT NULL,
  pipeline_name VARCHAR(60) NOT NULL,
  stage_name VARCHAR(60) NOT NULL,
  status VARCHAR(24) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  correlation_id VARCHAR(36) NOT NULL,
  trace_id VARCHAR(36) NOT NULL,
  safe_entity_reference VARCHAR(36),
  payload JSONB NOT NULL
);
CREATE INDEX idx_pipeline_events_run ON pipeline_stage_events(run_id, sequence_id);
CREATE INDEX idx_pipeline_events_time ON pipeline_stage_events(occurred_at DESC);
CREATE INDEX idx_pipeline_events_correlation ON pipeline_stage_events(correlation_id);
CREATE INDEX idx_pipeline_events_trace ON pipeline_stage_events(trace_id);
CREATE TABLE service_health_snapshots (
  id BIGSERIAL PRIMARY KEY, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(), payload JSONB NOT NULL
);
