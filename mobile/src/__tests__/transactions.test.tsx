import { fireEvent, render, screen } from '@testing-library/react-native'
import { listTransactions, setTransfer } from '@expense-analyzer/api-client'
import { useLocalSearchParams } from 'expo-router'
import Transactions from '../app/(tabs)/transactions'

const mockRow = {
  id: 't1',
  date: '2026-01-10',
  description: 'UPI/SWIGGY/123',
  normalizedDescription: 'SWIGGY',
  amount: 250,
  direction: 'DEBIT',
  category: 'Food',
  categoryId: 'c1',
  categoryOverridden: false,
  merchantId: 'm1',
  merchantName: 'Swiggy',
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
}

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
  setTransfer: jest.fn(async () => ({ ...mockRow, transfer: true })),
}))

jest.mock('@expo/vector-icons/Ionicons', () => () => null)
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}))
jest.mock('expo-router', () => ({ useLocalSearchParams: jest.fn(() => ({})) }))

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

it('opens filtered to the category it was opened for', async () => {
  jest.mocked(useLocalSearchParams).mockReturnValueOnce({ category: 'Food' })
  await render(<Transactions />)
  expect(lastQuery()).toMatchObject({ category: 'Food' })
})

it('marks a transaction as a transfer from its details', async () => {
  jest.mocked(listTransactions).mockResolvedValue({
    items: [mockRow],
    page: 0,
    size: 50,
    totalItems: 1,
    totalPages: 1,
  } as never)
  await render(<Transactions />)
  await fireEvent.press(await screen.findByText('Swiggy'))
  await fireEvent.press(
    screen.getByText('Mark as a transfer between my accounts'),
  )
  expect(setTransfer).toHaveBeenCalledWith('t1', true)
  expect(
    await screen.findByText('Marked as a transfer between your accounts.'),
  ).toBeTruthy()
})

it('shows one statement when opened from it and can show all again', async () => {
  jest.mocked(useLocalSearchParams).mockReturnValue({ statementId: 's1' })
  await render(<Transactions />)
  expect(lastQuery()).toMatchObject({ statementId: 's1' })
  await fireEvent.press(await screen.findByText('Show all statements'))
  expect(lastQuery()).toMatchObject({ statementId: undefined })
  jest.mocked(useLocalSearchParams).mockReturnValue({})
})
