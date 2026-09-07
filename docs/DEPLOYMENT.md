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

## Later homelab swap

Keep the same API contract and env vars. Replacing the VPS host should not require frontend redesign.
