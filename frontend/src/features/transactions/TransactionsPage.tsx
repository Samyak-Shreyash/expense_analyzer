import { Fragment, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import shared from '../../components/shared.module.css'
import { dayLabel, rupees } from '../../lib/format'
import {
  listCategories,
  getMerchantNameSettings,
  listPeople,
  listTransactions,
  setCounterparty,
  setTransactionMerchant,
  updatePerson,
  type Category,
  type Page,
  type Transaction,
} from '../../services/finance'
import { CategoryCell } from './CategoryCell'
import { MerchantCell } from './MerchantCell'
import { RenameOnHover } from './RenameOnHover'
import { TransactionActions } from './TransactionActions'
import styles from './TransactionsPage.module.css'

// Filters edited in the form; merchantId, personId and statementId are set by links.
const FORM_KEYS = [
  'q',
  'from',
  'to',
  'direction',
  'category',
  'transfer',
  'minAmount',
  'maxAmount',
] as const
const LINK_KEYS = ['merchantId', 'personId', 'statementId'] as const

const today = () => new Date().toISOString().slice(0, 10)

/** From/to for a date shortcut: the first of a month `back` months ago through today, or that whole month. */
const monthRange = (back: number, whole: boolean) => {
  const now = new Date()
  const start = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1),
  )
  const end = whole
    ? new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back + 1, 0))
    : null
  return {
    from: start.toISOString().slice(0, 10),
    to: end ? end.toISOString().slice(0, 10) : today(),
  }
}

const DATE_SHORTCUTS = [
  { label: 'This month', range: () => monthRange(0, false) },
  { label: 'Last month', range: () => monthRange(1, true) },
  { label: 'Last 3 months', range: () => monthRange(2, false) },
  {
    label: 'This year',
    range: () => ({ from: `${today().slice(0, 4)}-01-01`, to: today() }),
  },
]

/** The day `days` from an ISO date (YYYY-MM-DD). */
const shift = (date: string, days: number) => {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export function TransactionsPage() {
  const [params, setParams] = useSearchParams()
  const [result, setResult] = useState<Page<Transaction> | null>(null)
  const [error, setError] = useState(false)
  const [open, setOpen] = useState<string | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [categoriesError, setCategoriesError] = useState(false)
  const [sharing, setSharing] = useState(false)
  // Bumped to refetch after a change that can touch other rows.
  const [version, setVersion] = useState(0)
  // Row whose merchant was just named: its trigger unmounts, so focus moves on after the refetch.
  const focusRow = useRef<string | null>(null)
  const tableWrap = useRef<HTMLDivElement>(null)
  const detail = useRef<HTMLTableRowElement>(null)
  const [draft, setDraft] = useState(() =>
    Object.fromEntries(FORM_KEYS.map((k) => [k, params.get(k) ?? ''])),
  )

  const page = Number(params.get('page') ?? 0)
  const filterKey = params.toString()

  // Links (a category chip, a payee name) change the URL: show those values in the form too.
  const [draftKey, setDraftKey] = useState(filterKey)
  if (draftKey !== filterKey) {
    setDraftKey(filterKey)
    setDraft(Object.fromEntries(FORM_KEYS.map((k) => [k, params.get(k) ?? ''])))
  }

  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch(() => setCategoriesError(true))
    getMerchantNameSettings()
      .then((m) => setSharing(m.shareMerchantNames && !m.sharingRevoked))
      .catch(() => {})
  }, [])

  useEffect(() => {
    let live = true
    const current = new URLSearchParams(filterKey)
    const filter = Object.fromEntries(
      [...FORM_KEYS, ...LINK_KEYS].map((k) => [k, current.get(k) ?? undefined]),
    )
    listTransactions({ ...filter, page: Number(current.get('page') ?? 0) })
      .then((r) => {
        if (!live) return
        setResult(r)
        setError(false)
      })
      .catch(() => {
        if (!live) return
        focusRow.current = null // a later load must not steal focus
        setError(true)
      })
    return () => {
      live = false
    }
    // filterKey captures every param; params itself is a new object each render
  }, [filterKey, version])

  useEffect(() => {
    if (!result || !focusRow.current) return
    const row = focusRow.current
    focusRow.current = null
    const button = document.getElementById(`merchant-${row}`)
    ;(button ?? tableWrap.current)?.focus()
  }, [result])

  // A click outside the open details (and outside its own toggle) closes them. Not pointerdown:
  // that also fires when dragging the scrollbar or starting a touch scroll.
  useEffect(() => {
    if (!open) return
    function onClick(e: MouseEvent) {
      const target = e.target as Node
      const toggle = document.getElementById(`describe-${open}`)
      if (detail.current?.contains(target) || toggle?.contains(target)) return
      setOpen(null)
    }
    document.addEventListener('click', onClick)
    return () => document.removeEventListener('click', onClick)
  }, [open])

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  /** Refetch after a rename: it changes every row with that payee or person. */
  function renamed(t: Transaction) {
    focusRow.current = t.id
    setVersion((v) => v + 1)
  }

  // Names this payee for the user only; a hover rename never shares the name.
  async function renameMerchant(t: Transaction, name: string) {
    await setTransactionMerchant(t.id, name, sharing ? false : undefined)
    renamed(t)
  }

  async function renamePerson(t: Transaction, name: string) {
    // The person endpoint sets name and money-out category together; keep the current category.
    const person = (await listPeople()).find((p) => p.id === t.personId)
    await updatePerson(t.personId!, name, person?.categoryId ?? null)
    renamed(t)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    update(draft)
  }

  const filtered = [...FORM_KEYS, ...LINK_KEYS].some((k) => params.get(k))

  return (
    <>
      <h1 className={shared.title}>Transactions</h1>
      <p className={shared.lead}>
        Everything imported from your statements, newest first.
      </p>

      <form
        role="search"
        aria-label="Filter transactions"
        className={styles.filters}
        onSubmit={submit}
      >
        <label className={styles.search}>
          <span className={shared.label}>
            Search descriptions, merchants or people
          </span>
          <input
            className={shared.input}
            type="search"
            value={draft.q}
            onChange={(e) => setDraft({ ...draft, q: e.target.value })}
          />
        </label>
        <label>
          <span className={shared.label}>From</span>
          <input
            className={shared.input}
            type="date"
            value={draft.from}
            onChange={(e) => setDraft({ ...draft, from: e.target.value })}
          />
        </label>
        <label>
          <span className={shared.label}>To</span>
          <input
            className={shared.input}
            type="date"
            value={draft.to}
            onChange={(e) => setDraft({ ...draft, to: e.target.value })}
          />
        </label>
        <label>
          <span className={shared.label}>Show</span>
          <select
            className={shared.input}
            value={draft.direction}
            onChange={(e) => setDraft({ ...draft, direction: e.target.value })}
          >
            <option value="">Money in and out</option>
            <option value="DEBIT">Money out</option>
            <option value="CREDIT">Money in</option>
          </select>
        </label>
        <label>
          <span className={shared.label}>Category</span>
          <select
            className={shared.input}
            value={draft.category}
            onChange={(e) => setDraft({ ...draft, category: e.target.value })}
          >
            <option value="">All categories</option>
            <option value="Uncategorized">Uncategorized</option>
            {categories.map((c) => (
              <option key={c.id} value={c.name}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span className={shared.label}>Transfers</span>
          <select
            className={shared.input}
            value={draft.transfer}
            onChange={(e) => setDraft({ ...draft, transfer: e.target.value })}
          >
            <option value="">Include transfers</option>
            <option value="false">Hide transfers</option>
            <option value="true">Only transfers</option>
          </select>
        </label>
        <label>
          <span className={shared.label}>Min amount (₹)</span>
          <input
            className={shared.input}
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            value={draft.minAmount}
            onChange={(e) => setDraft({ ...draft, minAmount: e.target.value })}
          />
        </label>
        <label>
          <span className={shared.label}>Max amount (₹)</span>
          <input
            className={shared.input}
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            value={draft.maxAmount}
            onChange={(e) => setDraft({ ...draft, maxAmount: e.target.value })}
          />
        </label>
        <button className={shared.button} type="submit">
          Apply filters
        </button>
        <div className={styles.shortcuts}>
          {DATE_SHORTCUTS.map((s) => (
            <button
              key={s.label}
              type="button"
              className={shared.quiet}
              onClick={() => update({ ...draft, ...s.range() })}
            >
              {s.label}
            </button>
          ))}
          {filtered && (
            <button
              type="button"
              className={shared.quiet}
              onClick={() =>
                update(
                  Object.fromEntries(
                    [...FORM_KEYS, ...LINK_KEYS].map((k) => [k, '']),
                  ),
                )
              }
            >
              Clear all filters
            </button>
          )}
        </div>
      </form>

      {(params.get('merchantId') ||
        params.get('personId') ||
        params.get('statementId')) && (
        <p className={styles.chips}>
          {params.get('merchantId') && (
            <span>
              Showing one merchant.{' '}
              <button
                type="button"
                className={shared.quiet}
                onClick={() => update({ merchantId: '' })}
              >
                Show all merchants
              </button>
            </span>
          )}
          {params.get('personId') && (
            <span>
              Showing one person.{' '}
              <button
                type="button"
                className={shared.quiet}
                onClick={() => update({ personId: '' })}
              >
                Show everyone
              </button>
            </span>
          )}
          {params.get('statementId') && (
            <span>
              Showing one statement.{' '}
              <button
                type="button"
                className={shared.quiet}
                onClick={() => update({ statementId: '' })}
              >
                Show all statements
              </button>
            </span>
          )}
        </p>
      )}

      {categoriesError && (
        <p className={shared.error} role="alert">
          Could not load your categories, so the category pickers may be
          incomplete. Reload the page to try again.
        </p>
      )}

      {error && (
        <p className={shared.error}>
          Could not load transactions. Try again in a moment.
        </p>
      )}

      {result && result.totalItems === 0 && (
        <p className={shared.muted}>
          {filtered ? (
            'No transactions match these filters.'
          ) : (
            <>
              No transactions yet.{' '}
              <Link to="/statements">Upload a statement</Link>
            </>
          )}
        </p>
      )}

      {result && result.totalItems > 0 && (
        <>
          <div className={shared.tableWrap} ref={tableWrap} tabIndex={-1}>
            <table className={shared.table}>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>
                    <span className={styles.merchant}>Merchant</span> /{' '}
                    <span className={styles.person}>Person</span>
                  </th>
                  <th>Category</th>
                  <th className={shared.num}>Amount</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((t) => (
                  <Fragment key={t.id}>
                    <tr>
                      <td className={styles.date}>{dayLabel(t.date)}</td>
                      <td>
                        <button
                          type="button"
                          id={`describe-${t.id}`}
                          className={styles.describe}
                          aria-expanded={open === t.id}
                          onClick={() => setOpen(open === t.id ? null : t.id)}
                        >
                          {t.normalizedDescription || t.description}
                        </button>
                      </td>
                      <td>
                        {t.personId ? (
                          <RenameOnHover
                            name={t.personName!}
                            label="person"
                            onSave={(name) => renamePerson(t, name)}
                          >
                            {/* Same id as the merchant link: the row's focus target after a change. */}
                            <button
                              type="button"
                              id={`merchant-${t.id}`}
                              className={`${shared.quiet} ${styles.person}`}
                              aria-label={`Show all payments with ${t.personName}`}
                              onClick={() => update({ personId: t.personId! })}
                            >
                              {t.personName}
                              <span className={styles.hoverTag}>Person</span>
                            </button>
                          </RenameOnHover>
                        ) : t.merchantId ? (
                          <RenameOnHover
                            name={t.merchantName!}
                            label="merchant"
                            onSave={(name) => renameMerchant(t, name)}
                          >
                            <button
                              type="button"
                              id={`merchant-${t.id}`}
                              className={`${shared.quiet} ${styles.merchant}`}
                              aria-label={`Show all payments with ${t.merchantName}`}
                              onClick={() =>
                                update({ merchantId: t.merchantId! })
                              }
                            >
                              {t.merchantName}
                              <span className={styles.hoverTag}>Merchant</span>
                            </button>
                          </RenameOnHover>
                        ) : (
                          <>
                            {t.payeeName ? (
                              <RenameOnHover
                                name={t.payeeName}
                                label={
                                  t.payeeType === 'PERSON'
                                    ? 'person'
                                    : 'merchant'
                                }
                                onSave={(name) =>
                                  t.payeeType === 'PERSON'
                                    ? setCounterparty(
                                        t.id,
                                        'PERSON',
                                        name,
                                      ).then(() => renamed(t))
                                    : renameMerchant(t, name)
                                }
                              >
                                <button
                                  type="button"
                                  className={shared.quiet}
                                  aria-label={`Show all transactions with ${t.payeeName}`}
                                  onClick={() => {
                                    setDraft({ ...draft, q: t.payeeName! })
                                    update({ q: t.payeeName! })
                                  }}
                                >
                                  {t.payeeName}
                                </button>
                              </RenameOnHover>
                            ) : (
                              <span className={shared.muted}>Not matched</span>
                            )}
                            <MerchantCell
                              t={t}
                              sharing={sharing}
                              reload={() => {
                                focusRow.current = t.id
                                setVersion((v) => v + 1)
                              }}
                            />
                          </>
                        )}
                      </td>
                      <td>
                        {t.transfer && (
                          <span className={styles.transfer}>
                            <span className={styles.tag}>Transfer</span>
                            {t.transferPairId && (
                              <button
                                type="button"
                                className={shared.quiet}
                                onClick={() =>
                                  update({
                                    transfer: 'true',
                                    from: shift(t.date, -3),
                                    to: shift(t.date, 3),
                                  })
                                }
                              >
                                Show both halves
                              </button>
                            )}
                          </span>
                        )}
                        {t.reversalPairId && (
                          <span className={styles.transfer}>
                            <span className={styles.tag}>
                              Failed – refunded
                            </span>
                          </span>
                        )}
                        <CategoryCell
                          t={t}
                          categories={categories}
                          onCreated={(c) =>
                            setCategories((cs) =>
                              [...cs, c].sort((a, b) =>
                                a.name.localeCompare(b.name),
                              ),
                            )
                          }
                          onSaved={(saved) =>
                            setResult(
                              (r) =>
                                r && {
                                  ...r,
                                  items: r.items.map((x) =>
                                    x.id === saved.id ? saved : x,
                                  ),
                                },
                            )
                          }
                          onFilter={(category) => update({ category })}
                          reload={() => setVersion((v) => v + 1)}
                        />
                      </td>
                      <td
                        className={`${shared.num} ${t.direction === 'DEBIT' ? shared.debit : shared.credit}`}
                      >
                        {rupees(t.direction === 'DEBIT' ? -t.amount : t.amount)}
                      </td>
                    </tr>
                    {open === t.id && (
                      <tr className={styles.detailRow} ref={detail}>
                        <td colSpan={5}>
                          <dl className={styles.detail}>
                            <dt>As shown on your statement</dt>
                            <dd>{t.description}</dd>
                            <dt>Cleaned up</dt>
                            <dd>
                              {t.normalizedDescription ??
                                'Same as on the statement'}
                            </dd>
                            <dt>Direction</dt>
                            <dd>
                              {t.direction === 'DEBIT'
                                ? 'Money out'
                                : 'Money in'}
                            </dd>
                            {t.statementId && (
                              <>
                                <dt>Statement</dt>
                                <dd>
                                  <button
                                    type="button"
                                    className={shared.quiet}
                                    onClick={() =>
                                      update({ statementId: t.statementId! })
                                    }
                                  >
                                    Show this statement's transactions
                                  </button>
                                </dd>
                              </>
                            )}
                          </dl>
                          <TransactionActions
                            t={t}
                            categories={categories}
                            reload={() => setVersion((v) => v + 1)}
                            onSaved={(saved) =>
                              setResult(
                                (r) =>
                                  r && {
                                    ...r,
                                    items: r.items.map((x) =>
                                      x.id === saved.id ? saved : x,
                                    ),
                                  },
                              )
                            }
                          />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>

          <nav aria-label="Pages" className={styles.pager}>
            <button
              type="button"
              className={shared.quiet}
              disabled={page === 0}
              onClick={() => update({ page: String(page - 1) })}
            >
              Previous page
            </button>
            <span>
              Page {page + 1} of {result.totalPages},{' '}
              {result.totalItems.toLocaleString('en-IN')} transactions
            </span>
            <button
              type="button"
              className={shared.quiet}
              disabled={page + 1 >= result.totalPages}
              onClick={() => update({ page: String(page + 1) })}
            >
              Next page
            </button>
          </nav>
        </>
      )}
    </>
  )
}
