import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Transaction } from '../../services/finance'
import * as finance from '../../services/finance'
import { TransactionsPage } from './TransactionsPage'

vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  listTransactions: vi.fn(),
}))

const swiggy: Transaction = {
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
  statementId: 's1',
}

function Location() {
  return <output data-testid="location">{useLocation().search}</output>
}

function renderPage(url = '/transactions') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <TransactionsPage />
      <Location />
    </MemoryRouter>,
  )
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    vi.mocked(finance.listTransactions).mockResolvedValue({
      items: [swiggy],
      page: 0,
      size: 50,
      totalItems: 1,
      totalPages: 1,
    })
  })

  it('loads transactions with signed rupee amounts', async () => {
    renderPage()
    expect(await screen.findByText('Swiggy')).toBeTruthy()
    expect(screen.getByText('-₹450.00')).toBeTruthy()
  })

  it('puts filters in the URL and sends them to the API', async () => {
    renderPage()
    await screen.findByText('Swiggy')

    fireEvent.change(screen.getByLabelText('Search descriptions'), {
      target: { value: 'swiggy' },
    })
    fireEvent.change(screen.getByLabelText('From'), {
      target: { value: '2026-01-01' },
    })
    fireEvent.submit(screen.getByRole('search'))

    await waitFor(() =>
      expect(finance.listTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: 'swiggy', from: '2026-01-01', page: 0 }),
      ),
    )
    expect(screen.getByTestId('location').textContent).toContain('q=swiggy')
  })

  it('reads filters from the URL on load', async () => {
    renderPage('/transactions?merchantId=m1&direction=DEBIT')
    await waitFor(() =>
      expect(finance.listTransactions).toHaveBeenCalledWith(
        expect.objectContaining({ merchantId: 'm1', direction: 'DEBIT' }),
      ),
    )
  })

  it('shows raw and normalised descriptions in the row detail', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /SWIGGY ORDER/ }))

    expect(screen.getByText('UPI-SWIGGY-ORDER-12345678')).toBeTruthy()
    expect(screen.getAllByText('SWIGGY ORDER').length).toBeGreaterThan(1)
  })

  it('invites an upload when there are no transactions at all', async () => {
    vi.mocked(finance.listTransactions).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      totalItems: 0,
      totalPages: 0,
    })
    renderPage()
    expect(
      await screen.findByRole('link', { name: 'Upload a statement' }),
    ).toBeTruthy()
  })
})
