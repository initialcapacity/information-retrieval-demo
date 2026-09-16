#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
cd "${repo_root}"

# Ensure the test template database exists (ir_demo_development is created by the
# Docker container). Uses the running paradedb container.
docker exec -i ir-demo-postgres psql -U postgres -d postgres < databases/create_databases.sql

# Create the chunks table + extensions + BM25 index in both dev and test DBs.
./gradlew :databases:catalog:migrate

python3 -m unittest discover -s scripts -p 'test_*.py'

exec ./gradlew build
