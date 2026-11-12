import { test as setup } from '@playwright/test'
import { writeFileSync } from 'node:fs'
import { register } from './helpers'

setup('register alice, bob and carol', async ({ browser }) => {
  const users: Record<string, string> = {}
  for (const name of ['alice', 'bob', 'carol']) {
    const context = await browser.newContext()
    const page = await context.newPage()
    users[name] = await register(page)
    await context.storageState({ path: `.auth/${name}.json` })
    await context.close()
  }
  writeFileSync('.auth/users.json', JSON.stringify(users))
})
