# AcademicFlow

**Multi-tenant teaching allocation for universities** — request lecturers across departments, match by expertise and workload, import allocation timetables, and publish approved teaching loads.

AcademicFlow is a monorepo: a React SPA and a Spring Boot / Kotlin API over PostgreSQL.

**Status:** private repository. Viewing source does not grant rights to use, copy, modify, distribute, or deploy this software. See [License](#license).

---

## What problem it solves

Universities routinely need **Department A** to borrow teaching capacity from **Department B**. That hand-off today is email, spreadsheets, and informal negotiation. AcademicFlow turns it into a controlled workflow:

1. Institution registers and is approved by the platform owner  
2. Admin creates departments and assigns **one chair account per department**  
3. Chair A requests a lecturer from Chair B, attaches a **course outline**, and both sides **message** (eligibility notes, authority notices)  
4. B accepts → matching ranks candidates by **expertise + workload**  
5. Chairs **import** their department allocation timetable (CSV / Excel / scanned PDF + OCR)  
6. Unallocated units open **Allocate by context** for suitability-aware assignment  
7. Conflicts → approvals → timetable / export  

```text
Register → Onboard chairs → Request (+ docs / thread) → Accept
       → Match / Allocate by context → Board → Approve → Timetable
```

---

## Who uses it

| Role | Surface | Typical work |
|------|---------|--------------|
| **Super Admin** | `/platform/*` | Approve institutions, platform health, flags, support |
| **Institution Admin** | Institution app | Org tree, onboarding chairs, users, full teaching ops |
| **Department Chair** | Institution app (scoped) | Own dept’s lecturers/units/import; cross-dept requests & replies |
| **School Dean / Lecturer / Viewer** | Limited nav | Oversight or read-only views as permitted |

Frontend route gates are **not** the security boundary. The API enforces tenant + department scope.

---

## Product capabilities

### Institution setup
- Public **institution registration** → pending until Super Admin approval  
- Guided **post-approval onboarding**: create departments → invite one chair each  
- Organization hierarchy (university / school / department)  
- Invitations (copy link) and **assign department chair** (one active chair per department)

### Cross-department teaching
- Teaching requests with preferred source department  
- Incoming / Outgoing views for chairs  
- Accept / Decline with notes on a shared **request thread**  
- Attach **course outlines** and supporting documents  
- Bidirectional messages: comments, **eligibility notes** (“only eligible for this unit”), **notify academic authority**  
- Optional auto-created **course offering** for allocate-by-context

### Matching & allocation
- Scored recommendations (expertise, availability, workload, policy)  
- **Allocate by context** — suitability engine (topics, outline evidence, workload, cross-dept flag)  
- Allocation board, conflicts, approvals, workload, timetable, export

### Import / data
- CSV, TSV, Excel, PDF (text + **OCR** for scanned allocation sheets)  
- Column mapping profiles, validate, preview, commit  
- **Chair-scoped import**: commits only to the chair’s department; other-dept rows skipped with warnings  
- Post-import bridge to unallocated units and allocate-by-context

### Platform (product owner)
- Customer lifecycle (approve / suspend / archive)  
- Aggregate KPIs, security events, feature flags, health, support lookup  

---

## Architecture

```text
Browser (Vite / Vercel)
        │  HTTPS
        ▼
   Spring Boot API  ──►  PostgreSQL (private)
        │
   Flyway migrations V1–V12
```

```text
academicflow/
├── frontend/                 # React 19 + TypeScript + Vite institution + platform UI
├── backend/                  # Spring Boot 4 + Kotlin + JPA + Flyway
├── docs/                     # Guides (developer, user, platform, deploy, security)
├── docker-compose.yml        # Local PostgreSQL 16
├── .env.example
├── LICENSE
└── README.md
```

| Layer | Stack |
|-------|--------|
| Frontend | React 19, TypeScript, Vite 8, React Router 7 |
| Backend | Spring Boot 4, Kotlin, Spring Data JPA, Flyway, Actuator |
| Database | PostgreSQL 16 |
| Documents | PDFBox; optional Tesseract OCR (local or Docker image) |

**Tenancy:** every domain row is keyed by `tenant_id`. Institution calls send `X-Tenant-Id`.  
**Department scope:** chairs send optional `X-Active-Department-Id`; `ScopeService` filters lists and mutations.  
**Auth (current):** demo header auth (`X-User-Email`). Replace with JWT/session before production — see `docs/SECURITY.md`.

---

## Quick start (local)

**Requirements:** JDK 17+, Node 20+, Docker, Git.

```bash
git clone git@github.com:semedo-nob/academicflow-frontend.git
cd academicflow-frontend

cp .env.example .env
docker compose up -d db

# API — http://127.0.0.1:8081  (health: /actuator/health)
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# UI — http://127.0.0.1:5173  (Vite proxies /api → 8081)
cd ../frontend && npm ci && npm run dev
```

### Demo accounts (local / `dev` profile only)

| Role | Email | Password |
|------|-------|----------|
| CS Department Chair | `j.wanjiku@uonbi.ac.ke` | any (demo mode) |
| Maths Department Chair | `m.otieno@uonbi.ac.ke` | any (demo mode) |
| Super Admin | `admin@uonbi.ac.ke` | any (demo mode) |

Demo tenant id: `11111111-1111-1111-1111-111111111111` (also the frontend default when none is stored).

---

## Suggested walkthrough

1. **Platform** — sign in as Super Admin → `/platform` → approve a pending institution (or use the seeded demo tenant).  
2. **Onboarding** — as Institution Admin → `/onboarding` → add departments → invite chairs → copy invite links.  
3. **Request** — CS chair → Teaching Requests → New request to Mathematics → attach outline + briefing → submit.  
4. **Reply** — Maths chair → Incoming → **Open thread** → eligibility / authority messages → Accept.  
5. **Match** — Find candidates or **Allocate by context**.  
6. **Import** — Chair → Import → upload department timetable → commit (other departments rejected) → open unallocated units / allocate-by-context.

---

## API surface (high level)

| Area | Examples |
|------|----------|
| Auth | `POST /api/auth/login`, register institution, invitations |
| Core | lecturers, academic-units, requests, allocations, approvals, conflicts |
| Collaboration | `GET/POST /api/requests/{id}`, attachments, messages, respond |
| Offerings | `/api/course-offerings`, outline upload, suitability, allocate |
| Import | `/api/imports/upload`, mapping, advance, reprocess |
| Admin | users, invitations, assign-chair, memberships, org, rules |
| Platform | `/api/platform/**` (Super Admin only) |

Full local setup, headers, and package layout: **[docs/DEVELOPER.md](docs/DEVELOPER.md)**.

---

## Documentation

| Document | Audience |
|----------|----------|
| [docs/DEVELOPER.md](docs/DEVELOPER.md) | Engineers — run, architecture, migrations, conventions |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Institution operators |
| [docs/SUPER_ADMIN_GUIDE.md](docs/SUPER_ADMIN_GUIDE.md) | Platform product owner |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Vercel frontend + VPS/Docker API |
| [docs/SECURITY.md](docs/SECURITY.md) | Tenancy, auth, secrets |

---

## Security notes

- Never commit `.env`, database passwords, or JWT secrets.  
- Platform APIs are enforced server-side (`PlatformAuthFilter`).  
- Keep PostgreSQL on a private network; terminate TLS at Nginx (or equivalent).  
- Demo email-header auth is **not** production-safe.

---

## License

Copyright © 2026 Nelson Semedo (`nsemedo73@gmail.com`). All rights reserved.

This software and its documentation are proprietary. **No license is granted by default.**

You may not use, copy, modify, merge, publish, distribute, sublicense, sell, host, or deploy AcademicFlow (in whole or in part) without **prior written approval** from the copyright holder.

To request permission, contact: **nsemedo73@gmail.com**  
Include your name or organization, intended use, and deployment scope. Approval, if granted, will be provided in writing and may include conditions, duration, and revocation terms.

Unauthorized use is prohibited. See [`LICENSE`](LICENSE) for the full terms.
