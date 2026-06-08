begin;

create table if not exists public.hikvision_face_events (
  id uuid primary key default gen_random_uuid(),
  camera_event_id text not null unique,
  camera_serial_no text,
  employee_code text,
  employee_id uuid references public.employees (id) on delete set null,
  device_person_name text,
  matched_employee_name text,
  matched_department text,
  match_status text not null default 'unmatched' check (match_status in ('matched', 'unmatched')),
  event_time timestamptz not null,
  received_at timestamptz not null default now(),
  verify_mode text,
  attendance_status text,
  access_decision text,
  picture_url text,
  visible_light_pic_url text,
  thermal_pic_url text,
  temperature numeric(6, 2),
  mask_status text,
  major integer,
  minor integer,
  raw_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists hikvision_face_events_event_time_idx
  on public.hikvision_face_events (event_time desc);

create index if not exists hikvision_face_events_employee_code_idx
  on public.hikvision_face_events (employee_code, event_time desc);

create index if not exists hikvision_face_events_match_status_idx
  on public.hikvision_face_events (match_status, event_time desc);

alter table public.hikvision_face_events enable row level security;

grant select, insert, update, delete on public.hikvision_face_events to authenticated;

drop policy if exists "hikvision_face_events_select_operational_roles" on public.hikvision_face_events;
create policy "hikvision_face_events_select_operational_roles"
on public.hikvision_face_events
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'viewer']));

drop policy if exists "hikvision_face_events_manage_operational_roles" on public.hikvision_face_events;
create policy "hikvision_face_events_manage_operational_roles"
on public.hikvision_face_events
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

commit;
