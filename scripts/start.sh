#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
.venv/bin/python -m app.infrastructure.database
exec .venv/bin/python -m uvicorn app.api.main:app --host 0.0.0.0 --port "${PORT:-8000}"
