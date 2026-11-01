import { useState } from 'react'
import type { FormEvent } from 'react'
import shared from '../../components/shared.module.css'
import { deleteAccount } from '../../services/finance'
import { useAuth } from '../auth/AuthContext'
import styles from './ProfilePage.module.css'

/** Deletes the signed-in user's account after they confirm with their password. */
export function DeleteAccountSection() {
  const { logout } = useAuth()
  const [password, setPassword] = useState('')
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setDeleting(true)
    setError(null)
    try {
      await deleteAccount(password)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete that.')
      setDeleting(false)
      return
    }
    await logout()
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <h2>Delete account</h2>
      <p className={shared.muted}>
        This deletes your account, statements and transactions and cannot be
        undone. Deleted data remains in backups for up to 30 days, until those
        backups expire.
      </p>
      <label>
        Password
        <input
          className={styles.input}
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      {error && (
        <p className={shared.error} role="alert">
          {error}
        </p>
      )}
      <button className={shared.button} disabled={deleting}>
        {deleting ? 'Deleting...' : 'Delete account'}
      </button>
    </form>
  )
}
