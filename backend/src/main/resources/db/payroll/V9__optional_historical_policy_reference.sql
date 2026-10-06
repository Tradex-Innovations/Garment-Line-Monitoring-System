-- Keep existing calculation policy links for historical evidence. New calculations
-- use the effective salary structure rules and do not require a policy record.
ALTER TABLE payroll_calculations ALTER COLUMN policy_id DROP NOT NULL;
