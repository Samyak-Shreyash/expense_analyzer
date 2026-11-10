import { fireEvent, render, screen } from '@testing-library/react-native'
import { router } from 'expo-router'
import Categories from '../app/(tabs)/categories'

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }))
jest.mock('@expense-analyzer/api-client', () => ({
  listCategories: jest.fn(async () => [
    { id: 'c1', name: 'Food', countsAsSpending: true },
    { id: 'c2', name: 'Loan', countsAsSpending: false },
  ]),
  categoryCounts: jest.fn(async () => ({ c1: 12 })),
  createCategory: jest.fn(),
  deleteCategory: jest.fn(),
}))

it('shows each category with its count and opens its transactions', async () => {
  await render(<Categories />)
  expect(await screen.findByText('Loan (0)')).toBeTruthy()
  await fireEvent.press(screen.getByLabelText('Food, 12 transactions'))
  expect(router.push).toHaveBeenCalledWith({
    pathname: '/transactions',
    params: { category: 'Food' },
  })
})
