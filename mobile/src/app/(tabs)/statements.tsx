import {
  deleteStatement,
  listStatements,
  uploadStatement,
} from '@expense-analyzer/api-client'
import * as DocumentPicker from 'expo-document-picker'
import { useState } from 'react'
import {
  Button,
  FlatList,
  Pressable,
  Text,
  TextInput,
  View,
} from 'react-native'
import { ErrorText, Row, styles } from '../../components/ui'
import { useLoad } from '../../lib/useLoad'

type Picked = { uri: string; name: string; type: string }

export default function Statements() {
  const { data, error, loading, reload } = useLoad(listStatements)
  const [progress, setProgress] = useState<number | null>(null)
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(
    null,
  )
  // A password-protected PDF waits here until its password is entered.
  const [locked, setLocked] = useState<Picked | null>(null)
  const [password, setPassword] = useState('')

  async function send(file: Picked, pdfPassword?: string) {
    setMessage(null)
    setProgress(0)
    const outcome = await uploadStatement(file, setProgress, pdfPassword)
    setProgress(null)
    if (outcome.ok) {
      setLocked(null)
      setPassword('')
      setMessage({
        ok: true,
        text: `Uploaded ${file.name}. It is being processed.`,
      })
      reload()
    } else {
      if (/password/i.test(outcome.message)) setLocked(file)
      setMessage({ ok: false, text: outcome.message })
    }
  }

  async function choose() {
    const res = await DocumentPicker.getDocumentAsync({
      copyToCacheDirectory: true,
    })
    if (res.canceled) return
    const a = res.assets[0]
    await send({
      uri: a.uri,
      name: a.name,
      type: a.mimeType ?? 'application/octet-stream',
    })
  }

  async function remove(id: string) {
    try {
      await deleteStatement(id)
      reload()
    } catch (e) {
      setMessage({
        ok: false,
        text: e instanceof Error ? e.message : 'Could not delete it.',
      })
    }
  }

  return (
    <View style={styles.screen}>
      <Button
        title="Upload a statement"
        onPress={choose}
        disabled={progress !== null}
      />
      {progress !== null && (
        <Text style={styles.muted}>
          Uploading… {Math.round(progress * 100)}%
        </Text>
      )}
      {locked && (
        <View style={{ gap: 8, marginTop: 8 }}>
          <TextInput
            style={styles.input}
            placeholder="PDF password"
            accessibilityLabel="PDF password"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />
          <Button
            title="Upload with password"
            disabled={!password}
            onPress={() => send(locked, password)}
          />
        </View>
      )}
      {message &&
        (message.ok ? (
          <Text style={[styles.muted, { marginTop: 8 }]}>{message.text}</Text>
        ) : (
          <ErrorText>{message.text}</ErrorText>
        ))}
      {error && <ErrorText>{error}</ErrorText>}
      <FlatList
        style={{ marginTop: 12 }}
        data={data?.items ?? []}
        keyExtractor={(s) => s.id}
        refreshing={loading}
        onRefresh={reload}
        ListEmptyComponent={
          loading ? undefined : (
            <Text style={styles.muted}>No statements yet.</Text>
          )
        }
        renderItem={({ item: s }) => (
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <View style={{ flex: 1 }}>
              <Row
                left={s.fileName}
                sub={
                  s.status === 'FAILED'
                    ? (s.processingError ?? 'Failed')
                    : s.status.toLowerCase()
                }
              />
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Delete ${s.fileName}`}
              onPress={() => remove(s.id)}
            >
              <Text style={styles.debit}>Delete</Text>
            </Pressable>
          </View>
        )}
      />
    </View>
  )
}
