-- An employee can respond once to an internally approved calculation.
-- This table belongs to Payroll; it is not exposed to Supabase client roles.
CREATE TABLE employee_payslip_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calculation_id UUID NOT NULL UNIQUE REFERENCES payroll_calculations(id),
    employee_id UUID NOT NULL REFERENCES employees(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('CONFIRMED', 'QUERY')),
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT employee_payslip_review_note_check CHECK
      ((action = 'QUERY' AND note IS NOT NULL AND length(btrim(note)) > 0)
       OR (action = 'CONFIRMED' AND note IS NULL))
);
CREATE INDEX employee_payslip_reviews_employee_idx
    ON employee_payslip_reviews(employee_id, created_at DESC);
