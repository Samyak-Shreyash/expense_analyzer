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
  createCategory: vi.fn(),
  setCounterparty: vi.fn(),
  setMerchantCategory: vi.fn(),
  updatePerson: vi.fn(),
  setTransactionMerchant: vi.fn(),
  getMerchantNameSettings: vi.fn(),
  reportSuggestion: vi.fn(),
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
  suggestedMerchantName: null,
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
    vi.mocked(finance.getMerchantNameSettings).mockResolvedValue({
      shareMerchantNames: false,
      sharingRevoked: false,
    })
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
      await screen.findByRole('button', {
        name: 'Change category, now Food & Dining',
      }),
    )
    fireEvent.click(
      screen.getByRole('button', { name: 'Show only Food & Dining' }),
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
      await screen.findByRole('button', {
        name: 'Change category, now Uncategorized',
      }),
    )
    fireEvent.click(
      screen.getByRole('button', { name: 'Show only Uncategorized' }),
    )
    await waitFor(() =>
      expect(finance.listTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: 'Uncategorized' }),
      ),
    )
  })

  it('changes a transaction category by picking from the list', async () => {
    vi.mocked(finance.setTransactionCategory).mockResolvedValue({
      ...swiggy,
      category: 'Repayment',
      categoryId: 'c2',
      categoryOverridden: true,
    })
    renderPage()
    fireEvent.click(
      await screen.findByRole('button', {
        name: 'Change category, now Food & Dining',
      }),
    )
    fireEvent.change(screen.getByLabelText('Category for this transaction'), {
      target: { value: 'repayment' },
    })
    fireEvent.click(screen.getByLabelText('Only this transaction'))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(finance.setTransactionCategory).toHaveBeenCalledWith('t1', 'c2'),
    )
    expect(finance.createCategory).not.toHaveBeenCalled()
    const updated = await screen.findByRole('button', {
      name: 'Change category, now Repayment',
    })
    expect(document.activeElement).toBe(updated)
  })

  it('creates a new category when the typed name is not in the list', async () => {
    vi.mocked(finance.createCategory).mockResolvedValue({
      id: 'c9',
      name: 'Gifts',
      countsAsSpending: false,
    })
    vi.mocked(finance.setTransactionCategory).mockResolvedValue({
      ...swiggy,
      category: 'Gifts',
      categoryId: 'c9',
      categoryOverridden: true,
    })
    renderPage()
    fireEvent.click(
      await screen.findByRole('button', {
        name: 'Change category, now Food & Dining',
      }),
    )
    fireEvent.change(screen.getByLabelText('Category for this transaction'), {
      target: { value: ' Gifts ' },
    })
    fireEvent.click(screen.getByLabelText('Only this transaction'))
    fireEvent.click(screen.getByLabelText('New category counts as spending'))
    fireEvent.click(
      screen.getByRole('button', { name: 'Create "Gifts" and use it' }),
    )

    await waitFor(() =>
      expect(finance.setTransactionCategory).toHaveBeenCalledWith('t1', 'c9'),
    )
    expect(finance.createCategory).toHaveBeenCalledWith('Gifts', false)
  })

  it('closes the category editor on Escape and returns focus', async () => {
    renderPage()
    const trigger = await screen.findByRole('button', {
      name: 'Change category, now Food & Dining',
    })
    fireEvent.click(trigger)
    const input = screen.getByLabelText('Category for this transaction')
    expect(document.activeElement).toBe(input)
    fireEvent.keyDown(input, { key: 'Escape' })
    expect(document.activeElement?.getAttribute('aria-label')).toBe(
      'Change category, now Food & Dining',
    )
    expect(finance.setTransactionCategory).not.toHaveBeenCalled()
  })

  async function openCategoryEditor() {
    fireEvent.click(
      await screen.findByRole('button', {
        name: 'Change category, now Food & Dining',
      }),
    )
  }

  it('saves a category for the whole merchant and refetches', async () => {
    vi.mocked(finance.setMerchantCategory).mockResolvedValue(null)
    vi.mocked(finance.setTransactionCategory).mockResolvedValue(swiggy)
    renderPage()
    await openCategoryEditor()
    const calls = vi.mocked(finance.listTransactions).mock.calls.length
    fireEvent.change(screen.getByLabelText('Category for this transaction'), {
      target: { value: 'Repayment' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save for all Swiggy' }))

    await waitFor(() =>
      expect(finance.setMerchantCategory).toHaveBeenCalledWith('m1', 'c2'),
    )
    await waitFor(() =>
      expect(finance.setTransactionCategory).toHaveBeenCalledWith('t1', null),
    )
    await waitFor(() =>
      expect(vi.mocked(finance.listTransactions).mock.calls.length).toBe(
        calls + 1,
      ),
    )
  })

  it('still reloads, without an error, when clearing the own category fails after a merchant-wide save', async () => {
    vi.mocked(finance.setMerchantCategory).mockResolvedValue(null)
    vi.mocked(finance.setTransactionCategory).mockRejectedValue(
      new finance.ApiError(500, 'boom'),
    )
    renderPage()
    await openCategoryEditor()
    const calls = vi.mocked(finance.listTransactions).mock.calls.length
    fireEvent.change(screen.getByLabelText('Category for this transaction'), {
      target: { value: 'Repayment' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save for all Swiggy' }))

    await waitFor(() =>
      expect(finance.setTransactionCategory).toHaveBeenCalledWith('t1', null),
    )
    await waitFor(() =>
      expect(vi.mocked(finance.listTransactions).mock.calls.length).toBe(
        calls + 1,
      ),
    )
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('creates a new category for the whole merchant', async () => {
    vi.mocked(finance.createCategory).mockResolvedValue({
      id: 'c9',
      name: 'Gifts',
      countsAsSpending: true,
    })
    vi.mocked(finance.setMerchantCategory).mockResolvedValue(null)
    vi.mocked(finance.setTransactionCategory).mockResolvedValue(swiggy)
    renderPage()
    await openCategoryEditor()
    fireEvent.change(screen.getByLabelText('Category for this transaction'), {
      target: { value: 'Gifts' },
    })
    fireEvent.click(
      screen.getByRole('button', {
        name: 'Create "Gifts" and use it for all Swiggy',
      }),
    )
    await waitFor(() =>
      expect(finance.setMerchantCategory).toHaveBeenCalledWith('m1', 'c9'),
    )
  })

  it('saves only this transaction when asked', async () => {
    vi.mocked(finance.setTransactionCategory).mockResolvedValue({
      ...swiggy,
      category: 'Repayment',
      categoryId: 'c2',
      categoryOverridden: true,
    })
    renderPage()
    await openCategoryEditor()
    fireEvent.change(screen.getByLabelText('Category for this transaction'), {
      target: { value: 'Repayment' },
    })
    fireEvent.click(screen.getByLabelText('Only this transaction'))
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(finance.setTransactionCategory).toHaveBeenCalledWith('t1', 'c2'),
    )
    expect(finance.setMerchantCategory).not.toHaveBeenCalled()
  })

  it('saves a category for the person on a person row', async () => {
    vi.mocked(finance.listTransactions).mockResolvedValue({
      items: [
        {
          ...swiggy,
          merchantId: null,
          merchantName: null,
          personId: 'p1',
          personName: 'Ravi Kumar',
        },
      ],
      page: 0,
      size: 50,
      totalItems: 1,
      totalPages: 1,
    })
    vi.mocked(finance.updatePerson).mockResolvedValue({
      id: 'p1',
      name: 'Ravi Kumar',
      categoryId: 'c2',
    })
    vi.mocked(finance.setTransactionCategory).mockResolvedValue(swiggy)
    renderPage()
    await openCategoryEditor()
    fireEvent.change(screen.getByLabelText('Category for this transaction'), {
      target: { value: 'Repayment' },
    })
    fireEvent.click(
      screen.getByRole('button', { name: 'Save for all Ravi Kumar' }),
    )

    await waitFor(() =>
      expect(finance.updatePerson).toHaveBeenCalledWith(
        'p1',
        'Ravi Kumar',
        'c2',
      ),
    )
    expect(finance.setMerchantCategory).not.toHaveBeenCalled()
  })

  describe('unmatched transaction', () => {
    const unmatched = { ...swiggy, merchantId: null, merchantName: null }
    const addMerchant = 'Add merchant for UPI-SWIGGY-ORDER-12345678'

    beforeEach(() => {
      vi.mocked(finance.listTransactions).mockResolvedValue({
        items: [unmatched],
        page: 0,
        size: 50,
        totalItems: 1,
        totalPages: 1,
      })
    })

    it('has no scope checkbox and saves the category for this transaction', async () => {
      vi.mocked(finance.setTransactionCategory).mockResolvedValue(unmatched)
      renderPage()
      await openCategoryEditor()
      expect(screen.queryByLabelText('Only this transaction')).toBeNull()
      fireEvent.change(screen.getByLabelText('Category for this transaction'), {
        target: { value: 'Repayment' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))
      await waitFor(() =>
        expect(finance.setTransactionCategory).toHaveBeenCalledWith('t1', 'c2'),
      )
    })

    it('names the merchant and refetches', async () => {
      vi.mocked(finance.setTransactionMerchant).mockResolvedValue(unmatched)
      renderPage()
      fireEvent.click(await screen.findByRole('button', { name: addMerchant }))
      const calls = vi.mocked(finance.listTransactions).mock.calls.length
      const input = screen.getByLabelText('Merchant name')
      expect(document.activeElement).toBe(input)
      fireEvent.change(input, { target: { value: ' Acme Store ' } })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))

      await waitFor(() =>
        expect(finance.setTransactionMerchant).toHaveBeenCalledWith(
          't1',
          'Acme Store',
          undefined,
        ),
      )
      await waitFor(() =>
        expect(vi.mocked(finance.listTransactions).mock.calls.length).toBe(
          calls + 1,
        ),
      )
    })

    it('moves focus to the named merchant after the refetch', async () => {
      vi.mocked(finance.setTransactionMerchant).mockResolvedValue(unmatched)
      renderPage()
      fireEvent.click(await screen.findByRole('button', { name: addMerchant }))
      vi.mocked(finance.listTransactions).mockResolvedValue({
        items: [{ ...unmatched, merchantId: 'm9', merchantName: 'Acme Store' }],
        page: 0,
        size: 50,
        totalItems: 1,
        totalPages: 1,
      })
      fireEvent.change(screen.getByLabelText('Merchant name'), {
        target: { value: 'Acme Store' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))

      await waitFor(() =>
        expect(document.activeElement?.id).toBe('merchant-t1'),
      )
    })

    it('does not steal focus later when the refetch fails', async () => {
      vi.mocked(finance.setTransactionMerchant).mockResolvedValue(unmatched)
      renderPage()
      fireEvent.click(await screen.findByRole('button', { name: addMerchant }))
      vi.mocked(finance.listTransactions).mockRejectedValueOnce(new Error('x'))
      fireEvent.change(screen.getByLabelText('Merchant name'), {
        target: { value: 'Acme Store' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))
      await screen.findByText(/could not/i)
      const search = screen.getByLabelText('Search descriptions')
      search.focus()

      vi.mocked(finance.listTransactions).mockResolvedValue({
        items: [{ ...unmatched, merchantId: 'm9', merchantName: 'Acme Store' }],
        page: 0,
        size: 50,
        totalItems: 1,
        totalPages: 1,
      })
      fireEvent.change(search, { target: { value: 'acme' } })
      fireEvent.submit(screen.getByRole('search'))
      await screen.findByRole('button', { name: 'Acme Store' })
      expect(document.activeElement).toBe(search)
    })

    it('hides the share checkbox while sharing is off and links to the setting', async () => {
      renderPage()
      fireEvent.click(await screen.findByRole('button', { name: addMerchant }))
      expect(screen.queryByRole('checkbox')).toBeNull()
      const link = screen.getByRole('link', {
        name: 'Sharing is off — manage in Merchant names',
      })
      expect(link.getAttribute('href')).toBe('/categories?tab=merchant-names')
    })

    it('shows the share checkbox, checked, with the warning while sharing is on', async () => {
      vi.mocked(finance.getMerchantNameSettings).mockResolvedValue({
        shareMerchantNames: true,
        sharingRevoked: false,
      })
      vi.mocked(finance.setTransactionMerchant).mockResolvedValue(unmatched)
      renderPage()
      const trigger = await screen.findByRole('button', { name: addMerchant })
      await waitFor(() =>
        expect(finance.getMerchantNameSettings).toHaveBeenCalled(),
      )
      fireEvent.click(trigger)
      const box = (await screen.findByRole('checkbox', {
        name: 'Share this name with other users',
      })) as HTMLInputElement
      expect(box.checked).toBe(true)
      const hint = document.getElementById(
        box.getAttribute('aria-describedby')!,
      )
      expect(hint?.textContent).toMatch(/Offensive names are reported/)
      fireEvent.click(box)
      fireEvent.change(screen.getByLabelText('Merchant name'), {
        target: { value: 'Acme Store' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))
      await waitFor(() =>
        expect(finance.setTransactionMerchant).toHaveBeenCalledWith(
          't1',
          'Acme Store',
          false,
        ),
      )
    })

    it('reports a suggested name after confirming; Escape cancels', async () => {
      vi.mocked(finance.reportSuggestion).mockResolvedValue(null)
      vi.mocked(finance.listTransactions).mockResolvedValue({
        items: [{ ...unmatched, suggestedMerchantName: 'Acme Store' }],
        page: 0,
        size: 50,
        totalItems: 1,
        totalPages: 1,
      })
      renderPage()
      const report = await screen.findByRole('button', {
        name: 'Report suggested name Acme Store',
      })
      fireEvent.click(report)
      expect(
        screen.getByText(/Report this name as offensive or wrong\?/),
      ).toBeTruthy()
      const cancel = screen.getByRole('button', { name: 'Cancel' })
      expect(document.activeElement).toBe(cancel)
      fireEvent.keyDown(cancel, { key: 'Escape' })
      expect(document.activeElement?.getAttribute('aria-label')).toBe(
        'Report suggested name Acme Store',
      )
      expect(finance.reportSuggestion).not.toHaveBeenCalled()

      fireEvent.click(
        screen.getByRole('button', {
          name: 'Report suggested name Acme Store',
        }),
      )
      fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
      await waitFor(() =>
        expect(finance.reportSuggestion).toHaveBeenCalledWith('t1'),
      )
      await waitFor(() =>
        expect(screen.queryByText(/Suggested: Acme Store/)).toBeNull(),
      )
    })

    it('offers a suggested name that Use applies without sharing', async () => {
      vi.mocked(finance.setTransactionMerchant).mockResolvedValue(unmatched)
      vi.mocked(finance.listTransactions).mockResolvedValue({
        items: [{ ...unmatched, suggestedMerchantName: 'Acme Store' }],
        page: 0,
        size: 50,
        totalItems: 1,
        totalPages: 1,
      })
      renderPage()
      expect(await screen.findByText(/Suggested: Acme Store/)).toBeTruthy()
      expect(screen.getByRole('button', { name: addMerchant })).toBeTruthy()
      expect(screen.getByText('Use Acme Store')).toBeTruthy()
      const calls = vi.mocked(finance.listTransactions).mock.calls.length
      fireEvent.click(
        screen.getByRole('button', {
          name: 'Use Acme Store for UPI-SWIGGY-ORDER-12345678',
        }),
      )
      await waitFor(() =>
        expect(finance.setTransactionMerchant).toHaveBeenCalledWith(
          't1',
          'Acme Store',
          false,
        ),
      )
      await waitFor(() =>
        expect(vi.mocked(finance.listTransactions).mock.calls.length).toBe(
          calls + 1,
        ),
      )
    })

    it('shows the error and returns focus on Escape', async () => {
      vi.mocked(finance.setTransactionMerchant).mockRejectedValue(
        new finance.ApiError(
          400,
          'Use a merchant name of 1 to 100 characters.',
        ),
      )
      renderPage()
      const trigger = await screen.findByRole('button', { name: addMerchant })
      fireEvent.click(trigger)
      const input = screen.getByLabelText('Merchant name')
      fireEvent.change(input, { target: { value: 'x' } })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))
      expect((await screen.findByRole('alert')).textContent).toMatch(
        /merchant name/,
      )
      fireEvent.keyDown(input, { key: 'Escape' })
      expect(document.activeElement?.getAttribute('aria-label')).toBe(
        addMerchant,
      )
    })
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
