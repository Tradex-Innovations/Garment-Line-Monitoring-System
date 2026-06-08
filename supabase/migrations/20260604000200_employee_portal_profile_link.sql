begin;

alter table public.profiles
  add column if not exists employee_id uuid references public.employees (id) on delete set null,
  add column if not exists employee_code text;

create unique index if not exists profiles_employee_id_unique
  on public.profiles (employee_id)
  where employee_id is not null;

create index if not exists profiles_employee_code_idx
  on public.profiles (employee_code)
  where employee_code is not null;

update public.profiles as profile
set employee_id = employee.id
from public.employees as employee
where profile.employee_id is null
  and profile.employee_code is not null
  and employee.employee_code = profile.employee_code;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
  v_employee_code text;
  v_employee_id uuid;
begin
  v_role := case
    when coalesce(new.raw_user_meta_data ->> 'role', '') in ('admin', 'supervisor', 'hr', 'viewer')
      then new.raw_user_meta_data ->> 'role'
    else 'viewer'
  end;

  v_employee_code := nullif(btrim(coalesce(new.raw_user_meta_data ->> 'employee_code', '')), '');

  if v_employee_code is not null then
    select employees.id
    into v_employee_id
    from public.employees
    where employees.employee_code = v_employee_code
    limit 1;
  end if;

  insert into public.profiles (id, full_name, role, employee_code, employee_id)
  values (
    new.id,
    coalesce(
      nullif(new.raw_user_meta_data ->> 'full_name', ''),
      nullif(new.raw_user_meta_data ->> 'name', ''),
      split_part(coalesce(new.email, ''), '@', 1)
    ),
    v_role,
    v_employee_code,
    v_employee_id
  )
  on conflict (id) do nothing;

  return new;
end;
$$;

commit;
