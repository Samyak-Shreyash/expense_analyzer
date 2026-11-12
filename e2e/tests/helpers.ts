import { expect, type Page } from '@playwright/test'
import { fileURLToPath } from 'node:url'

export const PASSWORD = 'e2e-Password-123'
export const STATEMENT = fileURLToPath(new URL('../fixtures/statement.csv', import.meta.url))

/** A fresh user per test: tests never share data, so they can run in parallel. */
export function newEmail() {
  return `e2e+${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`
}

export async function register(page: Page, email = newEmail()) {
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
  await page.getByLabel('Confirm password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
  return email
}

/** Uploads the fixture and waits until processing finishes (the page polls the status). */
export async function uploadStatement(page: Page) {
  await nav(page, 'Statements')
  const done = page.getByRole('link', { name: 'View transactions' })
  await expect(page.getByRole('heading', { name: 'Uploaded statements' })).toBeVisible()
  const before = await done.count()
  await page.getByLabel('Choose a file').setInputFiles(STATEMENT)
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(done).toHaveCount(before + 1, { timeout: 60_000 })
}

export async function nav(page: Page, name: string) {
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name }).click()
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
}
