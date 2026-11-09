# Deploying to a single VPS

Interim setup: the backend Compose stack on one Linux VM, reachable only over HTTPS
through a Cloudflare Tunnel. No public ports. The web frontend is hosted on Vercel, which
proxies `/api/*` to the tunnel so the browser sees a single origin.

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
| `APP_BASE_URL` | `https://expenses.example.com` | Public HTTPS origin users open (the Vercel domain) |
| `RUSTFS_TAG` | the RustFS version you test with locally | Pins the RustFS image |
| `CLOUDFLARE_TUNNEL_TOKEN` | from the Cloudflare dashboard (step 4) | Runs the `cloudflared` container |
| `PROXY_SHARED_SECRET` | `openssl rand -hex 32` | Same value as in Vercel (step 5); lets the backend trust the user IP Vercel forwards |

Back up the `.env` somewhere safe. Losing `RUSTFS_SSE_S3_MASTER_KEY` makes stored statements unreadable.

## 3. Deploy

```bash
bash infrastructure/scripts/deploy.sh          # current branch
bash infrastructure/scripts/deploy.sh v0.1.0   # or a tag/SHA
```

It checks `.env`, rebuilds, starts the stack with `compose.prod.yaml`, and waits for
`/actuator/health/readiness`. No container publishes a host port; the backend is reached
only through the `cloudflared` container.

Check that nothing is exposed: `sudo ss -tlnp` should show only sshd.

## 4. Cloudflare Tunnel (HTTPS for the API)

HTTPS is mandatory: the refresh-token cookie is `Secure`. Needs a domain on Cloudflare.

1. Cloudflare dashboard → Zero Trust → Networks → Tunnels → **Create a tunnel** (cloudflared).
2. Copy the token into `.env` as `CLOUDFLARE_TUNNEL_TOKEN`. Skip the install command it shows;
   the `cloudflared` service in `compose.prod.yaml` runs the connector.
3. Add a public hostname, e.g. `api.example.com`, with service `http://backend:<SERVER_PORT>`.
4. Redeploy.

## 5. Frontend on Vercel

1. Import the repo in Vercel. Set **Root Directory** to `frontend` and enable
   **Include files outside the root directory** (the build needs `packages/`).
2. Add environment variables (Production): `API_ORIGIN` = your tunnel hostname
   (`https://api.example.com`) and `PROXY_SHARED_SECRET` = the same value as the VM's `.env`.
   `frontend/middleware.ts` proxies `/api/*` there with the user's IP and that secret.
3. Add your domain (e.g. `expenses.example.com`) and set `APP_BASE_URL` in the VM's `.env`
   to it, then redeploy the backend.

`frontend/vercel.json` also sets the security headers the nginx image used to send.

## 6. Backups

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

