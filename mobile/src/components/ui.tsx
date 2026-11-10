import type { ReactNode } from 'react'
import { StyleSheet, Text, View } from 'react-native'

export function Title({ children }: { children: ReactNode }) {
  return <Text style={styles.title}>{children}</Text>
}

export function ErrorText({ children }: { children: ReactNode }) {
  return (
    <Text style={styles.error} accessibilityRole="alert">
      {children}
    </Text>
  )
}

export function Row({
  left,
  right,
  sub,
}: {
  left: string
  right?: string
  sub?: string
}) {
  return (
    <View style={styles.row}>
      <View style={{ flex: 1 }}>
        <Text style={styles.rowText} numberOfLines={1}>
          {left}
        </Text>
        {sub ? <Text style={styles.muted}>{sub}</Text> : null}
      </View>
      {right ? <Text style={styles.rowText}>{right}</Text> : null}
    </View>
  )
}

export const styles = StyleSheet.create({
  screen: { flex: 1, padding: 16 },
  title: { fontSize: 22, fontWeight: '700', marginBottom: 12 },
  error: { color: '#b00020', marginVertical: 8 },
  muted: { color: '#666', fontSize: 13 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#ccc',
  },
  rowText: { fontSize: 16 },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 16,
  },
  debit: { color: '#b00020' },
  credit: { color: '#1b7f3b' },
})
