-- Payroll permissions are assigned by a trusted administrator. No role is
-- inferred from editable public profiles or from user-controlled JWT metadata.
CREATE TABLE user_role_assignments (
    auth_user_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL CHECK (role IN (
        'EMPLOYEE', 'LINE_SUPERVISOR', 'PRODUCTION_MANAGER', 'HR_OFFICER',
        'HR_MANAGER', 'PAYROLL_OFFICER', 'PAYROLL_REVIEWER', 'FINANCE',
        'MANAGEMENT_APPROVER', 'SECURITY_GUARD', 'ADMIN', 'SYSTEM_ADMIN',
        'DEVELOPER'
    )),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by UUID,
    PRIMARY KEY (auth_user_id, role)
);

CREATE INDEX idx_user_role_assignments_role ON user_role_assignments (role);
