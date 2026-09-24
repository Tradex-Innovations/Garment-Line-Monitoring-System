begin;

create temporary table department_duplicates on commit drop as
select duplicate.id as duplicate_id, canonical.id as canonical_id
from public.departments duplicate
join (
  select lower(name) as normalized_name, min(id::text)::uuid as id
  from public.departments
  group by lower(name)
) canonical on canonical.normalized_name = lower(duplicate.name)
where duplicate.id <> canonical.id;

update public.employees employee
set department_id = duplicate.canonical_id
from department_duplicates duplicate
where employee.department_id = duplicate.duplicate_id;

delete from public.departments department
using department_duplicates duplicate
where department.id = duplicate.duplicate_id;

create temporary table designation_duplicates on commit drop as
select duplicate.id as duplicate_id, canonical.id as canonical_id
from public.designations duplicate
join (
  select lower(name) as normalized_name, min(id::text)::uuid as id
  from public.designations
  group by lower(name)
) canonical on canonical.normalized_name = lower(duplicate.name)
where duplicate.id <> canonical.id;

update public.employees employee
set designation_id = duplicate.canonical_id
from designation_duplicates duplicate
where employee.designation_id = duplicate.duplicate_id;

delete from public.designations designation
using designation_duplicates duplicate
where designation.id = duplicate.duplicate_id;

create unique index if not exists departments_name_ci_key
  on public.departments (lower(name));
create unique index if not exists designations_name_ci_key
  on public.designations (lower(name));
create unique index if not exists teams_name_ci_key
  on public.teams (lower(name));
create unique index if not exists grades_name_ci_key
  on public.grades (lower(name));
create unique index if not exists work_shifts_name_ci_key
  on public.work_shifts (lower(name));

notify pgrst, 'reload schema';

commit;
