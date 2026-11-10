import {
  categoryCounts,
  createCategory,
  deleteCategory,
  listCategories,
} from '@expense-analyzer/api-client'
import { router } from 'expo-router'
import { useEffect, useState } from 'react'
import { Alert, FlatList, Switch, Text, View } from 'react-native'
import {
  Button,
  Card,
  ErrorText,
  Field,
  Row,
  TextAction,
  useColors,
  useStyles,
} from '../../components/ui'
import { useLoad } from '../../lib/useLoad'

export default function Categories() {
  const styles = useStyles()
  const c = useColors()
  const { data, error, loading, reload } = useLoad(listCategories)
  const [name, setName] = useState('')
  const [spending, setSpending] = useState(true)
  const [saveError, setSaveError] = useState<string | null>(null)
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

  return (
    <View style={[styles.screen, { paddingBottom: 0 }]}>
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
        renderItem={({ item: cat, index }) => (
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
              <TextAction
                title="Delete"
                danger
                accessibilityLabel={`Delete ${cat.name}`}
                onPress={() => confirmDelete(cat.id, cat.name)}
              />
            }
          />
        )}
      />
    </View>
  )
}
