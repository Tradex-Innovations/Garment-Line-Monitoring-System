begin;

create extension if not exists pgcrypto;

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  full_name text,
  role text not null default 'viewer' check (role in ('admin', 'supervisor', 'hr', 'viewer')),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.departments (
  id uuid primary key default gen_random_uuid(),
  name text not null unique
);

create table public.employees (
  id uuid primary key default gen_random_uuid(),
  employee_code text not null unique,
  epf_no text,
  display_name text,
  designation text,
  department_name text,
  source_priority_name text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employees_employee_code_not_blank check (btrim(employee_code) <> '')
);

create table public.leave_code_map (
  code text primary key,
  description text not null,
  attendance_class text not null
);

create table public.import_batches (
  id uuid primary key default gen_random_uuid(),
  source_type text not null check (source_type in ('face', 'fingerprint')),
  original_filename text not null,
  storage_path text not null,
  file_mime_type text,
  file_size_bytes bigint,
  uploaded_by uuid references public.profiles (id),
  import_status text not null check (
    import_status in (
      'uploaded',
      'processing',
      'parsed',
      'normalized',
      'reconciled',
      'completed',
      'failed',
      'partially_completed'
    )
  ),
  total_raw_rows integer not null default 0,
  total_valid_rows integer not null default 0,
  total_error_rows integer not null default 0,
  notes text,
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.face_raw_rows (
  id uuid primary key default gen_random_uuid(),
  import_batch_id uuid not null references public.import_batches (id) on delete cascade,
  row_number integer not null,
  source_first_name text,
  source_last_name text,
  source_employee_id text,
  source_department text,
  source_date_text text,
  source_weekday text,
  source_records_text text,
  raw_payload jsonb not null,
  parse_status text not null default 'pending',
  parse_error text,
  created_at timestamptz not null default now(),
  constraint face_raw_rows_batch_row_number_unique unique (import_batch_id, row_number)
);

create table public.fingerprint_raw_rows (
  id uuid primary key default gen_random_uuid(),
  import_batch_id uuid not null references public.import_batches (id) on delete cascade,
  row_number integer not null,
  source_emp_no text,
  source_epf_no text,
  source_name text,
  source_designation text,
  source_department text,
  source_date_text text,
  source_time_in_text text,
  source_time_out_text text,
  source_late_early_text text,
  source_day text,
  source_ot_text text,
  source_leave_type text,
  source_leave_days_total_text text,
  source_nopay_days_total_text text,
  source_other_leave_days_text text,
  raw_payload jsonb not null,
  parse_status text not null default 'pending',
  parse_error text,
  created_at timestamptz not null default now(),
  constraint fingerprint_raw_rows_batch_row_number_unique unique (import_batch_id, row_number)
);

create table public.face_events (
  id uuid primary key default gen_random_uuid(),
  import_batch_id uuid not null references public.import_batches (id) on delete cascade,
  raw_row_id uuid references public.face_raw_rows (id) on delete set null,
  employee_code text not null,
  event_date date not null,
  event_time time not null,
  event_timestamp timestamptz,
  event_sequence integer not null,
  source_records_text text,
  is_duplicate boolean not null default false,
  created_at timestamptz not null default now(),
  constraint face_events_unique unique (import_batch_id, employee_code, event_date, event_time, event_sequence)
);

create table public.face_daily_summary (
  id uuid primary key default gen_random_uuid(),
  import_batch_id uuid not null references public.import_batches (id) on delete cascade,
  employee_code text not null,
  event_date date not null,
  face_first_seen time,
  face_last_seen time,
  face_event_count integer not null default 0,
  duplicate_event_count integer not null default 0,
  normalized_records jsonb not null default '[]'::jsonb,
  quality_flags jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  constraint face_daily_summary_unique unique (import_batch_id, employee_code, event_date),
  constraint face_daily_summary_normalized_records_is_array check (jsonb_typeof(normalized_records) = 'array'),
  constraint face_daily_summary_quality_flags_is_array check (jsonb_typeof(quality_flags) = 'array')
);

create table public.fingerprint_daily_attendance (
  id uuid primary key default gen_random_uuid(),
  import_batch_id uuid not null references public.import_batches (id) on delete cascade,
  raw_row_id uuid references public.fingerprint_raw_rows (id) on delete set null,
  employee_code text not null,
  epf_no text,
  employee_name text,
  designation text,
  department_name text,
  attendance_date date not null,
  time_in time,
  time_out time,
  late_early_hours numeric(8, 2),
  ot_hours numeric(8, 2),
  leave_type text,
  leave_days_total numeric(8, 2),
  nopay_days_total numeric(8, 2),
  other_leave_days numeric(8, 2),
  attendance_state text not null check (attendance_state in ('present', 'leave', 'absent', 'no_data', 'review')),
  quality_flags jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  constraint fingerprint_daily_attendance_unique unique (import_batch_id, employee_code, attendance_date),
  constraint fingerprint_daily_attendance_quality_flags_is_array check (jsonb_typeof(quality_flags) = 'array')
);

create table public.attendance_reconciliation (
  id uuid primary key default gen_random_uuid(),
  face_import_batch_id uuid references public.import_batches (id),
  fingerprint_import_batch_id uuid references public.import_batches (id),
  employee_code text not null,
  attendance_date date not null,
  employee_name text,
  designation text,
  department_name text,
  face_first_seen time,
  face_last_seen time,
  face_event_count integer,
  duplicate_face_event_count integer,
  fingerprint_time_in time,
  fingerprint_time_out time,
  late_early_hours numeric(8, 2),
  ot_hours numeric(8, 2),
  leave_type text,
  reconciliation_status text not null check (
    reconciliation_status in (
      'validated',
      'face_only',
      'fingerprint_only',
      'leave',
      'absent',
      'needs_review',
      'anomaly'
    )
  ),
  exception_reason text,
  confidence_level text check (confidence_level in ('high', 'medium', 'low')),
  rule_flags jsonb not null default '[]'::jsonb,
  manually_overridden boolean not null default false,
  manual_override_status text check (
    manual_override_status is null
    or manual_override_status in (
      'validated',
      'face_only',
      'fingerprint_only',
      'leave',
      'absent',
      'needs_review',
      'anomaly'
    )
  ),
  manual_override_reason text,
  manual_override_by uuid references public.profiles (id),
  manual_override_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint attendance_reconciliation_unique unique (employee_code, attendance_date),
  constraint attendance_reconciliation_rule_flags_is_array check (jsonb_typeof(rule_flags) = 'array')
);

create table public.reconciliation_notes (
  id uuid primary key default gen_random_uuid(),
  reconciliation_id uuid not null references public.attendance_reconciliation (id) on delete cascade,
  note text not null,
  created_by uuid references public.profiles (id),
  created_at timestamptz not null default now()
);

create table public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid references public.profiles (id),
  action_type text not null,
  entity_type text not null,
  entity_id text not null,
  old_value jsonb,
  new_value jsonb,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint audit_logs_metadata_is_object check (jsonb_typeof(metadata) = 'object')
);

create index profiles_role_idx on public.profiles (role, is_active);
create index employees_department_name_idx on public.employees (department_name);
create index employees_epf_no_idx on public.employees (epf_no);
create index import_batches_source_status_created_at_idx on public.import_batches (source_type, import_status, created_at desc);
create index face_raw_rows_batch_parse_status_idx on public.face_raw_rows (import_batch_id, parse_status);
create index fingerprint_raw_rows_batch_parse_status_idx on public.fingerprint_raw_rows (import_batch_id, parse_status);
create index face_events_employee_date_idx on public.face_events (employee_code, event_date);
create index face_daily_summary_employee_date_idx on public.face_daily_summary (employee_code, event_date);
create index fingerprint_daily_attendance_employee_date_idx on public.fingerprint_daily_attendance (employee_code, attendance_date);
create index attendance_reconciliation_date_status_idx on public.attendance_reconciliation (attendance_date, reconciliation_status);
create index attendance_reconciliation_department_idx on public.attendance_reconciliation (department_name);
create index reconciliation_notes_reconciliation_idx on public.reconciliation_notes (reconciliation_id, created_at desc);
create index audit_logs_entity_idx on public.audit_logs (entity_type, entity_id, created_at desc);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger set_profiles_updated_at
before update on public.profiles
for each row
execute function public.touch_updated_at();

create trigger set_employees_updated_at
before update on public.employees
for each row
execute function public.touch_updated_at();

create trigger set_import_batches_updated_at
before update on public.import_batches
for each row
execute function public.touch_updated_at();

create trigger set_attendance_reconciliation_updated_at
before update on public.attendance_reconciliation
for each row
execute function public.touch_updated_at();

create or replace function public.app_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (select role from public.profiles where id = auth.uid()),
    'viewer'
  );
$$;

create or replace function public.has_role(allowed_roles text[])
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.role() = 'service_role'
    or (
      auth.uid() is not null
      and public.app_role() = any(allowed_roles)
    );
$$;

create or replace function public.log_audit_event(
  p_action_type text,
  p_entity_type text,
  p_entity_id text,
  p_old_value jsonb default null,
  p_new_value jsonb default null,
  p_metadata jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_audit_id uuid;
begin
  if auth.uid() is null or not public.has_role(array['admin', 'hr', 'supervisor']) then
    raise exception 'Only admin, HR, or supervisor users can write audit events.';
  end if;

  insert into public.audit_logs (
    actor_user_id,
    action_type,
    entity_type,
    entity_id,
    old_value,
    new_value,
    metadata
  )
  values (
    auth.uid(),
    p_action_type,
    p_entity_type,
    p_entity_id,
    p_old_value,
    p_new_value,
    coalesce(p_metadata, '{}'::jsonb)
  )
  returning id into v_audit_id;

  return v_audit_id;
end;
$$;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
begin
  v_role := case
    when coalesce(new.raw_user_meta_data ->> 'role', '') in ('admin', 'supervisor', 'hr', 'viewer')
      then new.raw_user_meta_data ->> 'role'
    else 'viewer'
  end;

  insert into public.profiles (id, full_name, role)
  values (
    new.id,
    coalesce(
      nullif(new.raw_user_meta_data ->> 'full_name', ''),
      nullif(new.raw_user_meta_data ->> 'name', ''),
      split_part(coalesce(new.email, ''), '@', 1)
    ),
    v_role
  )
  on conflict (id) do nothing;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
after insert on auth.users
for each row
execute function public.handle_new_user();

insert into public.profiles (id, full_name, role)
select
  users.id,
  coalesce(
    nullif(users.raw_user_meta_data ->> 'full_name', ''),
    nullif(users.raw_user_meta_data ->> 'name', ''),
    split_part(coalesce(users.email, ''), '@', 1)
  ),
  case
    when coalesce(users.raw_user_meta_data ->> 'role', '') in ('admin', 'supervisor', 'hr', 'viewer')
      then users.raw_user_meta_data ->> 'role'
    else 'viewer'
  end
from auth.users as users
on conflict (id) do nothing;

alter table public.profiles enable row level security;
alter table public.departments enable row level security;
alter table public.employees enable row level security;
alter table public.leave_code_map enable row level security;
alter table public.import_batches enable row level security;
alter table public.face_raw_rows enable row level security;
alter table public.fingerprint_raw_rows enable row level security;
alter table public.face_events enable row level security;
alter table public.face_daily_summary enable row level security;
alter table public.fingerprint_daily_attendance enable row level security;
alter table public.attendance_reconciliation enable row level security;
alter table public.reconciliation_notes enable row level security;
alter table public.audit_logs enable row level security;

grant usage on schema public to authenticated;

grant select, insert, update on public.profiles to authenticated;
grant select on public.departments to authenticated;
grant select on public.leave_code_map to authenticated;
grant select, insert, update, delete on public.employees to authenticated;
grant select, insert, update, delete on public.import_batches to authenticated;
grant select, insert, update, delete on public.face_raw_rows to authenticated;
grant select, insert, update, delete on public.fingerprint_raw_rows to authenticated;
grant select, insert, update, delete on public.face_events to authenticated;
grant select, insert, update, delete on public.face_daily_summary to authenticated;
grant select, insert, update, delete on public.fingerprint_daily_attendance to authenticated;
grant select, insert, update, delete on public.attendance_reconciliation to authenticated;
grant select, insert, update, delete on public.reconciliation_notes to authenticated;
grant select on public.audit_logs to authenticated;

grant execute on function public.app_role() to authenticated;
grant execute on function public.has_role(text[]) to authenticated;
grant execute on function public.log_audit_event(text, text, text, jsonb, jsonb, jsonb) to authenticated;

create policy "profiles_select_self_or_admin"
on public.profiles
for select
to authenticated
using (auth.uid() = id or public.has_role(array['admin']));

create policy "profiles_update_self_or_admin"
on public.profiles
for update
to authenticated
using (auth.uid() = id or public.has_role(array['admin']))
with check (auth.uid() = id or public.has_role(array['admin']));

create policy "profiles_insert_admin_only"
on public.profiles
for insert
to authenticated
with check (public.has_role(array['admin']));

create policy "departments_read_authenticated"
on public.departments
for select
to authenticated
using (true);

create policy "departments_write_admin_hr"
on public.departments
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "leave_code_map_read_authenticated"
on public.leave_code_map
for select
to authenticated
using (true);

create policy "leave_code_map_write_admin_hr"
on public.leave_code_map
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "employees_read_admin_hr_supervisor"
on public.employees
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "employees_write_admin_hr"
on public.employees
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "import_batches_read_admin_hr_supervisor"
on public.import_batches
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "import_batches_write_admin_hr"
on public.import_batches
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "face_raw_rows_admin_hr_only"
on public.face_raw_rows
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "fingerprint_raw_rows_admin_hr_only"
on public.fingerprint_raw_rows
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "face_events_read_admin_hr_supervisor"
on public.face_events
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "face_events_write_admin_hr"
on public.face_events
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "face_daily_summary_read_admin_hr_supervisor"
on public.face_daily_summary
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "face_daily_summary_write_admin_hr"
on public.face_daily_summary
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "fingerprint_daily_attendance_read_admin_hr_supervisor"
on public.fingerprint_daily_attendance
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "fingerprint_daily_attendance_write_admin_hr"
on public.fingerprint_daily_attendance
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "attendance_reconciliation_read_authenticated"
on public.attendance_reconciliation
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'viewer']));

create policy "attendance_reconciliation_write_admin_hr"
on public.attendance_reconciliation
for all
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "reconciliation_notes_read_admin_hr_supervisor"
on public.reconciliation_notes
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

create policy "reconciliation_notes_insert_admin_hr_supervisor"
on public.reconciliation_notes
for insert
to authenticated
with check (public.has_role(array['admin', 'hr', 'supervisor']) and created_by = auth.uid());

create policy "reconciliation_notes_update_admin_hr"
on public.reconciliation_notes
for update
to authenticated
using (public.has_role(array['admin', 'hr']))
with check (public.has_role(array['admin', 'hr']));

create policy "audit_logs_read_admin_hr"
on public.audit_logs
for select
to authenticated
using (public.has_role(array['admin', 'hr']));

insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'imports',
  'imports',
  false,
  104857600,
  array[
    'application/pdf',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'text/csv'
  ]
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy "storage_imports_read_admin_hr"
on storage.objects
for select
to authenticated
using (
  bucket_id = 'imports'
  and public.has_role(array['admin', 'hr'])
);

create policy "storage_imports_insert_admin_hr"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'imports'
  and public.has_role(array['admin', 'hr'])
);

create policy "storage_imports_update_admin_hr"
on storage.objects
for update
to authenticated
using (
  bucket_id = 'imports'
  and public.has_role(array['admin', 'hr'])
)
with check (
  bucket_id = 'imports'
  and public.has_role(array['admin', 'hr'])
);

create policy "storage_imports_delete_admin_hr"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'imports'
  and public.has_role(array['admin', 'hr'])
);

create or replace view public.vw_validation_summary
with (security_invoker = true)
as
select
  attendance_date,
  count(*) as total_reconciled,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'validated') as validated_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'face_only') as face_only_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'fingerprint_only') as fingerprint_only_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'leave') as leave_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'absent') as absent_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'needs_review') as needs_review_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'anomaly') as anomaly_count
from public.attendance_reconciliation
group by attendance_date;

create or replace view public.vw_department_validation_summary
with (security_invoker = true)
as
select
  attendance_date,
  coalesce(nullif(department_name, ''), 'Unknown') as department_name,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'validated') as validated_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'face_only') as face_only_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'fingerprint_only') as fingerprint_only_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'leave') as leave_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'absent') as absent_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'needs_review') as needs_review_count,
  count(*) filter (where coalesce(manual_override_status, reconciliation_status) = 'anomaly') as anomaly_count
from public.attendance_reconciliation
group by attendance_date, coalesce(nullif(department_name, ''), 'Unknown');

create or replace view public.vw_reconciliation_exceptions
with (security_invoker = true)
as
select
  reconciliation.*,
  coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status) as effective_status
from public.attendance_reconciliation as reconciliation
where coalesce(reconciliation.manual_override_status, reconciliation.reconciliation_status)
  in ('face_only', 'fingerprint_only', 'needs_review', 'anomaly');

grant select on public.vw_validation_summary to authenticated;
grant select on public.vw_department_validation_summary to authenticated;
grant select on public.vw_reconciliation_exceptions to authenticated;

create or replace function public.rpc_reconcile_attendance(face_batch_id uuid, fingerprint_batch_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_reconciled_count integer := 0;
begin
  if not public.has_role(array['admin', 'hr']) then
    raise exception 'Only admin and HR users can run reconciliation.';
  end if;

  if face_batch_id is null or fingerprint_batch_id is null then
    raise exception 'Both face_batch_id and fingerprint_batch_id are required.';
  end if;

  with face as (
    select
      summaries.import_batch_id,
      summaries.employee_code,
      summaries.event_date,
      summaries.face_first_seen,
      summaries.face_last_seen,
      summaries.face_event_count,
      summaries.duplicate_event_count,
      summaries.quality_flags
    from public.face_daily_summary as summaries
    where summaries.import_batch_id = face_batch_id
  ),
  fingerprint as (
    select
      attendance.import_batch_id,
      attendance.employee_code,
      attendance.attendance_date,
      attendance.epf_no,
      attendance.employee_name,
      attendance.designation,
      attendance.department_name,
      attendance.time_in,
      attendance.time_out,
      attendance.late_early_hours,
      attendance.ot_hours,
      attendance.leave_type,
      attendance.attendance_state,
      attendance.quality_flags
    from public.fingerprint_daily_attendance as attendance
    where attendance.import_batch_id = fingerprint_batch_id
  ),
  joined as (
    select
      face.import_batch_id as face_import_batch_id,
      fingerprint.import_batch_id as fingerprint_import_batch_id,
      coalesce(face.employee_code, fingerprint.employee_code) as employee_code,
      coalesce(face.event_date, fingerprint.attendance_date) as attendance_date,
      coalesce(
        nullif(fingerprint.employee_name, ''),
        nullif(employees.display_name, '')
      ) as employee_name,
      coalesce(
        nullif(fingerprint.designation, ''),
        nullif(employees.designation, '')
      ) as designation,
      coalesce(
        nullif(fingerprint.department_name, ''),
        nullif(employees.department_name, '')
      ) as department_name,
      face.face_first_seen,
      face.face_last_seen,
      face.face_event_count,
      face.duplicate_event_count,
      fingerprint.time_in as fingerprint_time_in,
      fingerprint.time_out as fingerprint_time_out,
      fingerprint.late_early_hours,
      fingerprint.ot_hours,
      fingerprint.leave_type,
      fingerprint.attendance_state,
      coalesce(face.quality_flags, '[]'::jsonb) as face_quality_flags,
      coalesce(fingerprint.quality_flags, '[]'::jsonb) as fingerprint_quality_flags
    from face
    full outer join fingerprint
      on face.employee_code = fingerprint.employee_code
     and face.event_date = fingerprint.attendance_date
    left join public.employees as employees
      on employees.employee_code = coalesce(face.employee_code, fingerprint.employee_code)
  ),
  classified as (
    select
      joined.*,
      coalesce(joined.face_event_count, 0) > 0 as has_face,
      joined.attendance_state = 'present' and (joined.fingerprint_time_in is not null or joined.fingerprint_time_out is not null) as has_present_fingerprint,
      joined.attendance_state = 'leave' as is_leave,
      joined.attendance_state = 'absent' as is_absent,
      joined.attendance_state = 'review' as is_review,
      joined.fingerprint_time_in is not null or joined.fingerprint_time_out is not null as has_any_fingerprint_time,
      joined.fingerprint_quality_flags ? 'zero_time_pair' as has_zero_time_pair,
      (
        joined.fingerprint_quality_flags ? 'invalid_time_in'
        or joined.fingerprint_quality_flags ? 'invalid_time_out'
        or joined.fingerprint_quality_flags ? 'malformed_numeric_field'
      ) as has_malformed_fingerprint_values,
      coalesce(joined.duplicate_event_count, 0) >= 2 as duplicate_heavy,
      (
        joined.face_first_seen is not null
        and joined.fingerprint_time_in is not null
        and abs(extract(epoch from (joined.face_first_seen - joined.fingerprint_time_in))) > 7200
      ) as suspicious_timing_mismatch,
      joined.employee_code is not null and joined.attendance_state is not null as has_fingerprint_row
    from joined
  ),
  prepared as (
    select
      classified.face_import_batch_id,
      classified.fingerprint_import_batch_id,
      classified.employee_code,
      classified.attendance_date,
      classified.employee_name,
      classified.designation,
      classified.department_name,
      classified.face_first_seen,
      classified.face_last_seen,
      classified.face_event_count,
      classified.duplicate_event_count,
      classified.fingerprint_time_in,
      classified.fingerprint_time_out,
      classified.late_early_hours,
      classified.ot_hours,
      classified.leave_type,
      case
        when classified.is_leave and classified.has_face then 'anomaly'
        when classified.has_malformed_fingerprint_values and classified.has_face then 'anomaly'
        when classified.has_zero_time_pair and classified.has_face then 'needs_review'
        when classified.duplicate_heavy then 'needs_review'
        when classified.suspicious_timing_mismatch then 'needs_review'
        when classified.has_face and classified.has_present_fingerprint then 'validated'
        when classified.is_leave and not classified.has_face then 'leave'
        when classified.is_absent and not classified.has_face then 'absent'
        when classified.has_face and (
          not classified.has_fingerprint_row
          or (classified.attendance_state in ('no_data', 'review') and not classified.has_any_fingerprint_time)
        ) then 'face_only'
        when classified.has_present_fingerprint and not classified.has_face then 'fingerprint_only'
        when classified.is_review then 'needs_review'
        else 'needs_review'
      end as reconciliation_status,
      case
        when classified.is_leave and classified.has_face then 'Fingerprint export marks leave while face activity exists on the same day.'
        when classified.has_malformed_fingerprint_values and classified.has_face then 'Fingerprint values could not be parsed cleanly for a day with face activity.'
        when classified.has_zero_time_pair and classified.has_face then 'Fingerprint row contains 00:00 / 00:00 without leave while face activity exists.'
        when classified.duplicate_heavy then 'Face capture contains a duplicate-heavy timestamp pattern.'
        when classified.suspicious_timing_mismatch then 'Face first seen time and fingerprint time-in differ beyond the review threshold.'
        when classified.has_face and not classified.has_fingerprint_row then 'Face activity exists without a matching fingerprint attendance row.'
        when classified.has_present_fingerprint and not classified.has_face then 'Fingerprint attendance exists without matching face activity.'
        when classified.is_review then 'Fingerprint row still requires manual review.'
        else null
      end as exception_reason,
      case
        when classified.has_face and classified.has_present_fingerprint and not classified.duplicate_heavy and not classified.suspicious_timing_mismatch and not classified.has_malformed_fingerprint_values then 'high'
        when (
          classified.is_leave
          or classified.is_absent
          or (classified.has_face and not classified.has_fingerprint_row)
          or (classified.has_present_fingerprint and not classified.has_face)
        ) and not classified.has_malformed_fingerprint_values then 'medium'
        else 'low'
      end as confidence_level,
      to_jsonb(array_remove(array[
        case when coalesce(classified.duplicate_event_count, 0) > 0 then 'duplicate_face_events' end,
        case when classified.has_face and not classified.has_fingerprint_row then 'face_present_fingerprint_missing' end,
        case when classified.has_present_fingerprint and not classified.has_face then 'fingerprint_present_face_missing' end,
        case when classified.is_leave and classified.has_face then 'leave_and_face_conflict' end,
        case when classified.has_zero_time_pair then 'zero_times_without_leave' end,
        case when classified.has_malformed_fingerprint_values then 'malformed_time_values' end,
        case when classified.suspicious_timing_mismatch then 'suspicious_timing_mismatch' end
      ]::text[], null)) as rule_flags
    from classified
    where classified.employee_code is not null
      and classified.attendance_date is not null
  ),
  upserted as (
    insert into public.attendance_reconciliation (
      face_import_batch_id,
      fingerprint_import_batch_id,
      employee_code,
      attendance_date,
      employee_name,
      designation,
      department_name,
      face_first_seen,
      face_last_seen,
      face_event_count,
      duplicate_face_event_count,
      fingerprint_time_in,
      fingerprint_time_out,
      late_early_hours,
      ot_hours,
      leave_type,
      reconciliation_status,
      exception_reason,
      confidence_level,
      rule_flags
    )
    select
      prepared.face_import_batch_id,
      prepared.fingerprint_import_batch_id,
      prepared.employee_code,
      prepared.attendance_date,
      prepared.employee_name,
      prepared.designation,
      prepared.department_name,
      prepared.face_first_seen,
      prepared.face_last_seen,
      prepared.face_event_count,
      prepared.duplicate_event_count,
      prepared.fingerprint_time_in,
      prepared.fingerprint_time_out,
      prepared.late_early_hours,
      prepared.ot_hours,
      prepared.leave_type,
      prepared.reconciliation_status,
      prepared.exception_reason,
      prepared.confidence_level,
      prepared.rule_flags
    from prepared
    on conflict (employee_code, attendance_date) do update
      set
        face_import_batch_id = excluded.face_import_batch_id,
        fingerprint_import_batch_id = excluded.fingerprint_import_batch_id,
        employee_name = excluded.employee_name,
        designation = excluded.designation,
        department_name = excluded.department_name,
        face_first_seen = excluded.face_first_seen,
        face_last_seen = excluded.face_last_seen,
        face_event_count = excluded.face_event_count,
        duplicate_face_event_count = excluded.duplicate_face_event_count,
        fingerprint_time_in = excluded.fingerprint_time_in,
        fingerprint_time_out = excluded.fingerprint_time_out,
        late_early_hours = excluded.late_early_hours,
        ot_hours = excluded.ot_hours,
        leave_type = excluded.leave_type,
        reconciliation_status = excluded.reconciliation_status,
        exception_reason = excluded.exception_reason,
        confidence_level = excluded.confidence_level,
        rule_flags = excluded.rule_flags,
        updated_at = now()
    returning 1
  )
  select count(*) into v_reconciled_count from upserted;

  update public.import_batches
  set
    import_status = case
      when import_status in ('uploaded', 'processing', 'parsed', 'normalized', 'partially_completed') then 'reconciled'
      else import_status
    end,
    updated_at = now()
  where id in (face_batch_id, fingerprint_batch_id);

  perform public.log_audit_event(
    'reconciliation_run',
    'import_batches',
    face_batch_id::text || ':' || fingerprint_batch_id::text,
    null,
    jsonb_build_object(
      'face_batch_id', face_batch_id,
      'fingerprint_batch_id', fingerprint_batch_id,
      'reconciled_count', v_reconciled_count
    ),
    jsonb_build_object('rpc', 'rpc_reconcile_attendance')
  );

  return jsonb_build_object(
    'ok', true,
    'reconciled_count', v_reconciled_count,
    'face_batch_id', face_batch_id,
    'fingerprint_batch_id', fingerprint_batch_id
  );
end;
$$;

create or replace function public.rpc_override_reconciliation(
  p_reconciliation_id uuid,
  p_new_status text,
  p_reason text,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_existing public.attendance_reconciliation%rowtype;
  v_updated public.attendance_reconciliation%rowtype;
begin
  if not public.has_role(array['admin', 'hr']) then
    raise exception 'Only admin and HR users can override reconciliation results.';
  end if;

  if p_new_status not in ('validated', 'face_only', 'fingerprint_only', 'leave', 'absent', 'needs_review', 'anomaly') then
    raise exception 'Invalid reconciliation status: %', p_new_status;
  end if;

  select *
  into v_existing
  from public.attendance_reconciliation
  where id = p_reconciliation_id
  for update;

  if not found then
    raise exception 'Reconciliation row % was not found.', p_reconciliation_id;
  end if;

  update public.attendance_reconciliation
  set
    manually_overridden = true,
    manual_override_status = p_new_status,
    manual_override_reason = p_reason,
    manual_override_by = auth.uid(),
    manual_override_at = now(),
    updated_at = now()
  where id = p_reconciliation_id
  returning * into v_updated;

  if coalesce(nullif(btrim(coalesce(p_note, '')), ''), '') <> '' then
    insert into public.reconciliation_notes (reconciliation_id, note, created_by)
    values (p_reconciliation_id, p_note, auth.uid());
  end if;

  perform public.log_audit_event(
    'reconciliation_override',
    'attendance_reconciliation',
    p_reconciliation_id::text,
    jsonb_build_object(
      'reconciliation_status', v_existing.reconciliation_status,
      'manual_override_status', v_existing.manual_override_status,
      'manual_override_reason', v_existing.manual_override_reason
    ),
    jsonb_build_object(
      'reconciliation_status', v_updated.reconciliation_status,
      'manual_override_status', v_updated.manual_override_status,
      'manual_override_reason', v_updated.manual_override_reason
    ),
    jsonb_build_object('note', p_note)
  );

  return jsonb_build_object(
    'ok', true,
    'id', v_updated.id,
    'manual_override_status', v_updated.manual_override_status,
    'manual_override_reason', v_updated.manual_override_reason,
    'manual_override_at', v_updated.manual_override_at
  );
end;
$$;

create or replace function public.rpc_add_reconciliation_note(
  p_reconciliation_id uuid,
  p_note text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_note_id uuid;
begin
  if not public.has_role(array['admin', 'hr', 'supervisor']) then
    raise exception 'Only admin, HR, and supervisors can add reconciliation notes.';
  end if;

  insert into public.reconciliation_notes (reconciliation_id, note, created_by)
  values (p_reconciliation_id, p_note, auth.uid())
  returning id into v_note_id;

  perform public.log_audit_event(
    'reconciliation_note_added',
    'attendance_reconciliation',
    p_reconciliation_id::text,
    null,
    jsonb_build_object('note_id', v_note_id),
    jsonb_build_object('note', p_note)
  );

  return jsonb_build_object('ok', true, 'note_id', v_note_id);
end;
$$;

grant execute on function public.rpc_reconcile_attendance(uuid, uuid) to authenticated;
grant execute on function public.rpc_override_reconciliation(uuid, text, text, text) to authenticated;
grant execute on function public.rpc_add_reconciliation_note(uuid, text) to authenticated;

commit;
