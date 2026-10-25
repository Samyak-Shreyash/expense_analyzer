# Expense Analyzer frontend

React 18 + TypeScript + Vite. Stack decisions are in [FRONTEND-STACK.md](FRONTEND-STACK.md).

## Run locally

Requires Node 18+ (CI uses 22).

```bash
npm install
cp .env.example .env.local   # optional; defaults work against a local backend
npm run dev                  # http://localhost:5173
```

The dev server proxies `/api` to the backend (`VITE_PROXY_TARGET`, default `http://localhost:8080`).
Start the backend with `./gradlew bootRun` from `backend/`.

## Scripts

| Script | Does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type-check and build to `dist/` (used by CI) |
| `npm run lint` | ESLint, fails on any warning |
| `npm run format` / `format:check` | Prettier write / check |

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | Base URL for API calls |
| `VITE_PROXY_TARGET` | `http://localhost:8080` | Backend the dev server proxies `/api` to |

## Layout

```
src/
  features/    one folder per product area (auth, upload, transactions, …)
  components/  shared UI components
  hooks/       shared React hooks
  services/    API client (api.ts)
```
