import {
  type Category,
  listCategories,
  listTransactions,
  setTransactionCategory,
  type Transaction,
} from '@expense-analyzer/api-client'
import { useCallback, useEffect, useState } from 'react'
import { FlatList, Pressable, Text, TextInput, View } from 'react-native'
import { ErrorText, styles } from '../../components/ui'
import { rupees } from '../../lib/format'

export default function Transactions() {
  const [q, setQ] = useState('')
  const [items, setItems] = useState<Transaction[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [categories, setCategories] = useState<Category[]>([])

  // State is set only once the request settles, so the mount effect can call it.
  const fetchPage = useCallback(
    (next: number) =>
      listTransactions({ q: q.trim(), page: next }).then(
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
    [q],
  )

  function load(next: number) {
    setLoading(true)
    return fetchPage(next)
  }

  useEffect(() => {
    fetchPage(0)
    // Only on mount; searching is explicit (submit).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    listCategories().then(setCategories, () => {})
  }, [])

  async function pick(t: Transaction, categoryId: string | null) {
    setError(null)
    try {
      const saved = await setTransactionCategory(t.id, categoryId)
      setItems((prev) => prev.map((x) => (x.id === saved.id ? saved : x)))
      setOpen(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save that.')
    }
  }

  return (
    <View style={styles.screen}>
      <TextInput
        style={styles.input}
        placeholder="Search descriptions"
        accessibilityLabel="Search descriptions"
        value={q}
        onChangeText={setQ}
        onSubmitEditing={() => load(0)}
        returnKeyType="search"
      />
      {error && <ErrorText>{error}</ErrorText>}
      <FlatList
        data={items}
        keyExtractor={(t) => t.id}
        refreshing={loading && page === 0}
        onRefresh={() => load(0)}
        onEndReached={() => {
          if (hasMore && !loading) load(page + 1)
        }}
        ListEmptyComponent={
          loading ? undefined : (
            <Text style={styles.muted}>No transactions found.</Text>
          )
        }
        renderItem={({ item: t }) => (
          <View>
            <Pressable
              accessibilityRole="button"
              onPress={() => setOpen(open === t.id ? null : t.id)}
              style={styles.row}
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
                </Text>
              </View>
              <Text
                style={[
                  styles.rowText,
                  t.direction === 'DEBIT' ? styles.debit : styles.credit,
                ]}
              >
                {rupees(t.direction === 'DEBIT' ? -t.amount : t.amount)}
              </Text>
            </Pressable>
            {open === t.id && (
              <View style={{ paddingVertical: 8, gap: 6 }}>
                <Text style={styles.muted}>{t.description}</Text>
                <Text style={styles.muted}>Category for this transaction</Text>
                <View
                  style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}
                >
                  <Chip
                    label="Merchant/person default"
                    selected={!t.categoryOverridden}
                    onPress={() => pick(t, null)}
                  />
                  {categories.map((c) => (
                    <Chip
                      key={c.id}
                      label={c.name}
                      selected={t.categoryOverridden && t.categoryId === c.id}
                      onPress={() => pick(t, c.id)}
                    />
                  ))}
                </View>
              </View>
            )}
          </View>
        )}
      />
    </View>
  )
}

function Chip({
  label,
  selected,
  onPress,
}: {
  label: string
  selected: boolean
  onPress: () => void
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={{
        paddingHorizontal: 10,
        paddingVertical: 6,
        borderRadius: 14,
        borderWidth: 1,
        borderColor: selected ? '#1f4e79' : '#ccc',
        backgroundColor: selected ? '#1f4e79' : 'transparent',
      }}
    >
      <Text style={{ color: selected ? '#fff' : '#222' }}>{label}</Text>
    </Pressable>
  )
}
