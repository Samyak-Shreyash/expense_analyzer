import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  apiFetch,
  configureApiClient,
  login,
  logout,
  refresh,
} from '@expense-analyzer/api-client'

const json = (status: number, body: unknown = {}) =>
  new Response(JSON.stringify(body), { status })

const fetchMock = vi.fn<typeof fetch>()
const call = (i: number) => fetchMock.mock.calls[i]

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

describe('cookie transport (web)', () => {
  beforeEach(() =>
    configureApiClient({
      baseUrl: '/api',
      transport: { credentials: 'same-origin' },
    }),
  )

  it('sends no token in the body and no client type', async () => {
    fetchMock.mockResolvedValue(json(200, { token: 'a1' }))
    await refresh()
    expect(call(0)[0]).toBe('/api/auth/refresh')
    expect(call(0)[1]?.credentials).toBe('same-origin')
    expect(call(0)[1]?.body).toBeUndefined()
    expect(call(0)[1]?.headers).toBeUndefined()
  })
})

describe('body transport (mobile)', () => {
  let stored: string | null
  const transport = {
    headers: { 'X-Client-Type': 'mobile' },
    tokens: {
      load: async () => stored,
      save: async (token: string | null) => {
        stored = token
      },
    },
  }

  beforeEach(() => {
    stored = null
    configureApiClient({ baseUrl: 'https://x/api', transport })
  })

  it('stores the refresh token from login', async () => {
    fetchMock.mockResolvedValue(json(200, { token: 'a', refreshToken: 'r1' }))
    expect(await login('a@b.c', 'pw')).toBe(true)
    expect(stored).toBe('r1')
    expect(new Headers(call(0)[1]?.headers).get('X-Client-Type')).toBe('mobile')
  })

  it('sends the stored token on refresh and stores the rotated one', async () => {
    stored = 'r1'
    fetchMock.mockResolvedValue(json(200, { token: 'a', refreshToken: 'r2' }))
    expect(await refresh()).toBe(true)
    expect(call(0)[1]?.body).toBe(JSON.stringify({ refreshToken: 'r1' }))
    expect(stored).toBe('r2')
  })

  it('shares one refresh between concurrent callers', async () => {
    stored = 'r1'
    fetchMock.mockResolvedValue(json(200, { token: 'a', refreshToken: 'r2' }))
    await Promise.all([refresh(), refresh()])
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('clears the stored token when refresh answers 401', async () => {
    stored = 'r1'
    fetchMock.mockResolvedValue(json(401))
    expect(await refresh()).toBe(false)
    expect(stored).toBeNull()
  })

  it('keeps the stored token when refresh answers 429', async () => {
    stored = 'r1'
    fetchMock.mockResolvedValue(json(429))
    expect(await refresh()).toBe(false)
    expect(stored).toBe('r1')
  })

  it('keeps the stored token when the network fails', async () => {
    stored = 'r1'
    fetchMock.mockRejectedValue(new Error('offline'))
    await expect(refresh()).rejects.toThrow('offline')
    expect(stored).toBe('r1')
  })

  it('revokes with the stored token on logout, then clears it', async () => {
    stored = 'r1'
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await logout()
    expect(call(0)[1]?.body).toBe(JSON.stringify({ refreshToken: 'r1' }))
    expect(stored).toBeNull()
  })

  it('retries a request once with the new access token after a 401', async () => {
    stored = 'r1'
    fetchMock
      .mockResolvedValueOnce(json(401))
      .mockResolvedValueOnce(json(200, { token: 'new', refreshToken: 'r2' }))
      .mockResolvedValueOnce(json(200))
    const res = await apiFetch('/transactions')
    expect(res.status).toBe(200)
    expect(new Headers(call(2)[1]?.headers).get('Authorization')).toBe(
      'Bearer new',
    )
  })
})
