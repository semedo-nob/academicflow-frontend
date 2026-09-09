# Oracle Cloud Always Free — AcademicFlow API deployment

This guide covers deploying the **Spring Boot/Kotlin API + PostgreSQL + Nginx** stack to an Oracle Cloud Always Free **ARM64** (Ampere A1) VM. The React/Vite SPA stays on **Vercel**.

Do **not** treat this document as proof that OCI networking, DNS, TLS, or secrets are already configured — those steps are yours.

## Target architecture

```
React + Vite (Vercel)
        │  HTTPS
        ▼
Nginx (:80 / later :443)  ── on Oracle ARM64 VM
        │  Docker network
        ▼
Spring Boot (:8081)       ── not published publicly
        │
        ▼
PostgreSQL (:5432)        ── Docker volume `academicflow_postgres_data`
```

## Oracle VM requirements

| Item | Guidance |
|------|----------|
| Image | Ubuntu ARM64 (Ampere A1 compatible) |
| Shape | Always Free flexible ARM, e.g. **2 OCPU / 12 GB RAM** |
| Storage | Boot volume + enough free space for Docker images and Postgres data |
| Arch | **linux/arm64** — images used here are multi-arch |

### Ports (security list / firewall)

| Port | Purpose |
|------|---------|
| **22** | SSH |
| **80** | HTTP → Nginx |
| **443** | HTTPS → Nginx (after you enable TLS) |

**Must NOT be publicly exposed:**

- **5432** — PostgreSQL (Docker internal only)
- **8081** — Spring Boot (Docker internal only; Nginx proxies to it)

## Repository layout (relevant paths)

| Path | Role |
|------|------|
| `backend/` | Spring Boot 4 / Java 17 / Gradle Kotlin |
| `backend/Dockerfile` | Multi-stage API image (Temurin 17, ARM64-capable) |
| `docker-compose.production.yml` | `postgres` + `backend` + `nginx` |
| `deploy/oci/nginx/default.conf` | Reverse proxy to `backend:8081` |
| `.env.production.example` | Backend/compose env placeholders |
| `frontend/.env.production.example` | Vite build env placeholders (`VITE_API_BASE_URL`) |
| Root `docker-compose.yml` | **Local/dev Postgres only** — do not use for OCI production |

Existing app config already reads `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` and `CORS_ALLOWED_ORIGINS` / `APP_BASE_URL` from the environment. Inside compose, `DB_HOST=postgres`.

## Server setup (on the VM)

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y docker.io docker-compose-plugin git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
# log out/in so group membership applies
```

Confirm:

```bash
docker version
docker compose version
uname -m   # expect aarch64
```

## Deployment

```bash
git clone <YOUR_GIT_REMOTE> academicflow
cd academicflow

cp .env.production.example .env
nano .env
# Fill every required placeholder. Never commit .env.
# APP_BASE_URL and CORS_ALLOWED_ORIGINS must be your real Vercel https:// origin.
# CLERK_* and email keys must be real production/dev Clerk + Resend (or Postal) values.

docker compose -f docker-compose.production.yml up -d --build
```

First boot can take several minutes while Gradle builds the JAR inside Docker and Spring starts (health `start_period` is generous).

### Verify

```bash
docker compose -f docker-compose.production.yml ps
curl -fsS http://127.0.0.1/actuator/health
curl -fsS http://127.0.0.1/api/auth/mode
```

From your laptop (after security list allows :80):

```bash
curl -fsS http://<VM_PUBLIC_IP>/actuator/health
```

## Logs

```bash
docker compose -f docker-compose.production.yml logs -f backend
docker compose -f docker-compose.production.yml logs -f postgres
docker compose -f docker-compose.production.yml logs -f nginx
```

## Status

```bash
docker compose -f docker-compose.production.yml ps
docker compose -f docker-compose.production.yml top
```

## PostgreSQL persistence

- Named Docker volume: **`academicflow_postgres_data`**
- Mounted at: **`/var/lib/postgresql/data`** inside the `postgres` container
- Survives: backend rebuilds, container restarts, and `docker compose up` recreation **as long as you do not** `docker volume rm academicflow_postgres_data` or `docker compose down -v`

Inspect:

```bash
docker volume ls | grep academicflow_postgres
docker volume inspect academicflow_postgres_data
```

## Manual backup (`pg_dump`)

```bash
# On the VM — writes a SQL dump to the current directory
docker compose -f docker-compose.production.yml exec -T postgres \
  pg_dump -U "$DB_USERNAME" -d "$DB_NAME" > "academicflow-$(date +%Y%m%d).sql"
```

Or without relying on host shell env (substitute user/db from your `.env`):

```bash
docker compose -f docker-compose.production.yml exec -T postgres \
  pg_dump -U academicflow -d academicflow > academicflow-backup.sql
```

Upload that file to Oracle Object Storage yourself (not automated here).

## Frontend (Vercel) — after API URL exists

1. Set `VITE_API_BASE_URL=https://<your-api-host>/api` (must include `/api`).
2. Set `VITE_CLERK_PUBLISHABLE_KEY`.
3. Redeploy the `frontend/` project on Vercel.
4. Set API `.env` `APP_BASE_URL` + `CORS_ALLOWED_ORIGINS` to the exact Vercel `https://…` origin.
5. Add the same origin (and `/login`, `/invite`) in Clerk redirect URLs.

See `frontend/.env.production.example`. The SPA already reads `VITE_API_BASE_URL` (or `VITE_API_BASE`); no invent domain here.

## Environment variables (backend compose)

Required for a successful `prod` start (see `ProductionSafetyValidator` + `application.properties`):

| Variable | Notes |
|----------|--------|
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | Postgres |
| `AUTH_MODE` | Must be `clerk` on `prod` |
| `CLERK_SECRET_KEY` | Required |
| `CLERK_ISSUER` or `CLERK_JWKS_URL` | JWT verification |
| `APP_BASE_URL` | Durable `https://` frontend origin |
| `CORS_ALLOWED_ORIGINS` | Exact frontend origin(s), not `*` |
| `EMAIL_PROVIDER` | Prefer `resend` (or `postal`) |
| `RESEND_API_KEY` / `EMAIL_FROM` | Required when provider is `resend` |

`DB_HOST` / `DB_PORT` are set by compose to `postgres` / `5432` — do not override to localhost inside the stack.

## You configure manually (not done by this repo)

- Oracle Cloud account / tenancy / region
- VCN, subnet, internet gateway
- Ampere A1 VM creation and public IP
- Security lists / NSGs (22, 80, 443 only as needed)
- SSH keys
- DNS / domain / Cloudflare
- Let's Encrypt / HTTPS certificates
- Oracle Object Storage and automated backups
- Vercel project domains and final production API hostname
- Real production secrets in `.env`

## Related docs

- Broader env and fail-closed rules: [`docs/DEPLOYMENT.md`](DEPLOYMENT.md)
- Security notes: [`docs/SECURITY.md`](SECURITY.md)
