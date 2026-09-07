# AcademicFlow

Proprietary software for multi-tenant academic teaching allocation.

AcademicFlow manages cross-department teaching requests, lecturer matching, allocation, conflict resolution, approvals, workload, and published timetables. The product is delivered as a monorepo: a React SPA and a Spring Boot / Kotlin API over PostgreSQL.

**Status:** private repository. Viewing source does not grant rights to use, copy, modify, distribute, or deploy this software. See [License](#license).

---

## Repository contents (as of `main`)

This repository is a monorepo. Both applications are present and tracked on `origin/main`.

### Frontend (`frontend/`)

| Area | Contents |
|---|---|
| Stack | React 19, TypeScript, Vite 8, React Router 7 |
| Institution UI | Dashboard, organization, lecturers, units, requests, recommendations, allocation, conflicts, approvals, workload, timetable, reports, import, admin, profile, login / invite accept |
| Platform UI | Separate Super Admin shell and routes under `/platform/*` (command center, customers, accounts, security, config, health) |
| Shared | App / platform layouts, topbar + sidebar, feedback (snackbars / dialogs), access helpers, ⌘K navigation search |
| Services | Institution REST client (`api.ts`, domain services) and platform client (`platformApi.ts`) |
| Deploy | `vercel.json` SPA rewrites |

### Backend (`backend/`)

| Area | Contents |
|---|---|
| Stack | Spring Boot 4, Kotlin, JPA, Flyway, PostgreSQL, PDFBox (import) |
| Domain API | Controllers and services for organization, lecturers, units, requests, matching, allocation, conflicts, approvals, workload, timetable, export, import, admin config, invitations |
| Platform API | `/api/platform/**` with `PlatformAuthFilter` (Super Admin only) |
| Tenancy | `TenantContext` and `tenant_id` isolation |
| Schema | Flyway `V1`–`V7` (core schema, seed, admin config, roles, institution signup, platform command center, invitations) |
| Ops | Dockerfile, Gradle wrapper, `application-dev.properties`, local properties example |

### Supporting

| Path | Purpose |
|---|---|
| `docs/` | Developer, user, Super Admin, security, and deployment guides |
| `docker-compose.yml` | Local PostgreSQL |
| `.env.example` | Environment placeholders (no secrets) |

---

## Product model

```text
Request → Match → Allocate → Conflicts → Approve → Timetable / export
```

Two operational surfaces:

1. **Institution workspace** — teaching operations for a single tenant (department chairs, admins, lecturers as applicable).
2. **Platform (Super Admin)** — product-owner console for customer lifecycle, platform accounts, security events, feature flags, health, and support lookup. This is not a university “super user” role inside one campus.

Institution callers are rejected on platform APIs. Frontend route gates are not the security boundary.

---

## Architecture

```text
Internet → Frontend (Vercel) → HTTPS → API (VPS / Nginx) → PostgreSQL (private)
```

```text
academicflow/
├── frontend/          # SPA — institution + platform
├── backend/           # REST API + Flyway migrations
├── docs/
├── docker-compose.yml
├── .env.example
├── LICENSE
└── README.md
```

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, React Router |
| Backend | Spring Boot, Kotlin, Spring Data JPA, Flyway, Validation, Actuator |
| Database | PostgreSQL 16 |
| Import | CSV / PDF parsing (Apache PDFBox) |

---

## Capabilities

### Institution

- Organization hierarchy (schools / departments)
- Lecturers, expertise, and workload limits
- Academic units with academic year / semester / year-of-study context
- Cross-department teaching requests (explicit department selection; no hardcoded CS/Math defaults)
- Scored lecturer recommendations with override and recorded reason
- Allocation board, conflict handling, approval workflow
- Timetable and export after publish
- File import with mapping, validation, commit, and progress feedback
- User invitations and accept flow
- In-app notifications and confirmations; command search for navigation

### Platform (product owner)

- Aggregate KPIs and operational overview
- Customer request → review → approve / suspend / archive
- Platform accounts and security event visibility
- Feature flags and global configuration
- Health, data-quality, and support lookup surfaces

---

## Local development

```bash
git clone git@github.com:semedo-nob/academicflow-frontend.git
cd academicflow-frontend

cp .env.example .env
docker compose up -d db

cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
# API: http://127.0.0.1:8081/api

cd ../frontend && npm ci && npm run dev
# UI:  http://127.0.0.1:5173
```

Demo credentials (local / demo auth only):

| Role | Email | Password |
|---|---|---|
| Department chair | `j.wanjiku@uonbi.ac.ke` | any (demo mode) |
| Super Admin | `admin@uonbi.ac.ke` | any (demo mode) |

Production deployments must replace demo auth with proper session or JWT authentication. See `docs/SECURITY.md` and `docs/DEPLOYMENT.md`.

---

## Documentation

| Document | Scope |
|---|---|
| [docs/DEVELOPER.md](docs/DEVELOPER.md) | Local setup and API boundaries |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Institution operators |
| [docs/SUPER_ADMIN_GUIDE.md](docs/SUPER_ADMIN_GUIDE.md) | Platform product-owner role |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Vercel frontend + VPS API |
| [docs/SECURITY.md](docs/SECURITY.md) | Tenancy, auth, and secrets handling |

---

## Security

- Do not commit `.env`, database passwords, or JWT secrets.
- Platform routes are enforced on the server via `PlatformAuthFilter`.
- PostgreSQL must remain private in production; expose only TLS-terminated HTTP(S) to the API.

---

## License

Copyright © 2026 Nelson Semedo (`nsemedo73@gmail.com`). All rights reserved.

This software and its documentation are proprietary. **No license is granted by default.**

You may not use, copy, modify, merge, publish, distribute, sublicense, sell, host, or deploy AcademicFlow (in whole or in part) without **prior written approval** from the copyright holder.

To request permission, contact: **nsemedo73@gmail.com**  
Include your name or organization, intended use, and deployment scope. Approval, if granted, will be provided in writing and may include conditions, duration, and revocation terms.

Unauthorized use is prohibited. See [`LICENSE`](LICENSE) for the full terms.
