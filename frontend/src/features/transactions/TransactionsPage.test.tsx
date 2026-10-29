import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Transaction } from '../../services/finance'
import * as finance from '../../services/finance'
import { TransactionsPage } from './TransactionsPage'

vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  listTransactions: vi.fn(),
  listCategories: vi.fn(),
  setTransactionCategory: vi.fn(),
  setCounterparty: vi.fn(),
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
  categoryId: 'c1',
  categoryOverridden: false,
  personId: null,
  personName: null,
  statementId: 's1',
}

const categories: finance.Category[] = [
  { id: 'c1', name: 'Food & Dining', countsAsSpending: true },
  { id: 'c2', name: 'Repayment', countsAsSpending: false },
]

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
    vi.mocked(finance.listCategories).mockResolvedValue(categories)
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

  it('filters by category from the table and clears it with the chip', async () => {
    renderPage()
    fireEvent.click(
      await screen.findByRole('button', { name: 'Food & Dining' }),
    )
    await waitFor(() =>
      expect(finance.listTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: 'Food & Dining' }),
      ),
    )
    expect(screen.getByText(/Category: Food & Dining/)).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Show all categories' }))
    await waitFor(() =>
      expect(screen.getByTestId('location').textContent).toBe(''),
    )
  })

  it('filters uncategorised transactions', async () => {
    vi.mocked(finance.listTransactions).mockResolvedValue({
      items: [{ ...swiggy, category: null }],
      page: 0,
      size: 50,
      totalItems: 1,
      totalPages: 1,
    })
    renderPage()
    fireEvent.click(
      await screen.findByRole('button', { name: 'Uncategorised' }),
    )
    await waitFor(() =>
      expect(finance.listTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: 'Uncategorized' }),
      ),
    )
  })

  it('shows raw and normalised descriptions in the row detail', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /SWIGGY ORDER/ }))

    expect(screen.getByText('UPI-SWIGGY-ORDER-12345678')).toBeTruthy()
    expect(screen.getAllByText('SWIGGY ORDER').length).toBeGreaterThan(1)
  })

  it('overrides one transaction category and clears it again', async () => {
    vi.mocked(finance.setTransactionCategory).mockResolvedValue({
      ...swiggy,
      category: 'Repayment',
      categoryId: 'c2',
      categoryOverridden: true,
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /SWIGGY ORDER/ }))
    const picker = (await screen.findByLabelText(
      'Category for this transaction',
    )) as HTMLSelectElement
    await screen.findAllByRole('option', { name: /Repayment/ })

    fireEvent.change(picker, { target: { value: 'c2' } })
    await waitFor(() =>
      expect(finance.setTransactionCategory).toHaveBeenLastCalledWith(
        't1',
        'c2',
      ),
    )
    await waitFor(() => expect(picker.value).toBe('c2'))

    vi.mocked(finance.setTransactionCategory).mockResolvedValue(swiggy)
    fireEvent.change(picker, { target: { value: '' } })
    await waitFor(() =>
      expect(finance.setTransactionCategory).toHaveBeenLastCalledWith(
        't1',
        null,
      ),
    )
    await waitFor(() => expect(picker.value).toBe(''))
  })

  it('marks a transaction as a person, then shows the person in the merchant column', async () => {
    const person = {
      ...swiggy,
      merchantId: null,
      merchantName: null,
      personId: 'p1',
      personName: 'Ravi Kumar',
    }
    // After saving, the refetched list shows the person.
    vi.mocked(finance.setCounterparty).mockImplementation(async () => {
      vi.mocked(finance.listTransactions).mockResolvedValue({
        items: [person],
        page: 0,
        size: 50,
        totalItems: 1,
        totalPages: 1,
      })
      return person
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /SWIGGY ORDER/ }))

    fireEvent.click(await screen.findByLabelText('This is a person'))
    fireEvent.change(screen.getByLabelText("Person's name"), {
      target: { value: 'Ravi Kumar' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(finance.setCounterparty).toHaveBeenCalledWith(
        't1',
        'PERSON',
        'Ravi Kumar',
      ),
    )
    expect(await screen.findByText('Ravi Kumar')).toBeTruthy()
    expect(screen.getByText('Person')).toBeTruthy()
  })

  it('shows the server message when the counterparty is rejected', async () => {
    vi.mocked(finance.setCounterparty).mockRejectedValue(
      new finance.ApiError(400, 'Enter a name that looks like a person.'),
    )
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /SWIGGY ORDER/ }))
    fireEvent.click(await screen.findByLabelText('This is a person'))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(
      /looks like a person/,
    )
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
