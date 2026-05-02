alter table public.employees
  add column if not exists employee_category text;

alter table public.fingerprint_raw_rows
  add column if not exists source_employee_category text;

alter table public.fingerprint_daily_attendance
  add column if not exists employee_category text,
  add column if not exists day_label text;

create table if not exists public.fingerprint_import_reports (
  import_batch_id uuid primary key references public.import_batches (id) on delete cascade,
  company_name text,
  company_address text,
  company_phone text,
  report_title text,
  report_scope text,
  report_date_from date,
  report_date_to date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists employees_employee_category_idx
  on public.employees (employee_category);

create index if not exists fingerprint_daily_attendance_department_date_idx
  on public.fingerprint_daily_attendance (department_name, attendance_date);

drop trigger if exists set_fingerprint_import_reports_updated_at on public.fingerprint_import_reports;
create trigger set_fingerprint_import_reports_updated_at
before update on public.fingerprint_import_reports
for each row
execute function public.touch_updated_at();

alter table public.fingerprint_import_reports enable row level security;

grant select, insert, update, delete on public.fingerprint_import_reports to authenticated;

drop policy if exists "fingerprint_import_reports_read_admin_hr_supervisor" on public.fingerprint_import_reports;
create policy "fingerprint_import_reports_read_admin_hr_supervisor"
on public.fingerprint_import_reports
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

drop policy if exists "fingerprint_import_reports_write_admin_hr" on public.fingerprint_import_reports;
create policy "fingerprint_import_reports_write_admin_hr"
on public.fingerprint_import_reports
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));
