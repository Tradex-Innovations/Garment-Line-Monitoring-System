begin;

create table if not exists public.employee_portal_credentials (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  phone text not null,
  phone_normalized text not null,
  password_hash text not null,
  password_salt text not null,
  is_active boolean not null default true,
  weekly_otp_verified_week_start date,
  weekly_otp_verified_at timestamptz,
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint employee_portal_credentials_employee_unique unique (employee_id),
  constraint employee_portal_credentials_phone_unique unique (phone_normalized),
  constraint employee_portal_credentials_phone_not_blank check (btrim(phone_normalized) <> '')
);

create table if not exists public.employee_portal_sessions (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  token_hash text not null unique,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  last_used_at timestamptz,
  revoked_at timestamptz
);

create table if not exists public.employee_portal_otp_challenges (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.employees (id) on delete cascade,
  phone text not null,
  phone_normalized text not null,
  purpose text not null check (purpose in ('weekly_revalidation')),
  week_start date not null,
  code_hash text not null,
  code_salt text not null,
  attempts integer not null default 0,
  expires_at timestamptz not null,
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  constraint employee_portal_otp_attempts_non_negative check (attempts >= 0)
);

create index if not exists employee_portal_sessions_employee_idx
  on public.employee_portal_sessions (employee_id, expires_at desc);

create index if not exists employee_portal_otp_employee_idx
  on public.employee_portal_otp_challenges (employee_id, created_at desc);

drop trigger if exists set_employee_portal_credentials_updated_at on public.employee_portal_credentials;
create trigger set_employee_portal_credentials_updated_at
before update on public.employee_portal_credentials
for each row
execute function public.touch_updated_at();

alter table public.employee_portal_credentials enable row level security;
alter table public.employee_portal_sessions enable row level security;
alter table public.employee_portal_otp_challenges enable row level security;

commit;
