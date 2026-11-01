import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Profile } from '../../services/finance'
import * as finance from '../../services/finance'
import { ProfilePage } from './ProfilePage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ logout: vi.fn() }),
}))
vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
}))

const profile = (over: Partial<Profile> = {}): Profile => ({
  email: 'a@example.com',
  fullName: 'Alice',
  role: 'USER',
  preferences: { theme: 'dark', currency: 'usd', notifications: false },
  ...over,
})

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.mocked(finance.getProfile).mockResolvedValue(profile())
  })

  it('shows the current details', async () => {
    render(<ProfilePage />)
    expect(await screen.findByLabelText('Full name')).toHaveProperty(
      'value',
      'Alice',
    )
    expect(screen.getByLabelText('Theme')).toHaveProperty('value', 'dark')
    expect(screen.getByLabelText('Currency')).toHaveProperty('value', 'usd')
    expect(screen.getByText(/a@example.com/)).toBeTruthy()
  })

  it('saves edits and confirms', async () => {
    vi.mocked(finance.updateProfile).mockResolvedValue(
      profile({ fullName: 'Alice B' }),
    )
    render(<ProfilePage />)
    fireEvent.change(await screen.findByLabelText('Full name'), {
      target: { value: 'Alice B' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect((await screen.findByRole('status')).textContent).toBe(
      'Profile saved.',
    )
    expect(finance.updateProfile).toHaveBeenCalledWith('Alice B', {
      theme: 'dark',
      currency: 'usd',
      notifications: false,
    })
  })

  it('shows the error when saving fails', async () => {
    vi.mocked(finance.updateProfile).mockRejectedValue(new Error('Bad name.'))
    render(<ProfilePage />)
    await screen.findByLabelText('Full name')
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect((await screen.findByRole('alert')).textContent).toBe('Bad name.')
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('shows an error when loading fails', async () => {
    vi.mocked(finance.getProfile).mockRejectedValue(new Error('x'))
    render(<ProfilePage />)
    expect((await screen.findByRole('alert')).textContent).toMatch(
      /could not load/i,
    )
  })
})
