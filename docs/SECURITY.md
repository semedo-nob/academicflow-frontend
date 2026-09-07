# Security

## Separation

- Institution users are tenant-scoped via `X-Tenant-Id`.
- Platform APIs under `/api/platform/**` require an active `SUPER_ADMIN` (`X-User-Email`).
- Frontend route gates are **not** sufficient alone; platform endpoints reject unauthorized callers.

## Auth model (current)

Demo/local auth uses email lookup without JWT. Production should add signed sessions or JWT (`JWT_SECRET`) before public launch.

## Audit & security events

- Platform actions write audit records (institution lifecycle, feature flags, settings, account status).
- Failed logins and lockouts write `security_events` (no passwords stored in events).

## Secrets

Never commit `.env`, `application-local.properties`, private keys, or production `JWT_SECRET` / database passwords.

- Commit only `.env.example` and `application-local.properties.example` / `application-dev.properties` (local Docker defaults).
- Production: set `DB_*`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS` via the host environment. Do not use the `dev` profile in production.
