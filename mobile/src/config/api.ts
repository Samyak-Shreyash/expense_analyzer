/** Base URL for the backend API, from the environment so each build targets its own server. */
export function apiBaseUrl(value = process.env.EXPO_PUBLIC_API_BASE_URL) {
  if (!value) throw new Error('EXPO_PUBLIC_API_BASE_URL is not set')
  return value.replace(/\/$/, '')
}
