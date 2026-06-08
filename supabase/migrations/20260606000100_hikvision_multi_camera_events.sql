begin;

alter table public.hikvision_face_events
  add column if not exists camera_id text,
  add column if not exists camera_name text,
  add column if not exists camera_location text,
  add column if not exists camera_base_url text;

create index if not exists hikvision_face_events_camera_id_time_idx
  on public.hikvision_face_events (camera_id, event_time desc);

commit;
