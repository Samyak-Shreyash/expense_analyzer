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

export async function uploadStatement(page: Page) {
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: /statements/i }).click()
  await page.getByLabel('Choose a file').setInputFiles(STATEMENT)
  await expect(page.getByRole('alert')).toHaveCount(0)
}
