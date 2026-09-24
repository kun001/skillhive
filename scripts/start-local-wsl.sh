#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
export COMPOSE_FILE="$project_dir/docker-compose.yml:$project_dir/compose.local.yml"
export PATH="/opt/node-v22.22.0/bin:/opt/skillhub-deps/pnpm-global/bin:$PATH"
storage_dir="${SKILLHIVE_STORAGE_DIR:-$HOME/skillhive-data/storage}"
mkdir -p "$storage_dir"

app_jar="$project_dir/server/skillhub-app/target/skillhub-app-0.1.0.jar"
if [[ ! -f "$app_jar" ]]; then
  echo "Missing SkillHive backend jar: $app_jar" >&2
  exit 1
fi
if [[ ! -f web/node_modules/.modules.yaml ]]; then
  echo "Missing WSL frontend dependencies: web/node_modules" >&2
  exit 1
fi

docker compose -p skillhive up -d --wait --no-build
if ! bash scripts/dev-process.sh status --pid-file .dev/server.pid >/dev/null 2>&1; then
  bash scripts/dev-process.sh start --pid-file .dev/server.pid --log-file .dev/server.log --cwd server -- \
    env STORAGE_BASE_PATH="$storage_dir" SKILLHUB_SECURITY_SCANNER_ENABLED=true SKILLHUB_SECURITY_SCANNER_URL=http://localhost:8000 \
    /usr/lib/jvm/java-21-openjdk-amd64/bin/java -jar skillhub-app/target/skillhub-app-0.1.0.jar --spring.profiles.active=local
fi
if ! bash scripts/dev-process.sh status --pid-file .dev/web.pid >/dev/null 2>&1; then
  bash scripts/dev-process.sh start --pid-file .dev/web.pid --log-file .dev/web.log --cwd web -- \
    /opt/skillhub-deps/pnpm-global/bin/pnpm exec vite --host 127.0.0.1 --strictPort
fi

for attempt in {1..90}; do
  if curl -fs http://127.0.0.1:8080/actuator/health >/dev/null \
      && curl -fs http://127.0.0.1:8000/health >/dev/null \
      && curl -fs http://127.0.0.1:3000/ >/dev/null; then
    echo 'SkillHive ready: http://localhost:3000/'
    exit 0
  fi
  sleep 2
done

echo 'SkillHive did not become ready; inspect .dev/server.log and .dev/web.log' >&2
exit 1
