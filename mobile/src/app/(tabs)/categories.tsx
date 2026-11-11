import {
  categoryCounts,
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
} from '@expense-analyzer/api-client'
import { router, useLocalSearchParams } from 'expo-router'
import { useEffect, useState } from 'react'
import { Alert, FlatList, Switch, Text, View } from 'react-native'
import {
  Button,
  Card,
  Chip,
  ErrorText,
  Field,
  Row,
  TextAction,
  useColors,
  useStyles,
} from '../../components/ui'
import { MerchantNames } from '../../features/categories/MerchantNames'
import { People } from '../../features/categories/People'
import { useLoad } from '../../lib/useLoad'

const TABS = [
  { id: 'categories', label: 'Categories' },
  { id: 'people', label: 'People' },
  { id: 'merchant-names', label: 'Merchant names' },
] as const
type Tab = (typeof TABS)[number]['id']

export default function Categories() {
  const styles = useStyles()
  const c = useColors()
  const { data, error, loading, reload } = useLoad(listCategories)
  // Same ?tab= values as the web page.
  const param = useLocalSearchParams<{ tab?: string }>().tab
  const tab: Tab = TABS.find((t) => t.id === param)?.id ?? 'categories'
  const [name, setName] = useState('')
  const [spending, setSpending] = useState(true)
  const [saveError, setSaveError] = useState<string | null>(null)
  // The category being renamed, and its draft name.
  const [renaming, setRenaming] = useState<string | null>(null)
  const [draftName, setDraftName] = useState('')
  // Transactions per category id; null until loaded (names show without counts).
  const [counts, setCounts] = useState<Record<string, number> | null>(null)

  useEffect(() => {
    if (!loading) categoryCounts().then(setCounts, () => {})
  }, [loading])

  async function run(task: () => Promise<unknown>) {
    setSaveError(null)
    try {
      await task()
      reload()
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : 'Could not save that.')
    }
  }

  function confirmDelete(id: string, categoryName: string) {
    Alert.alert(
      `Delete ${categoryName}?`,
      'Transactions in it become uncategorised.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => run(() => deleteCategory(id)),
        },
      ],
    )
  }

  const tabs = (
    <View style={styles.chipRow} accessibilityRole="tablist">
      {TABS.map((t) => (
        <Chip
          key={t.id}
          label={t.label}
          selected={tab === t.id}
          onPress={() => router.setParams({ tab: t.id })}
        />
      ))}
    </View>
  )

  if (tab !== 'categories')
    return (
      <View style={[styles.screen, { paddingBottom: 0 }]}>
        {tabs}
        {error && <ErrorText>{error}</ErrorText>}
        {data &&
          (tab === 'people' ? (
            <People categories={data} />
          ) : (
            <MerchantNames categories={data} />
          ))}
      </View>
    )

  return (
    <View style={[styles.screen, { paddingBottom: 0 }]}>
      {tabs}
      <Card style={{ paddingVertical: 16, gap: 12 }}>
        <Field
          label="New category name"
          maxLength={50}
          value={name}
          onChangeText={setName}
        />
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            minHeight: 48,
          }}
        >
          <Text style={styles.text}>Counts as spending</Text>
          <Switch
            accessibilityLabel="Counts as spending"
            value={spending}
            onValueChange={setSpending}
            trackColor={{ true: c.ledger, false: c.rule }}
          />
        </View>
        <Button
          title="Add category"
          disabled={!name.trim()}
          onPress={() =>
            run(async () => {
              await createCategory(name.trim(), spending)
              setName('')
            })
          }
        />
      </Card>
      {saveError && <ErrorText>{saveError}</ErrorText>}
      {error && <ErrorText>{error}</ErrorText>}
      <FlatList
        data={data ?? []}
        contentContainerStyle={{ paddingBottom: 16 }}
        keyExtractor={(c) => c.id}
        refreshing={loading}
        onRefresh={reload}
        renderItem={({ item: cat, index }) =>
          renaming === cat.id ? (
            <View
              style={[
                !!index && styles.divider,
                { gap: 8, paddingVertical: 12 },
              ]}
            >
              <Field
                label={`New name for ${cat.name}`}
                maxLength={50}
                autoFocus
                value={draftName}
                onChangeText={setDraftName}
              />
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <Button
                  title="Save"
                  disabled={!draftName.trim()}
                  onPress={() =>
                    run(async () => {
                      await updateCategory(
                        cat.id,
                        draftName.trim(),
                        cat.countsAsSpending,
                      )
                      setRenaming(null)
                    })
                  }
                />
                <Button
                  title="Cancel"
                  variant="secondary"
                  onPress={() => setRenaming(null)}
                />
              </View>
            </View>
          ) : (
            <Row
              first={index === 0}
              left={counts ? `${cat.name} (${counts[cat.id] ?? 0})` : cat.name}
              sub={cat.countsAsSpending ? undefined : 'Not counted as spending'}
              accessibilityLabel={
                counts
                  ? `${cat.name}, ${counts[cat.id] ?? 0} transactions`
                  : cat.name
              }
              accessibilityHint="Shows its transactions"
              onPress={() =>
                router.push({
                  pathname: '/transactions',
                  params: { category: cat.name },
                })
              }
              action={
                <>
                  <Switch
                    accessibilityLabel={`${cat.name} counts as spending`}
                    value={cat.countsAsSpending}
                    onValueChange={(on) =>
                      run(() => updateCategory(cat.id, cat.name, on))
                    }
                    trackColor={{ true: c.ledger, false: c.rule }}
                  />
                  <TextAction
                    title="Rename"
                    accessibilityLabel={`Rename ${cat.name}`}
                    onPress={() => {
                      setDraftName(cat.name)
                      setRenaming(cat.id)
                    }}
                  />
                  <TextAction
                    title="Delete"
                    danger
                    accessibilityLabel={`Delete ${cat.name}`}
                    onPress={() => confirmDelete(cat.id, cat.name)}
                  />
                </>
              }
            />
          )
        }
      />
    </View>
  )
}
