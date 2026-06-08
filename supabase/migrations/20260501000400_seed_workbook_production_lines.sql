begin;

alter table public.production_lines
  add column if not exists allocated_style text;

update public.production_lines
set is_active = false,
    allocated_style = coalesce(allocated_style, '412129')
where code in ('LINE-20', 'L-20')
   or name in ('Line 20', 'Line 20 Placeholder');

insert into public.production_lines (
  code,
  name,
  department_name,
  shift_name,
  target_manpower,
  target_output,
  current_output,
  current_efficiency,
  allocated_style,
  is_active
)
values
  ('LINE-01', 'Line 01', 'Production', 'Shift A', 16, 350, 350, 45.14, '232109', true),
  ('LINE-02', 'Line 02', 'Production', 'Shift A', 28, 785, 810, 93.33, '231334', true),
  ('LINE-03', 'Line 03', 'Production', 'Shift A', 22, 575, 590, 70.35, '411444', true),
  ('LINE-04', 'Line 04', 'Production', 'Shift A', 30, 715, 715, 85.33, '810755', true),
  ('LINE-05', 'Line 05', 'Production', 'Shift A', 28, 870, 900, 83.02, '231337', true),
  ('LINE-06', 'Line 06', 'Production', 'Shift A', 24, 595, 595, 65.11, '211505', true),
  ('LINE-07', 'Line 07', 'Production', 'Shift A', 13, 405, 405, 45.00, '231329', true),
  ('LINE-08', 'Line 08', 'Production', 'Shift A', 30, 575, 450, 66.37, '411010', true),
  ('LINE-09', 'Line 09', 'Production', 'Shift A', 24, 515, 585, 74.29, '232008', true),
  ('LINE-10', 'Line 10', 'Production', 'Shift A', 28, 365, 405, 60.00, '232007', true),
  ('LINE-11', 'Line 11', 'Production', 'Shift A', 21, 325, 325, 45.14, '232078', true),
  ('LINE-12', 'Line 12', 'Production', 'Shift A', 20, 757, 575, 75.42, '411438', true),
  ('LINE-13', 'Line 13', 'Production', 'Shift A', 13, 471, 270, 41.05, '412086', true),
  ('LINE-14', 'Line 14', 'Production', 'Shift A', 20, 231, 231, 32.08, '232009', true),
  ('LINE-15', 'Line 15', 'Production', 'Shift A', 22, 540, 540, 55.21, '411445', true),
  ('LINE-16', 'Line 16', 'Production', 'Shift A', 17, 600, 640, 80.03, '810434', true),
  ('LINE-17', 'Line 17', 'Production', 'Shift A', 21, 615, 615, 66.26, '232084', true),
  ('LINE-18', 'Line 18', 'Production', 'Shift A', 21, 645, 750, 94.59, '810750', true),
  ('LINE-19', 'Line 19', 'Production', 'Shift A', 29, 716, 400, 53.70, '810752', true)
on conflict (code) do update
set
  name = excluded.name,
  department_name = excluded.department_name,
  shift_name = excluded.shift_name,
  target_manpower = excluded.target_manpower,
  target_output = excluded.target_output,
  current_output = excluded.current_output,
  current_efficiency = excluded.current_efficiency,
  allocated_style = excluded.allocated_style,
  is_active = excluded.is_active,
  updated_at = now();

commit;
