const PIN_KEY = 'app-lock-pin'
const SALT_KEY = 'app-lock-salt'
const FAILURES_KEY = 'app-lock-failures'
const MAX_FAILURES = 5

export const LOCK_AFTER_MS = 60_000

type Store = {
  get(key: string): Promise<string | null>
  set(key: string, value: string): Promise<void>
  remove(key: string): Promise<void>
}

export type VerifyResult = 'ok' | 'wrong' | 'exhausted'

/** See docs/architecture/ADR/SECURITY-REQUIREMENTS.md section 45.2. The lock is a local gate, not authentication. */
export function createAppLock(
  store: Store,
  hash: (value: string) => Promise<string>,
  newSalt: () => string = () => '',
) {
  const exhausted = async () =>
    Number((await store.get(FAILURES_KEY)) ?? 0) >= MAX_FAILURES

  return {
    isSetUp: async () => (await store.get(PIN_KEY)) !== null,

    async setPin(pin: string) {
      if (!/^\d{6,}$/.test(pin))
        throw new Error('PIN must be at least 6 digits')
      const salt = newSalt()
      await store.set(SALT_KEY, salt)
      await store.set(PIN_KEY, await hash(salt + pin))
      await store.remove(FAILURES_KEY)
    },

    isExhausted: exhausted,

    async verify(pin: string): Promise<VerifyResult> {
      if (await exhausted()) return 'exhausted'
      const salt = (await store.get(SALT_KEY)) ?? ''
      if ((await hash(salt + pin)) === (await store.get(PIN_KEY))) {
        await store.remove(FAILURES_KEY)
        return 'ok'
      }
      const failures = Number((await store.get(FAILURES_KEY)) ?? 0) + 1
      await store.set(FAILURES_KEY, String(failures))
      return failures >= MAX_FAILURES ? 'exhausted' : 'wrong'
    },

    /** A biometric unlock also clears the PIN failure count. */
    resetFailures: () => store.remove(FAILURES_KEY),

    async clear() {
      await store.remove(PIN_KEY)
      await store.remove(SALT_KEY)
      await store.remove(FAILURES_KEY)
    },
  }
}

export function shouldLock(backgroundedAt: number, now: number) {
  return now < backgroundedAt || now - backgroundedAt >= LOCK_AFTER_MS
}
