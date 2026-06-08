begin;

with line_inputs as (
  select
    lines.id as production_line_id,
    lines.code as line_code,
    lines.name as line_name,
    current_date as production_date,
    lines.shift_name as shift_code,
    greatest(lines.target_manpower - 2, 0)::numeric as planned_mo,
    least(lines.target_manpower, 2)::numeric as planned_hel,
    greatest(lines.target_manpower - 2, 0)::numeric as actual_mo,
    1::numeric as actual_hel,
    1::numeric as team_members,
    8::numeric as working_hours,
    lines.target_output::numeric as planned_pcs,
    lines.current_output::numeric as forecast_pcs,
    lines.current_output::numeric as actual_pcs,
    lines.target_manpower::numeric as planned_cadre_total,
    lines.target_manpower::numeric as actual_cadre_total,
    (8 * lines.target_manpower)::numeric as clock_hours,
    (lines.current_efficiency / 100.0)::numeric as actual_efficiency,
    case
      when lines.current_output > 0 and lines.target_manpower > 0 then
        (((lines.current_efficiency / 100.0) * (8 * lines.target_manpower)) * 60.0 / lines.current_output)::numeric
      else 0::numeric
    end as smv
  from public.production_lines lines
  where lines.is_active = true
    and lines.code like 'LINE-%'
),
calculated as (
  select
    input.*,
    (input.planned_pcs * input.smv / 60.0)::numeric as planned_sah,
    case
      when input.planned_cadre_total * input.working_hours = 0 then 0::numeric
      else (input.planned_pcs * input.smv / 60.0) / (input.planned_cadre_total * input.working_hours)
    end as planned_efficiency,
    (input.forecast_pcs * input.smv / 60.0)::numeric as forecast_sah,
    case
      when input.clock_hours = 0 then 0::numeric
      else (input.forecast_pcs * input.smv / 60.0) / input.clock_hours
    end as forecast_efficiency,
    (input.actual_pcs * input.smv / 60.0)::numeric as actual_sah,
    (input.actual_pcs - input.forecast_pcs)::numeric as piece_variance,
    ((input.actual_pcs * input.smv / 60.0) - (input.forecast_pcs * input.smv / 60.0))::numeric as sah_variance,
    case
      when input.current_output_is_empty then '["NO_OUTPUT"]'::jsonb
      else '[]'::jsonb
    end as warnings
  from (
    select
      line_inputs.*,
      line_inputs.actual_pcs <= 0 as current_output_is_empty
    from line_inputs
  ) input
),
updated_metrics as (
  update public.production_line_daily_metrics metrics
  set
    metric_date = calculated.production_date,
    line_code = calculated.line_code,
    shift_code = calculated.shift_code,
    output = calculated.actual_pcs::integer,
    target_output = calculated.planned_pcs::integer,
    efficiency = round(calculated.actual_efficiency * 100.0, 2),
    planned_mo = calculated.planned_mo,
    planned_hel = calculated.planned_hel,
    actual_mo = calculated.actual_mo,
    actual_hel = calculated.actual_hel,
    team_members = calculated.team_members,
    working_hours = calculated.working_hours,
    smv = calculated.smv,
    planned_pcs = calculated.planned_pcs,
    forecast_pcs = calculated.forecast_pcs,
    actual_pcs = calculated.actual_pcs,
    planned_cadre_total = calculated.planned_cadre_total,
    actual_cadre_total = calculated.actual_cadre_total,
    clock_hours = calculated.clock_hours,
    planned_sah = calculated.planned_sah,
    planned_efficiency = calculated.planned_efficiency,
    forecast_sah = calculated.forecast_sah,
    forecast_efficiency = calculated.forecast_efficiency,
    actual_sah = calculated.actual_sah,
    actual_efficiency = calculated.actual_efficiency,
    piece_variance = calculated.piece_variance,
    sah_variance = calculated.sah_variance,
    warnings = calculated.warnings,
    formula_rule_set_id = 'efficiency-v1',
    formula_rule_version = 1,
    remarks = 'Seeded from workbook production line roster',
    lost_time_minutes = 0,
    source_metadata = jsonb_build_object('source', 'workbook_line_seed', 'lineName', calculated.line_name),
    updated_at = now()
  from calculated
  where metrics.production_line_id = calculated.production_line_id
    and metrics.production_date = calculated.production_date
    and coalesce(metrics.shift_code, '') = coalesce(calculated.shift_code, '')
  returning metrics.id
),
inserted_metrics as (
  insert into public.production_line_daily_metrics (
    production_line_id,
    metric_date,
    production_date,
    line_code,
    shift_code,
    output,
    target_output,
    efficiency,
    planned_mo,
    planned_hel,
    actual_mo,
    actual_hel,
    team_members,
    working_hours,
    smv,
    planned_pcs,
    forecast_pcs,
    actual_pcs,
    planned_cadre_total,
    actual_cadre_total,
    clock_hours,
    planned_sah,
    planned_efficiency,
    forecast_sah,
    forecast_efficiency,
    actual_sah,
    actual_efficiency,
    piece_variance,
    sah_variance,
    warnings,
    formula_rule_set_id,
    formula_rule_version,
    remarks,
    lost_time_minutes,
    source_metadata
  )
  select
    calculated.production_line_id,
    calculated.production_date,
    calculated.production_date,
    calculated.line_code,
    calculated.shift_code,
    calculated.actual_pcs::integer,
    calculated.planned_pcs::integer,
    round(calculated.actual_efficiency * 100.0, 2),
    calculated.planned_mo,
    calculated.planned_hel,
    calculated.actual_mo,
    calculated.actual_hel,
    calculated.team_members,
    calculated.working_hours,
    calculated.smv,
    calculated.planned_pcs,
    calculated.forecast_pcs,
    calculated.actual_pcs,
    calculated.planned_cadre_total,
    calculated.actual_cadre_total,
    calculated.clock_hours,
    calculated.planned_sah,
    calculated.planned_efficiency,
    calculated.forecast_sah,
    calculated.forecast_efficiency,
    calculated.actual_sah,
    calculated.actual_efficiency,
    calculated.piece_variance,
    calculated.sah_variance,
    calculated.warnings,
    'efficiency-v1',
    1,
    'Seeded from workbook production line roster',
    0,
    jsonb_build_object('source', 'workbook_line_seed', 'lineName', calculated.line_name)
  from calculated
  where not exists (
    select 1
    from public.production_line_daily_metrics metrics
    where metrics.production_line_id = calculated.production_line_id
      and metrics.production_date = calculated.production_date
      and coalesce(metrics.shift_code, '') = coalesce(calculated.shift_code, '')
  )
  returning id
),
seed_metrics as (
  select
    metrics.*,
    case
      when metrics.actual_efficiency >= 1.05 then '20th - 24th Day Final'
      when metrics.actual_efficiency >= 1.00 then '20th - 24th Day'
      when metrics.actual_efficiency >= 0.95 then '16th - 19th Day'
      when metrics.actual_efficiency >= 0.90 then '13th - 15th Day'
      when metrics.actual_efficiency >= 0.85 then '10th - 12th Day'
      when metrics.actual_efficiency >= 0.80 then '08th - 09th day'
      when metrics.actual_efficiency >= 0.75 then '07th Day'
      when metrics.actual_efficiency >= 0.70 then '06th Day'
      when metrics.actual_efficiency >= 0.65 then '04th Day - 05th Day'
      when metrics.actual_efficiency >= 0.55 then '03rd Day'
      when metrics.actual_efficiency >= 0.45 then '02nd Day'
      when metrics.actual_efficiency >= 0.35 then '01st Day'
      else null
    end as incentive_band_label,
    case
      when metrics.actual_efficiency >= 1.05 then 310
      when metrics.actual_efficiency >= 1.00 then 300
      when metrics.actual_efficiency >= 0.95 then 280
      when metrics.actual_efficiency >= 0.90 then 260
      when metrics.actual_efficiency >= 0.85 then 240
      when metrics.actual_efficiency >= 0.80 then 220
      when metrics.actual_efficiency >= 0.75 then 200
      when metrics.actual_efficiency >= 0.70 then 180
      when metrics.actual_efficiency >= 0.65 then 160
      when metrics.actual_efficiency >= 0.55 then 140
      when metrics.actual_efficiency >= 0.45 then 120
      when metrics.actual_efficiency >= 0.35 then 100
      else 0
    end::numeric as incentive_amount
  from public.production_line_daily_metrics metrics
  join calculated
    on calculated.production_line_id = metrics.production_line_id
   and calculated.production_date = metrics.production_date
   and coalesce(calculated.shift_code, '') = coalesce(metrics.shift_code, '')
)
insert into public.incentive_records (
  employee_id,
  month_start,
  amount,
  reason,
  production_line_id,
  production_date,
  line_code,
  shift_code,
  basis_metric,
  basis_value,
  actual_efficiency,
  incentive_band_label,
  incentive_amount,
  incentive_rule_set_id,
  incentive_rule_version,
  warnings,
  source_metric_record_id
)
select
  null,
  date_trunc('month', seed_metrics.production_date)::date,
  seed_metrics.incentive_amount,
  coalesce(seed_metrics.incentive_band_label, 'line_efficiency'),
  seed_metrics.production_line_id,
  seed_metrics.production_date,
  seed_metrics.line_code,
  seed_metrics.shift_code,
  'actual_efficiency',
  seed_metrics.actual_efficiency,
  seed_metrics.actual_efficiency,
  seed_metrics.incentive_band_label,
  seed_metrics.incentive_amount,
  'incentive-ladder-v1',
  1,
  seed_metrics.warnings,
  seed_metrics.id
from seed_metrics
where not exists (
  select 1
  from public.incentive_records incentives
  where incentives.source_metric_record_id = seed_metrics.id
);

insert into public.calculation_audit_snapshots (
  metric_record_id,
  incentive_record_id,
  input_payload,
  output_payload,
  warnings,
  formula_rule_set_id,
  formula_rule_version,
  incentive_rule_set_id,
  incentive_rule_version
)
select
  metrics.id,
  incentives.id,
  jsonb_build_object(
    'productionLineId', metrics.production_line_id,
    'productionDate', metrics.production_date,
    'shiftCode', metrics.shift_code,
    'plannedMo', metrics.planned_mo,
    'plannedHel', metrics.planned_hel,
    'actualMo', metrics.actual_mo,
    'actualHel', metrics.actual_hel,
    'teamMembers', metrics.team_members,
    'workingHours', metrics.working_hours,
    'smv', metrics.smv,
    'plannedPcs', metrics.planned_pcs,
    'forecastPcs', metrics.forecast_pcs,
    'actualPcs', metrics.actual_pcs,
    'remarks', metrics.remarks,
    'lostTimeMinutes', metrics.lost_time_minutes,
    'sourceMetadata', metrics.source_metadata
  ),
  jsonb_build_object(
    'plannedCadreTotal', metrics.planned_cadre_total,
    'actualCadreTotal', metrics.actual_cadre_total,
    'clockHours', metrics.clock_hours,
    'plannedSah', metrics.planned_sah,
    'plannedEfficiency', metrics.planned_efficiency,
    'forecastSah', metrics.forecast_sah,
    'forecastEfficiency', metrics.forecast_efficiency,
    'actualSah', metrics.actual_sah,
    'actualEfficiency', metrics.actual_efficiency,
    'pieceVariance', metrics.piece_variance,
    'sahVariance', metrics.sah_variance,
    'incentiveAmount', incentives.incentive_amount,
    'incentiveBand', incentives.incentive_band_label
  ),
  metrics.warnings,
  metrics.formula_rule_set_id,
  metrics.formula_rule_version,
  incentives.incentive_rule_set_id,
  incentives.incentive_rule_version
from public.production_line_daily_metrics metrics
join public.incentive_records incentives
  on incentives.source_metric_record_id = metrics.id
where metrics.source_metadata ->> 'source' = 'workbook_line_seed'
  and not exists (
    select 1
    from public.calculation_audit_snapshots audit
    where audit.metric_record_id = metrics.id
  );

commit;
