import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as finance from '../../services/finance'
import { DeleteAccountSection } from './DeleteAccountSection'

const logout = vi.fn()

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ logout }) }))
vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  deleteAccount: vi.fn(),
}))

function submit() {
  render(<DeleteAccountSection />)
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: 'secret' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Delete account' }))
}

describe('DeleteAccountSection', () => {
  beforeEach(() => vi.clearAllMocks())

  it('states the backup window', () => {
    render(<DeleteAccountSection />)
    expect(screen.getByText(/backups for up to 30 days/)).toBeTruthy()
  })

  it('deletes with the password, then logs out', async () => {
    vi.mocked(finance.deleteAccount).mockResolvedValue(null)
    submit()
    await waitFor(() => expect(logout).toHaveBeenCalled())
    expect(finance.deleteAccount).toHaveBeenCalledWith('secret')
  })

  it('shows the error and stays signed in when deletion fails', async () => {
    vi.mocked(finance.deleteAccount).mockRejectedValue(
      new Error('That password is not correct.'),
    )
    submit()
    expect((await screen.findByRole('alert')).textContent).toBe(
      'That password is not correct.',
    )
    expect(logout).not.toHaveBeenCalled()
  })
})
