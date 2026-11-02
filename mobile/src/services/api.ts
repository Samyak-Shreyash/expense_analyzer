import {
  configureApiClient,
  type SessionTransport,
} from '@expense-analyzer/api-client'
import * as Crypto from 'expo-crypto'
import * as SecureStore from 'expo-secure-store'
import { apiBaseUrl } from '../config/api'

const REFRESH_TOKEN_KEY = 'refresh-token'

/** Device-only: never in backups, and readable only while the device is unlocked. */
export const SECURE_OPTIONS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
}

export const secureTokens: NonNullable<SessionTransport['tokens']> = {
  load: () => SecureStore.getItemAsync(REFRESH_TOKEN_KEY, SECURE_OPTIONS),
  save: (token) =>
    token
      ? SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token, SECURE_OPTIONS)
      : SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY, SECURE_OPTIONS),
}

/** Refresh token in JSON bodies, kept in the given store. */
export function mobileTransport(
  tokens: NonNullable<SessionTransport['tokens']>,
): SessionTransport {
  return {
    headers: { 'X-Client-Type': 'mobile' },
    requestHeaders: () => ({ 'X-Correlation-ID': Crypto.randomUUID() }),
    tokens,
  }
}

export function startApiClient() {
  configureApiClient({
    baseUrl: apiBaseUrl(),
    transport: mobileTransport(secureTokens),
  })
}
