import { expect, test, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { nav, OVERLAPPING_STATEMENT, PASSWORD, STATEMENT, uploadStatement } from './helpers'

// alice's journey runs in one page: her saved session is single-use. Users come from auth.setup.ts.
const users = () => JSON.parse(readFileSync('.auth/users.json', 'utf8')) as Record<'alice' | 'bob', string>

// The 4 rows of fixtures/statement.csv, as the table shows their amounts (descriptions are cleaned up for display)
const AMOUNTS = ['-₹1,840.00', '₹25.00', '-₹312.40', '₹1,020.00']
const UBER = '-₹312.40'

test.describe.configure({ mode: 'serial' })

let page: Page
test.beforeAll(async ({ browser }) => {
  page = await (await browser.newContext({ storageState: '.auth/alice.json' })).newPage()
})

test('@smoke signed-in user lands on the dashboard', async () => {
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible()
})

test('@smoke uploaded statement shows its transactions', async () => {
  await page.goto('/dashboard')
  await uploadStatement(page)
  await nav(page, 'Transactions')
  for (const amount of AMOUNTS) {
    await expect(page.getByRole('cell', { name: amount, exact: true })).toBeVisible()
  }
})

test('@smoke uploading the same file again is refused', async () => {
  await page.goto('/dashboard')
  await nav(page, 'Statements')
  await page.getByLabel('Choose a file').setInputFiles(STATEMENT)
  await expect(page.getByRole('alert')).toHaveText('You have already uploaded this file.')
})

test('@smoke an overlapping statement adds only its new rows', async () => {
  await uploadStatement(page, OVERLAPPING_STATEMENT)
  await nav(page, 'Transactions')
  await expect(page.getByRole('cell', { name: '-₹1,500.00', exact: true })).toBeVisible()
  for (const amount of AMOUNTS) {
    await expect(page.getByRole('cell', { name: amount, exact: true })).toHaveCount(1)
  }
})

test('@smoke another user never sees my transactions', async ({ browser }) => {
  const bob = await (await browser.newContext({ storageState: '.auth/bob.json' })).newPage()
  await bob.goto('/transactions')
  await expect(bob.getByRole('heading', { level: 1, name: 'Transactions' })).toBeVisible()
  await expect(bob.getByText('No transactions yet.')).toBeVisible()
  await expect(bob.getByRole('cell', { name: UBER, exact: true })).toHaveCount(0)
})

// Last: a password login revokes the shared page's session.
test('@smoke log in with email and password', async ({ browser }) => {
  const fresh = await (await browser.newContext()).newPage()
  await fresh.goto('/dashboard')
  await expect(fresh).toHaveURL(/\/login/)
  await fresh.getByLabel('Email').fill(users().alice)
  await fresh.getByLabel('Password').fill(PASSWORD)
  await fresh.getByRole('button', { name: 'Log in' }).click()
  await expect(fresh).toHaveURL(/\/dashboard/)
})
