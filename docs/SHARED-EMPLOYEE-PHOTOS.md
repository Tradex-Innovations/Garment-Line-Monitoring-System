# Shared employee photographs

LineMatrix and Payroll use one private Supabase Storage bucket, `payroll-employee-photos`.
The canonical photo reference is `public.employee_profiles.photo_url`: it stores a
Storage object key such as `employee-photos/<employee-id>-<hash>.jpg`, not a public URL.
Authenticated staff clients request short-lived signed URLs; the employee portal
backend signs only the employee photo returned in its own session response.

## Import archive photographs

The importer reads `backend/.env` locally for the Supabase URL and service-role key.
Never commit that file, the archive, or the key. The source may be a folder of
images or a `.rar` archive. The originals are left untouched.

```sh
node scripts/import-employee-photos.mjs 'backend/media/emp-images/Face photos.rar'
```

The default is a dry run. It matches the trailing number in filenames like
`10364+_10364.jpg` to exactly one `employees.employee_code`. Conflicting,
missing, or malformed identifiers are skipped. Existing `photo_url` values are
preserved. Review the counts and exceptions before a write.

`--upload-only` stages objects in the private bucket without changing any
employee record. Use this while the application changes are not yet deployed.
After the storage-access migration, shared backend, and both frontends are
deployed, run `--apply` to upload/link verified matches. This operation is
repeatable; object names derive from content hashes. Use `--replace-existing`
with `--apply` only after explicitly reviewing the existing photo that would
be replaced.

```sh
node scripts/import-employee-photos.mjs 'backend/media/emp-images/Face photos.rar' --upload-only
node scripts/import-employee-photos.mjs 'backend/media/emp-images/Face photos.rar' --apply
```

Do not run a blanket `supabase db push` just for photos if unrelated local
migrations are also pending. Coordinate the exact migration deployment first.
The photo migration is `supabase/migrations/20261008000100_shared_employee_photo_access.sql`.

## Ongoing uploads

- LineMatrix HR/Admin: save a new employee, reopen **Edit**, and upload a JPEG/PNG
  photo (maximum 5 MB). This updates the canonical `photo_url`.
- Payroll: upload in the employee payroll profile. The shared backend stores the
  private object, updates Payroll's file metadata, and links the same key to
  the LineMatrix employee profile. An unlinked Payroll employee cannot publish
  a shared photo.
- The previous photo is not deleted by a LineMatrix upload, avoiding accidental
  loss while references or Payroll metadata may still point to it. Periodic
  orphan cleanup should be a separate, reviewed maintenance operation.

After import, verify a few different employee numbers in LineMatrix roster,
worker profile, and Payroll profile; then replace one test photo through each
app and confirm both views update. Never make the bucket public.
