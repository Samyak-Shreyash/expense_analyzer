import type { ReactNode } from 'react'
import {
  ActivityIndicator,
  Pressable,
  type StyleProp,
  StyleSheet,
  Text,
  TextInput,
  type TextInputProps,
  View,
  type ViewStyle,
} from 'react-native'
import { radius, space, TOUCH, useColors, useStyles } from './theme'

export { useColors, useStyles } from './theme'

export function Title({ children }: { children: ReactNode }) {
  const styles = useStyles()
  return (
    <Text style={styles.title} accessibilityRole="header">
      {children}
    </Text>
  )
}

export function Heading({ children }: { children: ReactNode }) {
  const styles = useStyles()
  return (
    <Text style={styles.heading} accessibilityRole="header">
      {children}
    </Text>
  )
}

export function ErrorText({ children }: { children: ReactNode }) {
  const styles = useStyles()
  return (
    <Text style={styles.error} accessibilityRole="alert">
      {children}
    </Text>
  )
}

/** Grouped list surface; children are separated by hairlines. */
export function Card({
  children,
  style,
}: {
  children: ReactNode
  style?: StyleProp<ViewStyle>
}) {
  const styles = useStyles()
  return <View style={[styles.card, style]}>{children}</View>
}

export function Row({
  left,
  right,
  sub,
  first,
  rightStyle,
  action,
  onPress,
  accessibilityLabel,
  accessibilityHint,
}: {
  left: string
  right?: string
  sub?: string
  /** First row in a card draws no divider above it. */
  first?: boolean
  rightStyle?: object
  action?: ReactNode
  /** Makes the text part of the row a link. */
  onPress?: () => void
  accessibilityLabel?: string
  accessibilityHint?: string
}) {
  const styles = useStyles()
  const text = (
    <>
      <Text style={styles.rowText} numberOfLines={1}>
        {left}
      </Text>
      {sub ? <Text style={styles.muted}>{sub}</Text> : null}
    </>
  )
  return (
    <View style={[styles.row, !first && styles.divider]}>
      {onPress ? (
        <Pressable
          style={({ pressed }) => [
            { flex: 1, minHeight: TOUCH, justifyContent: 'center' },
            pressed && { opacity: 0.6 },
          ]}
          accessibilityRole="link"
          accessibilityLabel={accessibilityLabel}
          accessibilityHint={accessibilityHint}
          onPress={onPress}
        >
          {text}
        </Pressable>
      ) : (
        <View style={{ flex: 1 }}>{text}</View>
      )}
      {right ? (
        <Text style={[styles.amount, { color: styles.text.color }, rightStyle]}>
          {right}
        </Text>
      ) : null}
      {action}
    </View>
  )
}

type Variant = 'primary' | 'secondary' | 'danger'

export function Button({
  title,
  onPress,
  disabled,
  busy,
  variant = 'primary',
  accessibilityLabel,
}: {
  title: string
  onPress: () => void
  disabled?: boolean
  busy?: boolean
  variant?: Variant
  accessibilityLabel?: string
}) {
  const c = useColors()
  const inactive = disabled || busy
  const fg =
    variant === 'primary'
      ? c.onLedger
      : variant === 'danger'
        ? c.debit
        : c.ledger
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ disabled: inactive, busy }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        b.base,
        variant === 'primary'
          ? { backgroundColor: c.ledger }
          : {
              borderWidth: 1,
              borderColor: variant === 'danger' ? c.debit : c.ledger,
            },
        pressed && { opacity: 0.8, transform: [{ scale: 0.98 }] },
        inactive && { opacity: 0.5 },
      ]}
    >
      {busy ? <ActivityIndicator color={fg} /> : null}
      <Text style={[b.text, { color: fg }]}>{title}</Text>
    </Pressable>
  )
}

/** Small text action inside a row; padded out to a full touch target. */
export function TextAction({
  title,
  onPress,
  danger,
  accessibilityLabel,
}: {
  title: string
  onPress: () => void
  danger?: boolean
  accessibilityLabel?: string
}) {
  const c = useColors()
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      onPress={onPress}
      hitSlop={space.sm}
      style={({ pressed }) => [b.textAction, pressed && { opacity: 0.6 }]}
    >
      <Text style={{ color: danger ? c.debit : c.ledger, fontWeight: '600' }}>
        {title}
      </Text>
    </Pressable>
  )
}

export function Chip({
  label,
  selected,
  onPress,
}: {
  label: string
  selected: boolean
  onPress: () => void
}) {
  const c = useColors()
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      hitSlop={4}
      style={({ pressed }) => [
        b.chip,
        {
          borderColor: selected ? c.ledger : c.field,
          backgroundColor: selected ? c.ledger : 'transparent',
        },
        pressed && { opacity: 0.7 },
      ]}
    >
      <Text style={{ color: selected ? c.onLedger : c.ink }}>{label}</Text>
    </Pressable>
  )
}

/** Text input with a visible label above it. */
export function Field({
  label,
  ...props
}: { label: string } & Omit<TextInputProps, 'style'>) {
  const styles = useStyles()
  const c = useColors()
  return (
    <View style={{ gap: space.xs }}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={styles.input}
        accessibilityLabel={label}
        placeholderTextColor={c.inkMuted}
        {...props}
      />
    </View>
  )
}

const b = StyleSheet.create({
  base: {
    minHeight: TOUCH,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.sm,
    paddingHorizontal: space.lg,
    borderRadius: radius.sm,
  },
  text: { fontSize: 16, fontWeight: '700' },
  textAction: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: space.sm,
  },
  chip: {
    minHeight: 36,
    justifyContent: 'center',
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
  },
})
