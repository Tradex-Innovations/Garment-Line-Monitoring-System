# Payroll workforce separation rollout

The shared backend now uses two independent Payroll access groups: `EXECUTIVE_STAFF` and `GENERAL_WORKFORCE`. Employee type (`permanent`, `intern`, `new_joiner`) is separate. Only permanent employees enter Payroll. An unassigned group is denied access to Payroll calculations and financial profiles.

## Deploy in one maintenance window

1. Back up `public.employee_master_details`, `public.employees`, and the `payroll` schema. Verify the backup can be read.
2. Ensure the existing Payroll Flyway migrations through V7 have run. The new Supabase migration references Payroll tables that Flyway owns.
3. Apply `supabase/migrations/20260928000100_payroll_workforce_boundaries.sql` to the shared Supabase database. It moves salary and bank values into `payroll.employee_financial_master`, strips old bank names from Payroll source snapshots, and removes financial columns from the broadly readable LineMatrix details table. It also creates group grants and immutable approval event snapshots.
4. Deploy the shared Spring backend and Payroll frontend versions from this change together. Do not deploy the new backend before the migration. During the interval after the migration and before the new backend deploys, avoid employee registration and linking.
5. Sign in as `SYSTEM_ADMIN`. In **User access → Workforce access**, classify each existing employee. Review the executive list with the client; do not derive groups from title, department, salary, or payroll category. A new LineMatrix employee remains unassigned until classified.
6. Once the two HR officers are chosen, open each account in **User access** and grant `View + Edit` for its own group only. Grant the relevant approver `View + Approve` for that group. Preparation and approval cannot be granted to the same account. Super Admin can administer both groups but cannot approve a run that the same account submitted.

## Acceptance checks

- General HR can open ordinary Executive profiles in LineMatrix. No salary or bank fields are present in the LineMatrix employee details response.
- General HR receives `403` when requesting an Executive Payroll profile or saved calculation by ID. Its Payroll list contains only General Workforce employees and history.
- Executive HR receives `403` for General Workforce financial records, and can register and prepare Executive employees.
- Each group's Payroll run is submitted and approved independently. Submission requires a current calculation for every eligible employee in that group. Saving calculations while a group is submitted or approved is rejected. The approver must be a different account from the submitter.
- After an approved employee is moved to another group, the saved calculation and approval event still carry the old `workforce_group_snapshot` and remain under the original group's history access. Legacy calculations without a group snapshot are visible only to Super Admin until reviewed.
- An `authenticated` Supabase client cannot write `public.employees.workforce_group` directly; the audited admin API can. The `payroll.employee_financial_master` table is not readable by `authenticated`.

Two real HR login emails had not been selected when this change was written. The automated access tests use synthetic General and Executive HR identities. Repeat the acceptance checks with the assigned accounts before opening payroll operations to users.
