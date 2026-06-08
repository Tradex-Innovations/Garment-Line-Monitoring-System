begin;

create table if not exists public.line_position_assignments (
  id uuid primary key default gen_random_uuid(),
  production_line_operation_id uuid not null references public.production_line_operations (id) on delete cascade,
  employee_id uuid not null references public.employees (id) on delete cascade,
  is_active boolean not null default true,
  assigned_by uuid references public.profiles (id),
  assigned_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint line_position_assignments_unique_employee unique (employee_id),
  constraint line_position_assignments_unique_position_employee unique (
    production_line_operation_id,
    employee_id
  )
);

create index if not exists line_position_assignments_operation_idx
  on public.line_position_assignments (production_line_operation_id, is_active);

create index if not exists line_position_assignments_employee_idx
  on public.line_position_assignments (employee_id, is_active);

drop trigger if exists set_line_position_assignments_updated_at on public.line_position_assignments;
create trigger set_line_position_assignments_updated_at
before update on public.line_position_assignments
for each row
execute function public.touch_updated_at();

alter table public.line_position_assignments enable row level security;

grant select, insert, update, delete on public.line_position_assignments to authenticated;

drop policy if exists "line_position_assignments_read_authenticated" on public.line_position_assignments;
create policy "line_position_assignments_read_authenticated"
on public.line_position_assignments
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "line_position_assignments_write_admin_supervisor" on public.line_position_assignments;
create policy "line_position_assignments_write_admin_supervisor"
on public.line_position_assignments
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

commit;
