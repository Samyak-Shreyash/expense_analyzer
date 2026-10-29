import { useEffect, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { flushSync } from 'react-dom'
import { useSearchParams } from 'react-router'
import shared from '../../components/shared.module.css'
import {
  createCategory,
  deleteCategory,
  listCategories,
  listPeople,
  updateCategory,
  updatePerson,
  type Category,
  type Person,
} from '../../services/finance'
import styles from './CategoriesPage.module.css'
import { MerchantNamesPanel } from './MerchantNamesPanel'

const TABS = [
  { id: 'categories', label: 'Categories' },
  { id: 'people', label: 'People' },
  { id: 'merchant-names', label: 'Merchant names' },
] as const
type Tab = (typeof TABS)[number]['id']

const message = (e: unknown) =>
  e instanceof Error ? e.message : 'Could not save that.'

/** A name with a Rename button that opens an inline editor; focus returns to the button. */
function EditableName({
  id,
  name,
  label,
  onSave,
}: {
  id: string
  name: string
  label: string
  onSave: (name: string) => Promise<void>
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(name)
  const buttonId = `rename-${id}`

  function close() {
    flushSync(() => setEditing(false))
    document.getElementById(buttonId)?.focus()
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    try {
      await onSave(draft.trim())
      close()
    } catch {
      // the page shows the message; keep the editor open to correct the name
    }
  }

  if (!editing) {
    return (
      <span className={styles.name}>
        {name}
        <button
          type="button"
          id={buttonId}
          className={shared.quiet}
          aria-label={`Rename ${label} ${name}`}
          onClick={() => {
            setDraft(name)
            setEditing(true)
          }}
        >
          Rename
        </button>
      </span>
    )
  }
  return (
    <form className={styles.editor} onSubmit={submit}>
      <input
        className={shared.input}
        aria-label={`New name for ${name}`}
        value={draft}
        autoFocus
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => e.key === 'Escape' && close()}
      />
      <button type="submit" className={shared.button}>
        Save
      </button>
      <button type="button" className={shared.quiet} onClick={close}>
        Cancel
      </button>
    </form>
  )
}

export function CategoriesPage() {
  const [params, setParams] = useSearchParams()
  const tab: Tab =
    TABS.find((t) => t.id === params.get('tab'))?.id ?? 'categories'
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [people, setPeople] = useState<Person[] | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [categoryError, setCategoryError] = useState<string | null>(null)
  const [peopleError, setPeopleError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [newCounts, setNewCounts] = useState(true)

  useEffect(() => {
    let live = true
    Promise.all([listCategories(), listPeople()])
      .then(([c, p]) => {
        if (!live) return
        setCategories(c)
        setPeople(p)
      })
      .catch(() => live && setLoadError(true))
    return () => {
      live = false
    }
  }, [])

  const replace = (c: Category) =>
    setCategories((all) => all!.map((x) => (x.id === c.id ? c : x)))

  /** Runs a save, showing its message in the section; rethrows so editors stay open. */
  async function run(
    task: () => Promise<void>,
    report: (m: string | null) => void,
  ) {
    report(null)
    try {
      await task()
    } catch (e) {
      report(message(e))
      throw e
    }
  }

  async function create(e: FormEvent) {
    e.preventDefault()
    await run(async () => {
      const c = await createCategory(newName.trim(), newCounts)
      setCategories((all) =>
        [...all!, c].sort((a, b) => a.name.localeCompare(b.name)),
      )
      setNewName('')
      setNewCounts(true)
    }, setCategoryError).catch(() => {})
  }

  async function remove(c: Category) {
    setConfirming(null)
    let deleted = false
    await run(async () => {
      await deleteCategory(c.id)
      deleted = true
      setCategories((all) => all!.filter((x) => x.id !== c.id))
      // The server clears this category from people too.
      setPeople((all) =>
        all!.map((p) =>
          p.categoryId === c.id ? { ...p, categoryId: null } : p,
        ),
      )
      document.getElementById('category-list')?.focus()
    }, setCategoryError).catch(() => {})
    if (!deleted) {
      // The failed row is back to its Delete button once the confirmation closed; put focus there.
      flushSync(() => {})
      document.getElementById(`delete-${c.id}`)?.focus()
    }
  }

  function select(id: Tab) {
    setParams(id === 'categories' ? {} : { tab: id }, { replace: true })
  }

  /** Arrow keys, Home and End move between tabs (automatic activation). */
  function onTabKey(e: KeyboardEvent<HTMLButtonElement>) {
    const i = TABS.findIndex((t) => t.id === tab)
    const next = {
      ArrowRight: (i + 1) % TABS.length,
      ArrowLeft: (i - 1 + TABS.length) % TABS.length,
      Home: 0,
      End: TABS.length - 1,
    }[e.key]
    if (next === undefined) return
    e.preventDefault()
    flushSync(() => select(TABS[next].id))
    document.getElementById(`tab-${TABS[next].id}`)?.focus()
  }

  const panel = (id: Tab) => ({
    role: 'tabpanel',
    id: `panel-${id}`,
    'aria-labelledby': `tab-${id}`,
    hidden: tab !== id,
  })

  function keep(id: string) {
    flushSync(() => setConfirming(null))
    document.getElementById(`delete-${id}`)?.focus()
  }

  return (
    <>
      <h1 className={shared.title}>Categories</h1>
      <p className={shared.lead}>
        Group your transactions. A category that does not count as spending,
        such as Repayment, is left out of "Money out" and shown separately.
      </p>

      {loadError && (
        <p className={shared.error} role="alert">
          Could not load your categories. Reload the page to try again.
        </p>
      )}

      <div role="tablist" aria-label="Sections" className={styles.tabs}>
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            id={`tab-${t.id}`}
            aria-selected={tab === t.id}
            aria-controls={`panel-${t.id}`}
            tabIndex={tab === t.id ? 0 : -1}
            className={tab === t.id ? styles.tabActive : styles.tab}
            onClick={() => select(t.id)}
            onKeyDown={onTabKey}
          >
            {t.label}
          </button>
        ))}
      </div>

      <section className={shared.section} {...panel('categories')}>
        <h2 id="category-list" tabIndex={-1}>
          Your categories
        </h2>
        <form className={styles.create} onSubmit={create}>
          <label>
            <span className={shared.label}>New category name</span>
            <input
              className={shared.input}
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              required
              maxLength={50}
            />
          </label>
          <label className={styles.check}>
            <input
              type="checkbox"
              role="switch"
              checked={newCounts}
              onChange={(e) => setNewCounts(e.target.checked)}
            />
            Counts as spending
          </label>
          <button type="submit" className={shared.button}>
            Add category
          </button>
        </form>
        {categoryError && (
          <p className={shared.error} role="alert">
            {categoryError}
          </p>
        )}
        {categories?.length === 0 && (
          <p className={shared.muted}>No categories yet.</p>
        )}
        {categories && categories.length > 0 && (
          <div className={shared.tableWrap}>
            <table className={shared.table}>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Counts as spending</th>
                  <th>
                    <span className={shared.visuallyHidden}>Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {categories.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <EditableName
                        id={c.id}
                        name={c.name}
                        label="category"
                        onSave={(name) =>
                          run(async () => {
                            replace(
                              await updateCategory(
                                c.id,
                                name,
                                c.countsAsSpending,
                              ),
                            )
                          }, setCategoryError)
                        }
                      />
                    </td>
                    <td>
                      <label className={styles.check}>
                        <input
                          type="checkbox"
                          role="switch"
                          checked={c.countsAsSpending}
                          onChange={(e) =>
                            run(async () => {
                              replace(
                                await updateCategory(
                                  c.id,
                                  c.name,
                                  e.target.checked,
                                ),
                              )
                            }, setCategoryError).catch(() => {})
                          }
                        />
                        <span>
                          {c.countsAsSpending ? 'Yes' : 'No'}
                          <span className={shared.visuallyHidden}>
                            , {c.name} counts as spending
                          </span>
                        </span>
                      </label>
                    </td>
                    <td>
                      <span className={styles.actions}>
                        {confirming === c.id ? (
                          <>
                            <button
                              type="button"
                              className={shared.danger}
                              onClick={() => remove(c)}
                              onKeyDown={(e) =>
                                e.key === 'Escape' && keep(c.id)
                              }
                            >
                              Delete {c.name}
                            </button>
                            <button
                              type="button"
                              className={shared.quiet}
                              autoFocus
                              onClick={() => keep(c.id)}
                              onKeyDown={(e) =>
                                e.key === 'Escape' && keep(c.id)
                              }
                            >
                              Keep
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            id={`delete-${c.id}`}
                            className={shared.danger}
                            aria-label={`Delete category ${c.name}`}
                            onClick={() => setConfirming(c.id)}
                          >
                            Delete
                          </button>
                        )}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={shared.section} {...panel('people')}>
        <h2 id="people">People</h2>
        <p className={shared.muted}>
          Found from UPI transfers. A person's default category applies to their
          transactions unless you set one on a single transaction.
        </p>
        {peopleError && (
          <p className={shared.error} role="alert">
            {peopleError}
          </p>
        )}
        {people?.length === 0 && (
          <p className={shared.muted}>No people found in your transfers yet.</p>
        )}
        {people && people.length > 0 && categories && (
          <div className={shared.tableWrap}>
            <table className={shared.table}>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Default category</th>
                </tr>
              </thead>
              <tbody>
                {people.map((p) => {
                  const save = async (
                    name: string,
                    categoryId: string | null,
                  ) => {
                    const saved = await updatePerson(p.id, name, categoryId)
                    setPeople((all) =>
                      all!.map((x) => (x.id === p.id ? saved : x)),
                    )
                  }
                  return (
                    <tr key={p.id}>
                      <td>
                        <EditableName
                          id={p.id}
                          name={p.name}
                          label="person"
                          onSave={(name) =>
                            run(() => save(name, p.categoryId), setPeopleError)
                          }
                        />
                      </td>
                      <td>
                        <select
                          className={shared.input}
                          aria-label={`Default category for ${p.name}`}
                          value={p.categoryId ?? ''}
                          onChange={(e) =>
                            run(
                              () => save(p.name, e.target.value || null),
                              setPeopleError,
                            ).catch(() => {})
                          }
                        >
                          <option value="">No default</option>
                          {categories.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={shared.section} {...panel('merchant-names')}>
        <h2 id="merchant-names">Merchant names</h2>
        {tab === 'merchant-names' && <MerchantNamesPanel />}
      </section>
    </>
  )
}
