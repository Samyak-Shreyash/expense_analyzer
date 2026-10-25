// Base URL for backend calls. Defaults to same-origin `/api` (proxied by Vite in dev).
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

// Access token lives in memory only (see docs/frontend/FRONTEND-STACK.md).
// The refresh token is an HttpOnly cookie the browser sends to /api/auth only.
let accessToken: string | null = null
let refreshing: Promise<boolean> | null = null
let onAuthLost = () => {}
let onForbidden = () => {}

export function setAuthHandlers(handlers: {
  onAuthLost: () => void
  onForbidden: () => void
}) {
  onAuthLost = handlers.onAuthLost
  onForbidden = handlers.onForbidden
}

function post(path: string, body?: unknown) {
  return fetch(API_BASE_URL + path, {
    method: 'POST',
    credentials: 'same-origin',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
}

/**
 * Exchanges the refresh cookie for a new access token.
 * Shared promise: refresh tokens are single-use, so concurrent callers must not race.
 */
export function refresh(): Promise<boolean> {
  refreshing ??= post('/auth/refresh')
    .then(async (res) => {
      accessToken = res.ok ? (await res.json()).token : null
      return res.ok
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

export async function login(email: string, password: string) {
  const res = await post('/auth/login', { email, password })
  if (!res.ok) return false
  accessToken = (await res.json()).token
  return true
}

/** Returns null on success, otherwise a user-facing error message. */
export async function register(email: string, password: string) {
  const res = await post('/auth/register', { email, password })
  if (res.ok) return null
  return res.status === 409
    ? 'That email is already registered.'
    : 'Registration failed.'
}

export async function logout() {
  accessToken = null
  await post('/auth/logout')
}

/** fetch with the Bearer token; refreshes once on 401, then gives up. */
export async function apiFetch(path: string, init: RequestInit = {}) {
  const send = () =>
    fetch(API_BASE_URL + path, {
      ...init,
      headers: {
        ...init.headers,
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    })

  let res = await send()
  if (res.status === 401 && (await refresh())) {
    res = await send()
  }
  if (res.status === 401) {
    accessToken = null
    onAuthLost()
  } else if (res.status === 403) {
    onForbidden()
  }
  return res
}
