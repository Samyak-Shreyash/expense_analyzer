import {
  createCategory,
  deleteCategory,
  listCategories,
} from '@expense-analyzer/api-client'
import { useState } from 'react'
import {
  Button,
  FlatList,
  Pressable,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native'
import { ErrorText, Row, styles } from '../../components/ui'
import { useLoad } from '../../lib/useLoad'

export default function Categories() {
  const { data, error, loading, reload } = useLoad(listCategories)
  const [name, setName] = useState('')
  const [spending, setSpending] = useState(true)
  const [saveError, setSaveError] = useState<string | null>(null)

  async function run(task: () => Promise<unknown>) {
    setSaveError(null)
    try {
      await task()
      reload()
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : 'Could not save that.')
    }
  }

  return (
    <View style={styles.screen}>
      <View style={{ gap: 8 }}>
        <TextInput
          style={styles.input}
          placeholder="New category name"
          accessibilityLabel="New category name"
          maxLength={50}
          value={name}
          onChangeText={setName}
        />
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <Switch
            accessibilityLabel="Counts as spending"
            value={spending}
            onValueChange={setSpending}
          />
          <Text>Counts as spending</Text>
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
      </View>
      {saveError && <ErrorText>{saveError}</ErrorText>}
      {error && <ErrorText>{error}</ErrorText>}
      <FlatList
        style={{ marginTop: 12 }}
        data={data ?? []}
        keyExtractor={(c) => c.id}
        refreshing={loading}
        onRefresh={reload}
        renderItem={({ item: c }) => (
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <View style={{ flex: 1 }}>
              <Row
                left={c.name}
                sub={c.countsAsSpending ? undefined : 'Not spending'}
              />
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Delete ${c.name}`}
              onPress={() => run(() => deleteCategory(c.id))}
            >
              <Text style={styles.debit}>Delete</Text>
            </Pressable>
          </View>
        )}
      />
    </View>
  )
}
