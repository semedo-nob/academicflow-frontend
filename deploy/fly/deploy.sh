#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export PATH="${HOME}/.fly/bin:${PATH}"

WEB_URL="${WEB_URL:-https://academicflow-frontend.fly.dev}"
# API origin (no path) — build arg must include /api for Spring controllers
API_ORIGIN="${API_ORIGIN:-https://academicflow-api.fly.dev}"
API_URL="${API_URL:-${API_ORIGIN}/api}"
PK_FILE="${PK_FILE:-$ROOT/frontend/.env.local}"

PK="$(python3 - <<PY
from pathlib import Path
p = Path("$PK_FILE")
for line in p.read_text().splitlines():
    if line.startswith("VITE_CLERK_PUBLISHABLE_KEY="):
        print(line.split("=",1)[1].strip().strip('"'))
        break
PY
)"
[[ -n "$PK" ]] || { echo "Missing VITE_CLERK_PUBLISHABLE_KEY in $PK_FILE" >&2; exit 1; }

echo "==> Deploying API (academicflow-api)"
( cd "$ROOT/backend" && fly deploy --remote-only )

echo "==> Deploying web (academicflow-frontend)"
( cd "$ROOT/frontend" && fly deploy --remote-only \
  --build-arg "VITE_API_BASE_URL=${API_URL}" \
  --build-arg "VITE_CLERK_PUBLISHABLE_KEY=${PK}" )

echo "==> Done"
echo "Web: $WEB_URL"
echo "API: $API_URL"
