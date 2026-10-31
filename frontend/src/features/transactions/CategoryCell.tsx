import { useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import shared from '../../components/shared.module.css'
import {
  ApiError,
  createCategory,
  setCounterparty,
  setMerchantCategory,
  setPersonCategory,
  setTransactionCategory,
  setTransactionMerchant,
  type Category,
  type Transaction,
} from '../../services/finance'
import styles from './TransactionsPage.module.css'

type Props = {
  t: Transaction
  categories: Category[]
  onCreated: (c: Category) => void
  onSaved: (t: Transaction) => void
  onFilter: (category: string) => void
  /** Refetch the list: a merchant or person default changes other rows. */
  reload: () => void
}

/** Category of one transaction: click to pick from the list or type a new one. */
export function CategoryCell({
  t,
  categories,
  onCreated,
  onSaved,
  onFilter,
  reload,
}: Props) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [countsAsSpending, setCountsAsSpending] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [only, setOnly] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  const listId = useId()

  const label = t.category ?? 'Uncategorized'
  // A matched merchant or person keeps the category for all its transactions. An unlinked UPI payee is linked
  // first (as the merchant or person its description names), then gets the category the same way.
  const holder = t.personId
    ? t.personName
    : t.merchantId
      ? t.merchantName
      : t.payeeName
  const hasHolder =
    t.personId !== null || t.merchantId !== null || t.payeeName !== null
  const wide = hasHolder && !only
  const existing = categories.find(
    (c) => c.name.toLowerCase() === name.trim().toLowerCase(),
  )
  const isNew = name.trim() !== '' && !existing

  function open() {
    setName(t.categoryOverridden ? label : '')
    setError(null)
    setOnly(false)
    setEditing(true)
  }

  function close() {
    flushSync(() => setEditing(false))
    trigger.current?.focus()
  }

  async function save(categoryId: string | null, forAll = wide) {
    setSaving(true)
    setError(null)
    try {
      if (forAll) {
        let linked = t
        if (!t.personId && !t.merchantId) {
          // Not shared: the user picked a category, not this name.
          linked =
            t.payeeType === 'PERSON'
              ? await setCounterparty(t.id, 'PERSON', t.payeeName!)
              : await setTransactionMerchant(t.id, t.payeeName!, false)
        }
        // Money in and money out keep separate defaults.
        const credit = t.direction === 'CREDIT'
        if (linked.personId)
          await setPersonCategory(linked.personId, categoryId, credit)
        else await setMerchantCategory(linked.merchantId!, categoryId, credit)
        try {
          await setTransactionCategory(t.id, null)
        } catch {
          // the merchant/person-wide save worked; the reload shows the real state
        }
        close()
        reload()
      } else {
        onSaved(await setTransactionCategory(t.id, categoryId))
        close()
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save that.')
    } finally {
      setSaving(false)
    }
  }

  async function submit() {
    if (existing) return save(existing.id)
    try {
      // Money in is never spending, so a category made from it does not count as spending.
      const created = await createCategory(
        name.trim(),
        t.direction === 'DEBIT' && countsAsSpending,
      )
      onCreated(created)
      return save(created.id)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save that.')
    }
  }

  if (!editing) {
    return (
      <button
        ref={trigger}
        type="button"
        className={shared.quiet}
        aria-label={`Change category, now ${label}`}
        onClick={open}
      >
        {t.category ?? <span className={shared.muted}>Uncategorised</span>}
      </button>
    )
  }

  return (
    <form
      className={styles.categoryEditor}
      onSubmit={(e) => {
        e.preventDefault()
        if (name.trim()) submit()
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.preventDefault()
          close()
        }
      }}
    >
      <label>
        <span className={shared.label}>Category for this transaction</span>
        <input
          className={shared.input}
          list={listId}
          value={name}
          maxLength={50}
          autoFocus
          autoComplete="off"
          placeholder="Pick or type a new one"
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <datalist id={listId}>
        {categories.map((c) => (
          <option key={c.id} value={c.name} />
        ))}
      </datalist>
      {hasHolder && (
        <label className={styles.inline}>
          <input
            type="checkbox"
            checked={only}
            onChange={(e) => setOnly(e.target.checked)}
          />
          Only this transaction
        </label>
      )}
      {isNew && t.direction === 'DEBIT' && (
        <label className={styles.inline}>
          <input
            type="checkbox"
            checked={countsAsSpending}
            onChange={(e) => setCountsAsSpending(e.target.checked)}
          />
          New category counts as spending
        </label>
      )}
      <div className={styles.editorActions}>
        <button
          type="submit"
          className={shared.button}
          disabled={saving || !name.trim()}
        >
          {isNew ? `Create "${name.trim()}" and use it` : 'Save'}
          {wide && ` for all ${holder}`}
        </button>
        {t.categoryOverridden && (
          <button
            type="button"
            className={shared.quiet}
            disabled={saving}
            onClick={() => save(null, false)}
          >
            Use merchant/person default
          </button>
        )}
        <button type="button" className={shared.quiet} onClick={close}>
          Cancel
        </button>
        <button
          type="button"
          className={shared.quiet}
          onClick={() => onFilter(label)}
        >
          Show only {label}
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
