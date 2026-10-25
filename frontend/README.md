# Expense Analyzer — Frontend

React + TypeScript + Vite. Stack decision: [FRONTEND-STACK.md](../docs/frontend/FRONTEND-STACK.md).

## Run locally

Requires Node 22+.

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api to the backend
```

Start the backend separately (`cd ../backend && ./gradlew bootRun`, port 8080).

## Configuration

Copy `.env.example` to `.env.local` to override:

- `VITE_API_BASE_URL` — base URL for API calls (default `/api`).
- `VITE_BACKEND_URL` — where the dev server proxies `/api` (default `http://localhost:8080`).

## Scripts

| Script                            | Does                                       |
| --------------------------------- | ------------------------------------------ |
| `npm run dev`                     | Dev server                                 |
| `npm run build`                   | Type-check and production build to `dist/` |
| `npm run lint`                    | ESLint                                     |
| `npm run format` / `format:check` | Prettier write / check                     |
| `npm run ci`                      | lint + format check + build (use in CI)    |

## Layout

`src/features/` feature modules, `src/components/` shared UI, `src/hooks/` shared hooks,
`src/services/` API access.
