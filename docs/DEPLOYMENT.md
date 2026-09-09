# Deployment

## Northflank

Preferred durable host for this monorepo.

### Prerequisites

1. Install CLI: `npm i -g @northflank/cli`
2. `northflank login` (API token from Account Settings → API → Tokens)
3. Link the GitHub monorepo in Northflank (services build via Dockerfile)
4. Provide Clerk + Resend secrets as template/argument overrides (never commit them)

### Repo artifacts

See [`deploy/northflank/README.md`](../deploy/northflank/README.md):

- `template.json` — project + Postgres + API + web + secret group
- `addon-postgres.json` / `deploy.sh` — CLI helpers
- `frontend/Dockerfile` — nginx SPA
- `backend/Dockerfile` — Spring Boot API

### After deploy

1. Copy public DNS for `academicflow-web` and `academicflow-api`
2. Set `APP_BASE_URL` / `CORS_ALLOWED_ORIGINS` to the web HTTPS origin
3. Rebuild web with `VITE_API_BASE_URL=https://<api-dns>/api`
4. Add both domains in Clerk (redirects) and verify Resend sender domain
5. Health: `GET https://<api-dns>/actuator/health`

Local tunnels / `trycloudflare.com` are **not** production.

---

## Required production environment variables

Never commit real values. Set these in the host secret store (Vercel / Railway / VPS).

### Frontend (Vercel)

| Variable | Example / notes |
|----------|-----------------|
| `VITE_API_BASE_URL` | `https://api.example.com/api` |
| `VITE_CLERK_PUBLISHABLE_KEY` | `pk_live_…` (publishable only) |

Build: `cd frontend && npm ci && npm run build`. Root directory: `frontend`. SPA rewrites: `frontend/vercel.json`.

### Backend (Railway / Docker / VPS)

| Variable | Required | Notes |
|----------|----------|-------|
| `SPRING_PROFILES_ACTIVE` | yes | `prod` |
| `AUTH_MODE` | yes | **must be `clerk`** (prod profile rejects `auto` / `legacy`) |
| `CLERK_SECRET_KEY` | yes | `sk_live_…` |
| `CLERK_ISSUER` | yes* | e.g. `https://<instance>.clerk.accounts.dev` |
| `CLERK_JWKS_URL` | optional | overrides issuer JWKS |
| `CLERK_API_BASE` | optional | default `https://api.clerk.com` |
| `EMAIL_PROVIDER` | yes | `resend` (or `postal`) — **not** `console` |
| `RESEND_API_KEY` | if resend | required in prod when provider=resend |
| `EMAIL_FROM` | if resend/postal | verified sender |
| `POSTAL_API_URL` / `POSTAL_API_KEY` | if postal | self-hosted |
| `APP_BASE_URL` | yes | durable `https://…` frontend origin — **not** localhost / trycloudflare |
| `INVITATION_EXPIRY_HOURS` | optional | default `336` |
| `CORS_ALLOWED_ORIGINS` | yes | exact frontend origin(s) |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | yes | managed Postgres |
| `SERVER_PORT` | optional | default `8081` |
| `SUPER_ADMIN_EMAIL` | optional | promotes/creates SUPER_ADMIN row on boot (no password stored) |
| `SUPER_ADMIN_NAME` | optional | display name for bootstrap |

\* Or set `CLERK_JWKS_URL` instead of issuer.

### Production fail-closed rules

On `prod` / `production` profile, the API **refuses to start** if:

- `AUTH_MODE` is `auto` or `legacy`
- `AUTH_MODE=clerk` but `CLERK_SECRET_KEY` or issuer/JWKS missing
- `APP_BASE_URL` is blank, `http://`, localhost, `127.0.0.1`, or `*.trycloudflare.com`
- `EMAIL_PROVIDER=resend` without `RESEND_API_KEY` / `EMAIL_FROM`
- `EMAIL_PROVIDER=console`

Missing Resend credentials never produce a fake “email sent” result: `ResendEmailService` returns `success=false` and invitations stay `PENDING` with `deliveryStatus=FAILED` (Copy link / Resend remain available).

---

## Clerk setup

1. Create a Clerk application (React / SPA).
2. Add production frontend URL to allowed origins / redirect URLs (`/login`, `/invite/*`).
3. Set `VITE_CLERK_PUBLISHABLE_KEY` on the frontend host.
4. Set `CLERK_SECRET_KEY` + `CLERK_ISSUER` on the API host with `AUTH_MODE=clerk`.
5. AcademicFlow authorization stays in Postgres (memberships, roles, permissions) — not Clerk metadata.

Super Admin test account: set `SUPER_ADMIN_EMAIL` to the Clerk user email (e.g. operator mailbox). Create/sign-in password **only in Clerk** — never in git, seeds, or docs.

---

## Resend setup

1. Verify a sending domain in Resend.
2. Create an API key; set `RESEND_API_KEY` and `EMAIL_FROM`.
3. Set `EMAIL_PROVIDER=resend`.
4. Invitation create always calls `EmailService.send` — never Resend SDK from invitation business logic.
5. On failure: invitation remains pending; UI shows failure + Copy / Retry.

Postal: set `EMAIL_PROVIDER=postal` plus `POSTAL_API_URL` / `POSTAL_API_KEY`.

---

## Invitation URLs

Production emails use:

```text
{APP_BASE_URL}/invite/{secure-token}
```

- Token is cryptographically random; DB stores **SHA-256 hash** only.
- List endpoints never return usable tokens; Copy link rotates and returns a fresh path.
- Frontend route `/invite/:token` opens the existing accept + Clerk flow.

---

## Frontend (Vercel)

```bash
cd frontend
npm ci
npm run build
```

Environment:

```text
VITE_API_BASE_URL=https://api.example.com/api
VITE_CLERK_PUBLISHABLE_KEY=pk_…
```

Do not deploy Spring Boot to Vercel.

---

## Backend (Railway / Docker / VPS)

```bash
docker build -t academicflow-api ./backend
docker run -d --name academicflow-api \
  --network <private> \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e AUTH_MODE=clerk \
  -e DB_HOST=… \
  -e DB_PORT=5432 \
  -e DB_NAME=academicflow \
  -e DB_USERNAME=… \
  -e DB_PASSWORD=… \
  -e CLERK_SECRET_KEY=… \
  -e CLERK_ISSUER=… \
  -e EMAIL_PROVIDER=resend \
  -e RESEND_API_KEY=… \
  -e EMAIL_FROM='AcademicFlow <noreply@example.com>' \
  -e APP_BASE_URL=https://app.example.com \
  -e CORS_ALLOWED_ORIGINS=https://app.example.com \
  -p 127.0.0.1:8081:8081 \
  academicflow-api
```

Health check: `GET /actuator/health`.

Flyway migrations run automatically on boot (`V1`–`V15+`). Do not edit applied SQL; add a new version.

---

## Local development

```bash
cp .env.example .env   # never commit .env
docker compose up -d db
# AUTH_MODE=auto (default) allows legacy header auth without Clerk keys
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
cd frontend && npm run dev
```

Local defaults may use `127.0.0.1` — that is **development only**.

---

## Troubleshooting

| Symptom | Check |
|---------|--------|
| App won't start on prod | `AUTH_MODE`, Clerk keys, `APP_BASE_URL`, email provider keys (see fail-closed rules) |
| 401 on all APIs | Missing/invalid Clerk Bearer token; `CLERK_ISSUER` mismatch |
| Invitation “created but email failed” | Resend key/from/domain; Copy link still works |
| Invite link opens wrong host | `APP_BASE_URL` must be the production frontend |
| CORS errors | `CORS_ALLOWED_ORIGINS` must match the SPA origin exactly |

---

## Security requirements (production)

- `AUTH_MODE=clerk` only — no header-email auth
- Never trust client role / tenant / department / permission claims
- Department scope enforced by `ScopeService` + memberships
- Permission checks via `PermissionService` (role defaults + grants − revokes)
- Audit invitation, permission, allocation, and membership changes without logging raw tokens
