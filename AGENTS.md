# Working in LineMatrix and the shared Payroll backend

## Repository map

- The LineMatrix UI is in `src/` and runs locally on port 3000.
- `backend/` contains the shared Spring Boot API used by both LineMatrix and the Payroll UI in the sibling `Union-North-ERP/frontend` repository. The shared API has been run locally on port 8084; check its current configuration and health endpoint before assuming the port.
- Shared employee records and organization masters are in the `public` schema; payroll workflow data is in the `payroll` schema. Treat migrations and hosted database writes as consequential.

## Efficient workflow

- Find the owning package or endpoint with `rg`, then read only the relevant source and tests. Avoid broad repository dumps and repeated scans.
- Check whether the API or UI is already running before launching another process. Verify the health endpoint after a startup or restart.
- Preserve existing uncommitted work. Do not reset, clean, or change unrelated files.
- Run targeted Maven tests for the changed service or controller first. Use broader tests only for cross-cutting changes or when requested.
- Never print `.env` values, tokens, database passwords, or service role keys. Do not change database migration or sync settings merely to troubleshoot a UI issue.

## Payroll workforce access

- `backend/src/main/java/com/tradex/unionnorth/employee/controller/EmployeeController.java` exposes `/api/v1/employees/registration-options`.
- `SharedEmployeeRegistrationService.options()` returns groups from `WorkforceAccess.groups(EDIT)`. Ordinary user groups are read from `payroll.user_workforce_grants` with `can_edit=true`; system admins receive all groups. Developers inherit view/edit access to both groups, while approval remains grant controlled.
- Group names are defined in `security/domain/WorkforceGroup.java`. User grants and employee assignments are managed by `security/WorkforceController.java`. Diagnose grants and request failures separately before changing group definitions.
