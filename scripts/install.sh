#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m venv .venv
.venv/bin/python -m pip install --no-cache-dir --require-hashes -r requirements.lock
.venv/bin/python -m app.infrastructure.database
