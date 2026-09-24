#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
export COMPOSE_FILE="$project_dir/docker-compose.yml:$project_dir/compose.local.yml"

docker compose -p skillhive ps
for service in server web; do
  if bash scripts/dev-process.sh status --pid-file ".dev/$service.pid"; then
    echo "$service: running"
  else
    echo "$service: stopped"
  fi
done
for address in http://127.0.0.1:3000/ http://127.0.0.1:8080/actuator/health http://127.0.0.1:8000/health; do
  code="$(curl -s -o /dev/null -w '%{http_code}' "$address")"
  echo "$address $code"
done
