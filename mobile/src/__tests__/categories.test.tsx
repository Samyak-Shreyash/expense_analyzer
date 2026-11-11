import { fireEvent, render, screen } from '@testing-library/react-native'
import {
  mergePerson,
  setMerchantCategory,
  setPersonCategory,
} from '@expense-analyzer/api-client'
import { router, useLocalSearchParams } from 'expo-router'
import Categories from '../app/(tabs)/categories'

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), setParams: jest.fn() },
  useLocalSearchParams: jest.fn(() => ({})),
}))
jest.mock('@expo/vector-icons/Ionicons', () => () => null)
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}))
const mockRavi = {
  id: 'p1',
  name: 'Ravi',
  categoryId: null,
  creditCategoryId: null,
  payees: ['RAVI'],
}
const mockAnu = { ...mockRavi, id: 'p2', name: 'Anu', payees: ['ANU'] }
jest.mock('@expense-analyzer/api-client', () => ({
  listCategories: jest.fn(async () => [
    { id: 'c1', name: 'Food', countsAsSpending: true },
    { id: 'c2', name: 'Loan', countsAsSpending: false },
  ]),
  categoryCounts: jest.fn(async () => ({ c1: 12 })),
  createCategory: jest.fn(),
  deleteCategory: jest.fn(),
  listPeople: jest.fn(async () => [mockRavi, mockAnu]),
  setPersonCategory: jest.fn(async () => ({ ...mockRavi, categoryId: 'c1' })),
  mergePerson: jest.fn(async () => [mockAnu]),
  updatePerson: jest.fn(),
  movePayee: jest.fn(),
  listMerchantNames: jest.fn(async () => ({
    shareMerchantNames: false,
    sharingRevoked: false,
    items: [
      {
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
      },
    ],
  })),
  setMerchantCategory: jest.fn(async () => null),
  renameMerchantName: jest.fn(),
  setMerchantNameShared: jest.fn(),
  setMerchantNameSharing: jest.fn(),
}))

const showTab = (tab: string) =>
  jest.mocked(useLocalSearchParams).mockReturnValue({ tab })

// Modal and list modules load on first use; slow CI runners need more than 5 s.
jest.setTimeout(20_000)

beforeEach(() => {
  jest.clearAllMocks()
  jest.mocked(useLocalSearchParams).mockReturnValue({})
})

it('shows each category with its count and opens its transactions', async () => {
  await render(<Categories />)
  expect(await screen.findByText('Loan (0)')).toBeTruthy()
  await fireEvent.press(screen.getByLabelText('Food, 12 transactions'))
  expect(router.push).toHaveBeenCalledWith({
    pathname: '/transactions',
    params: { category: 'Food' },
  })
  await fireEvent.press(screen.getByText('People'))
  expect(router.setParams).toHaveBeenCalledWith({ tab: 'people' })
})

it('shows people collapsed and sets defaults and merges once opened', async () => {
  showTab('people')
  await render(<Categories />)
  const ravi = await screen.findByLabelText('Ravi, 1 UPI name')
  expect(screen.queryByText('Default categories')).toBeNull()

  await fireEvent.press(ravi)
  await fireEvent.press(
    screen.getByLabelText('Money out default for Ravi: No default'),
  )
  await fireEvent.press(screen.getByText('Food'))
  expect(setPersonCategory).toHaveBeenCalledWith('p1', 'c1', false)

  await fireEvent.press(screen.getByLabelText('Merge Ravi into: Merge into…'))
  const anu = screen.getAllByText('Anu')
  await fireEvent.press(anu[0]) // the option: Ravi's sheet comes before Anu's row
  expect(mergePerson).toHaveBeenCalledWith('p1', 'p2')
})

it('shows merchants collapsed and sets a merchant default once opened', async () => {
  showTab('merchant-names')
  await render(<Categories />)
  const acme = await screen.findByLabelText(
    'Acme Store, 1 payee · 3 transactions',
  )
  expect(screen.queryByText('Default categories')).toBeNull()

  await fireEvent.press(acme)
  await fireEvent.press(
    screen.getByLabelText('Money in default for Acme Store: No default'),
  )
  await fireEvent.press(screen.getByText('Loan'))
  expect(setMerchantCategory).toHaveBeenCalledWith('m1', 'c2', true)
})
