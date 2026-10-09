-- Employee photographs remain private. Signed, short-lived URLs let the two
-- authenticated applications render the same object referenced by
-- public.employee_profiles.photo_url.
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

create policy "shared_employee_photos_read_authenticated"
on storage.objects
for select
to authenticated
using (bucket_id = 'payroll-employee-photos' and auth.uid() is not null);

create policy "shared_employee_photos_insert_admin_hr"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'payroll-employee-photos'
  and public.has_role(array['admin', 'hr'])
);

create policy "shared_employee_photos_delete_admin_hr"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'payroll-employee-photos'
  and public.has_role(array['admin', 'hr'])
);
