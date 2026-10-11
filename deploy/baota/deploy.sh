#!/bin/bash
# Deploy a published SkillHive release (ghcr.io/kun001/skillhive-*:vX.Y.Z) to the
# BaoTa production host that runs /www/skillhive/compose.yml.
#
# Usage (on the server, as root):
#   EXPECTED_REVISION=<git sha of the tag> bash deploy.sh vX.Y.Z
# The "Deploy production" GitHub Actions workflow streams this file over SSH.
#
# Steps: pull and verify the images, retag them to the local names compose.yml
# uses, stop the server and back up the database, switch the tags in .env, start
# scanner -> server -> web and wait for health, then smoke-test the public site.
# Any failure after the backup restores the previous .env/compose.yml and restarts
# the previous images. Database dumps are kept in the backup directory.
set -Eeuo pipefail
umask 077

tag="${1:-}"
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Usage: deploy.sh vX.Y.Z" >&2; exit 2; }

deploy_dir="${DEPLOY_DIR:-/www/skillhive}"
registry="${IMAGE_REGISTRY:-ghcr.io/kun001}"
expected_revision="${EXPECTED_REVISION:-}"
services=(skill-scanner server web)
compose=(docker compose --env-file .env -f compose.yml)

cd "$deploy_dir"
exec 9>.deploy.lock
flock -n 9 || { echo 'Another release is running' >&2; exit 1; }
echo "DEPLOY_BEGIN $tag"

image_name() { case "$1" in skill-scanner) echo skillhive-scanner ;; *) echo "skillhive-$1" ;; esac; }
container() { echo "skillhive-cloud-$1-1"; }
flyway_version() {
  docker exec "$(container postgres)" psql -U skillhub -d skillhub -Atc \
    "select max(version::int) from flyway_schema_history where success=true and version ~ '^[0-9]+$'"
}
env_value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2-; }

# Preflight: nothing below changes the running site.
avail_kb=$(df --output=avail -k . | tail -1 | tr -d ' ')
(( avail_kb > 3145728 )) || { echo "Not enough disk space: ${avail_kb}KB" >&2; exit 1; }
"${compose[@]}" config --quiet
grep -qx 'BOOTSTRAP_ADMIN_ENABLED=false' .env || { echo 'BOOTSTRAP_ADMIN_ENABLED must be false' >&2; exit 1; }
grep -qx 'SESSION_COOKIE_SECURE=true' .env || { echo 'SESSION_COOKIE_SECURE must be true' >&2; exit 1; }
public_url=$(env_value SKILLHUB_PUBLIC_BASE_URL)
[[ "$public_url" == https://* ]] || { echo "SKILLHUB_PUBLIC_BASE_URL must be https: $public_url" >&2; exit 1; }
smoke_url="${SMOKE_BASE_URL:-$public_url}"
web_port=$(env_value WEB_PORT); web_port="${web_port:-8081}"
flyway_before=$(flyway_version)
echo "Flyway before: $flyway_before"

for svc in "${services[@]}"; do
  name=$(image_name "$svc")
  docker pull -q "$registry/$name:$tag"
  revision=$(docker image inspect "$registry/$name:$tag" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')
  [[ -n "$revision" ]] || { echo "$name:$tag has no revision label" >&2; exit 1; }
  if [[ -n "$expected_revision" && "$revision" != "$expected_revision" ]]; then
    echo "$name:$tag revision $revision != expected $expected_revision" >&2; exit 1
  fi
  expected_revision="$revision"
  docker tag "$registry/$name:$tag" "$name:$tag"
done
echo "Images verified at revision $expected_revision"

backup=$(mktemp -d ".release-$tag-$(date +%Y%m%d-%H%M%S).XXXXXX")
cp -p .env compose.yml "$backup/"
"${compose[@]}" ps > "$backup/containers-before.txt"

restore() {
  result=$?
  trap - ERR
  echo "Release failed; restoring previous images and configuration. Backup kept in $deploy_dir/$backup" >&2
  cat "$backup/.env" > .env
  cp -p "$backup/compose.yml" compose.yml
  "${compose[@]}" up -d --no-deps --wait --wait-timeout 360 "${services[@]}" || true
  exit "$result"
}
trap restore ERR

"${compose[@]}" stop server
docker exec "$(container postgres)" pg_dump -U skillhub -d skillhub -Fc > "$backup/database.dump"
test -s "$backup/database.dump"
docker exec -i "$(container postgres)" pg_restore --list < "$backup/database.dump" > "$backup/database-toc.txt"
sha256sum "$backup/database.dump" > "$backup/data.sha256"
echo 'Database backup verified'

# Rewrite the image tags in place so .env keeps its owner and mode.
{
  grep -vE '^SKILLHIVE_(SERVER_|WEB_)?IMAGE_TAG=' .env
  printf 'SKILLHIVE_IMAGE_TAG=%s\nSKILLHIVE_SERVER_IMAGE_TAG=%s\nSKILLHIVE_WEB_IMAGE_TAG=%s\n' "$tag" "$tag" "$tag"
} > "$backup/.env.next"
cat "$backup/.env.next" > .env
"${compose[@]}" config --quiet

"${compose[@]}" up -d --no-deps --wait --wait-timeout 180 skill-scanner
"${compose[@]}" up -d --no-deps --wait --wait-timeout 360 server
"${compose[@]}" up -d --no-deps --wait --wait-timeout 120 web

flyway_after=$(flyway_version)
(( flyway_after >= flyway_before ))
echo "Flyway after: $flyway_after"
for svc in postgres redis "${services[@]}"; do
  c=$(container "$svc")
  state=$(docker inspect "$c" --format '{{.State.Running}} {{if .State.Health}}{{.State.Health.Status}}{{end}} {{.Config.Image}}')
  echo "$c $state"
  [[ "$state" == "true healthy "* ]]
done
for svc in "${services[@]}"; do
  [[ "$(docker inspect "$(container "$svc")" --format '{{.Config.Image}}')" == "$(image_name "$svc"):$tag" ]]
done
[[ "$(docker exec "$(container server)" printenv BOOTSTRAP_ADMIN_ENABLED)" == false ]]
curl -fsS --max-time 30 -o /dev/null "http://127.0.0.1:$web_port/nginx-health"
curl -fsS --max-time 30 -o /dev/null "$smoke_url/"
curl -fsS --max-time 30 -o /dev/null "$smoke_url/api/v1/auth/methods"
curl -fsS --max-time 30 -o /dev/null "$smoke_url/api/web/skills?library=public&q=&page=0&size=1"
echo 'Smoke checks passed'

trap - ERR
printf '%s\n%s\n%s\n' "$tag" "$expected_revision" "$deploy_dir/$backup" > ".release-$tag.complete"
echo "$(date -Iseconds) release tag=$tag revision=$expected_revision backup=$deploy_dir/$backup" >> deployment.log
echo "BACKUP=$deploy_dir/$backup"
echo "DEPLOY_COMPLETE $tag"
