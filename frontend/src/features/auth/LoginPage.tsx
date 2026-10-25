import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { AuthLayout } from '../../components/AuthLayout'
import { useAuth } from './AuthContext'
import styles from './AuthForm.module.css'

export function LoginPage() {
  const { status, login } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (status === 'authenticated') return <Navigate to="/dashboard" replace />

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setBusy(true)
    const ok = await login(
      String(form.get('email')),
      String(form.get('password')),
    )
    setBusy(false)
    if (ok) navigate(from ?? '/dashboard', { replace: true })
    else setError('Invalid email or password.')
  }

  const err = error
    ? { 'aria-invalid': true, 'aria-describedby': 'form-error' }
    : {}

  return (
    <AuthLayout title="Log in" lead="See where your money went.">
      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.label}>
          Email
          <input
            className={styles.input}
            name="email"
            type="email"
            autoComplete="email"
            required
            {...err}
          />
        </label>
        <label className={styles.label}>
          Password
          <input
            className={styles.input}
            name="password"
            type="password"
            autoComplete="current-password"
            minLength={8}
            required
            {...err}
          />
        </label>
        {error && (
          <p id="form-error" role="alert" className={styles.error}>
            {error}
          </p>
        )}
        <button className={styles.button} type="submit" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p className={styles.footer}>
        No account? <Link to="/register">Register</Link>
      </p>
    </AuthLayout>
  )
}
