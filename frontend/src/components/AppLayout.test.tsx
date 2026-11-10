import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../services/api'
import { AppLayout } from './AppLayout'

vi.mock('../features/auth/AuthContext', () => ({
  useAuth: () => ({ logout: vi.fn() }),
}))
vi.mock('../services/api', () => ({ apiFetch: vi.fn() }))

const me = (fullName: string | null) =>
  vi.mocked(apiFetch).mockResolvedValue({
    ok: true,
    json: async () => ({ email: 'a@example.com', fullName }),
  } as Response)

const renderLayout = () =>
  render(
    <AppLayout>
      <p>child</p>
    </AppLayout>,
    { wrapper: MemoryRouter },
  )

describe('AppLayout account menu', () => {
  beforeEach(() => me('Alice Smith'))

  it('shows the full name before Log out and no Profile tab', async () => {
    renderLayout()
    const button = await screen.findByRole('button', { name: /Alice Smith/ })
    expect(button.getAttribute('aria-expanded')).toBe('false')
    expect(screen.getByRole('button', { name: 'Log out' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'Profile' })).toBeNull()
  })

  it('falls back to the email when there is no name', async () => {
    me(null)
    renderLayout()
    expect(
      await screen.findByRole('button', { name: /a@example.com/ }),
    ).toBeTruthy()
  })

  it('opens the account options', async () => {
    renderLayout()
    const button = await screen.findByRole('button', { name: /Alice Smith/ })
    fireEvent.click(button)
    expect(button.getAttribute('aria-expanded')).toBe('true')
    const href = (name: string) =>
      screen.getByRole('link', { name }).getAttribute('href')
    expect(href('Edit profile')).toBe('/profile#details')
    expect(href('Preferences')).toBe('/profile#preferences')
    expect(href('Change password')).toBe('/profile#password')
    expect(href('Delete account')).toBe('/profile#delete-account')
  })

  it('closes on Escape and returns focus to the button', async () => {
    renderLayout()
    const button = await screen.findByRole('button', { name: /Alice Smith/ })
    fireEvent.click(button)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('link', { name: 'Edit profile' })).toBeNull()
    expect(button.getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(button)
  })

  it('shows the new name after the profile is saved', async () => {
    renderLayout()
    await screen.findByRole('button', { name: /Alice Smith/ })
    act(() => {
      window.dispatchEvent(
        new CustomEvent('profile-updated', {
          detail: { email: 'a@example.com', fullName: 'Bob' },
        }),
      )
    })
    expect(screen.getByRole('button', { name: /Bob/ })).toBeTruthy()
  })
})
