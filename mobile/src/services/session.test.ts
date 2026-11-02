import { configureApiClient } from '@expense-analyzer/api-client'
import { restoreSession } from './session'

const response = (status: number, body: unknown = {}) => ({
  ok: status < 400,
  status,
  json: async () => body,
})

describe('restoreSession', () => {
  let stored: string | null
  const fetchMock = jest.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    global.fetch = fetchMock
    stored = 'r1'
    configureApiClient({
      baseUrl: 'https://example.com/api',
      transport: {
        tokens: {
          load: async () => stored,
          save: async (token) => {
            stored = token
          },
        },
      },
    })
  })

  it('is signed out without a stored token, and makes no request', async () => {
    stored = null
    expect(await restoreSession(async () => stored)).toBe(false)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('survives an app restart using the stored token', async () => {
    fetchMock.mockResolvedValue(
      response(200, { token: 'a', refreshToken: 'r2' }),
    )
    expect(await restoreSession(async () => stored)).toBe(true)
  })

  it('is signed out when the server rejects the token', async () => {
    fetchMock.mockResolvedValue(response(401))
    expect(await restoreSession(async () => stored)).toBe(false)
  })

  it('stays signed in when offline', async () => {
    fetchMock.mockRejectedValue(new Error('offline'))
    expect(await restoreSession(async () => stored)).toBe(true)
  })

  it('stays signed in when rate limited', async () => {
    fetchMock.mockResolvedValue(response(429))
    expect(await restoreSession(async () => stored)).toBe(true)
  })
})
