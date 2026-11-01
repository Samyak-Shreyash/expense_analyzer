#!/usr/bin/env bash
# Nightly backup: Postgres dump + RustFS volume archive, with retention.
# Usage: infrastructure/scripts/backup.sh [backup-dir]   (default: /var/backups/expense-analyzer)
# Cron example (02:30 daily):
#   30 2 * * * /opt/expense_analyzer/infrastructure/scripts/backup.sh >> /var/log/expense-backup.log 2>&1
#
# Backups contain users' financial data. Copy them OFF this host to encrypted
# storage (e.g. `rclone copy --crypt ...` or restic), and keep the .env
# (which holds RUSTFS_SSE_S3_MASTER_KEY) backed up separately — without it
# the RustFS objects can't be decrypted.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEST="${1:-/var/backups/expense-analyzer}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

set -a; . "$ROOT/.env"; set +a

umask 077
mkdir -p "$DEST"

echo "[$STAMP] Dumping Postgres..."
docker exec postgres pg_dump -U "$DB_USER" -d "$DB_NAME" --format=custom \
  > "$DEST/postgres-$STAMP.dump"

echo "[$STAMP] Archiving RustFS data..."
volume="$(docker inspect rustfs --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}')"
docker run --rm -v "$volume":/data:ro -v "$DEST":/backup alpine \
  tar -czf "/backup/rustfs-$STAMP.tar.gz" -C /data .

find "$DEST" -maxdepth 1 -type f \( -name 'postgres-*.dump' -o -name 'rustfs-*.tar.gz' \) \
  -mtime +"$KEEP_DAYS" -delete

echo "[$STAMP] Done: $(du -sh "$DEST" | cut -f1) in $DEST"
# Restore: docker exec -i postgres pg_restore -U "$DB_USER" -d "$DB_NAME" --clean < postgres-<stamp>.dump
