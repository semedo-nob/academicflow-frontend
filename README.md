# AcademicFlow

**Multi-tenant SaaS for university teaching resource allocation** — from cross-department requests to lecturer matching, approvals, workload, and published timetables.

Built as a full product: institution workspace for academic operations, plus a separate **product-owner Super Admin** console for customers, security, analytics, and platform health.

---

## Why this project

Universities routinely move teaching across departments. Spreadsheets break under conflicts, workload limits, and approval trails. AcademicFlow models that workflow end-to-end:

```text
Request → Match lecturers → Allocate → Resolve conflicts → Approve → Timetable / export
```

It also demonstrates production SaaS concerns: **tenant isolation**, **role-based access**, **onboarding**, **invitations**, **imports**, **audit**, and a **platform command center** that is not confused with university admin.

---

## Highlights (for reviewers)

| Area | What I shipped |
|---|---|
| **Product architecture** | Clear split: institution ops (`/dashboard…`) vs product owner (`/platform…`) |
| **Multi-tenancy** | PostgreSQL `tenant_id` isolation; institution users cannot cross tenants |
| **Authorization** | Backend-enforced Super Admin APIs (`/api/platform/**`); frontend gates are not the security boundary |
| **Matching** | Scored lecturer recommendations with override + audit reason |
| **Data pipeline** | CSV/PDF import with mapping, validation, commit, progress UI; year/semester from file data |
| **UX** | In-app snackbars/confirmations (no `window.alert`), ⌘K command search that navigates pages |
| **Ops readiness** | Flyway migrations, Docker Compose Postgres, Vercel + VPS deployment docs, `.env.example` only |

---

## Architecture

```text
                    ACADEMICFLOW (product owner)
                         Super Admin
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
   Institution A        Institution B        Institution C
   (tenant)             (tenant)             (tenant)
         │
         ▼
   Depts → Lecturers → Units → Requests → Allocation → Timetable
```

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, React Router |
| Backend | Spring Boot, Kotlin, JPA, Flyway |
| Database | PostgreSQL 16 |
| Deploy target | Frontend → Vercel · API → Linux VPS (Nginx) · DB private |

```text
academicflow/
├── frontend/     # SPA (institution + platform UIs)
├── backend/      # REST API + migrations
├── docs/         # Developer, user, Super Admin, security, deployment
├── docker-compose.yml
└── .env.example
```

---

## Core capabilities

### Institution workspace
- Organization tree (schools / departments)
- Lecturers, expertise, workload
- Academic units with year/semester context
- Teaching requests (any department → any department; no hardcoded defaults)
- Recommendations, allocation board, conflicts, approvals
- Timetable + CSV/PDF export after publish
- Import engine + invitations for colleagues
- Global search: type `board` → Allocation Board, `profile` → account, etc.

### Product owner (Super Admin)
- Command center KPIs from real aggregates
- Customer lifecycle: request → review → approve / suspend / archive
- Platform accounts, security events, audit
- Feature flags, global configuration, health / data quality / support lookup
- Separate navigation, APIs, and mental model from university admin

---

## Quick start

```bash
git clone git@github.com:semedo-nob/academicflow-frontend.git
cd academicflow-frontend

cp .env.example .env
docker compose up -d db

# API (port 8081)
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# UI (port 5173)
cd ../frontend && npm ci && npm run dev
```

| Role | Demo email | Password |
|---|---|---|
| Department chair | `j.wanjiku@uonbi.ac.ke` | any (demo mode) |
| Product Super Admin | `admin@uonbi.ac.ke` | any (demo mode) |

API: `http://127.0.0.1:8081/api` · UI: `http://127.0.0.1:5173`

---

## Security notes

- Never commit `.env` or production secrets (see `.gitignore`, `docs/SECURITY.md`)
- Demo auth uses email headers for local flows; production should use JWT / sessions (`JWT_SECRET`)
- Platform routes reject non–Super Admin callers on the server

---

## Documentation

| Doc | Audience |
|---|---|
| [DEVELOPER.md](docs/DEVELOPER.md) | Local setup & API boundaries |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | Institution operators |
| [SUPER_ADMIN_GUIDE.md](docs/SUPER_ADMIN_GUIDE.md) | Product owner role (explicitly not university admin) |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | Vercel + VPS |
| [SECURITY.md](docs/SECURITY.md) | Tenancy, auth, secrets |

---

## What this shows employers

- End-to-end ownership of a **multi-tenant SaaS**, not a single-page demo
- Domain modeling for a non-trivial workflow (matching, conflicts, approvals)
- Clean separation of **product operations** vs **customer operations**
- Practical engineering: migrations, env hygiene, deployment docs, role testing mindset

---

## License

Private / portfolio project unless otherwise stated.
