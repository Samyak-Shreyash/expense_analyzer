import { useState } from 'react'
import type { FormEvent } from 'react'
import shared from '../../components/shared.module.css'
import { changePassword } from '../../services/finance'
import { useAuth } from '../auth/AuthContext'
import styles from './ProfilePage.module.css'

/** Changes the password; the server ends every session, so this signs the user out afterwards. */
export function ChangePasswordSection() {
  const { logout } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (next !== confirm) {
      setError('The new passwords do not match.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await changePassword(current, next)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save that.')
      setSaving(false)
      return
    }
    await logout()
  }

  return (
    <form id="password" className={styles.form} onSubmit={submit}>
      <h2>Change password</h2>
      <p className={shared.muted}>
        You will be signed out everywhere and can sign in again with the new
        password.
      </p>
      <label>
        Current password
        <input
          className={styles.input}
          type="password"
          autoComplete="current-password"
          required
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
      </label>
      <label>
        New password
        <input
          className={styles.input}
          type="password"
          autoComplete="new-password"
          minLength={8}
          required
          aria-describedby="new-password-hint"
          value={next}
          onChange={(e) => setNext(e.target.value)}
        />
      </label>
      <p id="new-password-hint" className={shared.muted}>
        At least 8 characters
      </p>
      <label>
        Confirm new password
        <input
          className={styles.input}
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
        />
      </label>
      {error && (
        <p className={shared.error} role="alert">
          {error}
        </p>
      )}
      <button className={shared.button} disabled={saving}>
        {saving ? 'Saving...' : 'Change password'}
      </button>
    </form>
  )
}
