begin;

-- No entitlement or approver is seeded: Union North must enter its approved rules.
create table public.employee_leave_policies (
  id uuid primary key default gen_random_uuid(),
  policy_year integer not null check (policy_year between 2000 and 2100),
  employee_category text not null check (employee_category in ('permanent', 'new_joiner', 'intern')),
  leave_category text not null check (leave_category in ('annual', 'casual', 'sick', 'emergency', 'personal', 'medical', 'other')),
  entitlement_days numeric(7,2) not null check (entitlement_days >= 0),
  approver_role text not null check (approver_role in ('hr', 'assigned_manager')),
  created_by uuid references public.profiles(id),
  updated_by uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (policy_year, employee_category, leave_category)
);

create trigger set_employee_leave_policies_updated_at
before update on public.employee_leave_policies
for each row execute function public.touch_updated_at();

create table public.employee_leave_approvers (
  employee_id uuid primary key references public.employees(id) on delete cascade,
  approver_user_id uuid not null references public.profiles(id),
  assigned_by uuid references public.profiles(id),
  assigned_at timestamptz not null default now(),
  constraint employee_leave_approver_not_self check (employee_id <> approver_user_id)
);
create index employee_leave_approvers_user_idx
  on public.employee_leave_approvers(approver_user_id);

create table public.employee_leave_events (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.employee_leave_requests(id) on delete cascade,
  actor_user_id uuid references public.profiles(id),
  actor_employee_id uuid references public.employees(id),
  action text not null check (action in ('requested', 'approved', 'rejected', 'cancelled')),
  previous_status text,
  new_status text not null,
  note text,
  created_at timestamptz not null default now()
);
create index employee_leave_events_request_idx
  on public.employee_leave_events(request_id, created_at desc);

create or replace function public.record_employee_leave_event()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    insert into public.employee_leave_events
      (request_id, actor_user_id, actor_employee_id, action, previous_status, new_status)
    values (new.id, new.requested_by,
      case when new.requested_by is null then new.employee_id else null end,
      'requested', null, new.status);
  elsif new.status is distinct from old.status then
    insert into public.employee_leave_events
      (request_id, actor_user_id, actor_employee_id, action, previous_status, new_status, note)
    values (new.id, new.reviewed_by,
      case when new.status = 'cancelled' then new.employee_id else null end,
      new.status, old.status, new.status, new.review_note);
  end if;
  return new;
end;
$$;
create trigger employee_leave_event_after_insert
after insert on public.employee_leave_requests
for each row execute function public.record_employee_leave_event();
create trigger employee_leave_event_after_status
after update of status on public.employee_leave_requests
for each row execute function public.record_employee_leave_event();

-- The backend calls this with its service credential after checking the caller's JWT.
-- A single employee lock protects entitlement checks from concurrent approvals.
create or replace function public.review_employee_leave_request(
  p_request_id uuid, p_actor uuid, p_status text, p_note text default null
)
returns public.employee_leave_requests
language plpgsql security definer set search_path = '' as $$
declare
  v_request public.employee_leave_requests%rowtype;
  v_role text;
  v_category text;
  v_policy public.employee_leave_policies%rowtype;
  v_year integer;
  v_requested numeric;
  v_used numeric;
  v_year_start date;
  v_year_end date;
begin
  if p_status not in ('approved', 'rejected') then
    raise exception 'Only approval or rejection is allowed' using errcode = '22023';
  end if;
  select role into v_role from public.profiles where id = p_actor and is_active;
  if v_role is null or v_role not in ('admin', 'hr', 'supervisor') then
    raise exception 'Active HR or assigned manager access is required' using errcode = '42501';
  end if;
  select * into v_request from public.employee_leave_requests
    where id = p_request_id for update;
  if not found or v_request.status <> 'pending' then
    raise exception 'Only a pending leave request can be reviewed' using errcode = '22023';
  end if;
  if exists (select 1 from public.profiles
             where id = p_actor and employee_id = v_request.employee_id) then
    raise exception 'Employees cannot review their own leave request' using errcode = '42501';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_request.employee_id::text, 0));
  select employee_category into v_category from public.employees
    where id = v_request.employee_id and is_active;
  if v_category is null then
    raise exception 'Employee is inactive or missing' using errcode = '22023';
  end if;

  if p_status = 'approved' then
    if exists (
      select 1 from public.employee_leave_requests other
      where other.employee_id = v_request.employee_id
        and other.id <> v_request.id and other.status = 'approved'
        and other.start_date <= v_request.end_date
        and other.end_date >= v_request.start_date
    ) then
      raise exception 'Approved leave already overlaps these dates' using errcode = '22023';
    end if;
    if v_request.leave_category <> 'no_pay' then
      for v_year in select generate_series(extract(year from v_request.start_date)::integer,
                                           extract(year from v_request.end_date)::integer) loop
        select * into v_policy from public.employee_leave_policies
          where policy_year = v_year and employee_category = v_category
            and leave_category = v_request.leave_category;
        if not found then
          raise exception 'Configure an approved leave policy for every year before approval'
            using errcode = '22023';
        end if;
        if v_role = 'supervisor' then
          if v_policy.approver_role <> 'assigned_manager' or not exists (
            select 1 from public.employee_leave_approvers
            where employee_id = v_request.employee_id and approver_user_id = p_actor
          ) then
            raise exception 'This manager is not assigned to approve this employee'
              using errcode = '42501';
          end if;
        elsif v_policy.approver_role = 'assigned_manager' then
          raise exception 'The assigned manager must approve this leave category'
            using errcode = '42501';
        end if;
        v_year_start := make_date(v_year, 1, 1);
        v_year_end := make_date(v_year, 12, 31);
        v_requested := case v_request.leave_type
          when 'short_leave' then 0
          when 'half_day' then 0.5
          else least(v_request.end_date, v_year_end) - greatest(v_request.start_date, v_year_start) + 1
        end;
        select coalesce(sum(case prior.leave_type
          when 'short_leave' then 0
          when 'half_day' then 0.5
          else least(prior.end_date, v_year_end) - greatest(prior.start_date, v_year_start) + 1
        end), 0) into v_used
        from public.employee_leave_requests prior
        where prior.employee_id = v_request.employee_id
          and prior.status = 'approved' and prior.leave_category = v_request.leave_category
          and prior.start_date <= v_year_end and prior.end_date >= v_year_start;
        if v_used + v_requested > v_policy.entitlement_days then
          raise exception 'Leave entitlement would be exceeded' using errcode = '22023';
        end if;
      end loop;
    elsif v_role = 'supervisor' and not exists (
      select 1 from public.employee_leave_approvers
      where employee_id = v_request.employee_id and approver_user_id = p_actor
    ) then
      raise exception 'This manager is not assigned to approve this employee'
        using errcode = '42501';
    end if;
  elsif v_role = 'supervisor' and not exists (
    select 1 from public.employee_leave_approvers
    where employee_id = v_request.employee_id and approver_user_id = p_actor
  ) then
    raise exception 'This manager is not assigned to review this employee'
      using errcode = '42501';
  end if;

  update public.employee_leave_requests
  set status = p_status, reviewed_by = p_actor, reviewed_at = now(),
      review_note = nullif(btrim(p_note), '')
  where id = p_request_id returning * into v_request;
  return v_request;
end;
$$;

revoke all on function public.review_employee_leave_request(uuid, uuid, text, text) from public, anon, authenticated;
grant execute on function public.review_employee_leave_request(uuid, uuid, text, text) to service_role;

alter table public.employee_leave_policies enable row level security;
alter table public.employee_leave_approvers enable row level security;
alter table public.employee_leave_events enable row level security;
grant select on public.employee_leave_policies to authenticated;
grant select on public.employee_leave_approvers to authenticated;
grant select on public.employee_leave_events to authenticated;
create policy employee_leave_policies_read on public.employee_leave_policies
  for select to authenticated using (public.has_role(array['admin', 'hr', 'supervisor']));
create policy employee_leave_approvers_read on public.employee_leave_approvers
  for select to authenticated using (
    public.has_role(array['admin', 'hr']) or approver_user_id = auth.uid());
create policy employee_leave_events_read on public.employee_leave_events
  for select to authenticated using (public.has_role(array['admin', 'hr']));

drop policy if exists "employee_leave_requests_read_hr" on public.employee_leave_requests;
drop policy if exists "employee_leave_requests_write_hr" on public.employee_leave_requests;
-- All writes go through the backend/service-role workflow. Direct authenticated
-- updates could bypass entitlement and manager-assignment checks in the RPC.
revoke insert, update, delete on public.employee_leave_requests from authenticated;
create policy employee_leave_requests_read_scoped on public.employee_leave_requests
  -- The backend service credential applies employee and manager scoping to its
  -- API responses; direct JWT reads are limited to HR/Admin.
  for select to authenticated using (public.has_role(array['admin', 'hr']));

commit;
