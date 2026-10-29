import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as finance from '../../services/finance'
import { DashboardPage } from './DashboardPage'

vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  getSummary: vi.fn(),
  getCategories: vi.fn(),
  getMerchants: vi.fn(),
  getCashFlow: vi.fn(),
  getTrends: vi.fn(),
}))

const summary = (count = 5): finance.Summary => ({
  from: '2026-01-01',
  to: '2026-01-31',
  inflow: 85100,
  outflow: 2800,
  net: 82300,
  transactionCount: count,
  previous: {
    from: '2025-12-01',
    to: '2025-12-31',
    inflow: 0,
    outflow: 1000,
    net: -1000,
    transactionCount: 1,
  },
  inflowChangePercent: null,
  outflowChangePercent: 180,
})

function renderPage(url = '/dashboard?from=2026-01-01&to=2026-01-31') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <DashboardPage />
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.mocked(finance.getSummary).mockResolvedValue(summary())
    vi.mocked(finance.getCategories).mockResolvedValue({
      totalSpend: 2700,
      items: [
        {
          category: 'Shopping',
          spend: 2000,
          sharePercent: 74.07,
          transactionCount: 1,
        },
      ],
    })
    vi.mocked(finance.getMerchants).mockResolvedValue({
      totalSpend: 2700,
      items: [
        {
          merchantId: 'm1',
          name: 'Amazon',
          spend: 2000,
          sharePercent: 74.07,
          transactionCount: 1,
        },
        {
          merchantId: null,
          name: 'Unmatched',
          spend: 300,
          sharePercent: 11.11,
          transactionCount: 1,
        },
      ],
    })
    vi.mocked(finance.getCashFlow).mockResolvedValue({
      months: [{ month: '2026-01', inflow: 85100, outflow: 2800, net: 82300 }],
    })
    vi.mocked(finance.getTrends).mockResolvedValue({
      months: [{ month: '2026-01', outflow: 2800, changePercent: null }],
    })
  })

  it('shows money in, out and net in rupees with the comparison', async () => {
    renderPage()
    // The totals list; the same figures also appear in the chart's screen-reader table.
    const totals = within(
      (await screen.findByText('Money in', { selector: 'dt' })).closest('dl')!,
    )
    expect(totals.getByText('₹85,100.00')).toBeTruthy()
    expect(totals.getByText('₹2,800.00')).toBeTruthy()
    expect(totals.getByText('₹82,300.00')).toBeTruthy()
    expect(totals.getByText('180% more than the previous period')).toBeTruthy()
  })

  it('links merchants to their transactions, except unmatched spend', async () => {
    renderPage()
    const amazon = await screen.findByRole('link', { name: 'Amazon' })
    expect(amazon.getAttribute('href')).toBe(
      '/transactions?merchantId=m1&from=2026-01-01&to=2026-01-31',
    )
    expect(screen.queryByRole('link', { name: 'Unmatched' })).toBeNull()
  })

  it('refetches every chart when the range changes', async () => {
    renderPage()
    await screen.findByText('Money in', { selector: 'dt' })

    fireEvent.click(screen.getByRole('radio', { name: 'Last 12 months' }))

    await waitFor(() => expect(finance.getSummary).toHaveBeenCalledTimes(2))
    for (const fn of [
      finance.getCategories,
      finance.getMerchants,
      finance.getCashFlow,
      finance.getTrends,
    ]) {
      expect(fn).toHaveBeenCalledTimes(2)
    }
    const [from, to] = vi.mocked(finance.getSummary).mock.calls[1]
    expect(from < to).toBe(true)
    expect(from).not.toBe('2026-01-01')
  })

  it('invites an upload when the range has no transactions', async () => {
    vi.mocked(finance.getSummary).mockResolvedValue(summary(0))
    renderPage()
    expect(
      await screen.findByRole('link', { name: 'Upload a statement' }),
    ).toBeTruthy()
  })

  it('explains when the data could not be loaded', async () => {
    vi.mocked(finance.getSummary).mockRejectedValue(new Error('boom'))
    renderPage()
    expect((await screen.findByRole('alert')).textContent).toMatch(
      /Could not load/,
    )
  })
})
