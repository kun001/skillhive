#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
export COMPOSE_FILE="$project_dir/docker-compose.yml:$project_dir/compose.local.yml"

bash scripts/dev-process.sh stop --pid-file .dev/server.pid
bash scripts/dev-process.sh stop --pid-file .dev/web.pid
docker compose -p skillhive down
echo 'SkillHive stopped; Docker data volumes retained.'
