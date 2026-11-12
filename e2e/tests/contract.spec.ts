import { expect, test } from '@playwright/test'
import Ajv from 'ajv'
import addFormats from 'ajv-formats'
import { readFileSync } from 'node:fs'
import { sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createGenerator } from 'ts-json-schema-generator'
import { PASSWORD } from './helpers'

// Consumer contract: live API responses must match the types the web and mobile apps compile against.
const CLIENT = fileURLToPath(new URL('../../packages/api-client/src/finance.ts', import.meta.url))
const CLIENT_PATH = CLIENT.split(sep).join('/') // forward slashes for the generated import
const RANGE = 'from=2026-01-01&to=2026-12-31'

const ENDPOINTS: [type: string, path: string][] = [
  ['StatementPage', '/api/statements?size=100'],
  ['TransactionPage', '/api/transactions?size=50'],
  ['Summary', `/api/analytics/summary?${RANGE}`],
  ['CategoryBreakdown', `/api/analytics/categories?${RANGE}`],
  ['MerchantBreakdown', `/api/analytics/merchants?${RANGE}&limit=8`],
  ['CashFlow', `/api/analytics/cash-flow?${RANGE}`],
  ['Trends', `/api/analytics/trends?${RANGE}`],
  ['NonSpending', `/api/analytics/non-spending?${RANGE}`],
  ['CategoryList', '/api/categories'],
  ['PersonList', '/api/people'],
  ['MerchantNames', '/api/merchant-names'],
  ['Profile', '/api/user/me'],
]

// Named aliases for the generic responses, so the generator can emit them.
const ALIASES = `
import type { Page, Statement, Transaction, Category, Person } from '${CLIENT_PATH}'
export type { Summary, CategoryBreakdown, MerchantBreakdown, CashFlow, Trends, NonSpending, MerchantNames, Profile } from '${CLIENT_PATH}'
export type StatementPage = Page<Statement>
export type TransactionPage = Page<Transaction>
export type CategoryList = Category[]
export type PersonList = Person[]
`

test('API responses match the shared api-client types', async ({ request }, info) => {
  const aliases = info.outputPath('contract-types.ts')
  const { writeFileSync } = await import('node:fs')
  writeFileSync(aliases, ALIASES)
  const schema = createGenerator({ path: aliases, type: '*', skipTypeCheck: true, additionalProperties: true }).createSchema('*')
  const ajv = addFormats(new Ajv({ allErrors: true, strict: false }))
  ajv.addSchema(schema, 'client')

  const { alice } = JSON.parse(readFileSync('.auth/users.json', 'utf8'))
  const login = await request.post('/api/auth/login', { data: { email: alice, password: PASSWORD } })
  expect(login.ok()).toBeTruthy()
  const headers = { Authorization: `Bearer ${(await login.json()).token}` }

  const failures: string[] = []
  for (const [type, path] of ENDPOINTS) {
    const res = await request.get(path, { headers })
    if (!res.ok()) {
      failures.push(`${path}: HTTP ${res.status()}`)
      continue
    }
    const validate = ajv.getSchema(`client#/definitions/${type}`)!
    if (!validate(await res.json())) {
      failures.push(`${type} ${path}: ${ajv.errorsText(validate.errors, { separator: '\n  ' })}`)
    }
  }
  expect(failures, failures.join('\n')).toEqual([])
})
