import { useMemo } from 'react'
import { StyleSheet, useColorScheme } from 'react-native'

// Same "ledger" palette as the web app (frontend/src/index.css).
const light = {
  paper: '#eef2ef',
  surface: '#ffffff',
  ink: '#173f3a',
  inkMuted: '#4f6b66',
  ledger: '#0f766e',
  ledgerTint: '#e3f1ec',
  onLedger: '#ffffff',
  rule: '#c9d4cf',
  field: '#4f6b66',
  debit: '#9b2c5a',
  debitTint: '#f8e8ef',
}

const dark: typeof light = {
  paper: '#0a1f1c',
  surface: '#10302b',
  ink: '#e6f2ee',
  inkMuted: '#9fbab3',
  ledger: '#5fd3bc',
  ledgerTint: '#143c36',
  onLedger: '#062420',
  rule: '#24463f',
  field: '#4c6d66',
  debit: '#f49ac1',
  debitTint: '#3a1a29',
}

export type Colors = typeof light

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 }
export const radius = { sm: 8, md: 12, pill: 999 }
/** Minimum touch target (Material 48dp; also clears iOS 44pt). */
export const TOUCH = 48

export function useColors(): Colors {
  return useColorScheme() === 'dark' ? dark : light
}

export function useStyles() {
  const c = useColors()
  return useMemo(() => makeStyles(c), [c])
}

function makeStyles(c: Colors) {
  return StyleSheet.create({
    screen: {
      flexGrow: 1,
      padding: space.lg,
      gap: space.md,
      backgroundColor: c.paper,
    },
    title: { fontSize: 26, fontWeight: '700', color: c.ink },
    heading: {
      fontSize: 18,
      fontWeight: '700',
      color: c.ink,
      marginTop: space.sm,
    },
    text: { fontSize: 16, color: c.ink },
    muted: { fontSize: 14, color: c.inkMuted },
    card: {
      backgroundColor: c.surface,
      borderRadius: radius.md,
      borderWidth: StyleSheet.hairlineWidth,
      borderColor: c.rule,
      paddingHorizontal: space.lg,
    },
    /** Inset surface for an expanded detail or filter panel. */
    panel: {
      padding: space.md,
      gap: space.sm,
      borderRadius: radius.md,
      backgroundColor: c.surface,
    },
    chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm },
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: space.md,
      minHeight: 56,
      paddingVertical: space.md,
    },
    divider: {
      borderTopWidth: StyleSheet.hairlineWidth,
      borderTopColor: c.rule,
    },
    rowText: { fontSize: 16, color: c.ink },
    amount: { fontSize: 16, fontWeight: '600', fontVariant: ['tabular-nums'] },
    label: { fontSize: 14, fontWeight: '700', color: c.ink },
    input: {
      minHeight: TOUCH,
      borderWidth: 1,
      borderColor: c.field,
      borderRadius: radius.sm,
      paddingHorizontal: space.md,
      fontSize: 16,
      color: c.ink,
      backgroundColor: c.surface,
    },
    error: {
      color: c.ink,
      backgroundColor: c.debitTint,
      borderLeftWidth: 4,
      borderLeftColor: c.debit,
      borderRadius: radius.sm,
      paddingVertical: space.sm,
      paddingHorizontal: space.md,
    },
    debit: { color: c.debit },
    credit: { color: c.ledger },
  })
}
