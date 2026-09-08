# AcademicFlow Developer Guide

Guide for engineers working in this monorepo. Product overview and licensing live in the root [README.md](../README.md).

---

## Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 19, TypeScript, Vite 8, React Router 7 |
| Backend | Spring Boot 4, Kotlin, Spring Data JPA, Validation, Actuator |
| Database | PostgreSQL 16 + Flyway (`V1`–`V12`) |
| Import / OCR | PDFBox; optional local `tesseract` or Docker image `franky1/tesseract` |

---

## Repository layout

```text
academicflow/
├── frontend/
│   ├── src/pages/           # Institution + platform screens
│   ├── src/services/        # REST clients (api.ts, lecturerService, platformApi)
│   ├── src/lib/             # access.ts (roles/nav), setup.ts (onboarding flags)
│   ├── src/context/         # AppContext (auth, period, selected request/offering)
│   └── vite.config.ts       # Dev proxy /api → :8081 (long timeout for OCR)
├── backend/
│   ├── src/main/kotlin/.../config/      # TenantContext, UserContext, WebConfig filters
│   ├── .../controller/                  # Institution + platform REST
│   ├── .../service/                     # Domain services
│   │   ├── ScopeService.kt              # Department authorization
│   │   ├── CourseOfferingService.kt     # Offerings, outlines, suitability allocate
│   │   ├── matching/ / suitability/     # Scoring engines
│   │   ├── ingestion/                   # Document extract + OCR helpers
│   │   └── importing/                   # Column map helpers
│   └── src/main/resources/db/migration/ # Flyway SQL
├── docs/
└── docker-compose.yml                   # Postgres on host port 5434 → 5432
```

---

## Local run

### Prerequisites

- JDK **17+**
- Node **20+** / npm
- Docker (Postgres)
- Optional: `tesseract-ocr` for scanned PDF/image outlines and allocation sheets

### Steps

```bash
cp .env.example .env          # never commit .env
docker compose up -d db

cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
# → http://127.0.0.1:8081
# → http://127.0.0.1:8081/actuator/health

cd ../frontend
npm ci
npm run dev
# → http://127.0.0.1:5173  (proxies /api)
```

Dev DB defaults (also in `application-dev.properties`): host `127.0.0.1`, port **5434**, db/user/password `academicflow`.

Production must set `DB_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` via environment — never commit real secrets.

### Demo identities

| Email | Role (seed) |
|-------|-------------|
| `j.wanjiku@uonbi.ac.ke` | Department Chair — Computer Science |
| `m.otieno@uonbi.ac.ke` | Department Chair — Mathematics |
| `admin@uonbi.ac.ke` | Super Admin |

Password is ignored in demo mode (`passwordHash=local`). Tenant: `11111111-1111-1111-1111-111111111111`.

After membership / scoping changes, **re-login** so `memberships` and `activeDepartmentId` refresh in `localStorage`.

---

## Auth & request headers

Current demo auth is **header-based** (not JWT):

| Header | Purpose |
|--------|---------|
| `X-Tenant-Id` | Required for institution APIs — tenant isolation |
| `X-User-Email` | Resolves `UserContext` principal |
| `X-Active-Department-Id` | Optional chair context; must be an authorized membership |

Platform APIs (`/api/platform/**`) require an active `SUPER_ADMIN` email and reject institution users (`PlatformAuthFilter`).

Frontend helpers: `frontend/src/services/api.ts` (`resolveTenantId`, `resolveActiveDepartmentId`).

---

## Tenancy & department scope

- **Institution-wide** roles (`INSTITUTION_ADMIN`, `SCHOOL_DEAN`, `SUPER_ADMIN`, …): `ScopeService.authorizedDepartmentIds()` returns `null` → no department filter.  
- **Department-scoped** roles (`DEPARTMENT_CHAIR`, `LECTURER`): lists and writes are limited to the active department / chair memberships.  
- **Import commit** (`AcademicFlowService.commitImport`): chairs default blank department columns to their active department; named other departments are **rejected** with row-level errors/details.  
- **Cross-department allocate**: allowed when tied to an accepted / related teaching request; otherwise lecturer must be in-scope.

Memberships live in `organization_memberships` (Flyway `V11`) — unique **one ACTIVE DEPARTMENT_CHAIR per department**.

---

## Domain flows (code map)

### Onboarding
- UI: `frontend/src/pages/OnboardingPage.tsx`, flags in `lib/setup.ts`  
- Login redirect: `resolvePostLoginPath` → `/onboarding` when admin has no depts/chairs  
- APIs: `POST` org nodes, `POST /admin/invitations`, `POST /departments/assign-chair`, `GET /memberships`

### Teaching requests + collaboration
- UI: `RequestsPage.tsx` (create, Incoming/Outgoing, **Open thread**)  
- Schema: `V12__request_collaboration.sql` — `briefing_note`, `request_attachments`, `request_messages`  
- APIs:
  - `POST /api/requests` (+ optional `briefingNote`)
  - `GET /api/requests/{id}` — detail + attachments + messages  
  - `POST /api/requests/{id}/attachments` (multipart `file`, `docType`)  
  - `GET /api/request-attachments/{id}/download`  
  - `POST /api/requests/{id}/messages` — `COMMENT` \| `ELIGIBILITY_NOTE` \| `AUTHORITY_NOTICE`  
  - `POST /api/requests/{id}/respond` — ACCEPT \| DECLINE  

Course-outline attachments are best-effort mirrored onto the linked course offering for suitability.

### Course offerings / allocate by context
- UI: `AllocateOfferingPage.tsx`; deep-link via `AppContext.selectedOfferingId` / `selectedRequestId`  
- Services: `CourseOfferingService`, `suitability/SuitabilityEngine`  
- Units drawer: `courseOfferingService.ensureForUnit` → navigate `/allocate-context`  
- Import success panel bridges to `/units?status=Unallocated` and `/allocate-context`

### Import pipeline
1. `POST /api/imports/upload` — parse CSV/XLSX/PDF (+ OCR path)  
2. Update column map → advance VALIDATE → PREVIEW → commit  
3. Commit in `AcademicFlowService.commitImport` (scoped for chairs)  
4. `POST /api/imports/{id}/reprocess` to retry commit  

OCR for large PDFs can take many minutes; Vite proxy timeout is raised for that path.

### Matching
- `MatchingService` — request candidates (expertise-weighted + workload)  
- Prefer preferred-department lecturers when set on the request  

---

## Flyway migrations

| Version | Focus |
|---------|--------|
| V1–V7 | Core schema, seed, admin config, signup, platform, invitations |
| V8 | Allocation import profiles |
| V9 | Course offerings, outlines, requirements, suitability audit fields |
| V10 | Document ingestion metadata on outlines |
| V11 | Organization memberships + students scaffold |
| V12 | Request attachments + messages + briefing note |

Migrations run automatically on API boot. Do not edit applied SQL; add a new version.

---

## Frontend conventions

- Role nav / path gates: `frontend/src/lib/access.ts`  
- Prefer existing UI primitives (`Modal`, `SuggestInput`, `PageHead`, feedback context)  
- Authenticated sessions should not silently fall back to mock data for critical paths; `useAsyncData` still accepts fallbacks for offline demos  
- SPA deploy: `frontend/vercel.json` rewrites  

---

## Backend conventions

- Controllers stay thin; business rules in services  
- Throw `IllegalArgumentException` / `NoSuchElementException` / `IllegalStateException` — mapped via `ApiExceptionHandler` / `ResponseStatusException`  
- Always filter by `TenantContext.get()`  
- Use `UserContext` + `ScopeService` for authorization, not role strings alone  
- Multipart uploads: field name `file`  

---

## Build & test

```bash
# Frontend typecheck / production build
cd frontend && npx tsc --noEmit && npm run build

# Backend compile + tests
cd backend && ./gradlew test
```

Useful smoke tests:

```bash
curl -s http://127.0.0.1:8081/actuator/health
curl -s -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" \
        -H "X-User-Email: j.wanjiku@uonbi.ac.ke" \
        http://127.0.0.1:8081/api/lecturers
```

---

## Environment variables

See `.env.example`. Important:

| Variable | Notes |
|----------|--------|
| `VITE_API_BASE_URL` | Browser → API base (include `/api`) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_*` | Postgres |
| `CORS_ALLOWED_ORIGINS` | Exact frontend origins in prod |
| `JWT_SECRET` | Reserved for upcoming real auth |
| `ACADEMICFLOW_TESSERACT_IMAGE` | Optional Docker OCR image override |

---

## Production checklist

Before any external chairperson deployment:

1. Replace header demo auth with JWT or session cookies  
2. Stop clients from choosing arbitrary `X-Tenant-Id`  
3. Move large documents off BYTEA to object storage  
4. Confirm OCR strategy (image layer vs sidecar)  
5. Follow [DEPLOYMENT.md](DEPLOYMENT.md) and [SECURITY.md](SECURITY.md)  

---

## Related docs

- [USER_GUIDE.md](USER_GUIDE.md) — institution operators  
- [SUPER_ADMIN_GUIDE.md](SUPER_ADMIN_GUIDE.md) — platform console  
- [DEPLOYMENT.md](DEPLOYMENT.md) — Vercel + VPS  
- [SECURITY.md](SECURITY.md) — tenancy and secrets  
