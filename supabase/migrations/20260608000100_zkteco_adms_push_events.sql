begin;

create table if not exists public.zkteco_devices (
  id uuid primary key default gen_random_uuid(),
  serial_no text not null unique,
  device_name text,
  location text,
  enabled boolean not null default true,
  comm_key text,
  last_ip text,
  last_seen_at timestamptz,
  last_event_at timestamptz,
  last_push_table text,
  raw_options jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint zkteco_devices_serial_no_not_blank check (btrim(serial_no) <> ''),
  constraint zkteco_devices_raw_options_is_object check (jsonb_typeof(raw_options) = 'object')
);

create table if not exists public.zkteco_fingerprint_events (
  id uuid primary key default gen_random_uuid(),
  event_uid text not null unique,
  employee_pin text not null,
  employee_code text,
  employee_id uuid references public.employees (id) on delete set null,
  matched_employee_name text,
  matched_department text,
  match_status text not null default 'unmatched' check (match_status in ('matched', 'unmatched')),
  device_serial_no text references public.zkteco_devices (serial_no) on delete set null,
  device_ip text,
  event_time timestamptz not null,
  attendance_date date not null,
  punch_time time not null,
  verify_mode text,
  in_out_mode text,
  work_code text,
  reserved_fields text[] not null default '{}',
  raw_line text not null,
  raw_payload jsonb not null default '{}'::jsonb,
  received_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  constraint zkteco_fingerprint_events_event_uid_not_blank check (btrim(event_uid) <> ''),
  constraint zkteco_fingerprint_events_employee_pin_not_blank check (btrim(employee_pin) <> ''),
  constraint zkteco_fingerprint_events_raw_payload_is_object check (jsonb_typeof(raw_payload) = 'object')
);

create index if not exists zkteco_devices_last_seen_idx
  on public.zkteco_devices (last_seen_at desc);

create index if not exists zkteco_fingerprint_events_event_time_idx
  on public.zkteco_fingerprint_events (event_time desc);

create index if not exists zkteco_fingerprint_events_employee_code_idx
  on public.zkteco_fingerprint_events (employee_code, event_time desc);

create index if not exists zkteco_fingerprint_events_device_time_idx
  on public.zkteco_fingerprint_events (device_serial_no, event_time desc);

create index if not exists zkteco_fingerprint_events_match_status_idx
  on public.zkteco_fingerprint_events (match_status, event_time desc);

drop trigger if exists set_zkteco_devices_updated_at on public.zkteco_devices;
create trigger set_zkteco_devices_updated_at
before update on public.zkteco_devices
for each row
execute function public.touch_updated_at();

alter table public.zkteco_devices enable row level security;
alter table public.zkteco_fingerprint_events enable row level security;

grant select, insert, update, delete on public.zkteco_devices to authenticated;
grant select, insert, update, delete on public.zkteco_fingerprint_events to authenticated;

drop policy if exists "zkteco_devices_select_operational_roles" on public.zkteco_devices;
create policy "zkteco_devices_select_operational_roles"
on public.zkteco_devices
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'viewer']));

drop policy if exists "zkteco_devices_manage_operational_roles" on public.zkteco_devices;
create policy "zkteco_devices_manage_operational_roles"
on public.zkteco_devices
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

drop policy if exists "zkteco_fingerprint_events_select_operational_roles" on public.zkteco_fingerprint_events;
create policy "zkteco_fingerprint_events_select_operational_roles"
on public.zkteco_fingerprint_events
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'viewer']));

drop policy if exists "zkteco_fingerprint_events_manage_operational_roles" on public.zkteco_fingerprint_events;
create policy "zkteco_fingerprint_events_manage_operational_roles"
on public.zkteco_fingerprint_events
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

commit;
