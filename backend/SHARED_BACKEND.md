# Union North shared backend

The LineMatrix `backend/` directory is the deployable Spring Boot application for
both LineMatrix and Payroll. Run one Render web service against the existing
Supabase project. The LineMatrix module owns the `public` employee and attendance
records. The Payroll module owns its calculation, setup, audit, and account-grant
tables in the `payroll` PostgreSQL schema. Supabase Auth supplies the signed user
tokens for both modules. Payroll files are stored in a private Supabase Storage
bucket and returned through authenticated Payroll API routes.

## Module boundaries

| Area | Backend package | Data owner |
| --- | --- | --- |
| LineMatrix | `com.garmentline.operations` | Supabase `public` tables and existing imports bucket |
| Payroll | `com.tradex.unionnorth` | PostgreSQL `payroll` schema and private Payroll Storage bucket |
| Shared authentication | `com.garmentline.operations.config` | Supabase Auth users; Payroll grants in `payroll.user_role_assignments` |

The Payroll developer control center reads the LineMatrix monitoring journal in
the same process; it no longer calls a second LineMatrix backend URL. Only users
with the server-side `DEVELOPER` role can open its API.

The `payroll` Spring profile enables the Payroll package, JPA, PostgreSQL and
Flyway. The default profile still runs the existing LineMatrix API without a
direct JDBC connection. Flyway reads only `classpath:db/payroll`; it does not
apply or manage the LineMatrix `public` migrations. The old Payroll repository's
`backend/` is retained as historical source code and must no longer be deployed
as a second API after cutover.

## Cloud cutover checklist

1. Back up the Supabase project. Apply the LineMatrix SQL migrations, including
   the existing shared employee master migrations dated `20260921`, through the
   normal Supabase migration workflow. Apply
   `supabase/migrations/20260924000100_payroll_private_storage.sql` to create the
   private photo bucket. Verify that LineMatrix's current API and employee pages
   still work before enabling Payroll.
2. Provide a direct PostgreSQL connection to this same Supabase project. The
   connection used on startup must be allowed to create and migrate the
   `payroll` schema, plus read the required `public` data through Supabase's
   server APIs. Use a DB account with only the permissions needed for this
   project where possible. Keep all connection credentials and the service-role
   key in Render secrets.
3. Set the variables shown in `backend/.env.example` on the **existing** Render
   backend. Point `PAYROLL_DB_HOST` at the chosen Supabase database endpoint,
   set `PAYROLL_DB_USER` and `PAYROLL_DB_PASSWORD`, and provide the bundled CA
   certificate path as `PAYROLL_DB_SSL_ROOT_CERT`. Include both Netlify frontend
   origins in `CORS_ALLOWED_ORIGINS`. Set `PAYROLL_INVITE_REDIRECT_URL` to a
   permitted Supabase Auth redirect URL on the Payroll frontend.
4. Deploy the backend with `SPRING_PROFILES_ACTIVE=payroll`. Flyway applies
   `V1` through `V7` in the `payroll` schema; Hibernate validates the entities.
   A failed migration or failed database connection must block a production
   cutover. Check `GET /actuator/health` and an unauthenticated Payroll route
   such as `GET /api/v1/payroll/calculations`, which should return `401`.
5. Bootstrap the first trusted administrator **after** verifying the Supabase
   Auth user ID and **before** exposing the Payroll frontend. Execute in the
   Supabase SQL editor with a real, already provisioned admin email:

   ```sql
   insert into payroll.user_role_assignments (auth_user_id, role)
   select id, 'SYSTEM_ADMIN'
   from auth.users
   where lower(email) = lower('admin@example.com')
   on conflict (auth_user_id, role) do nothing;
   ```

   Check that exactly one intended user was granted the role. No role is
   inferred from editable `public.profiles` data or user JWT metadata. Use the
   Payroll account API for subsequent grants. Keep at least one other trusted
   administrator before changing the initial administrator's access.
6. Point both frontends at the one Render API. The Payroll frontend now uses
   Supabase Auth and reads server-side Payroll grants from `GET /api/v1/auth/me`.
   Configure its Netlify public variables and exact Supabase Auth redirect URLs
   before publishing it. Migrate account invitations
   and role assignments deliberately; Keycloak IDs and sessions are not copied
   automatically. Remove the old Payroll Render service only after both
   frontends and representative user roles pass end-to-end checks.

## Employee and attendance rules

Payroll registration requires `lineMatrixEmployeeNumber`. The API reads the
canonical LineMatrix employee and rejects stale or conflicting identity fields.
Name, identity number, contact details, and employment status must be corrected
in LineMatrix first. A Payroll employee row is a linked workflow projection and
holds Payroll-specific eligibility and versioned salary setup; it is not a
second editable employee master. Existing unlinked Payroll records, if any,
need a reviewed backfill before they can be edited through the shared API.

Payroll calculations still accept explicit, audited period inputs. This merge
does not translate LineMatrix attendance records into payable days or overtime:
that requires an agreed approval point, leave mapping, cutoff rules and replay
policy. Do not activate automatic attendance-derived calculations until those
rules are signed off and tested.

## Local verification

Run `./mvnw test` from `backend/` for the default LineMatrix profile. For a
Payroll startup check, use a disposable PostgreSQL database, set the variables
above, activate `payroll`, and start `./mvnw spring-boot:run`. Local startup
checks verify migrations and route wiring only; they do not prove Supabase Auth,
private Storage, browser CORS, or production data migration. Verify those
against the actual cloud project before routing users to the new API.
