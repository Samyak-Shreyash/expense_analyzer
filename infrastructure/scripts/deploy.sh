#!/usr/bin/env bash
# Pull the latest code and (re)start the production stack on this host.
# Usage: infrastructure/scripts/deploy.sh [git-ref]   (default: current branch)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_DIR="$ROOT/infrastructure/compose"
ENV_FILE="$ROOT/.env"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_DIR/compose.yaml" -f "$COMPOSE_DIR/compose.yaml")

cd "$ROOT"

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE (copy your production values there)." >&2; exit 1; }

# Fail early on missing settings instead of half-starting the stack.
required=(DB_NAME DB_USER DB_USER_PASSWORD SERVER_PORT JWT_SECRET JWT_EXPIRATION_SECONDS
          JWT_REFRESH_SECONDS OAUTH_SECRET RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY
          RUSTFS_SSE_S3_MASTER_KEY RUSTFS_TAG APP_BASE_URL
          PROXY_SHARED_SECRET)
missing=()
for key in "${required[@]}"; do
  grep -Eq "^${key}=.+" "$ENV_FILE" || missing+=("$key")
done
if (( ${#missing[@]} )); then
  echo "Set these in .env before deploying: ${missing[*]}" >&2
  exit 1
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "Working tree has local changes; commit or stash them first." >&2
  exit 1
fi

git fetch --quiet origin
if [[ $# -ge 1 ]]; then
  git checkout --quiet "$1"
fi
git pull --ff-only --quiet || true   # detached refs (tags/SHAs) have nothing to pull

echo "Deploying $(git rev-parse --short HEAD)..."
"${COMPOSE[@]}" up -d --build --remove-orphans

echo "Waiting for the backend to report healthy..."
port="$(grep -E '^SERVER_PORT=' "$ENV_FILE" | cut -d= -f2-)"
for _ in $(seq 1 40); do
  if docker exec backend bash -c "exec 3<>/dev/tcp/127.0.0.1/${port} && printf 'GET /actuator/health/readiness HTTP/1.0\r\n\r\n' >&3 && grep -q UP <&3" 2>/dev/null; then
    echo "Backend is UP."
    "${COMPOSE[@]}" ps
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

echo "Backend did not become healthy in time. Recent logs:" >&2
docker logs --tail 80 backend >&2
exit 1
