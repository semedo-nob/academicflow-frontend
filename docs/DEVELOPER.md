# AcademicFlow Developer Guide

## Stack
- Frontend: React + TypeScript + Vite
- Backend: Spring Boot + Kotlin + JPA + Flyway
- Database: PostgreSQL

## Local run
1. `cp .env.example .env` (optional; never commit `.env`)
2. Start Postgres: `docker compose up -d db`
3. Backend: `cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun` (port 8081)
4. Frontend: `cd frontend && npm ci && npm run dev` (port 5173, proxies `/api`)

Credentials for the `dev` profile live only in `application-dev.properties` (local Docker defaults). Production must set `DB_*` and `JWT_SECRET` via environment — never commit real secrets.

## Tenancy
Institution APIs use `X-Tenant-Id`.
Platform APIs (`/api/platform/**`) require `X-User-Email` of an active `SUPER_ADMIN` and reject institution users.

## Key paths
- Institution app: `/dashboard`, `/organization`, …
- Product owner console: `/platform/*`
- Platform API: `/api/platform/*`
- Institution API: `/api/*` (non-platform)

## Build
```bash
cd frontend && npm run build
cd backend && ./gradlew test
```
