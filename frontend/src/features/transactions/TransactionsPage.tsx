import { Fragment, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import shared from '../../components/shared.module.css'
import { dayLabel, rupees } from '../../lib/format'
import {
  listCategories,
  getMerchantNameSettings,
  listTransactions,
  type Category,
  type Page,
  type Transaction,
} from '../../services/finance'
import { CategoryCell } from './CategoryCell'
import { MerchantCell } from './MerchantCell'
import { TransactionActions } from './TransactionActions'
import styles from './TransactionsPage.module.css'

// Filters edited in the form; merchantId, statementId and category are set by links elsewhere.
const FORM_KEYS = ['q', 'from', 'to', 'direction'] as const
const LINK_KEYS = ['merchantId', 'statementId', 'category'] as const

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
  const [draft, setDraft] = useState(() =>
    Object.fromEntries(FORM_KEYS.map((k) => [k, params.get(k) ?? ''])),
  )

  const page = Number(params.get('page') ?? 0)
  const filterKey = params.toString()

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

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    if (!('page' in changes)) next.delete('page')
    setParams(next)
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
          <span className={shared.label}>Search descriptions</span>
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
        <button className={shared.button} type="submit">
          Apply filters
        </button>
      </form>

      {(params.get('merchantId') ||
        params.get('statementId') ||
        params.get('category')) && (
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
          {params.get('category') && (
            <span>
              Category: {params.get('category')}.{' '}
              <button
                type="button"
                className={shared.quiet}
                onClick={() => update({ category: '' })}
              >
                Show all categories
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
                  <th>Merchant</th>
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
                          className={styles.describe}
                          aria-expanded={open === t.id}
                          onClick={() => setOpen(open === t.id ? null : t.id)}
                        >
                          {t.normalizedDescription || t.description}
                        </button>
                      </td>
                      <td>
                        {t.personId ? (
                          <>
                            {t.personName}
                            <span className={styles.tag}>Person</span>
                          </>
                        ) : t.merchantId ? (
                          <button
                            type="button"
                            id={`merchant-${t.id}`}
                            className={shared.quiet}
                            onClick={() =>
                              update({ merchantId: t.merchantId! })
                            }
                          >
                            {t.merchantName}
                          </button>
                        ) : (
                          <>
                            <span className={shared.muted}>Not matched</span>
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
                      <tr className={styles.detailRow}>
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
