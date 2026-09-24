-- The shared Supabase identity can be disabled immediately in the API while
-- GoTrue prevents new sessions. Payroll employee links stay in payroll.
CREATE TABLE user_account_access (
    auth_user_id UUID PRIMARY KEY,
    employee_id UUID REFERENCES employees(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID
);

CREATE INDEX idx_user_account_access_employee ON user_account_access(employee_id);
