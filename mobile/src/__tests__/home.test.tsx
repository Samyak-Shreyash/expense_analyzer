import { render, screen } from '@testing-library/react-native'
import Dashboard from '../app/(tabs)/index'

jest.mock('@expense-analyzer/api-client', () => ({
  getSummary: jest.fn(async () => ({ inflow: 1000, outflow: 400, net: 600 })),
  getCategories: jest.fn(async () => ({
    totalSpend: 400,
    items: [
      { category: 'Food', spend: 400, sharePercent: 100, transactionCount: 2 },
    ],
  })),
}))

it("shows this month's totals and spending by category", async () => {
  await render(<Dashboard />)
  expect(await screen.findByText('Food')).toBeTruthy()
  expect(screen.getByText('This month')).toBeTruthy()
  expect(screen.getByText('2 transactions')).toBeTruthy()
})
