import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { register } from '../../services/api'
import { useAuth } from './AuthContext'

export function RegisterPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const email = String(form.get('email'))
    const password = String(form.get('password'))
    if (password !== form.get('confirm')) {
      setError('Passwords do not match.')
      return
    }
    setBusy(true)
    const failure = await register(email, password)
    if (failure) {
      setBusy(false)
      setError(failure)
      return
    }
    // Registration does not return tokens, so log straight in.
    const ok = await login(email, password)
    setBusy(false)
    navigate(ok ? '/dashboard' : '/login', { replace: true })
  }

  return (
    <main>
      <h1>Register</h1>
      <form onSubmit={onSubmit}>
        <label>
          Email
          <input name="email" type="email" autoComplete="email" required />
        </label>
        <label>
          Password (at least 8 characters)
          <input
            name="password"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        <label>
          Confirm password
          <input
            name="confirm"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={busy}>
          Register
        </button>
      </form>
      <p>
        Have an account? <Link to="/login">Log in</Link>
      </p>
    </main>
  )
}
