import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as finance from '../../services/finance'
import { ChangePasswordSection } from './ChangePasswordSection'

const { logout } = vi.hoisted(() => ({ logout: vi.fn() }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ logout }) }))
vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  changePassword: vi.fn(),
}))

function fill(current: string, next: string, confirm: string) {
  const set = (label: string, value: string) =>
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
  set('Current password', current)
  set('New password', next)
  set('Confirm new password', confirm)
  fireEvent.click(screen.getByRole('button', { name: 'Change password' }))
}

describe('ChangePasswordSection', () => {
  beforeEach(() => {
    logout.mockReset()
    vi.mocked(finance.changePassword).mockReset()
  })

  it('stops when the new passwords differ', () => {
    render(<ChangePasswordSection />)
    fill('oldpass1', 'newpass12', 'newpass13')
    expect(screen.getByRole('alert').textContent).toBe(
      'The new passwords do not match.',
    )
    expect(finance.changePassword).not.toHaveBeenCalled()
  })

  it('changes the password and signs out', async () => {
    vi.mocked(finance.changePassword).mockResolvedValue(null)
    render(<ChangePasswordSection />)
    fill('oldpass1', 'newpass12', 'newpass12')
    await waitFor(() => expect(logout).toHaveBeenCalled())
    expect(finance.changePassword).toHaveBeenCalledWith('oldpass1', 'newpass12')
  })

  it('shows the error and stays signed in when it fails', async () => {
    const msg =
      'Check your current password. The new one needs at least 8 characters.'
    vi.mocked(finance.changePassword).mockRejectedValue(new Error(msg))
    render(<ChangePasswordSection />)
    fill('wrong', 'newpass12', 'newpass12')
    expect((await screen.findByRole('alert')).textContent).toBe(msg)
    expect(logout).not.toHaveBeenCalled()
  })
})
