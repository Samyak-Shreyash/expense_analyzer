import type { SessionTransport } from '@expense-analyzer/api-client'

/** Refresh token in JSON bodies, kept in the given store (Keychain/Keystore, added by #333). */
export function mobileTransport(
  tokens: NonNullable<SessionTransport['tokens']>,
): SessionTransport {
  return { headers: { 'X-Client-Type': 'mobile' }, tokens }
}
