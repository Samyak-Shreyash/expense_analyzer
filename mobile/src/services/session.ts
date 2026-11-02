import { refresh } from '@expense-analyzer/api-client'

/**
 * Whether the app starts signed in. Only the server rejecting the token signs the user out:
 * offline or rate limited, the stored token is still good.
 */
export async function restoreSession(
  loadToken: () => Promise<string | null>,
): Promise<boolean> {
  if (!(await loadToken())) return false
  try {
    return (await refresh()) || (await loadToken()) !== null
  } catch {
    return true
  }
}
