import { useState } from 'react'
import type { FormEvent } from 'react'
import shared from '../../components/shared.module.css'
import { updateStatement, type Statement } from '../../services/finance'
import styles from './StatementsPage.module.css'

/** Rename a statement and set the account it belongs to, offering the account found in the file. */
export function StatementEditor({
  statement: s,
  onDone,
}: {
  statement: Statement
  onDone: (saved: boolean) => void
}) {
  const [fileName, setFileName] = useState(s.fileName)
  const [accountNumber, setAccountNumber] = useState('')
  const [bankName, setBankName] = useState(s.bankName ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await updateStatement(s.id, {
        fileName,
        bankName,
        // Empty keeps the current number: the server only ever sends it back masked.
        ...(accountNumber.trim() ? { accountNumber } : {}),
      })
      onDone(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save that.')
      setSaving(false)
    }
  }

  return (
    <form className={styles.editor} onSubmit={submit}>
      <label>
        <span className={shared.label}>Name</span>
        <input
          className={shared.input}
          value={fileName}
          maxLength={255}
          required
          onChange={(e) => setFileName(e.target.value)}
        />
      </label>
      <label>
        <span className={shared.label}>
          Account number
          {s.accountNumber && ` (now ${s.accountNumber})`}
        </span>
        <input
          className={shared.input}
          value={accountNumber}
          inputMode="numeric"
          autoComplete="off"
          maxLength={50}
          placeholder="Full number, or at least the last 4 digits"
          onChange={(e) => setAccountNumber(e.target.value)}
        />
      </label>
      {s.detectedAccount && !accountNumber && (
        <p className={shared.muted}>
          Found an account ending {s.detectedAccount} in this file.{' '}
          <button
            type="button"
            className={shared.quiet}
            onClick={() => setAccountNumber(s.detectedAccount!)}
          >
            Use this
          </button>
        </p>
      )}
      <label>
        <span className={shared.label}>Bank</span>
        <input
          className={shared.input}
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
      <div>
        <button className={shared.button} disabled={saving}>
          {saving ? 'Saving...' : 'Save'}
        </button>{' '}
        <button
          type="button"
          className={shared.quiet}
          onClick={() => onDone(false)}
        >
          Cancel
        </button>
      </div>
    </form>
  )
}
