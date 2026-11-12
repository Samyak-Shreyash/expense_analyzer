import { expect, test, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { nav, PASSWORD, uploadStatement } from './helpers'

// alice's journey runs in one page: her saved session is single-use. Users come from auth.setup.ts.
const users = () => JSON.parse(readFileSync('.auth/users.json', 'utf8')) as Record<'alice' | 'bob', string>

// The 4 rows of fixtures/statement.csv
const DESCRIPTIONS = ['Electricity bill MSEDCL', 'Cashback', 'UPI Uber India', 'Interest']

test.describe.configure({ mode: 'serial' })

let page: Page
test.beforeAll(async ({ browser }) => {
  page = await (await browser.newContext({ storageState: '.auth/alice.json' })).newPage()
})

test('@smoke signed-in user lands on the dashboard', async () => {
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible()
})

test('@smoke log in with email and password', async ({ browser }) => {
  const fresh = await (await browser.newContext()).newPage()
  await fresh.goto('/dashboard')
  await expect(fresh).toHaveURL(/\/login/)
  await fresh.getByLabel('Email').fill(users().alice)
  await fresh.getByLabel('Password').fill(PASSWORD)
  await fresh.getByRole('button', { name: 'Log in' }).click()
  await expect(fresh).toHaveURL(/\/dashboard/)
})

test('@smoke uploaded statement shows its transactions', async () => {
  await page.goto('/dashboard')
  await uploadStatement(page)
  await nav(page, 'Transactions')
  for (const d of DESCRIPTIONS) {
    await expect(page.getByText(d).first()).toBeVisible()
  }
})

test('@smoke uploading the same file again adds no duplicates', async () => {
  await page.goto('/dashboard')
  await uploadStatement(page)
  await nav(page, 'Transactions')
  await expect(page.getByText('UPI Uber India')).toHaveCount(1)
})

test('@smoke another user never sees my transactions', async ({ browser }) => {
  const bob = await (await browser.newContext({ storageState: '.auth/bob.json' })).newPage()
  await bob.goto('/transactions')
  await expect(bob.getByRole('heading', { level: 1, name: 'Transactions' })).toBeVisible()
  await expect(bob.getByText('No transactions yet.')).toBeVisible()
  await expect(bob.getByText('UPI Uber India')).toHaveCount(0)
})
