-- Run only against an isolated local database that has the leave migration.
-- Synthetic rows roll back; no employee or auth data is retained.
begin;
-- Exercise RLS even when this isolated database has revoked direct schema usage.
grant usage on schema public to authenticated;
grant select on public.profiles to authenticated;

insert into public.employees (id, employee_code, employee_category)
values ('11111111-1111-4111-8111-111111111111', 'LEAVE-SIM-001', 'permanent');
insert into auth.users (id) values
  ('22222222-2222-4222-8222-222222222222'),
  ('33333333-3333-4333-8333-333333333333'),
  ('44444444-4444-4444-8444-444444444444'),
  ('88888888-8888-4888-8888-888888888888');
update public.profiles p
set full_name = v.full_name, role = v.role, employee_id = v.employee_id
from (values
  ('22222222-2222-4222-8222-222222222222'::uuid, 'Sim HR', 'hr', null::uuid),
  ('33333333-3333-4333-8333-333333333333'::uuid, 'Sim Manager', 'supervisor', null::uuid),
  ('44444444-4444-4444-8444-444444444444'::uuid, 'Sim Employee', 'viewer', '11111111-1111-4111-8111-111111111111'::uuid),
  ('88888888-8888-4888-8888-888888888888'::uuid, 'Other Manager', 'supervisor', null::uuid)
) v(id, full_name, role, employee_id)
where p.id = v.id;
insert into public.employee_leave_policies
  (policy_year, employee_category, leave_category, entitlement_days, approver_role)
values (2026, 'permanent', 'annual', 1, 'assigned_manager');
insert into public.employee_leave_approvers (employee_id, approver_user_id)
values ('11111111-1111-4111-8111-111111111111', '33333333-3333-4333-8333-333333333333');
insert into public.employee_leave_requests
  (id, employee_id, leave_type, leave_category, start_date, end_date, status, requested_by)
values
  ('55555555-5555-4555-8555-555555555555', '11111111-1111-4111-8111-111111111111',
   'full_day', 'annual', '2026-10-01', '2026-10-01', 'pending', '44444444-4444-4444-8444-444444444444'),
  ('66666666-6666-4666-8666-666666666666', '11111111-1111-4111-8111-111111111111',
   'full_day', 'annual', '2026-10-02', '2026-10-03', 'pending', '44444444-4444-4444-8444-444444444444'),
  ('77777777-7777-4777-8777-777777777777', '11111111-1111-4111-8111-111111111111',
   'full_day', 'casual', '2026-10-04', '2026-10-04', 'pending', null);

do $$
begin
  if has_table_privilege('authenticated', 'public.employee_leave_requests', 'UPDATE') then
    raise exception 'Authenticated users can bypass the review RPC';
  end if;
  begin
    perform public.review_employee_leave_request('55555555-5555-4555-8555-555555555555',
      '22222222-2222-4222-8222-222222222222', 'approved', null);
    raise exception 'HR bypassed assigned-manager policy';
  exception when insufficient_privilege then null;
  end;
  perform public.review_employee_leave_request('55555555-5555-4555-8555-555555555555',
    '33333333-3333-4333-8333-333333333333', 'approved', null);
  begin
    perform public.review_employee_leave_request('66666666-6666-4666-8666-666666666666',
      '33333333-3333-4333-8333-333333333333', 'approved', null);
    raise exception 'Approval exceeded entitlement';
  exception when invalid_parameter_value then null;
  end;
  begin
    perform public.review_employee_leave_request('77777777-7777-4777-8777-777777777777',
      '33333333-3333-4333-8333-333333333333', 'approved', null);
    raise exception 'Approval without a configured policy succeeded';
  exception when invalid_parameter_value then null;
  end;
  if (select status from public.employee_leave_requests
      where id='66666666-6666-4666-8666-666666666666') <> 'pending' then
    raise exception 'Over-entitlement request did not remain pending';
  end if;
  if (select count(*) from public.employee_leave_events
      where request_id='55555555-5555-4555-8555-555555555555') <> 2 then
    raise exception 'Audit events missing';
  end if;
  if (select actor_employee_id from public.employee_leave_events
      where request_id='77777777-7777-4777-8777-777777777777')
      <> '11111111-1111-4111-8111-111111111111'::uuid then
    raise exception 'Employee portal actor missing from request audit';
  end if;
end;
$$;

select set_config('request.jwt.claim.sub', '88888888-8888-4888-8888-888888888888', true);
set local role authenticated;
do $$
begin
  if (select count(*) from public.employee_leave_requests) <> 0
      or (select count(*) from public.employee_leave_approvers) <> 0
      or (select count(*) from public.employee_leave_events) <> 0 then
    raise exception 'Unassigned manager can read another employee leave data';
  end if;
end;
$$;
reset role;

rollback;
