begin;

create table if not exists public.production_lines (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null unique,
  department_name text not null,
  shift_name text not null default 'Shift A' check (shift_name in ('Shift A', 'Shift B')),
  supervisor_name text,
  target_manpower integer not null default 0 check (target_manpower >= 0),
  target_output integer not null default 0 check (target_output >= 0),
  current_output integer not null default 0 check (current_output >= 0),
  current_efficiency numeric(5, 2) not null default 0 check (current_efficiency >= 0),
  issue text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint production_lines_code_not_blank check (btrim(code) <> ''),
  constraint production_lines_name_not_blank check (btrim(name) <> ''),
  constraint production_lines_department_name_not_blank check (btrim(department_name) <> '')
);

create table if not exists public.employee_profiles (
  employee_id uuid primary key references public.employees (id) on delete cascade,
  shift_name text not null default 'Shift A' check (shift_name in ('Shift A', 'Shift B')),
  phone text,
  join_date date,
  skills text[] not null default '{}'::text[],
  daily_rate numeric(12, 2) not null default 0 check (daily_rate >= 0),
  ot_hourly_rate numeric(12, 2) not null default 0 check (ot_hourly_rate >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.employee_notes (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  note_type text not null check (note_type in ('note', 'flag', 'remark')),
  note text not null,
  created_by uuid references public.profiles (id),
  created_at timestamptz not null default now(),
  constraint employee_notes_note_not_blank check (btrim(note) <> '')
);

create table if not exists public.line_assignments (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  production_line_id uuid not null references public.production_lines (id) on delete cascade,
  assigned_at timestamptz not null default now(),
  assigned_by uuid references public.profiles (id),
  reason text,
  status text not null default 'Active' check (status in ('Active', 'Transferred')),
  ended_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.transfer_logs (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  source_line_id uuid references public.production_lines (id) on delete set null,
  destination_line_id uuid references public.production_lines (id) on delete set null,
  reason text not null,
  transferred_at timestamptz not null default now(),
  transferred_by uuid references public.profiles (id),
  created_at timestamptz not null default now(),
  constraint transfer_logs_reason_not_blank check (btrim(reason) <> '')
);

create table if not exists public.operations_alerts (
  id uuid primary key default gen_random_uuid(),
  alert_type text not null check (
    alert_type in (
      'unverified worker',
      'missing worker',
      'line understaffed',
      'line idle',
      'delayed fingerprint',
      'duplicate event',
      'unusual movement',
      'attendance anomaly'
    )
  ),
  priority text not null check (priority in ('low', 'medium', 'high', 'critical')),
  title text not null,
  description text not null,
  status text not null default 'Open' check (status in ('Open', 'Read', 'Resolved')),
  assigned_to_user_id uuid references public.profiles (id),
  employee_id uuid references public.employees (id) on delete set null,
  line_id uuid references public.production_lines (id) on delete set null,
  reconciliation_id uuid unique references public.attendance_reconciliation (id) on delete set null,
  source text not null default 'manual' check (source in ('manual', 'reconciliation', 'system')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.operations_alert_history (
  id uuid primary key default gen_random_uuid(),
  alert_id uuid not null references public.operations_alerts (id) on delete cascade,
  actor_user_id uuid references public.profiles (id),
  action text not null,
  created_at timestamptz not null default now(),
  constraint operations_alert_history_action_not_blank check (btrim(action) <> '')
);

create table if not exists public.system_settings (
  id boolean primary key default true check (id),
  face_recognition boolean not null default true,
  fingerprint_verification boolean not null default true,
  dual_validation_required boolean not null default true,
  auto_reject_unknown_faces boolean not null default false,
  manual_verification_fallback boolean not null default true,
  auto_mark_absent boolean not null default false,
  morning_shift_start time not null default '07:30',
  morning_shift_end time not null default '17:30',
  late_arrival_threshold integer not null default 10 check (late_arrival_threshold >= 0),
  grace_period integer not null default 5 check (grace_period >= 0),
  failed_entry_alerts boolean not null default true,
  low_efficiency_warnings boolean not null default true,
  worker_absence_alerts boolean not null default true,
  daily_summary_report boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(),
  message text not null,
  is_active boolean not null default true,
  display_order integer not null default 0,
  created_by uuid references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint announcements_message_not_blank check (btrim(message) <> '')
);

create table if not exists public.incentive_records (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  month_start date not null,
  amount numeric(12, 2) not null default 0,
  reason text not null,
  created_by uuid references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint incentive_records_reason_not_blank check (btrim(reason) <> '')
);

create table if not exists public.production_line_daily_metrics (
  id uuid primary key default gen_random_uuid(),
  production_line_id uuid not null references public.production_lines (id) on delete cascade,
  metric_date date not null,
  output integer not null default 0 check (output >= 0),
  target_output integer not null default 0 check (target_output >= 0),
  efficiency numeric(5, 2) not null default 0 check (efficiency >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint production_line_daily_metrics_unique unique (production_line_id, metric_date)
);

create index if not exists employee_notes_employee_created_idx
  on public.employee_notes (employee_id, created_at desc);
create index if not exists line_assignments_line_status_idx
  on public.line_assignments (production_line_id, status, assigned_at desc);
create index if not exists line_assignments_employee_assigned_idx
  on public.line_assignments (employee_id, assigned_at desc);
create unique index if not exists line_assignments_active_employee_unique
  on public.line_assignments (employee_id)
  where status = 'Active';
create index if not exists transfer_logs_employee_transferred_idx
  on public.transfer_logs (employee_id, transferred_at desc);
create index if not exists operations_alerts_status_priority_created_idx
  on public.operations_alerts (status, priority, created_at desc);
create index if not exists operations_alerts_employee_idx
  on public.operations_alerts (employee_id, created_at desc);
create index if not exists operations_alert_history_alert_created_idx
  on public.operations_alert_history (alert_id, created_at desc);
create index if not exists announcements_active_order_idx
  on public.announcements (is_active, display_order, created_at desc);
create index if not exists incentive_records_employee_month_idx
  on public.incentive_records (employee_id, month_start desc);
create index if not exists production_line_daily_metrics_metric_date_idx
  on public.production_line_daily_metrics (metric_date desc);

drop trigger if exists set_production_lines_updated_at on public.production_lines;
create trigger set_production_lines_updated_at
before update on public.production_lines
for each row
execute function public.touch_updated_at();

drop trigger if exists set_employee_profiles_updated_at on public.employee_profiles;
create trigger set_employee_profiles_updated_at
before update on public.employee_profiles
for each row
execute function public.touch_updated_at();

drop trigger if exists set_operations_alerts_updated_at on public.operations_alerts;
create trigger set_operations_alerts_updated_at
before update on public.operations_alerts
for each row
execute function public.touch_updated_at();

drop trigger if exists set_system_settings_updated_at on public.system_settings;
create trigger set_system_settings_updated_at
before update on public.system_settings
for each row
execute function public.touch_updated_at();

drop trigger if exists set_announcements_updated_at on public.announcements;
create trigger set_announcements_updated_at
before update on public.announcements
for each row
execute function public.touch_updated_at();

drop trigger if exists set_incentive_records_updated_at on public.incentive_records;
create trigger set_incentive_records_updated_at
before update on public.incentive_records
for each row
execute function public.touch_updated_at();

drop trigger if exists set_production_line_daily_metrics_updated_at on public.production_line_daily_metrics;
create trigger set_production_line_daily_metrics_updated_at
before update on public.production_line_daily_metrics
for each row
execute function public.touch_updated_at();

insert into public.system_settings (id)
values (true)
on conflict (id) do nothing;

alter table public.production_lines enable row level security;
alter table public.employee_profiles enable row level security;
alter table public.employee_notes enable row level security;
alter table public.line_assignments enable row level security;
alter table public.transfer_logs enable row level security;
alter table public.operations_alerts enable row level security;
alter table public.operations_alert_history enable row level security;
alter table public.system_settings enable row level security;
alter table public.announcements enable row level security;
alter table public.incentive_records enable row level security;
alter table public.production_line_daily_metrics enable row level security;

grant select, insert, update, delete on public.production_lines to authenticated;
grant select, insert, update, delete on public.employee_profiles to authenticated;
grant select, insert, update, delete on public.employee_notes to authenticated;
grant select, insert, update, delete on public.line_assignments to authenticated;
grant select, insert, update, delete on public.transfer_logs to authenticated;
grant select, insert, update, delete on public.operations_alerts to authenticated;
grant select, insert, update, delete on public.operations_alert_history to authenticated;
grant select, insert, update on public.system_settings to authenticated;
grant select, insert, update, delete on public.announcements to authenticated;
grant select, insert, update, delete on public.incentive_records to authenticated;
grant select, insert, update, delete on public.production_line_daily_metrics to authenticated;

drop policy if exists "profiles_select_self_or_admin" on public.profiles;
create policy "profiles_select_self_or_operational_roles"
on public.profiles
for select
to authenticated
using (auth.uid() = id or public.has_role(array['admin', 'hr', 'supervisor']));

drop policy if exists "employees_read_admin_hr_supervisor" on public.employees;
create policy "employees_read_authenticated"
on public.employees
for select
to authenticated
using (auth.uid() is not null);

create policy "production_lines_read_authenticated"
on public.production_lines
for select
to authenticated
using (auth.uid() is not null);

create policy "production_lines_write_admin_supervisor"
on public.production_lines
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

create policy "employee_profiles_read_authenticated"
on public.employee_profiles
for select
to authenticated
using (auth.uid() is not null);

create policy "employee_profiles_write_admin_hr_supervisor"
on public.employee_profiles
for all
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']))
with check (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "employee_notes_read_admin_hr_supervisor"
on public.employee_notes
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "employee_notes_write_admin_hr_supervisor"
on public.employee_notes
for all
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']))
with check (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "line_assignments_read_authenticated"
on public.line_assignments
for select
to authenticated
using (auth.uid() is not null);

create policy "line_assignments_write_admin_supervisor"
on public.line_assignments
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

create policy "transfer_logs_read_authenticated"
on public.transfer_logs
for select
to authenticated
using (auth.uid() is not null);

create policy "transfer_logs_write_admin_supervisor"
on public.transfer_logs
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

create policy "operations_alerts_read_authenticated"
on public.operations_alerts
for select
to authenticated
using (auth.uid() is not null);

create policy "operations_alerts_write_admin_hr_supervisor"
on public.operations_alerts
for all
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']))
with check (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "operations_alert_history_read_authenticated"
on public.operations_alert_history
for select
to authenticated
using (auth.uid() is not null);

create policy "operations_alert_history_write_admin_hr_supervisor"
on public.operations_alert_history
for all
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']))
with check (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "system_settings_read_admin"
on public.system_settings
for select
to authenticated
using (public.has_role(array['admin']));

create policy "system_settings_write_admin"
on public.system_settings
for all
to authenticated
using (public.has_role(array['admin']))
with check (public.has_role(array['admin']));

create policy "announcements_read_authenticated"
on public.announcements
for select
to authenticated
using (auth.uid() is not null);

create policy "announcements_write_admin_hr"
on public.announcements
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "incentive_records_read_authenticated"
on public.incentive_records
for select
to authenticated
using (auth.uid() is not null);

create policy "incentive_records_write_admin_hr"
on public.incentive_records
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "production_line_daily_metrics_read_authenticated"
on public.production_line_daily_metrics
for select
to authenticated
using (auth.uid() is not null);

create policy "production_line_daily_metrics_write_admin_supervisor"
on public.production_line_daily_metrics
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

create or replace function public.rpc_assign_worker_to_line(
  p_employee_id uuid,
  p_line_id uuid,
  p_reason text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_employee public.employees%rowtype;
  v_line public.production_lines%rowtype;
  v_existing_assignment public.line_assignments%rowtype;
  v_assignment_id uuid;
begin
  if not public.has_role(array['admin', 'supervisor']) then
    raise exception 'Only admin and supervisor users can assign workers to lines.';
  end if;

  select * into v_employee from public.employees where id = p_employee_id;
  if not found then
    raise exception 'Employee % was not found.', p_employee_id;
  end if;

  select * into v_line from public.production_lines where id = p_line_id and is_active = true;
  if not found then
    raise exception 'Production line % was not found or is inactive.', p_line_id;
  end if;

  select *
  into v_existing_assignment
  from public.line_assignments
  where employee_id = p_employee_id
    and status = 'Active'
  order by assigned_at desc
  limit 1;

  if found then
    raise exception 'Worker already has an active line assignment. Use transfer instead.';
  end if;

  insert into public.line_assignments (
    employee_id,
    production_line_id,
    assigned_by,
    reason,
    status
  )
  values (
    p_employee_id,
    p_line_id,
    auth.uid(),
    nullif(btrim(coalesce(p_reason, '')), ''),
    'Active'
  )
  returning id into v_assignment_id;

  perform public.log_audit_event(
    'worker_assigned',
    'employees',
    p_employee_id::text,
    jsonb_build_object('line', null),
    jsonb_build_object('line', v_line.name),
    jsonb_build_object('reason', p_reason, 'assignment_id', v_assignment_id)
  );

  return jsonb_build_object(
    'ok', true,
    'assignment_id', v_assignment_id,
    'employee_id', p_employee_id,
    'line_id', p_line_id
  );
end;
$$;

create or replace function public.rpc_transfer_worker_line(
  p_employee_id uuid,
  p_destination_line_id uuid,
  p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_current_assignment public.line_assignments%rowtype;
  v_source_line public.production_lines%rowtype;
  v_destination_line public.production_lines%rowtype;
  v_new_assignment_id uuid;
  v_transfer_id uuid;
begin
  if not public.has_role(array['admin', 'supervisor']) then
    raise exception 'Only admin and supervisor users can transfer workers between lines.';
  end if;

  if nullif(btrim(coalesce(p_reason, '')), '') is null then
    raise exception 'A transfer reason is required.';
  end if;

  select * into v_destination_line
  from public.production_lines
  where id = p_destination_line_id
    and is_active = true;

  if not found then
    raise exception 'Destination line % was not found or is inactive.', p_destination_line_id;
  end if;

  select *
  into v_current_assignment
  from public.line_assignments
  where employee_id = p_employee_id
    and status = 'Active'
  order by assigned_at desc
  limit 1
  for update;

  if not found then
    raise exception 'Worker % does not currently have an active line assignment.', p_employee_id;
  end if;

  if v_current_assignment.production_line_id = p_destination_line_id then
    raise exception 'Worker is already assigned to that line.';
  end if;

  select * into v_source_line
  from public.production_lines
  where id = v_current_assignment.production_line_id;

  update public.line_assignments
  set
    status = 'Transferred',
    ended_at = now()
  where id = v_current_assignment.id;

  insert into public.line_assignments (
    employee_id,
    production_line_id,
    assigned_by,
    reason,
    status
  )
  values (
    p_employee_id,
    p_destination_line_id,
    auth.uid(),
    p_reason,
    'Active'
  )
  returning id into v_new_assignment_id;

  insert into public.transfer_logs (
    employee_id,
    source_line_id,
    destination_line_id,
    reason,
    transferred_by
  )
  values (
    p_employee_id,
    v_current_assignment.production_line_id,
    p_destination_line_id,
    p_reason,
    auth.uid()
  )
  returning id into v_transfer_id;

  perform public.log_audit_event(
    'worker_transferred',
    'employees',
    p_employee_id::text,
    jsonb_build_object('line', coalesce(v_source_line.name, 'Unassigned')),
    jsonb_build_object('line', v_destination_line.name),
    jsonb_build_object('reason', p_reason, 'transfer_id', v_transfer_id)
  );

  return jsonb_build_object(
    'ok', true,
    'assignment_id', v_new_assignment_id,
    'transfer_id', v_transfer_id,
    'employee_id', p_employee_id,
    'destination_line_id', p_destination_line_id
  );
end;
$$;

create or replace function public.rpc_sync_reconciliation_alerts()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_synced_count integer := 0;
  v_resolved_count integer := 0;
begin
  if not public.has_role(array['admin', 'hr', 'supervisor']) then
    raise exception 'Only admin, HR, and supervisor users can synchronize reconciliation alerts.';
  end if;

  with exception_rows as (
    select
      reconciliation.id as reconciliation_id,
      employees.id as employee_id,
      assignment.production_line_id as line_id,
      case
        when coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status) = 'anomaly' then 'attendance anomaly'
        when coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status) = 'needs_review' then 'unverified worker'
        when coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status) = 'face_only' then 'missing worker'
        when coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status) = 'fingerprint_only' then 'delayed fingerprint'
        else 'attendance anomaly'
      end as alert_type,
      case
        when coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status) = 'anomaly' then 'critical'
        when coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status) = 'needs_review' then 'high'
        else 'medium'
      end as priority,
      format(
        '%s · %s',
        coalesce(reconciliation.employee_name, reconciliation.employee_code),
        replace(coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status), '_', ' ')
      ) as title,
      coalesce(
        reconciliation.exception_reason,
        'Reconciliation result requires operational attention.'
      ) as description
    from public.attendance_reconciliation as reconciliation
    left join public.employees as employees
      on employees.employee_code = reconciliation.employee_code
    left join lateral (
      select production_line_id
      from public.line_assignments
      where employee_id = employees.id
        and status = 'Active'
      order by assigned_at desc
      limit 1
    ) as assignment on true
    where coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status)
      in ('face_only', 'fingerprint_only', 'needs_review', 'anomaly')
  ),
  upserted as (
    insert into public.operations_alerts (
      alert_type,
      priority,
      title,
      description,
      status,
      employee_id,
      line_id,
      reconciliation_id,
      source
    )
    select
      exception_rows.alert_type,
      exception_rows.priority,
      exception_rows.title,
      exception_rows.description,
      'Open',
      exception_rows.employee_id,
      exception_rows.line_id,
      exception_rows.reconciliation_id,
      'reconciliation'
    from exception_rows
    on conflict (reconciliation_id) do update
      set
        alert_type = excluded.alert_type,
        priority = excluded.priority,
        title = excluded.title,
        description = excluded.description,
        employee_id = excluded.employee_id,
        line_id = excluded.line_id,
        status = case
          when public.operations_alerts.status = 'Resolved' then public.operations_alerts.status
          else excluded.status
        end,
        updated_at = now()
    returning 1
  )
  select count(*) into v_synced_count from upserted;

  with current_exception_ids as (
    select id
    from public.attendance_reconciliation
    where coalesce(manual_override_status, reconciliation_status)
      in ('face_only', 'fingerprint_only', 'needs_review', 'anomaly')
  ),
  resolved as (
    update public.operations_alerts
    set
      status = 'Resolved',
      updated_at = now()
    where source = 'reconciliation'
      and reconciliation_id is not null
      and reconciliation_id not in (select id from current_exception_ids)
      and status <> 'Resolved'
    returning id
  )
  select count(*) into v_resolved_count from resolved;

  return jsonb_build_object(
    'ok', true,
    'synced_count', v_synced_count,
    'resolved_count', v_resolved_count
  );
end;
$$;

grant execute on function public.rpc_assign_worker_to_line(uuid, uuid, text) to authenticated;
grant execute on function public.rpc_transfer_worker_line(uuid, uuid, text) to authenticated;
grant execute on function public.rpc_sync_reconciliation_alerts() to authenticated;

commit;
