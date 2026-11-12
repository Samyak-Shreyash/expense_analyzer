import { expect, test } from '@playwright/test'
import Ajv from 'ajv'
import addFormats from 'ajv-formats'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createGenerator } from 'ts-json-schema-generator'
import { PASSWORD } from './helpers'

// Consumer contract: live API responses must match the types the web and mobile apps compile against.
// Extra fields are allowed (clients ignore them); missing or mistyped fields fail.
// Runs as dave: a password login revokes the user's other sessions.
const CLIENT = fileURLToPath(new URL('../../packages/api-client/src/finance.ts', import.meta.url))
const RANGE = 'from=2026-01-01&to=2026-12-31'

// [api-client type, path, shape]: 'page' = Page<type>, 'list' = type[], 'one' = type
const ENDPOINTS: [string, string, 'page' | 'list' | 'one'][] = [
  ['Statement', '/api/statements?size=100', 'page'],
  ['Transaction', '/api/transactions?size=50', 'page'],
  ['Summary', `/api/analytics/summary?${RANGE}`, 'one'],
  ['CategoryBreakdown', `/api/analytics/categories?${RANGE}`, 'one'],
  ['MerchantBreakdown', `/api/analytics/merchants?${RANGE}&limit=8`, 'one'],
  ['CashFlow', `/api/analytics/cash-flow?${RANGE}`, 'one'],
  ['Trends', `/api/analytics/trends?${RANGE}`, 'one'],
  ['NonSpending', `/api/analytics/non-spending?${RANGE}`, 'one'],
  ['Category', '/api/categories', 'list'],
  ['Person', '/api/people', 'list'],
  ['MerchantNames', '/api/merchant-names', 'one'],
  ['Profile', '/api/user/me', 'one'],
]

const PAGE_FIELDS = ['page', 'size', 'totalItems', 'totalPages']

test('API responses match the shared api-client types', async ({ request }) => {
  test.setTimeout(120_000) // schema generation compiles the client
  const ajv = addFormats(new Ajv({ allErrors: true, strict: false }))
  const validator = (type: string) =>
    ajv.compile(createGenerator({ path: CLIENT, type, skipTypeCheck: true, additionalProperties: true }).createSchema(type))

  const { dave } = JSON.parse(readFileSync('.auth/users.json', 'utf8'))
  const login = await request.post('/api/auth/login', { data: { email: dave, password: PASSWORD } })
  expect(login.ok()).toBeTruthy()
  const headers = { Authorization: `Bearer ${(await login.json()).token}` }

  const failures: string[] = []
  for (const [type, path, shape] of ENDPOINTS) {
    const res = await request.get(path, { headers })
    if (!res.ok()) {
      failures.push(`${path}: HTTP ${res.status()}`)
      continue
    }
    const body = await res.json()
    const validate = validator(type)
    let items: unknown[] = [body]
    if (shape === 'page') {
      const missing = PAGE_FIELDS.filter((f) => typeof body[f] !== 'number')
      if (!Array.isArray(body.items) || missing.length) failures.push(`${path}: not a Page (${missing.join(', ')})`)
      items = body.items ?? []
    } else if (shape === 'list') {
      items = body
    }
    items.forEach((item, i) => {
      if (!validate(item)) failures.push(`${type} ${path} [${i}]: ${ajv.errorsText(validate.errors)}`)
    })
  }
  expect(failures, failures.join('\n')).toEqual([])
})
