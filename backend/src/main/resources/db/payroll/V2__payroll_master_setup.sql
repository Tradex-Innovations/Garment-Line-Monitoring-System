-- LineMatrix public tables are never changed. Uses the configured payroll schema.
CREATE TABLE setup_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind VARCHAR(40) NOT NULL,
    code VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (kind, code)
);
CREATE TABLE setup_item_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES setup_items(id),
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL,
    effective_from DATE NOT NULL,
    data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    UNIQUE (item_id, effective_from)
);
CREATE INDEX idx_setup_revisions_date ON setup_item_revisions(item_id, effective_from DESC);

CREATE TABLE employee_payroll_profiles (
    employee_id UUID PRIMARY KEY REFERENCES employees(id),
    linematrix_employee_id UUID UNIQUE,
    source_employee_number VARCHAR(50),
    source_details JSONB NOT NULL DEFAULT '{}',
    general_data JSONB NOT NULL DEFAULT '{}',
    financial_data JSONB NOT NULL DEFAULT '{}',
    registration_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
      CHECK (registration_status IN ('DRAFT', 'ACTIVE', 'CHANGES_PENDING', 'HOLD')),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE payroll_profile_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employee_payroll_profiles(employee_id),
    effective_from DATE NOT NULL,
    general_data JSONB NOT NULL,
    financial_data JSONB NOT NULL,
    master_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    UNIQUE (employee_id, effective_from)
);
CREATE INDEX idx_payroll_profile_history ON payroll_profile_revisions(employee_id, effective_from DESC);
-- Existing employees are not assumed to have complete salary or payment setup.
INSERT INTO employee_payroll_profiles(employee_id) SELECT id FROM employees;
