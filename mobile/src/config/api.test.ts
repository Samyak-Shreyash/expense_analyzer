import { apiBaseUrl } from './api'

describe('apiBaseUrl', () => {
  it('uses the configured URL without a trailing slash', () => {
    expect(apiBaseUrl('https://example.com/api/')).toBe(
      'https://example.com/api',
    )
  })

  it('rejects plain http outside development', () => {
    expect(() => apiBaseUrl('http://example.com', false)).toThrow('https')
  })

  it('allows plain http in development', () => {
    expect(apiBaseUrl('http://10.0.2.2:8080', true)).toBe(
      'http://10.0.2.2:8080',
    )
  })

  it('fails loudly when the URL is not configured', () => {
    expect(() => apiBaseUrl(undefined)).toThrow('EXPO_PUBLIC_API_BASE_URL')
  })
})
