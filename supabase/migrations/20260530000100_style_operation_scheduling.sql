begin;

create table if not exists public.style_operation_plans (
  id uuid primary key default gen_random_uuid(),
  style_number text not null,
  version integer not null default 1 check (version > 0),
  description text,
  is_active boolean not null default true,
  created_by uuid references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint style_operation_plans_style_not_blank check (btrim(style_number) <> ''),
  constraint style_operation_plans_unique unique (style_number, version)
);

create table if not exists public.style_operation_plan_machines (
  id uuid primary key default gen_random_uuid(),
  style_operation_plan_id uuid not null references public.style_operation_plans (id) on delete cascade,
  operation_id uuid not null references public.skill_operations (id) on delete cascade,
  position_label text not null,
  required_skill_percentage numeric(5, 2) not null default 60 check (
    required_skill_percentage >= 0 and required_skill_percentage <= 100
  ),
  planned_operators integer not null default 1 check (planned_operators > 0),
  station_type text not null default 'mo' check (station_type in ('mo', 'helper', 'other')),
  sequence_no integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint style_operation_plan_machines_position_not_blank check (btrim(position_label) <> ''),
  constraint style_operation_plan_machines_unique unique (
    style_operation_plan_id,
    position_label
  )
);

create table if not exists public.line_style_schedules (
  id uuid primary key default gen_random_uuid(),
  production_line_id uuid not null references public.production_lines (id) on delete cascade,
  style_operation_plan_id uuid not null references public.style_operation_plans (id) on delete cascade,
  scheduled_start_at timestamptz not null,
  scheduled_end_at timestamptz,
  shift_name text not null default 'Shift A' check (shift_name in ('Shift A', 'Shift B')),
  status text not null default 'scheduled' check (
    status in ('draft', 'scheduled', 'active', 'completed', 'cancelled')
  ),
  notes text,
  scheduled_by uuid references public.profiles (id),
  activated_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint line_style_schedules_start_before_end check (
    scheduled_end_at is null or scheduled_end_at > scheduled_start_at
  )
);

create index if not exists style_operation_plans_style_idx
  on public.style_operation_plans (style_number, version desc, is_active);

create index if not exists style_operation_plan_machines_plan_sequence_idx
  on public.style_operation_plan_machines (style_operation_plan_id, is_active, sequence_no, position_label);

create index if not exists style_operation_plan_machines_operation_idx
  on public.style_operation_plan_machines (operation_id);

create index if not exists line_style_schedules_line_start_idx
  on public.line_style_schedules (production_line_id, scheduled_start_at desc);

create index if not exists line_style_schedules_status_start_idx
  on public.line_style_schedules (status, scheduled_start_at);

drop trigger if exists set_style_operation_plans_updated_at on public.style_operation_plans;
create trigger set_style_operation_plans_updated_at
before update on public.style_operation_plans
for each row
execute function public.touch_updated_at();

drop trigger if exists set_style_operation_plan_machines_updated_at on public.style_operation_plan_machines;
create trigger set_style_operation_plan_machines_updated_at
before update on public.style_operation_plan_machines
for each row
execute function public.touch_updated_at();

drop trigger if exists set_line_style_schedules_updated_at on public.line_style_schedules;
create trigger set_line_style_schedules_updated_at
before update on public.line_style_schedules
for each row
execute function public.touch_updated_at();

alter table public.style_operation_plans enable row level security;
alter table public.style_operation_plan_machines enable row level security;
alter table public.line_style_schedules enable row level security;

grant select, insert, update, delete on public.style_operation_plans to authenticated;
grant select, insert, update, delete on public.style_operation_plan_machines to authenticated;
grant select, insert, update, delete on public.line_style_schedules to authenticated;

drop policy if exists "style_operation_plans_read_authenticated" on public.style_operation_plans;
create policy "style_operation_plans_read_authenticated"
on public.style_operation_plans
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "style_operation_plans_write_planners" on public.style_operation_plans;
create policy "style_operation_plans_write_planners"
on public.style_operation_plans
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "style_operation_plan_machines_read_authenticated" on public.style_operation_plan_machines;
create policy "style_operation_plan_machines_read_authenticated"
on public.style_operation_plan_machines
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "style_operation_plan_machines_write_planners" on public.style_operation_plan_machines;
create policy "style_operation_plan_machines_write_planners"
on public.style_operation_plan_machines
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "line_style_schedules_read_authenticated" on public.line_style_schedules;
create policy "line_style_schedules_read_authenticated"
on public.line_style_schedules
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "line_style_schedules_write_planners" on public.line_style_schedules;
create policy "line_style_schedules_write_planners"
on public.line_style_schedules
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

commit;
