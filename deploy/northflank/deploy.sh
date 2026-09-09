#!/usr/bin/env bash
# Deploy AcademicFlow to Northflank using the CLI.
# Prerequisites:
#   1) npm i -g @northflank/cli
#   2) northflank login   (API token from Account Settings → API → Tokens)
#   3) GitHub linked to Northflank for the monorepo
#   4) Secrets ready: Clerk + Resend (see docs/DEPLOYMENT.md)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NF_DIR="$ROOT/deploy/northflank"
PROJECT_ID="${NORTHFLANK_PROJECT_ID:-academicflow}"

if ! command -v northflank >/dev/null 2>&1; then
  echo "Install CLI: npm i -g @northflank/cli" >&2
  exit 1
fi

if ! northflank context ls 2>&1 | grep -qiE '^[A-Za-z0-9_-]+[[:space:]]|active'; then
  # "No contexts found" or empty list
  if northflank context ls 2>&1 | grep -qi 'No contexts'; then
    echo "BLOCKED: Northflank CLI is not logged in." >&2
    echo "Run: northflank login" >&2
    echo "Or:  northflank login --token-login -t \"\$NORTHFLANK_API_TOKEN\" -n academicflow --override" >&2
    exit 2
  fi
fi

# Extra hard check: get projects requires auth
if ! northflank list projects -o json >/dev/null 2>&1; then
  echo "BLOCKED: Northflank CLI is not authenticated (northflank list projects failed)." >&2
  echo "Run: northflank login" >&2
  exit 2
fi

echo "Creating / ensuring project: $PROJECT_ID"
northflank create project -i "{\"name\":\"AcademicFlow\",\"description\":\"AcademicFlow production\"}" -o json 2>/dev/null \
  || northflank get project --projectId "$PROJECT_ID" -o json >/dev/null

echo "Creating PostgreSQL addon (idempotent best-effort)…"
northflank create addon --projectId "$PROJECT_ID" -f "$NF_DIR/addon-postgres.json" -o json \
  || echo "Addon may already exist — continuing"

echo "Template file ready at $NF_DIR/template.json"
echo "Create & run it in the Northflank UI (Templates) or:"
echo "  northflank create template -f $NF_DIR/template.json"
echo "Then set argument overrides for CLERK_* and RESEND_* and run the template."
echo
echo "After services are live, set APP_BASE_URL / CORS / VITE_API_BASE_URL to the issued nf domains,"
echo "and add those domains in Clerk + Resend."
