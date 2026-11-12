import { test as setup, type Page } from '@playwright/test'
import { writeFileSync } from 'node:fs'
import { register } from './helpers'

/**
 * Refresh tokens rotate and a reused one is rejected, so the saved cookie must be the latest one:
 * wait until no /api/auth/refresh is in flight and none has started for a moment.
 */
async function refreshSettled(page: Page) {
  let pending = 0
  let last = Date.now()
  const isRefresh = (url: string) => url.endsWith('/api/auth/refresh')
  page.on('request', (r) => isRefresh(r.url()) && (pending++, (last = Date.now())))
  const done = (r: { url(): string }) => isRefresh(r.url()) && ((pending = Math.max(0, pending - 1)), (last = Date.now()))
  page.on('requestfinished', done)
  page.on('requestfailed', done)
  await waitUntil(() => pending === 0 && Date.now() - last > 1_000)
}

async function waitUntil(ok: () => boolean) {
  for (let i = 0; i < 100 && !ok(); i++) await new Promise((r) => setTimeout(r, 100))
}

setup('register alice, bob, carol and dave', async ({ browser }) => {
  setup.setTimeout(120_000)
  const users: Record<string, string> = {}
  for (const name of ['alice', 'bob', 'carol', 'dave']) {
    const context = await browser.newContext()
    const page = await context.newPage()
    users[name] = await register(page)
    await page.getByRole('heading', { level: 1, name: 'Overview' }).waitFor()
    await refreshSettled(page)
    await context.storageState({ path: `.auth/${name}.json` })
    await context.close()
  }
  writeFileSync('.auth/users.json', JSON.stringify(users))
})
