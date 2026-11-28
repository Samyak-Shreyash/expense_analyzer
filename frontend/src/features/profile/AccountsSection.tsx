import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import shared from '../../components/shared.module.css'
import {
  addAccount,
  deleteAccountNumber,
  listAccounts,
  type OwnAccount,
} from '../../services/finance'
import styles from './ProfilePage.module.css'

/** The user's own account numbers: money to or from them counts as a transfer. */
export function AccountsSection() {
  const [accounts, setAccounts] = useState<OwnAccount[] | null>(null)
  const [accountNumber, setAccountNumber] = useState('')
  const [bankName, setBankName] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = () =>
    listAccounts().then(setAccounts, () =>
      setError('Could not load your accounts.'),
    )
  useEffect(() => {
    reload()
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await addAccount(accountNumber, bankName)
      setAccountNumber('')
      setBankName('')
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save that.')
    } finally {
      setSaving(false)
    }
  }

  async function remove(id: string) {
    setError(null)
    try {
      await deleteAccountNumber(id)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete that.')
    }
  }

  return (
    <form id="accounts" className={styles.form} onSubmit={submit}>
      <h2>Your accounts</h2>
      <p className={shared.muted}>
        Money sent to or received from these accounts is shown as a transfer,
        not spending or income. Accounts you set on a statement are added here.
      </p>
      {accounts?.length === 0 && (
        <p className={shared.muted}>No accounts added yet.</p>
      )}
      {accounts && accounts.length > 0 && (
        <ul>
          {accounts.map((a) => (
            <li key={a.id}>
              {[a.bankName, a.accountNumber].filter(Boolean).join(' ')}{' '}
              <button
                type="button"
                className={shared.danger}
                onClick={() => remove(a.id)}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
      <label>
        Account number
        <input
          className={styles.input}
          value={accountNumber}
          inputMode="numeric"
          autoComplete="off"
          maxLength={50}
          required
          placeholder="Full number, or as printed, e.g. XXXX1234"
          onChange={(e) => setAccountNumber(e.target.value)}
        />
      </label>
      <label>
        Bank
        <input
          className={styles.input}
          value={bankName}
          maxLength={255}
          onChange={(e) => setBankName(e.target.value)}
        />
      </label>
      {error && (
        <p className={shared.error} role="alert">
          {error}
        </p>
      )}
      <button className={shared.button} disabled={saving}>
        {saving ? 'Adding...' : 'Add account'}
      </button>
    </form>
  )
}
