begin;

with seed_metrics as (
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
  where metrics.source_metadata ->> 'source' = 'workbook_line_seed'
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
