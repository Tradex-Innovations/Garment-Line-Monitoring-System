begin;

-- LineMatrix owns the shared employee masters. Payroll reads these canonical
-- records and resolves them into its effective-dated workflow references and
-- immutable publication snapshots.
create table if not exists public.designations (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null unique,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.teams (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null unique,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.grades (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null unique,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.work_shifts (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null unique,
  start_time time,
  end_time time,
  ends_following_day boolean not null default false,
  unpaid_break_minutes integer not null default 0 check (unpaid_break_minutes >= 0),
  grace_minutes integer not null default 0 check (grace_minutes >= 0),
  schedule_verified boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.employees
  add column if not exists designation_id uuid references public.designations(id) on delete set null,
  add column if not exists team_id uuid references public.teams(id) on delete set null,
  add column if not exists grade_id uuid references public.grades(id) on delete set null;

alter table public.employee_profiles
  add column if not exists shift_id uuid references public.work_shifts(id) on delete set null;

create index if not exists employees_designation_id_idx on public.employees (designation_id);
create index if not exists employees_team_id_idx on public.employees (team_id);
create index if not exists employees_grade_id_idx on public.employees (grade_id);
create index if not exists employee_profiles_shift_id_idx on public.employee_profiles (shift_id);

-- Preserve the complete legacy payroll report in a protected, structured row.
-- Frequently-used values have typed columns; the original source row remains in
-- legacy_payload for traceability and for fields not yet used by either UI.
create table if not exists public.employee_master_details (
  employee_id uuid primary key references public.employees(id) on delete cascade,
  identity_number text unique,
  first_name text,
  last_name text,
  full_name text,
  initials text,
  name_with_initials text,
  call_name text,
  gender text check (gender is null or gender in ('FEMALE', 'MALE')),
  date_of_birth date,
  residential_address text,
  email text,
  basic_salary numeric(18, 2),
  barcode_number text,
  occupation_code text,
  bank_name text,
  bank_branch text,
  bank_account_number text,
  phone text,
  mobile_phone text,
  bus_route text,
  distance_km numeric(10, 2),
  district text,
  electorate text,
  group_joined_date date,
  direct_indirect_status text check (
    direct_indirect_status is null or direct_indirect_status in ('DIRECT', 'INDIRECT')
  ),
  payroll_category text check (
    payroll_category is null or payroll_category in ('WORKER', 'STAFF', 'MANAGEMENT', 'EXECUTIVE')
  ),
  overtime_paid boolean,
  attendance_bonus_eligible boolean,
  emergency_name text,
  emergency_phone text,
  emergency_relationship text,
  source_name text not null default 'legacy-employee-detail-report',
  source_captured_at timestamptz,
  legacy_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists employee_master_details_identity_number_idx
  on public.employee_master_details (identity_number);

-- Backfill masters already present in LineMatrix before importing the report.
insert into public.designations (code, name)
select
  left(upper(regexp_replace(btrim(designation), '[^A-Za-z0-9]+', '_', 'g')) || '_' || substr(md5(btrim(designation)), 1, 8), 60),
  btrim(designation)
from public.employees
where nullif(btrim(designation), '') is not null
on conflict (name) do nothing;

insert into public.work_shifts (code, name)
select
  left(upper(regexp_replace(btrim(shift_name), '[^A-Za-z0-9]+', '_', 'g')) || '_' || substr(md5(btrim(shift_name)), 1, 8), 60),
  btrim(shift_name)
from public.employee_profiles
where nullif(btrim(shift_name), '') is not null
on conflict (name) do nothing;

update public.employees employee
set designation_id = designation.id
from public.designations designation
where employee.designation_id is null
  and nullif(btrim(employee.designation), '') is not null
  and lower(designation.name) = lower(btrim(employee.designation));

update public.employee_profiles profile
set shift_id = shift.id
from public.work_shifts shift
where profile.shift_id is null
  and nullif(btrim(profile.shift_name), '') is not null
  and lower(shift.name) = lower(btrim(profile.shift_name));

create or replace function public.sync_employee_designation_name()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.designation_id is not null then
    select name into new.designation
    from public.designations
    where id = new.designation_id;
  end if;
  return new;
end;
$$;

drop trigger if exists sync_employee_designation_name_trigger on public.employees;
create trigger sync_employee_designation_name_trigger
before insert or update of designation_id on public.employees
for each row execute function public.sync_employee_designation_name();

create or replace function public.sync_employee_shift_name()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.shift_id is not null then
    select name into new.shift_name
    from public.work_shifts
    where id = new.shift_id;
  end if;
  return new;
end;
$$;

drop trigger if exists sync_employee_shift_name_trigger on public.employee_profiles;
create trigger sync_employee_shift_name_trigger
before insert or update of shift_id on public.employee_profiles
for each row execute function public.sync_employee_shift_name();

drop trigger if exists touch_designations_updated_at on public.designations;
create trigger touch_designations_updated_at
before update on public.designations
for each row execute function public.touch_updated_at();

drop trigger if exists touch_teams_updated_at on public.teams;
create trigger touch_teams_updated_at
before update on public.teams
for each row execute function public.touch_updated_at();

drop trigger if exists touch_grades_updated_at on public.grades;
create trigger touch_grades_updated_at
before update on public.grades
for each row execute function public.touch_updated_at();

drop trigger if exists touch_work_shifts_updated_at on public.work_shifts;
create trigger touch_work_shifts_updated_at
before update on public.work_shifts
for each row execute function public.touch_updated_at();

drop trigger if exists touch_employee_master_details_updated_at on public.employee_master_details;
create trigger touch_employee_master_details_updated_at
before update on public.employee_master_details
for each row execute function public.touch_updated_at();

alter table public.designations enable row level security;
alter table public.teams enable row level security;
alter table public.grades enable row level security;
alter table public.work_shifts enable row level security;
alter table public.employee_master_details enable row level security;

create policy "designations_read_authenticated" on public.designations
  for select to authenticated using (true);
create policy "designations_write_admin_hr" on public.designations
  for all to authenticated using (public.has_role(array['admin', 'hr']))
  with check (public.has_role(array['admin', 'hr']));
create policy "teams_read_authenticated" on public.teams
  for select to authenticated using (true);
create policy "teams_write_admin_hr" on public.teams
  for all to authenticated using (public.has_role(array['admin', 'hr']))
  with check (public.has_role(array['admin', 'hr']));
create policy "grades_read_authenticated" on public.grades
  for select to authenticated using (true);
create policy "grades_write_admin_hr" on public.grades
  for all to authenticated using (public.has_role(array['admin', 'hr']))
  with check (public.has_role(array['admin', 'hr']));
create policy "work_shifts_read_authenticated" on public.work_shifts
  for select to authenticated using (true);
create policy "work_shifts_write_admin_hr" on public.work_shifts
  for all to authenticated using (public.has_role(array['admin', 'hr']))
  with check (public.has_role(array['admin', 'hr']));
create policy "employee_master_details_read_admin_hr" on public.employee_master_details
  for select to authenticated using (public.has_role(array['admin', 'hr']));
create policy "employee_master_details_write_admin_hr" on public.employee_master_details
  for all to authenticated using (public.has_role(array['admin', 'hr']))
  with check (public.has_role(array['admin', 'hr']));

grant select on public.designations, public.teams, public.grades, public.work_shifts
  to authenticated;
grant select, insert, update, delete on public.designations, public.teams,
  public.grades, public.work_shifts, public.employee_master_details
  to authenticated;

notify pgrst, 'reload schema';

commit;
