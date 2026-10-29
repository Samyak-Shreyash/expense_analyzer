import { useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import shared from '../../components/shared.module.css'
import {
  ApiError,
  setTransactionMerchant,
  type Transaction,
} from '../../services/finance'
import styles from './TransactionsPage.module.css'

type Props = {
  t: Transaction
  /** Refetch the list: naming a payee changes every row with that payee. */
  reload: () => void
}

/** Names the merchant of a payment no rule matched; applies to the same payee everywhere. */
export function MerchantCell({ t, reload }: Props) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [suggest, setSuggest] = useState(false)
  const [saving, setSaving] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  const hintId = useId()

  function open() {
    setName('')
    setSuggest(false)
    setError(null)
    setEditing(true)
  }

  function close() {
    flushSync(() => setEditing(false))
    trigger.current?.focus()
  }

  async function submit(chosen: string, share: boolean, closeEditor: boolean) {
    setSaving(true)
    setError(null)
    try {
      await setTransactionMerchant(t.id, chosen, share)
      if (closeEditor) close()
      reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save that.')
    } finally {
      setSaving(false)
    }
  }

  if (!editing) {
    return (
      <>
        {t.suggestedMerchantName && (
          <span>
            Suggested: {t.suggestedMerchantName}{' '}
            <button
              type="button"
              className={shared.quiet}
              disabled={saving}
              aria-label={`Use ${t.suggestedMerchantName} for ${t.description}`}
              onClick={() => submit(t.suggestedMerchantName!, false, false)}
            >
              Use {t.suggestedMerchantName}
            </button>
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
        if (name.trim()) submit(name.trim(), suggest, true)
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
      <label>
        <input
          type="checkbox"
          checked={suggest}
          aria-describedby={hintId}
          onChange={(e) => setSuggest(e.target.checked)}
        />{' '}
        Suggest this name to other users
      </label>
      <span id={hintId} className={shared.muted}>
        Only the merchant name is shared, never your transactions.
      </span>
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
