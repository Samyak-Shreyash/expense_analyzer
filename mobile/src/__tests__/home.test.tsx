import { fireEvent, render, screen } from '@testing-library/react-native'
import { getSummary } from '@expense-analyzer/api-client'
import { router } from 'expo-router'
import Dashboard from '../app/(tabs)/index'

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }))
const totals = {
  inflow: 1000,
  outflow: 400,
  nonSpendingOutflow: 50,
  net: 600,
  transactionCount: 3,
}
jest.mock('@expense-analyzer/api-client', () => ({
  getSummary: jest.fn(async () => ({
    ...totals,
    from: '2026-01-01',
    to: '2026-03-15',
    previous: { ...totals, from: '2025-10-01', to: '2025-12-31' },
    inflowChangePercent: 10,
    outflowChangePercent: -5,
    transfersIn: 0,
    transfersOut: 200,
  })),
  getCategories: jest.fn(async () => ({
    totalSpend: 400,
    items: [
      { category: 'Food', spend: 400, sharePercent: 100, transactionCount: 2 },
    ],
  })),
  getMerchants: jest.fn(async () => ({
    totalSpend: 400,
    items: [
      {
        merchantId: 'm1',
        name: 'Swiggy',
        spend: 400,
        sharePercent: 100,
        transactionCount: 2,
      },
    ],
  })),
  getCashFlow: jest.fn(async () => ({
    months: [{ month: '2026-01', inflow: 1000, outflow: 400, net: 600 }],
  })),
  getTrends: jest.fn(async () => ({
    months: [{ month: '2026-01', outflow: 400, changePercent: null }],
  })),
  getNonSpending: jest.fn(async () => ({ total: 0, items: [] })),
}))

it('shows the period totals, sections, and opens a merchant’s transactions', async () => {
  await render(<Dashboard />)
  expect(await screen.findByText('Top merchants')).toBeTruthy()
  expect(screen.getByText('10% more than the previous period')).toBeTruthy()
  expect(screen.getByText('Cash flow by month')).toBeTruthy()

  await fireEvent.press(screen.getByLabelText(/^Swiggy: /))
  expect(router.push).toHaveBeenCalledWith({
    pathname: '/transactions',
    params: expect.objectContaining({ merchantId: 'm1' }),
  })
})

it('reloads for another period', async () => {
  await render(<Dashboard />)
  await screen.findByText('Top merchants')
  await fireEvent.press(screen.getByText('Last 12 months'))
  expect(jest.mocked(getSummary).mock.calls.length).toBeGreaterThan(1)
})
