# Deployment

## Target architecture

```text
Internet → Vercel (React) → HTTPS → Nginx/VPS → Spring Boot → private PostgreSQL
```

PostgreSQL must not be publicly exposed. Only 80/443 on the VPS.

## Frontend (Vercel)

Root directory: `frontend`

```bash
cd frontend
npm ci
npm run build
```

`vercel.json` rewrites SPA routes to `index.html`.

Environment:

```text
VITE_API_BASE_URL=https://api.example.com/api
```

Do not deploy Spring Boot to Vercel.

## Backend (VPS)

```bash
# Database (internal network; avoid publishing 5432 in production)
docker compose up -d db

# API image
docker build -t academicflow-api ./backend
docker run -d --name academicflow-api \
  --network academicflow_af_internal \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=db \
  -e DB_PORT=5432 \
  -e DB_NAME=academicflow \
  -e DB_USERNAME=academicflow \
  -e DB_PASSWORD=change-me \
  -e CORS_ALLOWED_ORIGINS=https://app.example.com \
  -p 127.0.0.1:8081:8081 \
  academicflow-api
```

Put Nginx in front for TLS termination to `127.0.0.1:8081`.

Environment placeholders also live in `.env.example`. Never commit secrets.

## Railway readiness (audit)

AcademicFlow is not Railway-configured yet. Before deploying:

| Area | Status / blocker |
|---|---|
| Frontend | Vite build works; set `VITE_API_BASE_URL` to the public API URL. Prefer Vercel or Railway static site with SPA rewrite. |
| Backend | Needs a Dockerfile-ready JVM service (exists under `backend/Dockerfile`). Set `DB_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `DEFAULT_TENANT_ID`, `SPRING_PROFILES_ACTIVE=prod`. |
| PostgreSQL | Use Railway Postgres; do **not** expose 5432 publicly. Run Flyway on boot (already wired). |
| File uploads | Course outlines stored as BYTEA (MVP). Prefer object storage for production. Install `tesseract-ocr` in the API image for scanned PDF/image OCR; without it, uploads still succeed with `OCR_REQUIRED`. |
| Auth | Demo email-header auth is **not** production-safe. Add JWT/session before any chairperson production link. |
| Health | Actuator `/actuator/health` exists — point Railway healthcheck there. |
| CORS | Must list the exact frontend origin. |
| Secrets | Never commit `.env`; use Railway variables. |
| Multi-tenant | `X-Tenant-Id` isolation is enforced in repositories; harden auth so clients cannot spoof another tenant. |
| OCR | Prefers local `tesseract`. If missing, uses Docker image `franky1/tesseract` when present (`docker pull franky1/tesseract`). Override with `ACADEMICFLOW_TESSERACT_IMAGE`. Scanned allocation PDFs are OCR'd then parsed into Staff Name / Course Code / Course Title rows. |

Do not deploy until JWT auth and object-storage for outlines are addressed.
