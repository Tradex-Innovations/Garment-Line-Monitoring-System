-- New joiners are payroll-eligible alongside permanent employees. Interns remain excluded.
ALTER TABLE employment_records DROP CONSTRAINT IF EXISTS ck_employment_records_employment_type;
ALTER TABLE employment_records ADD CONSTRAINT ck_employment_records_employment_type
    CHECK (employment_type IN ('PERMANENT', 'NEW_JOINER', 'CONTRACT', 'TEMPORARY', 'INTERN'));
-- The profile catalogue already allows EXECUTIVE; keep published employment records aligned.
ALTER TABLE employment_records DROP CONSTRAINT IF EXISTS ck_employment_records_employee_category;
ALTER TABLE employment_records ADD CONSTRAINT ck_employment_records_employee_category
    CHECK (employee_category IN ('MANAGEMENT', 'STAFF', 'WORKER', 'TRAINEE', 'EXECUTIVE'));
