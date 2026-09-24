-- Payroll-owned immutable calculation evidence; no changes to LineMatrix tables.
CREATE TABLE payroll_calculations (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    request_data JSONB NOT NULL,
    employee_id UUID NOT NULL REFERENCES employees(id),
    period_id UUID NOT NULL REFERENCES setup_items(id),
    profile_revision_id UUID NOT NULL REFERENCES payroll_profile_revisions(id),
    policy_id UUID NOT NULL REFERENCES setup_items(id),
    snapshot JSONB NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL
);
CREATE INDEX idx_payroll_calculations_period ON payroll_calculations(period_id, created_at DESC);
CREATE INDEX idx_payroll_calculations_employee ON payroll_calculations(employee_id, period_id, created_at DESC);
