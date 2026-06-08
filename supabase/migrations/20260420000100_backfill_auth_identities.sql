begin;

insert into auth.identities (
  provider_id,
  user_id,
  identity_data,
  provider,
  last_sign_in_at,
  created_at,
  updated_at
)
select
  users.id::text as provider_id,
  users.id as user_id,
  jsonb_strip_nulls(
    jsonb_build_object(
      'sub',
      users.id::text,
      'email',
      users.email,
      'full_name',
      coalesce(
        nullif(users.raw_user_meta_data ->> 'full_name', ''),
        nullif(users.raw_user_meta_data ->> 'name', ''),
        split_part(coalesce(users.email, ''), '@', 1)
      ),
      'email_verified',
      coalesce(users.email_confirmed_at is not null, false),
      'phone_verified',
      false
    )
  ) as identity_data,
  coalesce(nullif(users.raw_app_meta_data ->> 'provider', ''), 'email') as provider,
  users.last_sign_in_at,
  users.created_at,
  users.updated_at
from auth.users as users
where coalesce(nullif(users.raw_app_meta_data ->> 'provider', ''), 'email') = 'email'
  and not exists (
    select 1
    from auth.identities as identities
    where identities.user_id = users.id
      and identities.provider = 'email'
  );

commit;
