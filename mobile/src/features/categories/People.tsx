import {
  type Category,
  listPeople,
  mergePerson,
  movePayee,
  type Person,
  setPersonCategory,
  updatePerson,
} from '@expense-analyzer/api-client'
import { useEffect, useState } from 'react'
import { Alert, FlatList, Text, View } from 'react-native'
import { Card, ErrorText, useStyles } from '../../components/ui'
import { space } from '../../components/theme'
import { DirectionDefaults, Disclosure, Picker, RenameField } from './controls'

/** "Move to" value for a person of its own. */
const NEW = 'new'

const message = (e: unknown) =>
  e instanceof Error ? e.message : 'Could not save that.'

const same = (a: string, b: string) =>
  a.trim().toLowerCase() === b.trim().toLowerCase()

/** People found in UPI transfers: one row each; tap for rename, defaults, UPI names and merge. */
export function People({ categories }: { categories: Category[] }) {
  const styles = useStyles()
  const [people, setPeople] = useState<Person[] | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listPeople().then(setPeople, () => setLoadError(true))
  }, [])

  async function run(task: () => Promise<void>) {
    setError(null)
    try {
      await task()
    } catch (e) {
      setError(message(e))
      throw e
    }
  }

  const replace = (saved: Person) =>
    setPeople((all) => all!.map((x) => (x.id === saved.id ? saved : x)))

  /** Renames, asking first when another person already has that name. */
  function rename(p: Person, name: string) {
    const other = people!.find((o) => o.id !== p.id && same(o.name, name))
    if (!other)
      return run(async () =>
        replace(await updatePerson(p.id, name, p.categoryId)),
      )
    return new Promise<void>((resolve, reject) =>
      Alert.alert(`${other.name} already exists`, 'Same person?', [
        { text: 'Cancel', style: 'cancel', onPress: () => reject() },
        {
          text: 'Keep separate',
          onPress: () =>
            run(async () =>
              replace(await updatePerson(p.id, name, p.categoryId)),
            ).then(resolve, reject),
        },
        {
          text: `Merge into ${other.name}`,
          onPress: () =>
            run(async () => setPeople(await mergePerson(p.id, other.id))).then(
              resolve,
              reject,
            ),
        },
      ]),
    )
  }

  if (loadError)
    return <ErrorText>Could not load your people. Pull to try again.</ErrorText>

  return (
    <FlatList
      data={people ?? []}
      keyExtractor={(p) => p.id}
      contentContainerStyle={{ gap: space.md, paddingBottom: space.lg }}
      ListHeaderComponent={
        <View style={{ gap: space.sm }}>
          <Text style={styles.muted}>
            Found from UPI transfers. A person’s default category applies to
            their transactions unless you set one on a single transaction.
          </Text>
          {error && <ErrorText>{error}</ErrorText>}
          {people?.length === 0 && (
            <Text style={styles.muted}>
              No people found in your transfers yet.
            </Text>
          )}
        </View>
      }
      renderItem={({ item: p }) => {
        const others = people!.filter((x) => x.id !== p.id)
        return (
          <Card>
            <Disclosure
              first
              title={p.name}
              summary={
                p.payees.length === 1
                  ? '1 UPI name'
                  : `${p.payees.length} UPI names`
              }
            >
              <RenameField
                name={p.name}
                label="person"
                onSave={(name) => rename(p, name)}
              />
              <Text style={styles.label} accessibilityRole="header">
                Default categories
              </Text>
              <DirectionDefaults
                name={p.name}
                out={p.categoryId}
                into={p.creditCategoryId}
                categories={categories}
                onChange={(credit, categoryId) =>
                  run(async () =>
                    replace(await setPersonCategory(p.id, categoryId, credit)),
                  ).catch(() => {})
                }
              />
              <Text style={styles.label} accessibilityRole="header">
                UPI names
              </Text>
              {p.payees.map((payee) => (
                <View key={payee} style={{ gap: space.xs }}>
                  <Text style={styles.text}>{payee}</Text>
                  {p.payees.length > 1 && (
                    <Picker
                      label={`Move ${payee} to`}
                      value={null}
                      placeholder="Move to…"
                      options={[
                        { value: NEW, label: 'A new person' },
                        ...others.map((o) => ({ value: o.id, label: o.name })),
                      ]}
                      onChange={(to) =>
                        run(async () =>
                          setPeople(
                            await movePayee(
                              p.id,
                              payee,
                              to === NEW ? null : to,
                            ),
                          ),
                        ).catch(() => {})
                      }
                    />
                  )}
                </View>
              ))}
              <Text style={styles.label} accessibilityRole="header">
                Merge
              </Text>
              <Picker
                label={`Merge ${p.name} into`}
                value={null}
                placeholder="Merge into…"
                disabled={others.length === 0}
                options={others.map((o) => ({ value: o.id, label: o.name }))}
                onChange={(into) =>
                  run(async () =>
                    setPeople(await mergePerson(p.id, into)),
                  ).catch(() => {})
                }
              />
            </Disclosure>
          </Card>
        )
      }}
    />
  )
}
