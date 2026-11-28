import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as finance from '../../services/finance'
import { AnalysisPage } from './AnalysisPage'

vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  getSummary: vi.fn(),
  getCategories: vi.fn(),
  getCashFlow: vi.fn(),
  listTransactions: vi.fn(),
}))

const summary = (
  outflowChangePercent: number | null = 180,
): finance.Summary => ({
  from: '2026-01-01',
  to: '2026-01-31',
  inflow: 85100,
  outflow: 2800,
  nonSpendingOutflow: 1000,
  net: 81300,
  transactionCount: 5,
  previous: {
    from: '2025-12-01',
    to: '2025-12-31',
    inflow: 0,
    outflow: 1000,
    nonSpendingOutflow: 0,
    net: -1000,
    transactionCount: 1,
  },
  inflowChangePercent: null,
  outflowChangePercent,
  transfersIn: 0,
  transfersOut: 0,
  loanReceived: 0,
})

const swiggy: finance.Transaction = {
  id: 't1',
  date: '2026-01-02',
  description: 'UPI-SWIGGY-ORDER-12345678',
  normalizedDescription: 'SWIGGY ORDER',
  amount: 450,
  currency: 'INR',
  direction: 'DEBIT',
  merchantId: 'm1',
  merchantName: 'Swiggy',
  category: 'Food & Dining',
  categoryId: 'c1',
  categoryOverridden: false,
  personId: null,
  personName: null,
  statementId: 's1',
  suggestedMerchantName: null,
  payeeName: null,
  payeeType: null,
  transfer: false,
  transferPairId: null,
  reversalPairId: null,
  reversalPending: false,
  duplicate: false,
  loanId: null,
  loanInterest: null,
  loanClosed: false,
}

const page = (items: finance.Transaction[]) => ({
  items,
  page: 0,
  size: 50,
  totalItems: items.length,
  totalPages: 1,
})

function renderPage() {
  return render(
    <MemoryRouter>
      <AnalysisPage />
    </MemoryRouter>,
  )
}

describe('AnalysisPage', () => {
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
    vi.mocked(finance.getCashFlow).mockResolvedValue({
      months: [
        { month: '2025-12', inflow: 0, outflow: 1000, net: -1000 },
        { month: '2026-01', inflow: 85100, outflow: 2800, net: 82300 },
      ],
    })
    vi.mocked(finance.listTransactions).mockResolvedValue(page([swiggy]))
  })

  it('shows stats, chart, categories, recent rows and insights', async () => {
    renderPage()
    expect(await screen.findByText('Analysis')).toBeTruthy()
    expect(screen.getByText('Swiggy')).toBeTruthy()
    expect(screen.getByText('Shopping')).toBeTruthy()
    expect(screen.getByText('Other')).toBeTruthy()
    expect(screen.getByText(/more than last period/)).toBeTruthy()
    expect(screen.getByText(/Shopping is .* of your spending/)).toBeTruthy()
  })

  it('shows an alert when loading fails', async () => {
    vi.mocked(finance.getSummary).mockRejectedValue(new Error('down'))
    renderPage()
    expect((await screen.findByRole('alert')).textContent).toContain(
      'Could not load your analysis',
    )
  })

  it('shows empty states with no spending', async () => {
    vi.mocked(finance.getSummary).mockResolvedValue(summary(null))
    vi.mocked(finance.getCategories).mockResolvedValue({
      totalSpend: 0,
      items: [],
    })
    renderPage()
    expect(await screen.findByText('No spending this month.')).toBeTruthy()
    expect(screen.getByText('Nothing stands out yet this month.')).toBeTruthy()
  })

  it('falls back to payee name and a dash for missing category', async () => {
    vi.mocked(finance.getSummary).mockResolvedValue(summary(-20))
    vi.mocked(finance.listTransactions).mockResolvedValue(
      page([
        {
          ...swiggy,
          direction: 'CREDIT',
          merchantName: null,
          payeeName: 'Acme',
          category: null,
        },
      ]),
    )
    renderPage()
    expect(await screen.findByText('Acme')).toBeTruthy()
    expect(screen.getByText('—')).toBeTruthy()
    expect(screen.getByText(/less than last period/)).toBeTruthy()
  })
})
