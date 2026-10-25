# Frontend Stack

Decision for #278. Status: proposed.

## Decision

| Concern | Choice |
|---|---|
| Framework | React 19 single-page app |
| Language | TypeScript (strict) |
| Build / dev server | Vite |
| Routing | React Router |
| Server state | `fetch` wrapper + custom hooks; add TanStack Query only if caching/refetch logic starts repeating |
| Client state | React context (auth session only) |
| HTTP client | Native `fetch` behind one `api.ts` wrapper |
| UI components | None at MVP; plain CSS modules. Revisit with #286 (accessibility) |
| Unit / component tests | Vitest + React Testing Library |
| End-to-end tests | Playwright against the running backend |
| Lint / format | ESLint (typescript-eslint) + Prettier |

## Rationale against MVP requirements

- **Pages are all behind login** (upload #281, transactions #282, dashboard #283, AI chat #284).
  No SEO or server rendering need, so Next.js/SvelteKit add a Node server for no gain.
  A static SPA build can be served by any web server or the backend.
- **React + Vite + TypeScript** is the most common, best-documented combination and
  matches the abandoned local scaffold already in `frontend/node_modules`.
- **`fetch` over Axios:** one wrapper covers the auth header and 401 handling; no extra dependency.
- **No component library yet:** the MVP screens are forms, a table and a few charts.
  Pick one (e.g. Radix primitives) if #286 shows hand-rolled accessibility is costly.
- **Playwright** covers the #280 criterion "integration tested against `/api/auth/**`".

## Alignment with backend auth and CSRF

Checked against `backend/src/main/java/com/expenseanalyzer/auth/`:

- **Endpoints:** `POST /api/auth/login`, `POST /api/auth/register`,
  `GET /api/auth/register/check-email`. Everything else requires
  `Authorization: Bearer <jwt>` (HS256, validated by `oauth2ResourceServer`).
- **Token storage:** in memory (auth context) only. Not `localStorage` (readable by any XSS)
  and not cookies.
- **CSRF:** the backend disables CSRF for `/api/**` *because* auth is header-only and the
  browser never sends credentials automatically. Keep it that way: if tokens ever move to
  cookies, CSRF protection must be re-enabled first.
- **Session expiry:** there is **no refresh endpoint** today. On any 401 the app clears the
  token and redirects to login; a page reload also logs the user out. A refresh flow needs
  a backend issue first (e.g. `POST /api/auth/refresh` with an HttpOnly, SameSite=Strict
  refresh cookie, which then needs CSRF handling on that one endpoint).
- **CORS:** the backend has no CORS config. In development the Vite dev server proxies
  `/api` to the backend, so the browser sees one origin. Production must serve the SPA from
  the same origin or add an explicit CORS allow-list.

## Open questions

- Is logout-on-reload acceptable for MVP, or should a refresh endpoint be added before #280?
- Charting library for #283: decide in that issue.
