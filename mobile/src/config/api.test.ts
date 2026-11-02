import { apiBaseUrl } from './api'

describe('apiBaseUrl', () => {
  it('uses the configured URL without a trailing slash', () => {
    expect(apiBaseUrl('https://example.com/api/')).toBe(
      'https://example.com/api',
    )
  })

  it('fails loudly when the URL is not configured', () => {
    expect(() => apiBaseUrl(undefined)).toThrow('EXPO_PUBLIC_API_BASE_URL')
  })
})
