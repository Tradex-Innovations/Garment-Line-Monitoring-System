begin;

drop policy if exists "skill_operations_select_planners" on public.skill_operations;
create policy "skill_operations_select_planners"
on public.skill_operations
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor']));

drop policy if exists "skill_operations_write_admin_supervisor_hr" on public.skill_operations;
create policy "skill_operations_write_admin_supervisor_hr"
on public.skill_operations
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "production_line_operations_write_admin_supervisor" on public.production_line_operations;
create policy "production_line_operations_write_admin_supervisor"
on public.production_line_operations
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

drop policy if exists "employee_operation_skills_write_admin_supervisor_hr" on public.employee_operation_skills;
create policy "employee_operation_skills_write_admin_supervisor_hr"
on public.employee_operation_skills
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "line_position_assignments_write_admin_supervisor" on public.line_position_assignments;
create policy "line_position_assignments_write_admin_supervisor"
on public.line_position_assignments
for all
to authenticated
using (public.has_role(array['admin', 'supervisor']))
with check (public.has_role(array['admin', 'supervisor']));

drop policy if exists "style_operation_plans_write_planners" on public.style_operation_plans;
create policy "style_operation_plans_write_planners"
on public.style_operation_plans
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "style_operation_plan_machines_write_planners" on public.style_operation_plan_machines;
create policy "style_operation_plan_machines_write_planners"
on public.style_operation_plan_machines
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "line_style_schedules_write_planners" on public.line_style_schedules;
create policy "line_style_schedules_write_planners"
on public.line_style_schedules
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'hr']))
with check (public.has_role(array['admin', 'supervisor', 'hr']));

drop policy if exists "hikvision_face_events_select_operational_roles" on public.hikvision_face_events;
create policy "hikvision_face_events_select_operational_roles"
on public.hikvision_face_events
for select
to authenticated
using (public.has_role(array['admin', 'hr', 'supervisor', 'ie', 'viewer']));

drop policy if exists "hikvision_face_events_manage_operational_roles" on public.hikvision_face_events;
create policy "hikvision_face_events_manage_operational_roles"
on public.hikvision_face_events
for all
to authenticated
using (public.has_role(array['admin', 'supervisor', 'ie']))
with check (public.has_role(array['admin', 'supervisor', 'ie']));

commit;
