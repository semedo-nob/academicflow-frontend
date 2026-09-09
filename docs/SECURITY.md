# Security

## Separation

- Institution users are tenant-scoped; with Clerk, tenant is taken from the linked AcademicFlow user (client `X-Tenant-Id` cannot escalate across tenants for that identity).
- Platform APIs under `/api/platform/**` require an active `SUPER_ADMIN` (from Clerk-resolved `UserContext`, or legacy `X-User-Email` when `AUTH_MODE=legacy`/`auto` without Clerk).
- Frontend route gates are **not** sufficient alone; platform endpoints reject unauthorized callers.

## Auth model (current)

**Clerk authenticates** (who). **AcademicFlow authorizes** (what: institution, department, role, permissions).

| Mode (`AUTH_MODE`) | Behavior |
|--------------------|----------|
| `auto` (default) | Clerk when `CLERK_SECRET_KEY` / `CLERK_ISSUER` set; otherwise legacy `X-User-Email` |
| `clerk` | Bearer JWT required; password / header login disabled |
| `legacy` | Demo header auth only |

Production should use `AUTH_MODE=clerk` with `CLERK_*` and `VITE_CLERK_PUBLISHABLE_KEY`. Never trust client-supplied role, institution, or department IDs — `TenantFilter` verifies the Clerk JWT, resolves `clerk_user_id` → `users`, then `ScopeService` enforces membership.

Platform APIs still require `SUPER_ADMIN` via resolved `UserContext` (Clerk or legacy).

## Audit & security events

- Platform actions write audit records (institution lifecycle, feature flags, settings, account status).
- Failed logins and lockouts write `security_events` (no passwords stored in events).

## Invitations

- Invitation URLs carry a random opaque token; the API stores a SHA-256 hash (`token` / `token_hash`), not a reusable plaintext secret after create/resend/rotate.
- List endpoints do not return usable tokens; **Copy link** rotates and returns a fresh path.
- Email delivery (`Resend` / `Postal` / `console`) is optional — failed delivery does not revoke the invitation.
- When Clerk is enabled, accept requires a verified Clerk session whose email matches the invitation email (server-side).
- Authorization after accept comes from `organization_memberships` + tenant scope, not from the invite token or Clerk metadata.

## Secrets

Never commit `.env`, `application-local.properties`, private keys, or production `JWT_SECRET` / database passwords / `RESEND_API_KEY` / `POSTAL_API_KEY` / `CLERK_SECRET_KEY`.

- Commit only `.env.example` and `application-local.properties.example` / `application-dev.properties` (local Docker defaults).
- Production: set `DB_*`, `CLERK_*`, email provider keys, and `CORS_ALLOWED_ORIGINS` via the host environment. Do not use the `dev` profile in production.
