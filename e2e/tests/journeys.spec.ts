import { expect, test } from '@playwright/test'
import { PASSWORD, register, uploadStatement } from './helpers'

// The 4 rows of fixtures/statement.csv
const DESCRIPTIONS = ['Electricity bill MSEDCL', 'Cashback', 'UPI Uber India', 'Interest']

test('@smoke register lands on the dashboard', async ({ page }) => {
  await register(page)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
})

test('@smoke log out and back in', async ({ page }) => {
  const email = await register(page)
  await page.context().clearCookies()
  await page.evaluate(() => sessionStorage.clear())
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/login/)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: /log in|sign in/i }).click()
  await expect(page).toHaveURL(/\/dashboard/)
})

test('@smoke uploaded statement shows its transactions', async ({ page }) => {
  await register(page)
  await uploadStatement(page)
  await expect(page.getByRole('cell', { name: 'statement.csv' })).toBeVisible()

  await page.getByRole('link', { name: /transactions/i }).first().click()
  for (const d of DESCRIPTIONS) {
    await expect(page.getByText(d, { exact: false }).first()).toBeVisible({ timeout: 30_000 })
  }
})

test('@smoke uploading the same file twice adds no duplicates', async ({ page }) => {
  await register(page)
  await uploadStatement(page)
  await page.getByRole('link', { name: /transactions/i }).first().click()
  await expect(page.getByText('UPI Uber India').first()).toBeVisible({ timeout: 30_000 })

  await uploadStatement(page)
  await page.getByRole('link', { name: /transactions/i }).first().click()
  await expect(page.getByText('UPI Uber India').first()).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText('UPI Uber India')).toHaveCount(1)
})

test('@smoke another user never sees my transactions', async ({ browser }) => {
  const alice = await browser.newPage()
  await register(alice)
  await uploadStatement(alice)
  await alice.getByRole('link', { name: /transactions/i }).first().click()
  await expect(alice.getByText('UPI Uber India').first()).toBeVisible({ timeout: 30_000 })

  const bob = await (await browser.newContext()).newPage()
  await register(bob)
  await bob.goto('/transactions')
  await expect(bob.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(bob.getByText('UPI Uber India')).toHaveCount(0)
})
