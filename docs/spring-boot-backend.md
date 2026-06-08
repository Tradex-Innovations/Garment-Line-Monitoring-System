# Spring Boot Backend

This repository is now moving toward a `frontend + Spring Boot + Supabase` architecture.

## Current split

- `src/`: React/Vite frontend
- `backend/`: Spring Boot API
- `supabase/`: schema, RLS, storage, seed data, and SQL-side reconciliation logic

## What already moved to Spring Boot

- import batch listing
- face file upload and processing
- fingerprint file upload and processing
- batch normalization re-runs
- reconciliation trigger by face/fingerprint batch pair
- validation summary API
- validation queue API
- reconciliation detail API
- reconciliation override API
- reconciliation note API
- active app-user directory API

## What still remains to migrate

- operations snapshot endpoints for dashboard, workers, line assignment, alerts, reports, and settings
- line assignment and alert mutation APIs
- optional backend-managed auth endpoints if we later stop using direct `supabase-js` auth in the browser

## Environment

Frontend:

```env
VITE_SUPABASE_URL=http://127.0.0.1:54321
VITE_SUPABASE_ANON_KEY=...
VITE_SUPABASE_IMPORTS_BUCKET=imports
VITE_BACKEND_URL=http://localhost:8080
```

Backend:

```env
SUPABASE_URL=http://127.0.0.1:54321
SUPABASE_ANON_KEY=...
SUPABASE_SERVICE_ROLE_KEY=...
SUPABASE_IMPORTS_BUCKET=imports
SUPABASE_JWT_ISSUER=http://127.0.0.1:54321/auth/v1
CORS_ALLOWED_ORIGIN=http://localhost:3000
```

## Local run

1. Start Supabase and apply the schema.
2. Install a JDK 21 runtime locally.
3. Start the Spring API:

   ```bash
   npm run backend:dev
   ```

4. Start the frontend:

   ```bash
   npm run dev
   ```

## Auth model

The frontend still signs users in with Supabase Auth directly.

The Spring backend expects the browser to send the Supabase access token as a bearer token. Spring Security verifies that JWT and then resolves the current user's role from `public.profiles` before allowing import or validation operations.

## Supabase access strategy

The backend currently talks to Supabase through:

- PostgREST for table reads/writes
- RPC endpoints for reconciliation override and batch reconciliation
- Storage REST API for import file uploads

This keeps the existing Supabase schema intact while moving orchestration and parsing off the browser.
