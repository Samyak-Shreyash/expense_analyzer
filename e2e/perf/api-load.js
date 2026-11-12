// k6 load test for the read paths behind the dashboard and transactions page.
// Run: k6 run -e BASE_URL=http://127.0.0.1:3001 -e EMAIL=... -e PASSWORD=... perf/api-load.js
// Targets from docs/product/SUCCESS-METRICS.md: typical API < 500 ms, error rate < 1%.
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:3001'
const RANGE = 'from=2026-01-01&to=2026-12-31'

export const options = {
  scenarios: {
    steady: { executor: 'constant-vus', vus: 20, duration: '2m' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
}

// Logs in once; the 5-per-window login limit makes per-VU logins impossible.
export function setup() {
  const res = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ email: __ENV.EMAIL, password: __ENV.PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } })
  check(res, { 'login ok': (r) => r.status === 200 })
  return { headers: { Authorization: `Bearer ${res.json('token')}` } }
}

export default function ({ headers }) {
  const responses = http.batch([
    ['GET', `${BASE}/api/analytics/summary?${RANGE}`, null, { headers, tags: { name: 'summary' } }],
    ['GET', `${BASE}/api/analytics/categories?${RANGE}`, null, { headers, tags: { name: 'categories' } }],
    ['GET', `${BASE}/api/analytics/cash-flow?${RANGE}`, null, { headers, tags: { name: 'cash-flow' } }],
    ['GET', `${BASE}/api/transactions?size=50`, null, { headers, tags: { name: 'transactions' } }],
  ])
  for (const r of responses) check(r, { '200': (x) => x.status === 200 })
  sleep(1)
}
