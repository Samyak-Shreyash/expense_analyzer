import { configureApiClient } from '@expense-analyzer/api-client'

// The refresh token is an HttpOnly cookie the browser sends to /api/auth only.
configureApiClient({
  // Defaults to same-origin `/api` (proxied by Vite in dev).
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
  transport: { credentials: 'same-origin' },
})

export {
  apiFetch,
  login,
  logout,
  refresh,
  register,
  setAuthHandlers,
} from '@expense-analyzer/api-client'
