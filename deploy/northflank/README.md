# Northflank deployment

## Status

CLI is installed (`@northflank/cli`). Deployment cannot finish until you authenticate.

## 1. Log in (required)

In a terminal:

```bash
northflank login
```

Or with a token from **Northflank → Account Settings → API → Tokens**:

```bash
export NORTHFLANK_API_TOKEN='…'   # never commit
northflank login --token-login -t "$NORTHFLANK_API_TOKEN" -n academicflow --override
```

Then reply in chat: **“Northflank login done”** so deployment can continue.

## 2. Also required before production is live

| Secret / link | Why |
|---------------|-----|
| GitHub repo linked in Northflank | Combined services build from git (`backend/Dockerfile`, `frontend/Dockerfile`) |
| `CLERK_SECRET_KEY` + `CLERK_ISSUER` + `VITE_CLERK_PUBLISHABLE_KEY` | `AUTH_MODE=clerk` prod fail-closed |
| `RESEND_API_KEY` + verified `EMAIL_FROM` | Real invitation email |
| Push latest monorepo to the GitHub remote Northflank builds | Includes Dockerfiles + V15 migrations |

## 3. Deploy

```bash
chmod +x deploy/northflank/deploy.sh
./deploy/northflank/deploy.sh
# or create template from UI using deploy/northflank/template.json
```

Template wires:

- Managed PostgreSQL → `DB_*` for API
- `academicflow-api` (port 8081)
- `academicflow-web` (nginx SPA, port 80)
- Prod env: `AUTH_MODE=clerk`, `EMAIL_PROVIDER=resend`, `APP_BASE_URL` / CORS from web DNS

## Artifacts

| Path | Purpose |
|------|---------|
| `deploy/northflank/template.json` | IaC template |
| `deploy/northflank/addon-postgres.json` | Postgres addon spec |
| `deploy/northflank/deploy.sh` | CLI bootstrap script |
| `backend/Dockerfile` | API image (+ healthcheck) |
| `frontend/Dockerfile` + `nginx.conf` | SPA image |
