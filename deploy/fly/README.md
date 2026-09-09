# Fly.io deployment

Apps:
- `academicflow-frontend` — React/Vite + nginx
- `academicflow-api` — Spring Boot
- `academicflow-db` — Fly Postgres (flex)

## One-shot deploy

```bash
./deploy/fly/deploy.sh
```

Requires:
- `fly auth login`
- Clerk keys in `frontend/.env.local` (`clerk env pull`)
- Secrets already set on `academicflow-api` (Clerk, Resend, DB, `APP_BASE_URL`)

## Secrets (API)

Set with `fly secrets set -a academicflow-api ...` (never commit):

- `AUTH_MODE=clerk`, `CLERK_SECRET_KEY`, `CLERK_ISSUER`
- `EMAIL_PROVIDER=resend`, `RESEND_API_KEY`, `EMAIL_FROM`
  - **Free / no custom domain:** `EMAIL_FROM=AcademicFlow <onboarding@resend.dev>` — Resend only delivers to the Resend account owner email; invitees use **Copy link** in Admin.
  - **Real invites to any address:** requires a domain you own (even Cloudflare Free DNS needs a purchased domain) + Resend domain verify + `EMAIL_FROM=AcademicFlow <noreply@yourdomain.com>`
- `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS` (frontend HTTPS origin)
- `SPRING_DATASOURCE_URL=jdbc:postgresql://academicflow-db.internal:5433/<db>?sslmode=disable`
- `DB_*` matching that URL
- `JWT_SECRET`, `SUPER_ADMIN_EMAIL`

## URLs

- Web: https://academicflow-frontend.fly.dev
- API: https://academicflow-api.fly.dev

Add the web origin to Clerk **Allowed redirect URLs** (and allowed origins) for the linked instance.
