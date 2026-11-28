import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AuthLayout } from './AuthLayout'

describe('AuthLayout', () => {
  it('renders title, lead, children and BalanceLine chart', () => {
    render(
      <AuthLayout title="Sign in" lead="Welcome back">
        <button>Go</button>
      </AuthLayout>,
    )
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeTruthy()
    expect(screen.getByText('Welcome back')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Go' })).toBeTruthy()
    expect(screen.getByText('₹92,000')).toBeTruthy()
    expect(screen.getByText('₹30,500')).toBeTruthy()
    expect(screen.getByText('₹68,000')).toBeTruthy()
  })
})
