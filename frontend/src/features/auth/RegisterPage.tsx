import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { register } from '../../services/api'
import { AuthLayout } from '../../components/AuthLayout'
import { useAuth } from './AuthContext'
import styles from './AuthForm.module.css'

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

  const err = error ? { 'aria-invalid': true } : {}
  const describedBy = error ? 'password-hint form-error' : 'password-hint'

  return (
    <AuthLayout title="Create your account" lead="It takes under a minute.">
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
            aria-describedby={error ? 'form-error' : undefined}
          />
        </label>
        <div>
          <label className={styles.label}>
            Password
            <input
              className={styles.input}
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={8}
              required
              {...err}
              aria-describedby={describedBy}
            />
          </label>
          <p id="password-hint" className={styles.hint}>
            At least 8 characters
          </p>
        </div>
        <label className={styles.label}>
          Confirm password
          <input
            className={styles.input}
            name="confirm"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            {...err}
            aria-describedby={error ? 'form-error' : undefined}
          />
        </label>
        {error && (
          <p id="form-error" role="alert" className={styles.error}>
            {error}
          </p>
        )}
        <button className={styles.button} type="submit" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className={styles.footer}>
        Have an account? <Link to="/login">Log in</Link>
      </p>
    </AuthLayout>
  )
}
