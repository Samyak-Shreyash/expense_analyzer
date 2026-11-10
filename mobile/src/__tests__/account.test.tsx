import { fireEvent, render, screen } from '@testing-library/react-native'
import { changePassword, updateProfile } from '@expense-analyzer/api-client'
import { router, useLocalSearchParams } from 'expo-router'
import Account from '../app/account'
import { AccountMenu } from '../components/AccountMenu'

const mockSignOut = jest.fn(async () => {})
jest.mock('../features/auth/AuthGate', () => ({
  useSignOut: () => mockSignOut,
}))
jest.mock('@expo/vector-icons/Ionicons', () => () => null)
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}))
jest.mock('expo-router', () => ({
  router: { push: jest.fn() },
  useLocalSearchParams: jest.fn(),
  Stack: { Screen: () => null },
}))

const mockProfile = {
  email: 'a@example.com',
  fullName: 'Alice Smith',
  role: 'USER',
  preferences: { theme: 'light', currency: 'inr', notifications: true },
}
jest.mock('@expense-analyzer/api-client', () => ({
  getProfile: jest.fn(async () => mockProfile),
  updateProfile: jest.fn(async () => mockProfile),
  changePassword: jest.fn(async () => null),
  deleteAccount: jest.fn(async () => null),
}))

const showSection = (section: string) =>
  jest.mocked(useLocalSearchParams).mockReturnValue({ section })

beforeEach(() => jest.clearAllMocks())

it('shows the name and a log out button, and opens account options', async () => {
  await render(<AccountMenu />)
  await fireEvent.press(await screen.findByLabelText('Account: Alice Smith'))
  await fireEvent.press(screen.getByText('Change password'))
  expect(router.push).toHaveBeenCalledWith({
    pathname: '/account',
    params: { section: 'password' },
  })

  await fireEvent.press(screen.getByLabelText('Log out'))
  expect(mockSignOut).toHaveBeenCalled()
})

it('saves preferences without touching the name', async () => {
  showSection('preferences')
  await render(<Account />)
  await fireEvent.press(await screen.findByText('USD'))
  await fireEvent.press(screen.getByText('Save'))
  expect(updateProfile).toHaveBeenCalledWith(null, {
    theme: 'light',
    currency: 'usd',
    notifications: true,
  })
  expect(await screen.findByText('Preferences saved.')).toBeTruthy()
})

it('rejects mismatched passwords, then changes it and signs out', async () => {
  showSection('password')
  await render(<Account />)
  await fireEvent.changeText(
    await screen.findByLabelText('Current password'),
    'oldpass1',
  )
  await fireEvent.changeText(screen.getByLabelText('New password'), 'newpass12')
  await fireEvent.changeText(
    screen.getByLabelText('Confirm new password'),
    'newpass13',
  )
  await fireEvent.press(screen.getByRole('button', { name: 'Change password' }))
  expect(screen.getByText('The new passwords do not match.')).toBeTruthy()
  expect(changePassword).not.toHaveBeenCalled()

  await fireEvent.changeText(
    screen.getByLabelText('Confirm new password'),
    'newpass12',
  )
  await fireEvent.press(screen.getByRole('button', { name: 'Change password' }))
  expect(changePassword).toHaveBeenCalledWith('oldpass1', 'newpass12')
  expect(mockSignOut).toHaveBeenCalled()
})
