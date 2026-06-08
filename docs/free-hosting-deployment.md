# Free Hosting Deployment

This project can run with the frontend on GitHub Pages and the Spring Boot backend on Render's free web service plan.

## Backend: Render

1. Push this repository to GitHub.
2. In Render, create a new Blueprint and select this repository.
3. Render will read `render.yaml` from the repo root and build the backend from `backend/Dockerfile`.
4. Add these Render environment variables:

| Key | Value |
| --- | --- |
| `SUPABASE_URL` | `https://your-project-ref.supabase.co` |
| `SUPABASE_ANON_KEY` | Supabase anon or publishable key |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase service role key |
| `SUPABASE_IMPORTS_BUCKET` | `imports` |
| `SUPABASE_JWT_ISSUER` | `https://your-project-ref.supabase.co/auth/v1` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,https://your-github-username.github.io` |

After deploy, confirm the backend health check:

```text
https://your-render-service.onrender.com/actuator/health
```

Render free services can sleep when idle, so the first request after inactivity can take longer.

## Frontend: GitHub Pages

1. In GitHub, open the repository settings.
2. Go to **Pages**.
3. Set the publishing source to **GitHub Actions**.
4. Add these repository variables:

| Name | Value |
| --- | --- |
| `VITE_SUPABASE_URL` | `https://your-project-ref.supabase.co` |
| `VITE_SUPABASE_IMPORTS_BUCKET` | `imports` |
| `VITE_BACKEND_URL` | `https://your-render-service.onrender.com` |

5. Add this repository secret:

| Name | Value |
| --- | --- |
| `VITE_SUPABASE_ANON_KEY` | Supabase anon or publishable key |

6. Push to `main`, or manually run **Deploy to GitHub Pages** from the Actions tab.

The workflow automatically sets the Vite base path to:

```text
/${repository-name}/
```

For a normal GitHub Pages project site, the frontend URL will be:

```text
https://your-github-username.github.io/your-repository-name/
```

## Supabase Auth

In Supabase, add the GitHub Pages URL to the Auth URL configuration:

```text
https://your-github-username.github.io/your-repository-name/
```

Keep local URLs in the redirect list while developing:

```text
http://localhost:3000
http://localhost:3000/
```

## CORS Note

CORS origins do not include path segments. For GitHub Pages, use:

```text
https://your-github-username.github.io
```

Do not use:

```text
https://your-github-username.github.io/your-repository-name/
```
