import { useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { Link } from 'react-router'
import shared from '../../components/shared.module.css'
import { SHARING_WARNING } from '../categories/MerchantNamesPanel'
import {
  ApiError,
  reportSuggestion,
  setTransactionMerchant,
  type Transaction,
} from '../../services/finance'
import styles from './TransactionsPage.module.css'

type Props = {
  t: Transaction
  /** Refetch the list: naming a payee changes every row with that payee. */
  reload: () => void
  /** Account-level merchant-name sharing is on (and not revoked). */
  sharing: boolean
}

/** Names the merchant of a payment no rule matched; applies to the same payee everywhere. */
export function MerchantCell({ t, reload, sharing }: Props) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [share, setShare] = useState(true)
  const [saving, setSaving] = useState(false)
  const [reporting, setReporting] = useState(false)
  const [reported, setReported] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  const hintId = useId()

  function open() {
    setName('')
    setShare(true)
    setError(null)
    setEditing(true)
  }

  function close() {
    flushSync(() => setEditing(false))
    trigger.current?.focus()
  }

  async function submit(
    chosen: string,
    shareIt: boolean | undefined,
    closeEditor: boolean,
  ) {
    setSaving(true)
    setError(null)
    try {
      await setTransactionMerchant(t.id, chosen, shareIt)
      if (closeEditor) close()
      reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save that.')
    } finally {
      setSaving(false)
    }
  }

  function cancelReport() {
    flushSync(() => setReporting(false))
    document.getElementById(`report-${t.id}`)?.focus()
  }

  async function confirmReport() {
    setError(null)
    try {
      await reportSuggestion(t.id)
      flushSync(() => {
        setReporting(false)
        setReported(true)
      })
      trigger.current?.focus()
      reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not report that.')
    }
  }

  if (!editing) {
    const suggested = reported ? null : t.suggestedMerchantName
    return (
      <>
        {suggested && (
          <span>
            Suggested: {suggested}{' '}
            <button
              type="button"
              className={shared.quiet}
              disabled={saving}
              aria-label={`Use ${suggested} for ${t.description}`}
              onClick={() => submit(suggested, false, false)}
            >
              Use {suggested}
            </button>{' '}
            {reporting ? (
              <span
                role="group"
                aria-label={`Report ${suggested}`}
                onKeyDown={(e) => e.key === 'Escape' && cancelReport()}
              >
                Report this name as offensive or wrong?{' '}
                <button
                  type="button"
                  className={shared.danger}
                  onClick={confirmReport}
                >
                  Confirm
                </button>{' '}
                <button
                  type="button"
                  className={shared.quiet}
                  autoFocus
                  onClick={cancelReport}
                >
                  Cancel
                </button>
              </span>
            ) : (
              <button
                type="button"
                id={`report-${t.id}`}
                className={shared.quiet}
                aria-label={`Report suggested name ${suggested}`}
                onClick={() => setReporting(true)}
              >
                Report
              </button>
            )}
          </span>
        )}
        <button
          ref={trigger}
          type="button"
          className={shared.quiet}
          aria-label={`Add merchant for ${t.description}`}
          onClick={open}
        >
          Add merchant
        </button>
        {error && (
          <p className={shared.error} role="alert">
            {error}
          </p>
        )}
      </>
    )
  }

  return (
    <form
      className={styles.categoryEditor}
      onSubmit={(e) => {
        e.preventDefault()
        if (name.trim()) submit(name.trim(), sharing ? share : undefined, true)
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.preventDefault()
          close()
        }
      }}
    >
      <label>
        <span className={shared.label}>Merchant name</span>
        <input
          className={shared.input}
          value={name}
          maxLength={100}
          autoFocus
          autoComplete="off"
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      {sharing ? (
        <>
          <label>
            <input
              type="checkbox"
              checked={share}
              aria-describedby={hintId}
              onChange={(e) => setShare(e.target.checked)}
            />{' '}
            Share this name with other users
          </label>
          <span id={hintId} className={shared.muted}>
            {SHARING_WARNING}
          </span>
        </>
      ) : (
        <Link to="/categories?tab=merchant-names" className={shared.muted}>
          Sharing is off — manage in Merchant names
        </Link>
      )}
      <div className={styles.editorActions}>
        <button
          type="submit"
          className={shared.button}
          disabled={saving || !name.trim()}
        >
          Save
        </button>
        <button type="button" className={shared.quiet} onClick={close}>
          Cancel
        </button>
      </div>
      {error && (
        <p className={shared.error} role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
