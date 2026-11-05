import * as client from '@expense-analyzer/api-client'
import { act, fireEvent, render, screen } from '@testing-library/react-native'
import { Text } from 'react-native'
import { restoreSession } from '../../services/session'
import { secureTokens } from '../../services/api'
import { appLock, unlockWithBiometrics } from '../../services/deviceLock'
import { AuthGate } from './AuthGate'

jest.mock('../../services/api', () => ({
  startApiClient: jest.fn(),
  secureTokens: { load: jest.fn(), save: jest.fn() },
}))
jest.mock('../../services/session', () => ({ restoreSession: jest.fn() }))
jest.mock('../../services/deviceLock', () => ({
  appLock: {
    isSetUp: jest.fn(),
    isExhausted: jest.fn(),
    setPin: jest.fn(),
    verify: jest.fn(),
    clear: jest.fn(),
    resetFailures: jest.fn(),
  },
  unlockWithBiometrics: jest.fn(),
}))
jest.mock('@expense-analyzer/api-client', () => ({
  login: jest.fn(),
  logout: jest.fn(),
  register: jest.fn(),
  setAuthHandlers: jest.fn(),
}))

const app = () => (
  <AuthGate>
    <Text>Financial data</Text>
  </AuthGate>
)
const type = (label: string, value: string) =>
  fireEvent.changeText(screen.getByLabelText(label), value)

beforeEach(() => {
  jest.clearAllMocks()
  jest.mocked(unlockWithBiometrics).mockResolvedValue(false)
  jest.mocked(appLock.setPin).mockResolvedValue(undefined)
  jest.mocked(appLock.isExhausted).mockResolvedValue(false)
  jest.mocked(secureTokens.save).mockResolvedValue(undefined)
  jest.mocked(client.logout).mockResolvedValue(undefined)
  jest.mocked(appLock.clear).mockResolvedValue(undefined)
  jest.mocked(appLock.resetFailures).mockResolvedValue(undefined)
})

it('shows login, not the app, when signed out', async () => {
  jest.mocked(restoreSession).mockResolvedValue(false)
  await render(app())
  expect(await screen.findByLabelText('Email')).toBeTruthy()
  expect(screen.queryByText('Financial data')).toBeNull()
})

it('requires lock setup after first login before showing the app', async () => {
  jest.mocked(restoreSession).mockResolvedValue(false)
  jest.mocked(client.login).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(false)
  await render(app())
  await screen.findByLabelText('Email')
  await type('Email', 'a@example.com')
  await type('Password', 'long-enough-1')
  await fireEvent.press(screen.getByRole('button', { name: 'Log in' }))

  expect(await screen.findByLabelText('New PIN')).toBeTruthy()
  expect(screen.queryByText('Financial data')).toBeNull()

  await type('New PIN', '123456')
  await fireEvent.press(screen.getByRole('button', { name: 'Set PIN' }))
  expect(await screen.findByText('Financial data')).toBeTruthy()
  expect(appLock.setPin).toHaveBeenCalledWith('123456')
})

it('starts locked after a restart and opens on the right PIN', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(appLock.verify).mockResolvedValue('ok')
  await render(app())
  expect(await screen.findByLabelText('PIN')).toBeTruthy()
  expect(screen.queryByText('Financial data')).toBeNull()

  await type('PIN', '123456')
  await fireEvent.press(screen.getByRole('button', { name: 'Unlock' }))
  expect(await screen.findByText('Financial data')).toBeTruthy()
})

it('opens with biometrics and clears PIN failures', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(unlockWithBiometrics).mockResolvedValue(true)
  await render(app())
  expect(await screen.findByText('Financial data')).toBeTruthy()
  expect(appLock.resetFailures).toHaveBeenCalled()
})

it('signs out after too many wrong PINs', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(appLock.verify).mockResolvedValue('exhausted')
  jest.mocked(client.logout).mockResolvedValue(undefined)
  await render(app())
  await screen.findByLabelText('PIN')
  await type('PIN', '000000')
  await fireEvent.press(screen.getByRole('button', { name: 'Unlock' }))
  expect(await screen.findByLabelText('Email')).toBeTruthy()
  expect(client.logout).toHaveBeenCalled()
  expect(appLock.clear).toHaveBeenCalled()
})

it('still signs out locally when the server cannot be reached', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(appLock.verify).mockResolvedValue('exhausted')
  jest.mocked(client.logout).mockRejectedValue(new Error('offline'))
  await render(app())
  await screen.findByLabelText('PIN')
  await type('PIN', '000000')
  await fireEvent.press(screen.getByRole('button', { name: 'Unlock' }))
  expect(await screen.findByLabelText('Email')).toBeTruthy()
})

it('returns to login when the session is lost', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(unlockWithBiometrics).mockResolvedValue(true)
  await render(app())
  await screen.findByText('Financial data')
  await act(async () => {
    jest.mocked(client.setAuthHandlers).mock.calls[0][0].onAuthLost()
  })
  expect(await screen.findByLabelText('Email')).toBeTruthy()
  expect(appLock.clear).toHaveBeenCalled()
})

it('tells the user when an email is already registered', async () => {
  jest.mocked(restoreSession).mockResolvedValue(false)
  jest
    .mocked(client.register)
    .mockResolvedValue('That email is already registered.')
  await render(app())
  await fireEvent.press(
    await screen.findByRole('button', { name: 'Create an account' }),
  )
  await type('Email', 'a@example.com')
  await type('Password', 'long-enough-1')
  await fireEvent.press(screen.getByRole('button', { name: 'Register' }))
  expect((await screen.findByRole('alert')).props.children).toBe(
    'That email is already registered.',
  )
  expect(client.login).not.toHaveBeenCalled()
})

it('treats a stored token without a PIN as signed out, so no one can choose a new PIN without the password', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(false)
  await render(app())
  expect(await screen.findByLabelText('Email')).toBeTruthy()
  expect(screen.queryByLabelText('New PIN')).toBeNull()
  expect(secureTokens.save).toHaveBeenCalledWith(null)
})

it('clears the old lock when the session is gone at startup', async () => {
  jest.mocked(restoreSession).mockResolvedValue(false)
  await render(app())
  await screen.findByLabelText('Email')
  expect(appLock.clear).toHaveBeenCalled()
})

it('signs out at startup if the PIN attempts were already used up', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(appLock.isExhausted).mockResolvedValue(true)
  await render(app())
  expect(await screen.findByLabelText('Email')).toBeTruthy()
  expect(client.logout).toHaveBeenCalled()
})

it('always sets up a new PIN after a password login, even if an old one remains', async () => {
  jest.mocked(restoreSession).mockResolvedValue(false)
  jest.mocked(client.login).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  await render(app())
  await screen.findByLabelText('Email')
  await type('Email', 'a@example.com')
  await type('Password', 'long-enough-1')
  await fireEvent.press(screen.getByRole('button', { name: 'Log in' }))
  expect(await screen.findByLabelText('New PIN')).toBeTruthy()
})

it('ignores a biometric success that arrives after signing out', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  let finishPrompt = (_: boolean) => {}
  jest.mocked(unlockWithBiometrics).mockReturnValue(
    new Promise((resolve) => {
      finishPrompt = resolve
    }),
  )
  await render(app())
  await fireEvent.press(
    await screen.findByRole('button', { name: 'Forgot PIN? Log in again' }),
  )
  await screen.findByLabelText('Email')
  await act(async () => finishPrompt(true))
  expect(screen.queryByText('Financial data')).toBeNull()
  expect(screen.getByLabelText('Email')).toBeTruthy()
})

it('shows login at once when signing out, without waiting for the network', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(client.logout).mockReturnValue(new Promise(() => {}))
  await render(app())
  await fireEvent.press(
    await screen.findByRole('button', { name: 'Forgot PIN? Log in again' }),
  )
  expect(await screen.findByLabelText('Email')).toBeTruthy()
})

it('deletes the token when the session is lost', async () => {
  jest.mocked(restoreSession).mockResolvedValue(true)
  jest.mocked(appLock.isSetUp).mockResolvedValue(true)
  jest.mocked(unlockWithBiometrics).mockResolvedValue(true)
  await render(app())
  await screen.findByText('Financial data')
  await act(async () => {
    jest.mocked(client.setAuthHandlers).mock.calls[0][0].onAuthLost()
  })
  await screen.findByLabelText('Email')
  expect(secureTokens.save).toHaveBeenCalledWith(null)
})
