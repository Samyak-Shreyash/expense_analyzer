import {
  type Category,
  listCategories,
  listTransactions,
  type Transaction,
} from '@expense-analyzer/api-client'
import { useLocalSearchParams } from 'expo-router'
import { useCallback, useEffect, useState } from 'react'
import { FlatList, Pressable, Text, TextInput, View } from 'react-native'
import {
  Button,
  Chip,
  ErrorText,
  TextAction,
  useColors,
  useStyles,
} from '../../components/ui'
import {
  emiLabel,
  TransactionActions,
} from '../../features/transactions/TransactionActions'
import { rupees } from '../../lib/format'

type Filters = {
  from: string
  to: string
  direction: string
  category: string
  transfer: string
  minAmount: string
  maxAmount: string
  /** Set only by links from other screens (one merchant, person or statement). */
  merchantId: string
  personId: string
  statementId: string
}

const NO_FILTERS: Filters = {
  from: '',
  to: '',
  direction: '',
  category: '',
  transfer: '',
  minAmount: '',
  maxAmount: '',
  merchantId: '',
  personId: '',
  statementId: '',
}

/** Link-only filters, with the banner text and the label of the button that clears each. */
const LINKS = [
  {
    key: 'merchantId',
    text: 'Showing one merchant.',
    clear: 'Show all merchants',
  },
  { key: 'personId', text: 'Showing one person.', clear: 'Show everyone' },
  {
    key: 'statementId',
    text: 'Showing one statement.',
    clear: 'Show all statements',
  },
] as const

const iso = (d: Date) => d.toISOString().slice(0, 10)

/** From the first of the month `back` months ago to today, or (whole) to the end of that month. */
function monthRange(back: number, whole: boolean) {
  const now = new Date()
  const y = now.getUTCFullYear()
  const m = now.getUTCMonth()
  return {
    from: iso(new Date(Date.UTC(y, m - back, 1))),
    to: whole ? iso(new Date(Date.UTC(y, m - back + 1, 0))) : iso(now),
  }
}

// Same shortcuts as the web Transactions page.
const DATE_SHORTCUTS = [
  { label: 'This month', range: () => monthRange(0, false) },
  { label: 'Last month', range: () => monthRange(1, true) },
  { label: 'Last 3 months', range: () => monthRange(2, false) },
  {
    label: 'This year',
    range: () => ({
      from: `${iso(new Date()).slice(0, 4)}-01-01`,
      to: iso(new Date()),
    }),
  },
]

const DIRECTIONS = [
  { value: '', label: 'In and out' },
  { value: 'DEBIT', label: 'Money out' },
  { value: 'CREDIT', label: 'Money in' },
]

const TRANSFERS = [
  { value: '', label: 'Include transfers' },
  { value: 'false', label: 'Hide transfers' },
  { value: 'true', label: 'Only transfers' },
]

/** Request parameters for the set filters; empty values are left out. */
function query(q: string, f: Filters) {
  return {
    q: q.trim() || undefined,
    from: f.from || undefined,
    to: f.to || undefined,
    direction: f.direction || undefined,
    category: f.category || undefined,
    transfer: f.transfer || undefined,
    minAmount: f.minAmount ? Number(f.minAmount) : undefined,
    maxAmount: f.maxAmount ? Number(f.maxAmount) : undefined,
    merchantId: f.merchantId || undefined,
    personId: f.personId || undefined,
    statementId: f.statementId || undefined,
  }
}

export default function Transactions() {
  const styles = useStyles()
  const c = useColors()
  const [q, setQ] = useState('')
  const [items, setItems] = useState<Transaction[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [filters, setFilters] = useState<Filters>(NO_FILTERS)
  const [draft, setDraft] = useState<Filters>(NO_FILTERS)
  const [showFilters, setShowFilters] = useState(false)

  // State is set only once the request settles, so the mount effect can call it.
  const fetchPage = useCallback(
    (next: number, f: Filters = filters) =>
      listTransactions({ ...query(q, f), page: next }).then(
        (res) => {
          setItems((prev) => (next === 0 ? res.items : [...prev, ...res.items]))
          setPage(next)
          setHasMore(next + 1 < res.totalPages)
          setError(null)
          setLoading(false)
        },
        (e) => {
          setError(
            e instanceof Error ? e.message : 'Could not load transactions.',
          )
          setLoading(false)
        },
      ),
    [q, filters],
  )

  function load(next: number) {
    setLoading(true)
    return fetchPage(next)
  }

  function apply(f: Filters) {
    setFilters(f)
    setDraft(f)
    setShowFilters(false)
    setLoading(true)
    return fetchPage(0, f)
  }

  // A date range counts once; link-only filters show their own banner.
  const active = (Object.keys(NO_FILTERS) as (keyof Filters)[]).filter(
    (k) => k !== 'to' && !LINKS.some((l) => l.key === k) && filters[k],
  ).length
  const shortcut = DATE_SHORTCUTS.find((s) => {
    const r = s.range()
    return r.from === filters.from && r.to === filters.to
  })

  // Set when opened from another screen; replaces the filters each time it changes.
  const { category, merchantId, personId, statementId, from, to } =
    useLocalSearchParams<{
      from?: string
      to?: string
      category?: string
      merchantId?: string
      personId?: string
      statementId?: string
    }>()
  useEffect(() => {
    const linked = { category, merchantId, personId, statementId, from, to }
    const f = Object.values(linked).some(Boolean)
      ? {
          ...NO_FILTERS,
          ...Object.fromEntries(Object.entries(linked).filter(([, v]) => v)),
        }
      : filters
    fetchPage(0, f).then(() => {
      setFilters(f)
      setDraft(f)
    })
    // On mount and when opened for another category; searching is explicit (submit).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [category, merchantId, personId, statementId, from, to])

  useEffect(() => {
    listCategories().then(setCategories, () => {})
  }, [])

  const replace = (saved: Transaction) =>
    setItems((prev) => prev.map((x) => (x.id === saved.id ? saved : x)))

  return (
    <View style={[styles.screen, { paddingBottom: 0 }]}>
      <TextInput
        style={styles.input}
        placeholderTextColor={c.inkMuted}
        clearButtonMode="while-editing"
        placeholder="Search descriptions, merchants or people"
        accessibilityLabel="Search descriptions, merchants or people"
        value={q}
        onChangeText={setQ}
        onSubmitEditing={() => load(0)}
        returnKeyType="search"
      />
      <View style={styles.chipRow}>
        {DATE_SHORTCUTS.map((s) => (
          <Chip
            key={s.label}
            label={s.label}
            selected={shortcut === s}
            onPress={() =>
              apply(
                shortcut === s
                  ? { ...filters, from: '', to: '' }
                  : { ...filters, ...s.range() },
              )
            }
          />
        ))}
        <Chip
          label={active ? `Filters (${active})` : 'Filters'}
          selected={showFilters || active > 0}
          onPress={() => {
            setDraft(filters)
            setShowFilters(!showFilters)
          }}
        />
      </View>
      {showFilters && (
        <View style={[styles.panel, { marginTop: 8 }]}>
          <Text style={styles.label}>Show</Text>
          <View style={styles.chipRow}>
            {DIRECTIONS.map((o) => (
              <Chip
                key={o.label}
                label={o.label}
                selected={draft.direction === o.value}
                onPress={() => setDraft({ ...draft, direction: o.value })}
              />
            ))}
          </View>
          <Text style={styles.label}>Transfers</Text>
          <View style={styles.chipRow}>
            {TRANSFERS.map((o) => (
              <Chip
                key={o.label}
                label={o.label}
                selected={draft.transfer === o.value}
                onPress={() => setDraft({ ...draft, transfer: o.value })}
              />
            ))}
          </View>
          <Text style={styles.label}>Category</Text>
          <View style={styles.chipRow}>
            {['', 'Uncategorized', ...categories.map((x) => x.name)].map(
              (name) => (
                <Chip
                  key={name || 'all'}
                  label={name || 'All categories'}
                  selected={draft.category === name}
                  onPress={() => setDraft({ ...draft, category: name })}
                />
              ),
            )}
          </View>
          <Text style={styles.label}>Amount (₹)</Text>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <TextInput
              style={[styles.input, { flex: 1 }]}
              placeholderTextColor={c.inkMuted}
              placeholder="Min"
              accessibilityLabel="Minimum amount"
              keyboardType="decimal-pad"
              value={draft.minAmount}
              onChangeText={(v) => setDraft({ ...draft, minAmount: v })}
            />
            <TextInput
              style={[styles.input, { flex: 1 }]}
              placeholderTextColor={c.inkMuted}
              placeholder="Max"
              accessibilityLabel="Maximum amount"
              keyboardType="decimal-pad"
              value={draft.maxAmount}
              onChangeText={(v) => setDraft({ ...draft, maxAmount: v })}
            />
          </View>
          <Button title="Apply filters" onPress={() => apply(draft)} />
          {active > 0 && (
            <Button
              title="Clear all filters"
              variant="secondary"
              onPress={() => apply(NO_FILTERS)}
            />
          )}
        </View>
      )}
      {LINKS.filter((l) => filters[l.key]).map((l) => (
        <View
          key={l.key}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}
        >
          <Text style={[styles.text, { flex: 1 }]}>{l.text}</Text>
          <TextAction
            title={l.clear}
            onPress={() => apply({ ...filters, [l.key]: '' })}
          />
        </View>
      ))}
      {error && <ErrorText>{error}</ErrorText>}
      <FlatList
        data={items}
        keyExtractor={(t) => t.id}
        refreshing={loading && page === 0}
        onRefresh={() => load(0)}
        contentContainerStyle={{ paddingBottom: 16 }}
        onEndReached={() => {
          if (hasMore && !loading) load(page + 1)
        }}
        ListEmptyComponent={
          loading ? undefined : (
            <Text style={[styles.muted, { paddingVertical: 24 }]}>
              {q.trim() || active
                ? 'No transactions match these filters.'
                : 'No transactions yet. Upload a statement to get started.'}
            </Text>
          )
        }
        renderItem={({ item: t }) => (
          <View style={styles.divider}>
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ expanded: open === t.id }}
              accessibilityHint="Shows details and actions"
              onPress={() => setOpen(open === t.id ? null : t.id)}
              style={({ pressed }) => [
                styles.row,
                pressed && { backgroundColor: c.ledgerTint },
              ]}
            >
              <View style={{ flex: 1 }}>
                <Text style={styles.rowText} numberOfLines={1}>
                  {t.merchantName ??
                    t.personName ??
                    t.normalizedDescription ??
                    t.description}
                </Text>
                <Text style={styles.muted}>
                  {t.date} · {t.category}
                  {emiLabel(t) ? ` · ${emiLabel(t)}` : ''}
                </Text>
              </View>
              <Text
                style={[
                  styles.amount,
                  t.direction === 'DEBIT' ? styles.debit : styles.credit,
                ]}
              >
                {rupees(t.direction === 'DEBIT' ? -t.amount : t.amount)}
              </Text>
            </Pressable>
            {open === t.id && (
              <View
                style={{
                  marginBottom: 12,
                  padding: 12,
                  gap: 8,
                  borderRadius: 12,
                  backgroundColor: c.surface,
                }}
              >
                <TransactionActions
                  t={t}
                  categories={categories}
                  onSaved={replace}
                  // Some changes touch other rows (a merchant's default, who was paid).
                  reload={() => fetchPage(0)}
                />
              </View>
            )}
          </View>
        )}
      />
    </View>
  )
}
