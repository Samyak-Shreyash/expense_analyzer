import { fireEvent, render, screen } from '@testing-library/react-native'
import { listTransactions } from '@expense-analyzer/api-client'
import Transactions from '../app/(tabs)/transactions'

jest.mock('@expense-analyzer/api-client', () => ({
  listTransactions: jest.fn(async () => ({
    items: [],
    page: 0,
    size: 50,
    totalItems: 0,
    totalPages: 0,
  })),
  listCategories: jest.fn(async () => [
    { id: 'c1', name: 'Food', countsAsSpending: true },
  ]),
  setTransactionCategory: jest.fn(),
}))

const lastQuery = () => jest.mocked(listTransactions).mock.lastCall?.[0]

beforeEach(() => jest.mocked(listTransactions).mockClear())

it('applies a date shortcut and toggles it off again', async () => {
  jest.useFakeTimers({ doNotFake: ['nextTick', 'setImmediate'] })
  jest.setSystemTime(new Date('2026-03-15T12:00:00Z'))
  try {
    await render(<Transactions />)
    await fireEvent.press(await screen.findByText('Last month'))
    expect(lastQuery()).toMatchObject({ from: '2026-02-01', to: '2026-02-28' })

    await fireEvent.press(screen.getByText('Last month'))
    expect(lastQuery()).toMatchObject({ from: undefined, to: undefined })
  } finally {
    jest.useRealTimers()
  }
})

it('applies the filter panel and counts active filters', async () => {
  await render(<Transactions />)
  await fireEvent.press(await screen.findByText('Filters'))
  await fireEvent.press(screen.getByText('Money out'))
  await fireEvent.press(screen.getByText('Hide transfers'))
  await fireEvent.press(await screen.findByText('Food'))
  await fireEvent.changeText(screen.getByLabelText('Minimum amount'), '100')
  await fireEvent.press(screen.getByText('Apply filters'))

  expect(lastQuery()).toMatchObject({
    direction: 'DEBIT',
    transfer: 'false',
    category: 'Food',
    minAmount: 100,
    maxAmount: undefined,
  })
  expect(screen.getByText('Filters (4)')).toBeTruthy()
  expect(
    await screen.findByText('No transactions match these filters.'),
  ).toBeTruthy()

  await fireEvent.press(screen.getByText('Filters (4)'))
  await fireEvent.press(screen.getByText('Clear all filters'))
  expect(lastQuery()).toMatchObject({
    direction: undefined,
    category: undefined,
  })
  expect(screen.getByText('Filters')).toBeTruthy()
})
