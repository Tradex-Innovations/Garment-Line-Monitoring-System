begin;

create table if not exists public.skill_operations (
  id uuid primary key default gen_random_uuid(),
  operation_code text not null unique,
  name text not null,
  category text,
  description text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint skill_operations_code_not_blank check (btrim(operation_code) <> ''),
  constraint skill_operations_name_not_blank check (btrim(name) <> '')
);

create table if not exists public.production_line_operations (
  id uuid primary key default gen_random_uuid(),
  production_line_id uuid not null references public.production_lines (id) on delete cascade,
  operation_id uuid not null references public.skill_operations (id) on delete cascade,
  position_label text not null,
  required_skill_percentage numeric(5, 2) not null default 60 check (
    required_skill_percentage >= 0 and required_skill_percentage <= 100
  ),
  planned_operators integer not null default 1 check (planned_operators > 0),
  sequence_no integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint production_line_operations_position_not_blank check (btrim(position_label) <> ''),
  constraint production_line_operations_unique unique (
    production_line_id,
    operation_id,
    position_label
  )
);

create table if not exists public.employee_operation_skills (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  operation_id uuid not null references public.skill_operations (id) on delete cascade,
  skill_level_percentage numeric(5, 2) not null check (
    skill_level_percentage >= 0 and skill_level_percentage <= 100
  ),
  is_speciality boolean not null default false,
  notes text,
  certified_at date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employee_operation_skills_unique unique (employee_id, operation_id)
);

create index if not exists skill_operations_active_name_idx
  on public.skill_operations (is_active, name);

create index if not exists production_line_operations_line_sequence_idx
  on public.production_line_operations (production_line_id, is_active, sequence_no, position_label);

create index if not exists production_line_operations_operation_idx
  on public.production_line_operations (operation_id);

create index if not exists employee_operation_skills_operation_level_idx
  on public.employee_operation_skills (operation_id, skill_level_percentage desc);

create index if not exists employee_operation_skills_employee_idx
  on public.employee_operation_skills (employee_id);

drop trigger if exists set_skill_operations_updated_at on public.skill_operations;
create trigger set_skill_operations_updated_at
before update on public.skill_operations
for each row
execute function public.touch_updated_at();

drop trigger if exists set_production_line_operations_updated_at on public.production_line_operations;
create trigger set_production_line_operations_updated_at
before update on public.production_line_operations
for each row
execute function public.touch_updated_at();

drop trigger if exists set_employee_operation_skills_updated_at on public.employee_operation_skills;
create trigger set_employee_operation_skills_updated_at
before update on public.employee_operation_skills
for each row
execute function public.touch_updated_at();

alter table public.skill_operations enable row level security;
alter table public.production_line_operations enable row level security;
alter table public.employee_operation_skills enable row level security;

grant select, insert, update, delete on public.skill_operations to authenticated;
grant select, insert, update, delete on public.production_line_operations to authenticated;
grant select, insert, update, delete on public.employee_operation_skills to authenticated;

drop policy if exists "skill_operations_read_authenticated" on public.skill_operations;
create policy "skill_operations_read_authenticated"
on public.skill_operations
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "skill_operations_write_admin_supervisor_hr" on public.skill_operations;
create policy "skill_operations_write_admin_supervisor_hr"
on public.skill_operations
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "production_line_operations_read_authenticated" on public.production_line_operations;
create policy "production_line_operations_read_authenticated"
on public.production_line_operations
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "production_line_operations_write_admin_supervisor" on public.production_line_operations;
create policy "production_line_operations_write_admin_supervisor"
on public.production_line_operations
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

drop policy if exists "employee_operation_skills_read_authenticated" on public.employee_operation_skills;
create policy "employee_operation_skills_read_authenticated"
on public.employee_operation_skills
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "employee_operation_skills_write_admin_supervisor_hr" on public.employee_operation_skills;
create policy "employee_operation_skills_write_admin_supervisor_hr"
on public.employee_operation_skills
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

insert into public.skill_operations (operation_code, name, category, description)
values
  ('SNGL-NDL', 'Single Needle Operation', 'Machine Operation', 'Standard single needle sewing operation.'),
  ('OVERLOCK', 'Overlock Operation', 'Machine Operation', 'Overlock joining and edge finishing operation.'),
  ('FLATLOCK', 'Flatlock Operation', 'Machine Operation', 'Flat seam and cover stitch operation.'),
  ('HELPER', 'Line Helper', 'Support', 'Material handling, bundling, feeding, and line support.')
on conflict (operation_code) do update
set
  name = excluded.name,
  category = excluded.category,
  description = excluded.description,
  is_active = true;

commit;
