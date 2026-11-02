import { configureApiClient, login } from '@expense-analyzer/api-client'
import { mobileTransport } from './api'

it('logs in through the shared client as a mobile client, keeping the refresh token in the store', async () => {
  let stored: string | null = null
  const fetchMock = jest.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ token: 'a', refreshToken: 'r1' }),
  })
  global.fetch = fetchMock

  configureApiClient({
    baseUrl: 'https://example.com/api',
    transport: mobileTransport({
      load: async () => stored,
      save: async (token) => {
        stored = token
      },
    }),
  })

  expect(await login('a@example.com', 'long-enough-1')).toBe(true)
  expect(stored).toBe('r1')
  expect(fetchMock.mock.calls[0][1].headers['X-Client-Type']).toBe('mobile')
})
