import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
}))

const food = { id: 'c1', name: 'Food', countsAsSpending: true }
const loan = { id: 'c2', name: 'Loan', countsAsSpending: false }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(finance.listCategories).mockResolvedValue([food, loan])
  vi.mocked(finance.listPeople).mockResolvedValue([
    { id: 'p1', name: 'Ravi', categoryId: null },
  ])
})

describe('CategoriesPage', () => {
  it('lists categories with their counts-as-spending switch', async () => {
    render(<CategoriesPage />)
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
    render(<CategoriesPage />)
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
    render(<CategoriesPage />)
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
    render(<CategoriesPage />)
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
    render(<CategoriesPage />)
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
    render(<CategoriesPage />)
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

  it('sets a person default category', async () => {
    vi.mocked(finance.updatePerson).mockResolvedValue({
      id: 'p1',
      name: 'Ravi',
      categoryId: 'c2',
    })
    render(<CategoriesPage />)
    fireEvent.change(
      await screen.findByLabelText('Default category for Ravi'),
      {
        target: { value: 'c2' },
      },
    )
    await waitFor(() =>
      expect(finance.updatePerson).toHaveBeenCalledWith('p1', 'Ravi', 'c2'),
    )
  })
})
