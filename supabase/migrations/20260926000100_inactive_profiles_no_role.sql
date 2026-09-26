-- A removed shared Auth user retains its profile for historical references,
-- but an unexpired access token must not retain LineMatrix role access.
create or replace function public.app_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select case
    when exists (
      select 1 from auth.users
      where id = auth.uid() and deleted_at is not null
    ) then null
    when exists (
      select 1 from public.profiles
      where id = auth.uid() and is_active = false
    ) then null
    else coalesce(
      (select role from public.profiles where id = auth.uid()),
      'viewer'
    )
  end;
$$;
