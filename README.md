# AcademicFlow

Multi-tenant SaaS for academic teaching resource allocation.

```text
academicflow/
├── frontend/          # React + TypeScript + Vite (Vercel)
├── backend/           # Spring Boot + Kotlin (VPS)
├── docs/              # Developer, user, Super Admin, security, deployment
├── docker-compose.yml # Local/private PostgreSQL
└── .env.example       # Placeholders only — copy to .env (gitignored)
```

## Stack

- **Frontend:** React + TypeScript + Vite
- **Backend:** Spring Boot + Kotlin + Flyway
- **Database:** PostgreSQL (tenant isolation via `tenant_id`)

## Quick start

```bash
cp .env.example .env
docker compose up -d db

cd backend
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun

cd ../frontend
npm ci
npm run dev
```

- UI: http://127.0.0.1:5173  
- API: http://127.0.0.1:8081/api  

Demo: `j.wanjiku@uonbi.ac.ke` (institution) · `admin@uonbi.ac.ke` (product Super Admin) · any password in demo mode.

## Git hygiene

Do **not** commit:

- `.env`, `application-local.properties`
- `node_modules/`, `frontend/dist/`, `backend/build/`, `.gradle/`
- secrets, keys, zips, HTML prototypes

See `.gitignore` and `docs/SECURITY.md`.

## Docs

- [Developer](docs/DEVELOPER.md)
- [User guide](docs/USER_GUIDE.md)
- [Super Admin](docs/SUPER_ADMIN_GUIDE.md)
- [Deployment](docs/DEPLOYMENT.md)
- [Security](docs/SECURITY.md)
