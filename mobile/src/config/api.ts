/** Base URL for the backend API, from the environment so each build targets its own server. */
export function apiBaseUrl(
  value = process.env.EXPO_PUBLIC_API_BASE_URL,
  dev = __DEV__,
) {
  if (!value) throw new Error('EXPO_PUBLIC_API_BASE_URL is not set')
  // Tokens travel to this URL: plain http is only for a local dev server.
  if (!dev && !value.startsWith('https://')) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL must use https')
  }
  return value.replace(/\/$/, '')
}
