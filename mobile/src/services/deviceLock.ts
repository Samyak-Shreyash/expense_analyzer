import * as Crypto from 'expo-crypto'
import * as LocalAuthentication from 'expo-local-authentication'
import * as SecureStore from 'expo-secure-store'
import { SECURE_OPTIONS } from './api'
import { createAppLock } from './appLock'

export const appLock = createAppLock(
  {
    get: (key) => SecureStore.getItemAsync(key, SECURE_OPTIONS),
    set: (key, value) => SecureStore.setItemAsync(key, value, SECURE_OPTIONS),
    remove: (key) => SecureStore.deleteItemAsync(key, SECURE_OPTIONS),
  },
  (value) =>
    Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, value),
  Crypto.randomUUID,
)

/** False when the device has no biometrics enrolled or the user cancels; the PIN remains. */
export async function unlockWithBiometrics() {
  if (
    !(await LocalAuthentication.hasHardwareAsync()) ||
    !(await LocalAuthentication.isEnrolledAsync())
  ) {
    return false
  }
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: 'Unlock Expense Analyzer',
    disableDeviceFallback: true,
    biometricsSecurityLevel: 'strong',
  })
  return result.success
}
