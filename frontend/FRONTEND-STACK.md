# Frontend stack

Decision record for FE-001 (#278). It covers what the MVP frontend needs: auth, statement upload, a transaction list with filters, a spending dashboard and an AI query chat.

## Decisions

| Area | Choice | Why |
|---|---|---|
| Framework | React 18 (SPA) | Already in `package.json`. Every screen sits behind a login, so SSR and SEO (the reasons to pick Next.js) don't apply. |
| Language | TypeScript, `strict: true` | Typed DTOs that mirror the backend records catch contract drift at compile time. |
| Build | Vite 6 | Already installed. Fast dev server, and its built-in proxy removes CORS from local development (see below). |
| Routing | React Router 7 (`react-router`) | The standard for SPAs. Gives route guards for authenticated pages and nested layouts. |
| Server state | TanStack Query 5 | Transactions, analytics and the upload status are all server data. Query handles caching, polling (statement processing) and invalidation after upload. |
| Client state | React context + `useState` | The only global client state is the auth token and the current user. No Redux or Zustand until a real need shows up. |
| HTTP client | Axios | Already installed. Interceptors are where the bearer token, `X-Correlation-ID` and the 401 redirect live (FE-008). |
| UI components | No kit. Plain CSS modules over native elements; add Radix primitives per component only when a native element can't do the job accessibly (dialog, combobox, menu). | The product needs its own visual identity, not a stock MUI or shadcn look. Native `<input type="date">`, `<select>` and `<input type="file">` already cover most of the forms. |
| Charts | Recharts, added in FE-006 | Declarative React API that covers the bar and line charts the dashboard needs. |
| Unit/component tests | Vitest + Testing Library | Shares Vite's config and transforms, and has a Jest-compatible API. |
| E2E tests | Playwright | Required by FE-010. Records traces and screenshots on failure for CI artifacts. |
| Lint/format | ESLint (flat config) + Prettier | Already installed. |

## Alignment with backend auth

How the backend works today (`backend/.../auth/config/SecurityConfiguration.java`):

- `POST /api/auth/register` and `POST /api/auth/login` return `{ "token": "<jwt>" }` in the response body (`AuthResponse`).
- Sessions are `STATELESS`.
- CSRF is enabled for everything except `/api/auth/**`.

Frontend strategy:

- **Token transport:** send `Authorization: Bearer <jwt>` on every API call through an Axios request interceptor. No auth cookies.
- **Token storage:** keep the token in memory and mirror it in `sessionStorage`, so it survives a reload but not closing the tab. Never use `localStorage`. On a 401, clear the token and redirect to `/login`.
- **CSRF:** CSRF attacks need a credential the browser attaches automatically, such as a cookie. A bearer header is never attached automatically, so with bearer-only auth CSRF protection adds nothing. FE-008 should still be done, but by settling the backend side (below) rather than by fetching tokens.
- **CORS:** in development the Vite dev server proxies `/api` to `http://localhost:8080`, so the browser only ever sees one origin and needs no CORS config. In production, serve the built SPA and the API from the same origin (a reverse proxy routes `/api` to the backend). The backend only needs a CORS config if the two ever have to run on different origins.
- **API base URL:** set `VITE_API_BASE_URL` per environment, defaulting to `/api`.

### Backend gaps found while confirming alignment

These need backend issues before FE-003, FE-008 and FE-010 can pass end to end:

1. **Requests are never authenticated with the JWT.** The filter chain doesn't configure `oauth2ResourceServer().jwt()` or any token filter. Every protected endpoint, such as `GET /api/user/me`, will reject the token it was issued.
2. **CSRF blocks authenticated writes.** CSRF is on with the default session-based token repository while sessions are stateless. A bearer-authenticated `POST` to a protected endpoint (statement upload, for example) will get a 403 and there's no way to obtain a valid token. Fix: disable CSRF for `/api/**`, since auth is by header only.
3. **The JWT TTL variables look swapped.** In `application.yml`, `tokenExpirationSeconds` reads `JWT_REFRESH_SECONDS` and `refreshTokenExpirationSeconds` reads `JWT_EXPIRATION_SECONDS`.

## Out of scope for the MVP

SSR or Next.js, a global state library, a component kit, i18n, a PWA or offline support.
