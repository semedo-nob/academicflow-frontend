# AcademicFlow Super Admin Guide

## Role definition

**Super Admin manages the AcademicFlow platform and its customer institutions.**

Institution administrators manage academic operations **within** their institution.

Super Admin is **not** a university administrator.

## What you operate

| Product owner | Institution admin |
|---|---|
| Institutions / tenants | Schools, departments |
| Platform accounts | Lecturers, units, classes |
| Adoption & analytics | Teaching requests & matching |
| Security & audit | Allocations, workload, timetable |
| Feature flags & config | Approvals & institution reports |
| System health & support | Day-to-day academic ops |

## Console

Sign in as platform Super Admin → `/platform/dashboard`.

You get a **separate product command center** (navy shell, platform navigation). You are not routed into the institution teaching workspace.

Navigation groups:

- **Command Center** — product KPIs, health, onboarding queue
- **Customers** — institutions lifecycle and customer overview
- **Users** — platform accounts and status
- **Product** — analytics, feature flags, global configuration
- **Security** — security events and platform audit
- **Operations** — system health, data quality, support lookup
- **System** — version / environment / maintenance-oriented settings

## Customer lifecycle

Institutions register publicly → **PENDING / REQUESTED** → you **Approve** or **Reject** → admins can sign in → you may **Suspend**, **Activate**, or **Archive**.

Opening an institution shows a **customer overview** (usage counts, admins, activity) — not the institution’s operational allocation board.

## What Super Admin should not do here

Do not use the platform console to assign lecturers, create teaching requests, run department allocations, or manage timetables. Those remain in the institution application after a school admin signs in.

## Demo access (local)

- Super Admin: `admin@uonbi.ac.ke` (any password in demo mode)
- Department chair: `j.wanjiku@uonbi.ac.ke`

Institution users receive **403** on `/api/platform/**`.
