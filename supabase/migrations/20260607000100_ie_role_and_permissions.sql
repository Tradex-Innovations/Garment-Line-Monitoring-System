begin;

alter table public.profiles
  drop constraint if exists profiles_role_check;

alter table public.profiles
  add constraint profiles_role_check
  check (role in ('admin', 'supervisor', 'hr', 'ie', 'viewer'));

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
  v_employee_code text;
  v_employee_id uuid;
begin
  v_role := case
    when coalesce(new.raw_user_meta_data ->> 'role', '') in ('admin', 'supervisor', 'hr', 'ie', 'viewer')
      then new.raw_user_meta_data ->> 'role'
    else 'viewer'
  end;

  v_employee_code := nullif(btrim(coalesce(new.raw_user_meta_data ->> 'employee_code', '')), '');

  if v_employee_code is not null then
    select employees.id
    into v_employee_id
    from public.employees
    where employees.employee_code = v_employee_code
    limit 1;
  end if;

  insert into public.profiles (id, full_name, role, employee_code, employee_id)
  values (
    new.id,
    coalesce(
      nullif(new.raw_user_meta_data ->> 'full_name', ''),
      nullif(new.raw_user_meta_data ->> 'name', ''),
      split_part(coalesce(new.email, ''), '@', 1)
    ),
    v_role,
    v_employee_code,
    v_employee_id
  )
  on conflict (id) do nothing;

  return new;
end;
$$;

drop policy if exists "profiles_select_self_or_operational_roles" on public.profiles;
create policy "profiles_select_self_or_operational_roles"
on public.profiles
for select
to authenticated
using (auth.uid() = id or public.has_role(array['admin', 'hr', 'supervisor', 'ie']));

drop policy if exists "face_events_read_admin_hr_supervisor" on public.face_events;
create policy "face_events_read_admin_hr_supervisor"
on public.face_events
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'ie']));

drop policy if exists "face_daily_summary_read_admin_hr_supervisor" on public.face_daily_summary;
create policy "face_daily_summary_read_admin_hr_supervisor"
on public.face_daily_summary
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'ie']));

drop policy if exists "fingerprint_daily_attendance_read_admin_hr_supervisor" on public.fingerprint_daily_attendance;
create policy "fingerprint_daily_attendance_read_admin_hr_supervisor"
on public.fingerprint_daily_attendance
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'ie']));

drop policy if exists "attendance_reconciliation_read_authenticated" on public.attendance_reconciliation;
create policy "attendance_reconciliation_read_authenticated"
on public.attendance_reconciliation
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'ie', 'viewer']));

drop policy if exists "reconciliation_notes_read_admin_hr_supervisor" on public.reconciliation_notes;
create policy "reconciliation_notes_read_admin_hr_supervisor"
on public.reconciliation_notes
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'ie']));

drop policy if exists "employee_notes_read_admin_hr_supervisor" on public.employee_notes;
create policy "employee_notes_read_admin_hr_supervisor"
on public.employee_notes
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'ie']));

drop policy if exists "skill_operations_write_admin_supervisor_hr" on public.skill_operations;
create policy "skill_operations_write_admin_supervisor_hr"
on public.skill_operations
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'hr', 'ie']));

drop policy if exists "production_line_operations_write_admin_supervisor" on public.production_line_operations;
create policy "production_line_operations_write_admin_supervisor"
on public.production_line_operations
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'ie']));

drop policy if exists "employee_operation_skills_write_admin_supervisor_hr" on public.employee_operation_skills;
create policy "employee_operation_skills_write_admin_supervisor_hr"
on public.employee_operation_skills
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'hr', 'ie']));

drop policy if exists "line_position_assignments_write_admin_supervisor" on public.line_position_assignments;
create policy "line_position_assignments_write_admin_supervisor"
on public.line_position_assignments
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'ie']));

drop policy if exists "style_operation_plans_write_planners" on public.style_operation_plans;
create policy "style_operation_plans_write_planners"
on public.style_operation_plans
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'hr', 'ie']));

drop policy if exists "style_operation_plan_machines_write_planners" on public.style_operation_plan_machines;
create policy "style_operation_plan_machines_write_planners"
on public.style_operation_plan_machines
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'hr', 'ie']));

drop policy if exists "line_style_schedules_write_planners" on public.line_style_schedules;
create policy "line_style_schedules_write_planners"
on public.line_style_schedules
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'hr', 'ie']));

commit;
