begin;

create table if not exists public.calculation_rule_sets (
  id uuid primary key default gen_random_uuid(),
  rule_type text not null,
  rule_set_id text not null,
  version integer not null,
  description text,
  source_path text not null,
  is_active boolean not null default true,
  checksum text,
  created_at timestamptz not null default now(),
  constraint calculation_rule_sets_rule_type_not_blank check (btrim(rule_type) <> ''),
  constraint calculation_rule_sets_rule_set_id_not_blank check (btrim(rule_set_id) <> ''),
  constraint calculation_rule_sets_source_path_not_blank check (btrim(source_path) <> '')
);

create unique index if not exists calculation_rule_sets_rule_type_id_version_idx
  on public.calculation_rule_sets (rule_type, rule_set_id, version);

create table if not exists public.calculation_audit_snapshots (
  id uuid primary key default gen_random_uuid(),
  metric_record_id uuid references public.production_line_daily_metrics (id) on delete cascade,
  incentive_record_id uuid references public.incentive_records (id) on delete cascade,
  input_payload jsonb not null,
  output_payload jsonb not null,
  warnings jsonb not null default '[]'::jsonb,
  formula_rule_set_id text,
  formula_rule_version integer,
  incentive_rule_set_id text,
  incentive_rule_version integer,
  created_at timestamptz not null default now()
);

alter table public.production_line_daily_metrics
  drop constraint if exists production_line_daily_metrics_unique;

alter table public.production_line_daily_metrics
  add column if not exists production_date date,
  add column if not exists line_code text,
  add column if not exists shift_code text,
  add column if not exists planned_mo numeric,
  add column if not exists planned_hel numeric,
  add column if not exists actual_mo numeric,
  add column if not exists actual_hel numeric,
  add column if not exists team_members numeric,
  add column if not exists working_hours numeric,
  add column if not exists smv numeric,
  add column if not exists planned_pcs numeric,
  add column if not exists forecast_pcs numeric,
  add column if not exists actual_pcs numeric,
  add column if not exists planned_cadre_total numeric,
  add column if not exists actual_cadre_total numeric,
  add column if not exists clock_hours numeric,
  add column if not exists planned_sah numeric,
  add column if not exists planned_efficiency numeric,
  add column if not exists forecast_sah numeric,
  add column if not exists forecast_efficiency numeric,
  add column if not exists actual_sah numeric,
  add column if not exists actual_efficiency numeric,
  add column if not exists piece_variance numeric,
  add column if not exists sah_variance numeric,
  add column if not exists warnings jsonb not null default '[]'::jsonb,
  add column if not exists formula_rule_set_id text,
  add column if not exists formula_rule_version integer,
  add column if not exists remarks text,
  add column if not exists lost_time_minutes numeric,
  add column if not exists source_metadata jsonb not null default '{}'::jsonb;

update public.production_line_daily_metrics metrics
set
  production_date = coalesce(metrics.production_date, metrics.metric_date),
  line_code = coalesce(metrics.line_code, lines.code)
from public.production_lines lines
where metrics.production_line_id = lines.id;

alter table public.production_line_daily_metrics
  alter column production_date set not null,
  alter column line_code set not null;

create unique index if not exists production_line_daily_metrics_line_day_shift_idx
  on public.production_line_daily_metrics (production_line_id, production_date, coalesce(shift_code, ''));

create index if not exists production_line_daily_metrics_line_code_date_idx
  on public.production_line_daily_metrics (line_code, production_date desc);

alter table public.incentive_records
  alter column employee_id drop not null;

alter table public.incentive_records
  add column if not exists production_line_id uuid references public.production_lines (id) on delete set null,
  add column if not exists production_date date,
  add column if not exists line_code text,
  add column if not exists shift_code text,
  add column if not exists basis_metric text not null default 'actual_efficiency',
  add column if not exists basis_value numeric,
  add column if not exists actual_efficiency numeric,
  add column if not exists incentive_band_label text,
  add column if not exists incentive_amount numeric not null default 0,
  add column if not exists incentive_rule_set_id text,
  add column if not exists incentive_rule_version integer,
  add column if not exists warnings jsonb not null default '[]'::jsonb,
  add column if not exists source_metric_record_id uuid references public.production_line_daily_metrics (id) on delete set null;

update public.incentive_records
set incentive_amount = coalesce(incentive_amount, amount, 0);

create unique index if not exists incentive_records_source_metric_record_unique
  on public.incentive_records (source_metric_record_id)
  where source_metric_record_id is not null;

create index if not exists incentive_records_line_code_date_idx
  on public.incentive_records (line_code, production_date desc);

create index if not exists calculation_audit_snapshots_metric_created_idx
  on public.calculation_audit_snapshots (metric_record_id, created_at desc);

create index if not exists calculation_audit_snapshots_incentive_created_idx
  on public.calculation_audit_snapshots (incentive_record_id, created_at desc);

alter table public.calculation_rule_sets enable row level security;
alter table public.calculation_audit_snapshots enable row level security;

grant select, insert, update, delete on public.calculation_rule_sets to authenticated;
grant select, insert, update, delete on public.calculation_audit_snapshots to authenticated;

drop policy if exists "calculation_rule_sets_read_authenticated" on public.calculation_rule_sets;
create policy "calculation_rule_sets_read_authenticated"
on public.calculation_rule_sets
for select
to authenticated
using (auth.uid() is not null);

drop policy if exists "calculation_rule_sets_write_admin" on public.calculation_rule_sets;
create policy "calculation_rule_sets_write_admin"
on public.calculation_rule_sets
for all
to authenticated
using (public.has_role(array['admin']))
with check (public.has_role(array['admin']));

drop policy if exists "calculation_audit_snapshots_read_admin" on public.calculation_audit_snapshots;
create policy "calculation_audit_snapshots_read_admin"
on public.calculation_audit_snapshots
for select
to authenticated
using (public.has_role(array['admin']));

drop policy if exists "calculation_audit_snapshots_write_admin" on public.calculation_audit_snapshots;
create policy "calculation_audit_snapshots_write_admin"
on public.calculation_audit_snapshots
for all
to authenticated
using (public.has_role(array['admin']))
with check (public.has_role(array['admin']));

commit;

