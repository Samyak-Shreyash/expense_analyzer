import {
  deleteStatement,
  listStatements,
  uploadStatement,
} from '@expense-analyzer/api-client'
import * as DocumentPicker from 'expo-document-picker'
import { useState } from 'react'
import { Alert, FlatList, Text, View } from 'react-native'
import {
  Button,
  ErrorText,
  Field,
  Row,
  TextAction,
  useColors,
  useStyles,
} from '../../components/ui'
import { useLoad } from '../../lib/useLoad'

type Picked = { uri: string; name: string; type: string }

export default function Statements() {
  const styles = useStyles()
  const c = useColors()
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

  function confirmRemove(id: string, fileName: string) {
    Alert.alert(
      'Delete statement?',
      `${fileName} and its transactions will be removed.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => remove(id) },
      ],
    )
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
    <View style={[styles.screen, { paddingBottom: 0 }]}>
      <Button
        title={
          progress !== null
            ? `Uploading… ${Math.round(progress * 100)}%`
            : 'Upload a statement'
        }
        onPress={choose}
        busy={progress !== null}
      />
      {progress !== null && (
        <View
          style={{ height: 4, borderRadius: 2, backgroundColor: c.rule }}
          accessibilityRole="progressbar"
          accessibilityValue={{
            min: 0,
            max: 100,
            now: Math.round(progress * 100),
          }}
        >
          <View
            style={{
              height: 4,
              borderRadius: 2,
              width: `${Math.round(progress * 100)}%`,
              backgroundColor: c.ledger,
            }}
          />
        </View>
      )}
      {locked && (
        <View style={{ gap: 8 }}>
          <Field
            label="PDF password"
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
          <Text style={styles.muted}>{message.text}</Text>
        ) : (
          <ErrorText>{message.text}</ErrorText>
        ))}
      {error && <ErrorText>{error}</ErrorText>}
      <FlatList
        data={data?.items ?? []}
        contentContainerStyle={{ paddingBottom: 16 }}
        keyExtractor={(s) => s.id}
        refreshing={loading}
        onRefresh={reload}
        ListEmptyComponent={
          loading ? undefined : (
            <Text style={[styles.muted, { paddingVertical: 24 }]}>
              No statements yet. Upload a PDF or CSV from your bank.
            </Text>
          )
        }
        renderItem={({ item: s, index }) => (
          <Row
            first={index === 0}
            left={s.fileName}
            sub={
              s.status === 'FAILED'
                ? `Failed: ${s.processingError ?? 'could not be read'}`
                : s.status.charAt(0) + s.status.slice(1).toLowerCase()
            }
            action={
              <TextAction
                title="Delete"
                danger
                accessibilityLabel={`Delete ${s.fileName}`}
                onPress={() => confirmRemove(s.id, s.fileName)}
              />
            }
          />
        )}
      />
    </View>
  )
}
