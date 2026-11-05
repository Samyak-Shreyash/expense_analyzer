/** How an app carries the refresh token: the browser's cookie, or a store the app provides. */
export type SessionTransport = {
  credentials?: RequestCredentials
  headers?: Record<string, string>
  /** Absent for cookies. The token then travels in JSON bodies. */
  tokens?: {
    load(): Promise<string | null>
    save(token: string | null): Promise<void>
  }
}

// Access token lives in memory only (see docs/frontend/FRONTEND-STACK.md).
let baseUrl = ''
let transport: SessionTransport = {}
let accessToken: string | null = null
let refreshing: Promise<boolean> | null = null
let onAuthLost = () => {}
let onForbidden = () => {}

/** Each app calls this once at startup, before any request. */
export function configureApiClient(config: {
  baseUrl: string
  transport: SessionTransport
}) {
  baseUrl = config.baseUrl
  transport = config.transport
  accessToken = null
  refreshing = null
}

export function setAuthHandlers(handlers: {
  onAuthLost: () => void
  onForbidden: () => void
}) {
  onAuthLost = handlers.onAuthLost
  onForbidden = handlers.onForbidden
}

function post(path: string, body?: unknown) {
  const headers = {
    ...transport.headers,
    ...(body ? { 'Content-Type': 'application/json' } : {}),
  }
  return fetch(baseUrl + path, {
    method: 'POST',
    credentials: transport.credentials,
    headers: Object.keys(headers).length ? headers : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
}

async function tokenBody() {
  const refreshToken = await transport.tokens?.load()
  return refreshToken ? { refreshToken } : undefined
}

async function storeSession(res: Response) {
  const body = await res.json()
  accessToken = body.token
  if (body.refreshToken) await transport.tokens?.save(body.refreshToken)
}

/**
 * Exchanges the refresh token for a new access token.
 * Shared promise: refresh tokens are single-use, so concurrent callers must not race.
 * Only a 401 discards the stored token: a 429 or a network failure leaves it usable.
 */
export function refresh(): Promise<boolean> {
  refreshing ??= tokenBody()
    .then((body) => post('/auth/refresh', body))
    .then(async (res) => {
      if (res.ok) {
        await storeSession(res)
      } else {
        accessToken = null
        if (res.status === 401) await transport.tokens?.save(null)
      }
      return res.ok
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

export async function login(email: string, password: string) {
  const res = await post('/auth/login', { email, password })
  if (res.status === 429)
    throw new Error('Too many attempts. Try again in a few minutes.')
  if (!res.ok) return false
  await storeSession(res)
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
  const body = await tokenBody()
  await transport.tokens?.save(null) // local sign-out holds even if the server call fails
  await post('/auth/logout', body)
}

/** fetch with the Bearer token; refreshes once on 401, then gives up. */
export async function apiFetch(path: string, init: RequestInit = {}) {
  const send = () =>
    fetch(baseUrl + path, {
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

export type UploadResult = { status: number; body: unknown }

/** A browser File, or React Native's description of a file on the device. */
export type UploadFile = Blob | { uri: string; name: string; type: string }

/**
 * Multipart upload with progress (fetch can't report upload progress).
 * Same auth rules as apiFetch: Bearer token, one refresh on 401.
 */
export async function apiUpload(
  path: string,
  file: UploadFile,
  onProgress: (fraction: number) => void,
  fields: Record<string, string> = {},
): Promise<UploadResult> {
  const send = () =>
    new Promise<UploadResult>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', baseUrl + path)
      if (accessToken)
        xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`)
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) onProgress(e.loaded / e.total)
      }
      xhr.onload = () => {
        let body: unknown = null
        try {
          body = JSON.parse(xhr.responseText)
        } catch {
          // empty or non-JSON body
        }
        resolve({ status: xhr.status, body })
      }
      xhr.onerror = () => reject(new Error('network'))
      const form = new FormData()
      form.append('file', file as Blob)
      for (const [k, v] of Object.entries(fields)) form.append(k, v)
      xhr.send(form)
    })

  let res = await send()
  if (res.status === 401 && (await refresh())) {
    res = await send()
  }
  if (res.status === 401) {
    accessToken = null
    onAuthLost()
  }
  return res
}
