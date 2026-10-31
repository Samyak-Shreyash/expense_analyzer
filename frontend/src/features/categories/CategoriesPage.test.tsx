import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as finance from '../../services/finance'
import { CategoriesPage } from './CategoriesPage'

vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  listCategories: vi.fn(),
  listPeople: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
  updatePerson: vi.fn(),
  listMerchantNames: vi.fn(),
  setMerchantNameSharing: vi.fn(),
  setMerchantNameShared: vi.fn(),
  setMerchantCategory: vi.fn(),
  setPersonCategory: vi.fn(),
  renameMerchantName: vi.fn(),
  mergePerson: vi.fn(),
  movePayee: vi.fn(),
}))

function Location() {
  return <output data-testid="location">{useLocation().search}</output>
}

function renderPage(url = '/categories') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <CategoriesPage />
      <Location />
    </MemoryRouter>,
  )
}

const acme: finance.MerchantName = {
  id: 'a1',
  name: 'Acme Store',
  payee: 'ACME STORE',
  transactionCount: 3,
  shared: false,
  optedOut: false,
  shareable: true,
  notShareableReason: null,
  reported: false,
  merchantId: 'm1',
  categoryId: null,
  creditCategoryId: null,
}
const rahul: finance.MerchantName = {
  ...acme,
  id: 'a2',
  name: 'Rahul Shop',
  payee: 'RAHUL KUMAR',
  merchantId: 'm2',
  shareable: false,
  notShareableReason: 'TRANSFER',
}
const beta: finance.MerchantName = {
  ...acme,
  id: 'a3',
  name: 'Beta Mart',
  payee: 'BETA MART',
  merchantId: 'm3',
  categoryId: 'c2',
}
const names = (
  over: Partial<finance.MerchantNames> = {},
): finance.MerchantNames => ({
  shareMerchantNames: false,
  sharingRevoked: false,
  items: [acme, beta, rahul],
  ...over,
})

const food = { id: 'c1', name: 'Food', countsAsSpending: true }
const loan = { id: 'c2', name: 'Loan', countsAsSpending: false }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(finance.listCategories).mockResolvedValue([food, loan])
  vi.mocked(finance.listPeople).mockResolvedValue([
    {
      id: 'p1',
      name: 'Ravi',
      categoryId: null,
      creditCategoryId: null,
      payees: ['RAVI'],
    },
  ])
})

describe('CategoriesPage', () => {
  it('lists categories with their counts-as-spending switch', async () => {
    renderPage()
    const food = await screen.findByRole('switch', {
      name: /Food counts as spending/,
    })
    expect((food as HTMLInputElement).checked).toBe(true)
    const loan = screen.getByRole('switch', { name: /Loan counts as spending/ })
    expect((loan as HTMLInputElement).checked).toBe(false)
  })

  it('creates a category and shows it', async () => {
    vi.mocked(finance.createCategory).mockResolvedValue({
      id: 'c3',
      name: 'Pets',
      countsAsSpending: true,
    })
    renderPage()
    fireEvent.change(await screen.findByLabelText('New category name'), {
      target: { value: 'Pets' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add category' }))
    expect(
      await screen.findByRole('button', { name: 'Rename category Pets' }),
    ).toBeTruthy()
    expect(finance.createCategory).toHaveBeenCalledWith('Pets', true)
  })

  it('shows the message for a duplicate name', async () => {
    vi.mocked(finance.createCategory).mockRejectedValue(
      new finance.ApiError(409, 'You already have a category with that name.'),
    )
    renderPage()
    fireEvent.change(await screen.findByLabelText('New category name'), {
      target: { value: 'food' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add category' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(
      /already have a category/,
    )
  })

  it('toggles counts-as-spending', async () => {
    vi.mocked(finance.updateCategory).mockResolvedValue({
      ...food,
      countsAsSpending: false,
    })
    renderPage()
    fireEvent.click(
      await screen.findByRole('switch', { name: /Food counts as spending/ }),
    )
    await waitFor(() =>
      expect(finance.updateCategory).toHaveBeenCalledWith('c1', 'Food', false),
    )
    await waitFor(() =>
      expect(
        (
          screen.getByRole('switch', {
            name: /Food counts as spending/,
          }) as HTMLInputElement
        ).checked,
      ).toBe(false),
    )
  })

  it('renames inline, keeps the editor open on a 400 and returns focus on success', async () => {
    vi.mocked(finance.updateCategory).mockRejectedValueOnce(
      new finance.ApiError(400, 'Use a name of 1 to 50 characters.'),
    )
    renderPage()
    fireEvent.click(
      await screen.findByRole('button', { name: 'Rename category Food' }),
    )
    const input = screen.getByLabelText('New name for Food')
    fireEvent.change(input, { target: { value: 'Uncategorized' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/1 to 50/)
    expect(screen.getByLabelText('New name for Food')).toBeTruthy()

    vi.mocked(finance.updateCategory).mockResolvedValue({
      ...food,
      name: 'Meals',
    })
    fireEvent.change(input, { target: { value: 'Meals' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    const back = await screen.findByRole('button', {
      name: 'Rename category Meals',
    })
    expect(document.activeElement).toBe(back)
  })

  it('confirms before deleting; Escape keeps it and returns focus', async () => {
    vi.mocked(finance.deleteCategory).mockResolvedValue(null)
    renderPage()
    fireEvent.click(
      await screen.findByRole('button', { name: 'Delete category Food' }),
    )
    const keep = screen.getByRole('button', { name: 'Keep' })
    expect(document.activeElement).toBe(keep)
    fireEvent.keyDown(keep, { key: 'Escape' })
    expect(document.activeElement).toBe(
      screen.getByRole('button', { name: 'Delete category Food' }),
    )
    expect(finance.deleteCategory).not.toHaveBeenCalled()

    fireEvent.click(
      screen.getByRole('button', { name: 'Delete category Food' }),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Delete Food' }))
    await waitFor(() =>
      expect(finance.deleteCategory).toHaveBeenCalledWith('c1'),
    )
    await waitFor(() => expect(screen.queryByText('Food')).toBeNull())
  })

  it('returns focus to the Delete button when a delete fails', async () => {
    vi.mocked(finance.deleteCategory).mockRejectedValue(
      new finance.ApiError(404, 'That category no longer exists.'),
    )
    renderPage()
    fireEvent.click(
      await screen.findByRole('button', { name: 'Delete category Food' }),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Delete Food' }))

    expect((await screen.findByRole('alert')).textContent).toMatch(
      /no longer exists/,
    )
    await waitFor(() =>
      expect(document.activeElement).toBe(
        screen.getByRole('button', { name: 'Delete category Food' }),
      ),
    )
  })

  it('sets a person default category for money in and money out', async () => {
    vi.mocked(finance.setPersonCategory).mockResolvedValue({
      id: 'p1',
      name: 'Ravi',
      categoryId: 'c2',
      creditCategoryId: null,
      payees: ['RAVI'],
    })
    renderPage()
    fireEvent.click(await screen.findByRole('tab', { name: 'People' }))
    fireEvent.change(
      await screen.findByLabelText('Money out default for Ravi'),
      {
        target: { value: 'c2' },
      },
    )
    await waitFor(() =>
      expect(finance.setPersonCategory).toHaveBeenCalledWith('p1', 'c2', false),
    )
    fireEvent.change(screen.getByLabelText('Money in default for Ravi'), {
      target: { value: 'c1' },
    })
    await waitFor(() =>
      expect(finance.setPersonCategory).toHaveBeenCalledWith('p1', 'c1', true),
    )
  })

  it('merges people and moves a UPI name back out', async () => {
    const ravi = {
      id: 'p1',
      name: 'Ravi',
      categoryId: null,
      creditCategoryId: null,
      payees: ['RAVI'],
    }
    const raviK = {
      id: 'p2',
      name: 'Ravi K',
      categoryId: null,
      creditCategoryId: null,
      payees: ['RAVI K'],
    }
    vi.mocked(finance.listPeople).mockResolvedValue([ravi, raviK])
    vi.mocked(finance.mergePerson).mockResolvedValue([
      { ...ravi, creditCategoryId: null, payees: ['RAVI', 'RAVI K'] },
    ])
    vi.mocked(finance.movePayee).mockResolvedValue([ravi, raviK])
    renderPage('/categories?tab=people')
    fireEvent.change(
      await screen.findByRole('combobox', { name: 'Merge Ravi K into' }),
      { target: { value: 'p1' } },
    )
    const move = await screen.findByRole('combobox', { name: 'Move RAVI K to' })
    expect(finance.mergePerson).toHaveBeenCalledWith('p2', 'p1')

    fireEvent.change(move, { target: { value: 'new' } })
    await waitFor(() =>
      expect(finance.movePayee).toHaveBeenCalledWith('p1', 'RAVI K', null),
    )
    expect(
      await screen.findByRole('combobox', { name: 'Merge Ravi K into' }),
    ).toBeTruthy()
  })

  it('switches tabs with the arrow keys and keeps the URL in sync', async () => {
    vi.mocked(finance.listMerchantNames).mockResolvedValue(names())
    renderPage()
    const categoriesTab = await screen.findByRole('tab', { name: 'Categories' })
    expect(categoriesTab.getAttribute('aria-selected')).toBe('true')
    fireEvent.keyDown(categoriesTab, { key: 'ArrowRight' })
    const people = screen.getByRole('tab', { name: 'People' })
    expect(people.getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(people)
    expect(screen.getByTestId('location').textContent).toBe('?tab=people')
    fireEvent.keyDown(people, { key: 'End' })
    expect(screen.getByTestId('location').textContent).toBe(
      '?tab=merchant-names',
    )
    expect(
      screen.getByRole('tabpanel', { name: 'Merchant names' }),
    ).toBeTruthy()
    fireEvent.keyDown(document.activeElement!, { key: 'ArrowRight' })
    expect(document.activeElement).toBe(categoriesTab)
    expect(screen.getByTestId('location').textContent).toBe('')
  })

  describe('merchant names', () => {
    const open = () => renderPage('/categories?tab=merchant-names')
    const shareSwitch = () =>
      screen.findByRole('switch', {
        name: 'Share my merchant names to improve suggestions for other users',
      }) as Promise<HTMLInputElement>

    it('renames a merchant name inline', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(names())
      vi.mocked(finance.renameMerchantName).mockResolvedValue(
        names({ items: [{ ...acme, name: 'Acme Mart' }, beta, rahul] }),
      )
      open()
      fireEvent.click(
        await screen.findByRole('button', {
          name: 'Rename merchant Acme Store',
        }),
      )
      fireEvent.change(screen.getByLabelText('New name for Acme Store'), {
        target: { value: ' Acme Mart ' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))
      expect(
        await screen.findByRole('button', {
          name: 'Rename merchant Acme Mart',
        }),
      ).toBeTruthy()
      expect(finance.renameMerchantName).toHaveBeenCalledWith('a1', 'Acme Mart')
    })

    it('moves a payee to another merchant', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(names())
      vi.mocked(finance.renameMerchantName).mockResolvedValue(
        names({
          items: [
            acme,
            { ...beta, name: 'Acme Store', merchantId: 'm1' },
            rahul,
          ],
        }),
      )
      open()
      fireEvent.change(
        await screen.findByRole('combobox', {
          name: 'Move BETA MART to merchant',
        }),
        { target: { value: 'Acme Store' } },
      )
      await waitFor(() =>
        expect(finance.renameMerchantName).toHaveBeenCalledWith(
          'a3',
          'Acme Store',
        ),
      )
      expect(
        (await screen.findAllByText(/one of 2 payees of this merchant/)).length,
      ).toBe(2)
    })

    it('shows and sets a merchant default category', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(names())
      vi.mocked(finance.setMerchantCategory).mockResolvedValue(null)
      open()
      const select = (await screen.findByRole('combobox', {
        name: 'Money out default for Acme Store',
      })) as HTMLSelectElement
      expect(select.value).toBe('')
      expect(
        (
          screen.getByRole('combobox', {
            name: 'Money out default for Beta Mart',
          }) as HTMLSelectElement
        ).value,
      ).toBe('c2')
      fireEvent.change(select, { target: { value: 'c1' } })
      await waitFor(() => expect(select.value).toBe('c1'))
      expect(finance.setMerchantCategory).toHaveBeenCalledWith(
        'm1',
        'c1',
        false,
      )
    })

    it('is off by default with every row disabled', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(names())
      open()
      expect((await shareSwitch()).checked).toBe(false)
      const box = screen.getByRole('checkbox', {
        name: 'Share Acme Store',
      }) as HTMLInputElement
      expect(box.disabled).toBe(true)
      expect(
        document.getElementById(box.getAttribute('aria-describedby')!)
          ?.textContent,
      ).toMatch(/Turn on sharing above/)
      expect(screen.getByText(/Offensive names are reported/)).toBeTruthy()
    })

    it('confirms turning on with the number of names to share', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(names())
      vi.mocked(finance.setMerchantNameSharing).mockResolvedValue(
        names({ shareMerchantNames: true }),
      )
      open()
      fireEvent.click(await shareSwitch())
      expect(screen.getByText('2 names will be shared.')).toBeTruthy()
      const cancel = screen.getByRole('button', { name: 'Cancel' })
      expect(document.activeElement).toBe(cancel)
      fireEvent.keyDown(cancel, { key: 'Escape' })
      expect(screen.queryByText('2 names will be shared.')).toBeNull()
      expect(finance.setMerchantNameSharing).not.toHaveBeenCalled()

      fireEvent.click(await shareSwitch())
      fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
      await waitFor(() =>
        expect(finance.setMerchantNameSharing).toHaveBeenCalledWith(true),
      )
      await waitFor(async () =>
        expect((await shareSwitch()).checked).toBe(true),
      )
    })

    it('opts a single name out through its checkbox', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(
        names({
          shareMerchantNames: true,
          items: [{ ...acme, shared: true }, beta],
        }),
      )
      vi.mocked(finance.setMerchantNameShared).mockResolvedValue({
        ...acme,
        optedOut: true,
      })
      open()
      const box = (await screen.findByRole('checkbox', {
        name: 'Share Acme Store',
      })) as HTMLInputElement
      expect(box.checked).toBe(true)
      fireEvent.click(box)
      await waitFor(() =>
        expect(finance.setMerchantNameShared).toHaveBeenCalledWith('a1', false),
      )
      await waitFor(() => expect(box.checked).toBe(false))
    })

    it('disables rows that cannot be shared and says why', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(
        names({
          shareMerchantNames: true,
          items: [
            rahul,
            { ...beta, shareable: false, notShareableReason: 'NAME_OFFENSIVE' },
          ],
        }),
      )
      open()
      const box = (await screen.findByRole('checkbox', {
        name: 'Share Rahul Shop',
      })) as HTMLInputElement
      expect(box.disabled).toBe(true)
      expect(screen.getByText(/Transfer to a person/)).toBeTruthy()
      const blocked = screen.getByRole('checkbox', { name: 'Share Beta Mart' })
      const hint = document.getElementById(
        blocked.getAttribute('aria-describedby')!,
      )
      expect(hint?.textContent).toMatch(/This name can.t be shared/)
      expect(hint?.textContent).toMatch(/Offensive names are reported/)
    })

    it('marks a reported shared name and offers to unshare it', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(
        names({
          shareMerchantNames: true,
          items: [{ ...acme, shared: true, reported: true }],
        }),
      )
      vi.mocked(finance.setMerchantNameShared).mockResolvedValue({
        ...acme,
        optedOut: true,
        reported: true,
      })
      open()
      expect(await screen.findByText('Reported')).toBeTruthy()
      const box = screen.getByRole('checkbox', {
        name: 'Unshare Acme Store',
      }) as HTMLInputElement
      expect(box.checked).toBe(true)
      box.focus()
      fireEvent.click(box)
      await waitFor(() =>
        expect(finance.setMerchantNameShared).toHaveBeenCalledWith('a1', false),
      )
      const after = (await screen.findByRole('checkbox', {
        name: 'Share Acme Store',
      })) as HTMLInputElement
      expect(after).toBe(box)
      expect(after.checked).toBe(false)
      expect(document.activeElement).toBe(box)
      expect(screen.getByText('Reported')).toBeTruthy()
      expect(
        document.getElementById(box.getAttribute('aria-describedby')!)
          ?.textContent,
      ).toMatch(/Offensive names are reported/)
    })

    it('checks a row only when its name is actually shared while on', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(
        names({
          shareMerchantNames: true,
          items: [acme, { ...beta, shared: true }],
        }),
      )
      open()
      const a = (await screen.findByRole('checkbox', {
        name: 'Share Acme Store',
      })) as HTMLInputElement
      const b = screen.getByRole('checkbox', {
        name: 'Share Beta Mart',
      }) as HTMLInputElement
      expect(a.checked).toBe(false)
      expect(b.checked).toBe(true)
    })

    it('explains a revoked account and disables every control', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(
        names({ sharingRevoked: true }),
      )
      open()
      expect((await shareSwitch()).disabled).toBe(true)
      expect(
        screen.getByText(/Sharing has been turned off for your account/),
      ).toBeTruthy()
      const box = screen.getByRole('checkbox', {
        name: 'Share Acme Store',
      }) as HTMLInputElement
      expect(box.disabled).toBe(true)
    })

    it('shows the empty state', async () => {
      vi.mocked(finance.listMerchantNames).mockResolvedValue(
        names({ items: [] }),
      )
      open()
      expect(
        await screen.findByText(/You haven.t named any merchants yet/),
      ).toBeTruthy()
    })
  })
})
