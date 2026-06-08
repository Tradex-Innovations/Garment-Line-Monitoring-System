create table if not exists public.production_line_output_entries (
  id uuid primary key default gen_random_uuid(),
  production_line_id uuid not null references public.production_lines (id) on delete cascade,
  production_date date not null,
  entry_time time not null,
  output_quantity integer not null check (output_quantity > 0),
  cumulative_output integer not null default 0 check (cumulative_output >= 0),
  note text,
  created_by uuid references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists production_line_output_entries_line_day_time_idx
  on public.production_line_output_entries (production_line_id, production_date desc, entry_time desc, created_at desc);

drop trigger if exists set_production_line_output_entries_updated_at on public.production_line_output_entries;
create trigger set_production_line_output_entries_updated_at
before update on public.production_line_output_entries
for each row
execute function public.touch_updated_at();

alter table public.production_line_output_entries enable row level security;

grant select, insert, update, delete on public.production_line_output_entries to authenticated;

drop policy if exists "production_line_output_entries_read_authenticated" on public.production_line_output_entries;
create policy "production_line_output_entries_read_authenticated"
on public.production_line_output_entries
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "production_line_output_entries_write_admin_supervisor" on public.production_line_output_entries;
create policy "production_line_output_entries_write_admin_supervisor"
on public.production_line_output_entries
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));
