begin;

-- The original prototype limited employee profiles to Shift A or Shift B.
-- Real payroll shift codes are now validated through work_shifts.shift_id.
alter table public.employee_profiles
  drop constraint if exists employee_profiles_shift_name_check;

update public.employee_profiles profile
set shift_id = shift.id
from public.work_shifts shift
where profile.shift_id is null
  and lower(shift.name) = lower(profile.shift_name);

notify pgrst, 'reload schema';

commit;
