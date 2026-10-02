begin;

-- Workforce ownership is independent of permanent/intern/new-joiner status and
-- independent of the salary calculation category. Existing people stay
-- unassigned until an administrator reviews them explicitly.
alter table public.employees add column if not exists workforce_group text;
alter table public.employees add constraint employees_workforce_group_check
  check (workforce_group is null or workforce_group in ('EXECUTIVE_STAFF', 'GENERAL_WORKFORCE'));
create index if not exists employees_workforce_group_idx on public.employees(workforce_group);

-- LineMatrix browsers write this table through PostgREST as authenticated.
-- Classification changes must instead pass through the audited Spring API.
create or replace function public.guard_employee_workforce_group()
returns trigger language plpgsql as $$
begin
  if current_user in ('anon','authenticated') then
    if tg_op = 'INSERT' then
      if new.workforce_group is not null then
        raise exception 'Workforce group changes require the Payroll administration API';
      end if;
    elsif new.workforce_group is distinct from old.workforce_group then
      raise exception 'Workforce group changes require the Payroll administration API';
    end if;
  end if;
  return new;
end;
$$;
create trigger guard_employee_workforce_group_update
before update on public.employees
for each row execute function public.guard_employee_workforce_group();
create trigger guard_employee_workforce_group_insert
before insert on public.employees
for each row execute function public.guard_employee_workforce_group();

create schema if not exists payroll;

-- No Data API grants or RLS policies: only the trusted shared backend may read
-- this table. Move historic values before removing their public columns.
create table if not exists payroll.employee_financial_master (
  employee_id uuid primary key references public.employees(id) on delete cascade,
  basic_salary numeric(18,2),
  bank_name text,
  bank_branch text,
  bank_account_number text,
  overtime_paid boolean,
  attendance_bonus_eligible boolean,
  legacy_payload jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
insert into payroll.employee_financial_master
  (employee_id,basic_salary,bank_name,bank_branch,bank_account_number,
   overtime_paid,attendance_bonus_eligible,legacy_payload)
select employee_id,basic_salary,bank_name,bank_branch,bank_account_number,
       overtime_paid,attendance_bonus_eligible,legacy_payload
from public.employee_master_details
on conflict (employee_id) do nothing;

alter table public.employee_master_details
  drop column basic_salary,
  drop column bank_name,
  drop column bank_branch,
  drop column bank_account_number,
  drop column overtime_paid,
  drop column attendance_bonus_eligible,
  drop column legacy_payload;

create table if not exists payroll.user_workforce_grants (
  auth_user_id uuid not null,
  workforce_group text not null check (workforce_group in ('EXECUTIVE_STAFF','GENERAL_WORKFORCE')),
  can_view boolean not null default false,
  can_edit boolean not null default false,
  can_approve boolean not null default false,
  assigned_by uuid not null,
  assigned_at timestamptz not null default now(),
  primary key (auth_user_id,workforce_group),
  check (not can_edit or can_view),
  check (not can_approve or can_view),
  check (not (can_edit and can_approve))
);

alter table payroll.employees add column if not exists workforce_group text;
-- Previous source snapshots duplicated bank names outside financial_data.
update payroll.employee_payroll_profiles
set source_details = source_details - 'bank' - 'bankBranch'
where source_details ?| array['bank','bankBranch'];
alter table payroll.employees add constraint payroll_employees_workforce_group_check
  check (workforce_group is null or workforce_group in ('EXECUTIVE_STAFF','GENERAL_WORKFORCE'));
create index if not exists payroll_employees_workforce_group_idx on payroll.employees(workforce_group);

alter table payroll.payroll_calculations add column if not exists workforce_group_snapshot text;
alter table payroll.payroll_calculations add constraint payroll_calculations_workforce_group_check
  check (workforce_group_snapshot is null or workforce_group_snapshot in ('EXECUTIVE_STAFF','GENERAL_WORKFORCE'));
create index if not exists payroll_calculations_group_period_idx
  on payroll.payroll_calculations(workforce_group_snapshot,period_id,created_at desc);

create table if not exists payroll.payroll_group_approvals (
  id uuid primary key default gen_random_uuid(),
  period_id uuid not null references payroll.setup_items(id),
  workforce_group text not null check (workforce_group in ('EXECUTIVE_STAFF','GENERAL_WORKFORCE')),
  status text not null check (status in ('SUBMITTED','APPROVED','REJECTED')),
  calculation_ids uuid[] not null,
  submitted_by uuid not null,
  submitted_at timestamptz not null default now(),
  approved_by uuid,
  approved_at timestamptz,
  rejection_reason text,
  unique(period_id,workforce_group),
  check (status <> 'APPROVED' or (approved_by is not null and approved_at is not null))
);

create table if not exists payroll.payroll_group_approval_events (
  id uuid primary key default gen_random_uuid(),
  approval_id uuid not null references payroll.payroll_group_approvals(id),
  period_id uuid not null,
  workforce_group text not null check (workforce_group in ('EXECUTIVE_STAFF','GENERAL_WORKFORCE')),
  action text not null check (action in ('SUBMITTED','APPROVED','REJECTED')),
  calculation_ids uuid[] not null,
  actor_id uuid not null,
  reason text not null,
  created_at timestamptz not null default now()
);

create table if not exists payroll.workforce_access_audit (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid not null,
  subject_id uuid not null,
  action text not null,
  old_value text,
  new_value text,
  reason text not null,
  created_at timestamptz not null default now()
);

-- Payroll is private even when this schema is added to PostgREST later.
alter table payroll.employee_financial_master enable row level security;
alter table payroll.user_workforce_grants enable row level security;
alter table payroll.payroll_group_approvals enable row level security;
alter table payroll.payroll_group_approval_events enable row level security;
alter table payroll.workforce_access_audit enable row level security;
revoke all on payroll.employee_financial_master, payroll.user_workforce_grants,
  payroll.payroll_group_approvals, payroll.payroll_group_approval_events,
  payroll.workforce_access_audit from anon, authenticated;

commit;
