# Deploying to a single VPS

Interim setup: the whole Compose stack on one Linux VM, reachable only over HTTPS
through Tailscale Serve or a Cloudflare Tunnel. No public ports.

## 1. Server

- Ubuntu 24.04, about 4 GB RAM (Oracle Cloud Always Free Ampere in Mumbai/Hyderabad, or any
  small VM). All images used here have arm64 builds.
- Install Docker Engine + Compose plugin (Compose >= 2.24): https://docs.docker.com/engine/install/ubuntu/
- Firewall: allow SSH only (`ufw allow OpenSSH && ufw enable`). Also leave the cloud
  provider's security list closed except 22.

```bash
sudo git clone https://github.com/Samyak-Shreyash/expense_analyzer.git /opt/expense_analyzer
sudo chown -R $USER /opt/expense_analyzer
```

## 2. `.env`

Create `/opt/expense_analyzer/.env` (`chmod 600`). Use **new** secrets, never your local ones:

```bash
openssl rand -base64 48   # JWT_SECRET, OAUTH_SECRET
openssl rand -base64 32   # RUSTFS_SSE_S3_MASTER_KEY
openssl rand -hex 24      # DB_USER_PASSWORD, RUSTFS_SECRET_KEY
```

Same keys as local dev, plus these production-only ones:

| Key | Example | Purpose |
|---|---|---|
| `APP_BASE_URL` | `https://expenses.your-tailnet.ts.net` | Public HTTPS origin |
| `RUSTFS_TAG` | the RustFS version you test with locally | Pins the RustFS image |
| `FRONTEND_PORT` | `3001` (optional) | Loopback port the tunnel points at |

Back up the `.env` somewhere safe. Losing `RUSTFS_SSE_S3_MASTER_KEY` makes stored statements unreadable.

## 3. Deploy

```bash
bash infrastructure/scripts/deploy.sh          # current branch
bash infrastructure/scripts/deploy.sh v0.1.0   # or a tag/SHA
```

It checks `.env`, rebuilds, starts the stack with `compose.prod.yaml`, and waits for
`/actuator/health/readiness`. Only the frontend listens, on `127.0.0.1:${FRONTEND_PORT}`.

Check that nothing else is exposed: `sudo ss -tlnp` should show only sshd and `127.0.0.1:3001`.

## 4. HTTPS in front

HTTPS is mandatory: the refresh-token cookie is `Secure`, so plain `http://<ip>` logins won't stay signed in.

**Private (you + testers): Tailscale**
```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
sudo tailscale serve --bg 3001      # https://<host>.<tailnet>.ts.net
```

**Public URL: Cloudflare Tunnel** (needs a domain on Cloudflare)
```bash
cloudflared tunnel login
cloudflared tunnel create expense-analyzer
cloudflared tunnel route dns expense-analyzer expenses.example.com
# ~/.cloudflared/config.yml: ingress -> service: http://127.0.0.1:3001
sudo cloudflared service install
```

Set `APP_BASE_URL` to whichever URL you use, then redeploy.

## 5. Backups

```bash
sudo crontab -e
30 2 * * * /bin/bash /opt/expense_analyzer/infrastructure/scripts/backup.sh >> /var/log/expense-backup.log 2>&1
```

Writes a Postgres dump and a RustFS archive to `/var/backups/expense-analyzer`, keeping 14 days
(`KEEP_DAYS`). These hold users' financial data: copy them off the host to encrypted storage
(restic, or rclone with a crypt remote). Do a test restore once:

```bash
docker exec -i postgres pg_restore -U "$DB_USER" -d "$DB_NAME" --clean < postgres-<stamp>.dump
```

## Not covered yet

Observability stack (start it with `--profile observability` once its image tags are pinned),
CI-driven deploys, and Redis auth (Redis is internal-only here).
