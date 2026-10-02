-- Employee photos are served only by the shared authenticated Spring API.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'payroll-employee-photos',
  'payroll-employee-photos',
  false,
  5242880,
  array['image/jpeg', 'image/png']
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;
