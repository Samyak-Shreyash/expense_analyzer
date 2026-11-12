import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

async function serious(page: import('@playwright/test').Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG).analyze()
  return violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id} (${v.impact}): ${v.nodes.length} node(s) — ${v.help}`)
}

for (const path of ['/login', '/register']) {
  test(`${path} has no serious accessibility violations`, async ({ browser }) => {
    const page = await (await browser.newContext()).newPage()
    await page.goto(path)
    expect(await serious(page)).toEqual([])
  })
}

test('signed-in pages have no serious accessibility violations', async ({ browser }) => {
  const page = await (await browser.newContext({ storageState: '.auth/carol.json' })).newPage()
  for (const path of ['/dashboard', '/statements', '/transactions', '/categories', '/profile']) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    expect(await serious(page), path).toEqual([])
  }
})
