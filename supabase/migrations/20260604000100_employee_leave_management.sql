begin;

create table if not exists public.employee_leave_requests (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  leave_type text not null check (leave_type in ('full_day', 'half_day', 'short_leave')),
  leave_category text not null default 'casual' check (
    leave_category in ('annual', 'casual', 'sick', 'no_pay', 'emergency', 'personal', 'medical', 'other')
  ),
  start_date date not null,
  end_date date not null,
  start_time time,
  end_time time,
  half_day_session text check (half_day_session is null or half_day_session in ('first_half', 'second_half')),
  reason text,
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected', 'cancelled')),
  requested_by uuid references public.profiles (id),
  requested_at timestamptz not null default now(),
  reviewed_by uuid references public.profiles (id),
  reviewed_at timestamptz,
  review_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employee_leave_requests_date_order check (end_date >= start_date),
  constraint employee_leave_requests_short_leave_times check (
    leave_type <> 'short_leave' or (start_time is not null and end_time is not null and end_time > start_time)
  ),
  constraint employee_leave_requests_half_day_single_date check (
    leave_type <> 'half_day' or start_date = end_date
  )
);

create index if not exists employee_leave_requests_employee_idx
  on public.employee_leave_requests (employee_id, start_date desc);

create index if not exists employee_leave_requests_status_idx
  on public.employee_leave_requests (status, requested_at desc);

create index if not exists employee_leave_requests_range_idx
  on public.employee_leave_requests (start_date, end_date);

drop trigger if exists set_employee_leave_requests_updated_at on public.employee_leave_requests;
create trigger set_employee_leave_requests_updated_at
before update on public.employee_leave_requests
for each row
execute function public.touch_updated_at();

alter table public.employee_leave_requests enable row level security;

grant select, insert, update, delete on public.employee_leave_requests to authenticated;

drop policy if exists "employee_leave_requests_read_hr" on public.employee_leave_requests;
create policy "employee_leave_requests_read_hr"
on public.employee_leave_requests
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

drop policy if exists "employee_leave_requests_write_hr" on public.employee_leave_requests;
create policy "employee_leave_requests_write_hr"
on public.employee_leave_requests
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

commit;
