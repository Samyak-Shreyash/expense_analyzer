import { createAppLock, shouldLock, LOCK_AFTER_MS } from './appLock'

function memoryStore() {
  const values = new Map<string, string>()
  return {
    values,
    get: async (key: string) => values.get(key) ?? null,
    set: async (key: string, value: string) => void values.set(key, value),
    remove: async (key: string) => void values.delete(key),
  }
}

const hash = async (value: string) => `hashed:${value}`

describe('app lock', () => {
  it('is not set up until a PIN is chosen', async () => {
    const lock = createAppLock(memoryStore(), hash)
    expect(await lock.isSetUp()).toBe(false)
    await lock.setPin('123456')
    expect(await lock.isSetUp()).toBe(true)
  })

  it('rejects a PIN shorter than 6 digits or not numeric', async () => {
    const lock = createAppLock(memoryStore(), hash)
    await expect(lock.setPin('12345')).rejects.toThrow()
    await expect(lock.setPin('12345a')).rejects.toThrow()
  })

  it('never stores the PIN itself', async () => {
    const store = memoryStore()
    await createAppLock(store, hash).setPin('123456')
    expect([...store.values.values()]).not.toContain('123456')
  })

  it('accepts the right PIN and clears earlier failures', async () => {
    const lock = createAppLock(memoryStore(), hash)
    await lock.setPin('123456')
    expect(await lock.verify('000000')).toBe('wrong')
    expect(await lock.verify('123456')).toBe('ok')
    for (let i = 0; i < 4; i++) {
      expect(await lock.verify('000000')).toBe('wrong')
    }
  })

  it('demands a password login after 5 failed attempts, even across restarts', async () => {
    const store = memoryStore()
    await createAppLock(store, hash).setPin('123456')
    for (let i = 0; i < 4; i++) {
      expect(await createAppLock(store, hash).verify('000000')).toBe('wrong')
    }
    expect(await createAppLock(store, hash).verify('000000')).toBe('exhausted')
  })

  it('refuses even the right PIN once attempts are used up', async () => {
    const lock = createAppLock(memoryStore(), hash)
    await lock.setPin('123456')
    for (let i = 0; i < 5; i++) await lock.verify('000000')
    expect(await lock.isExhausted()).toBe(true)
    expect(await lock.verify('123456')).toBe('exhausted')
  })

  it('clear removes everything', async () => {
    const store = memoryStore()
    const lock = createAppLock(store, hash)
    await lock.setPin('123456')
    await lock.verify('000000')
    await lock.clear()
    expect(store.values.size).toBe(0)
  })
})

describe('shouldLock', () => {
  it('locks after a minute in the background, not before', () => {
    expect(shouldLock(1000, 1000 + LOCK_AFTER_MS - 1)).toBe(false)
    expect(shouldLock(1000, 1000 + LOCK_AFTER_MS)).toBe(true)
  })

  it('locks if the clock moved backwards', () => {
    expect(shouldLock(5000, 1000)).toBe(true)
  })
})
