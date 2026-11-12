import { expect, type Page } from '@playwright/test'
import { fileURLToPath } from 'node:url'

export const PASSWORD = 'e2e-Password-123'
const fixture = (name: string) => fileURLToPath(new URL(`../fixtures/${name}`, import.meta.url))
export const STATEMENT = fixture('statement.csv')
/** The 4 rows of statement.csv plus one new row, in a different file. */
export const OVERLAPPING_STATEMENT = fixture('statement-overlap.csv')

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

/** Uploads a statement and waits until processing finishes (the page polls the status). */
export async function uploadStatement(page: Page, file = STATEMENT) {
  await nav(page, 'Statements')
  const done = page.getByRole('link', { name: 'View transactions' })
  await expect(page.getByRole('heading', { name: 'Uploaded statements' })).toBeVisible()
  const before = await done.count()
  await page.getByLabel('Choose a file').setInputFiles(file)
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(done).toHaveCount(before + 1, { timeout: 60_000 })
}

export async function nav(page: Page, name: string) {
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name }).click()
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
}
