CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(160) NOT NULL,
    registration_number VARCHAR(80),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_companies_code UNIQUE (code)
);

CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(160) NOT NULL,
    address_line VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_locations_company_code UNIQUE (company_id, code)
);

CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (id),
    location_id UUID REFERENCES locations (id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_departments_company_code UNIQUE (company_id, code)
);

CREATE TABLE production_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES departments (id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_production_lines_department_code UNIQUE (department_id, code)
);

CREATE TABLE designations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (id),
    code VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_designations_company_code UNIQUE (company_id, code)
);

CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_number VARCHAR(50) NOT NULL,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    identity_number VARCHAR(80) NOT NULL,
    email VARCHAR(180),
    phone VARCHAR(40),
    employment_status VARCHAR(40) NOT NULL,
    cadre_status VARCHAR(40) NOT NULL,
    payroll_status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_employees_employee_number UNIQUE (employee_number),
    CONSTRAINT uk_employees_identity_number UNIQUE (identity_number),
    CONSTRAINT uk_employees_email UNIQUE (email),
    CONSTRAINT ck_employees_employment_status CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'EXITED')),
    CONSTRAINT ck_employees_cadre_status CHECK (cadre_status IN ('ACTIVE', 'RESIGNED', 'TERMINATED', 'INACTIVE')),
    CONSTRAINT ck_employees_payroll_status CHECK (payroll_status IN ('ACTIVE', 'HOLD', 'FINAL_PAYROLL_PENDING', 'CLOSED'))
);

CREATE TABLE employment_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees (id),
    company_id UUID NOT NULL REFERENCES companies (id),
    location_id UUID REFERENCES locations (id),
    department_id UUID REFERENCES departments (id),
    production_line_id UUID REFERENCES production_lines (id),
    designation_id UUID REFERENCES designations (id),
    supervisor_id UUID REFERENCES employees (id),
    employment_type VARCHAR(40) NOT NULL,
    employee_category VARCHAR(40) NOT NULL,
    joined_date DATE NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_employment_records_employment_type CHECK (employment_type IN ('PERMANENT', 'CONTRACT', 'TEMPORARY', 'INTERN')),
    CONSTRAINT ck_employment_records_employee_category CHECK (employee_category IN ('MANAGEMENT', 'STAFF', 'WORKER', 'TRAINEE')),
    CONSTRAINT ck_employment_records_effective_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(120),
    employee_id UUID REFERENCES employees (id),
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    entity_id UUID,
    old_value TEXT,
    new_value TEXT,
    reason VARCHAR(500),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address VARCHAR(80)
);

CREATE TABLE file_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(128),
    related_entity_type VARCHAR(80),
    related_entity_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_file_metadata_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_locations_company_id ON locations (company_id);
CREATE INDEX idx_departments_company_id ON departments (company_id);
CREATE INDEX idx_departments_location_id ON departments (location_id);
CREATE INDEX idx_production_lines_department_id ON production_lines (department_id);
CREATE INDEX idx_designations_company_id ON designations (company_id);
CREATE INDEX idx_employees_cadre_status ON employees (cadre_status);
CREATE INDEX idx_employees_display_name ON employees (display_name);
CREATE INDEX idx_employment_records_employee_effective ON employment_records (employee_id, effective_from DESC);
CREATE INDEX idx_employment_records_company_id ON employment_records (company_id);
CREATE INDEX idx_employment_records_department_id ON employment_records (department_id);
CREATE INDEX idx_employment_records_supervisor_id ON employment_records (supervisor_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_employee_id ON audit_logs (employee_id);
