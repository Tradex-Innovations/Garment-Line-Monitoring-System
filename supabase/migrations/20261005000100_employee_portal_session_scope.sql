-- Existing sessions become kiosk-limited. Users must sign in again to view payroll.
ALTER TABLE public.employee_portal_sessions
  ADD COLUMN IF NOT EXISTS session_scope text NOT NULL DEFAULT 'KIOSK';
ALTER TABLE public.employee_portal_sessions
  ADD CONSTRAINT employee_portal_session_scope_check
  CHECK (session_scope IN ('STANDARD', 'KIOSK'));
