import Ionicons from '@expo/vector-icons/Ionicons'
import type { Category } from '@expense-analyzer/api-client'
import { type ReactNode, useState } from 'react'
import {
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Button, Field, useColors, useStyles } from '../../components/ui'
import { radius, space, TOUCH } from '../../components/theme'

export type Option = { value: string; label: string }

/** A labelled button showing the current choice; tapping it lists the options in a sheet. */
export function Picker({
  label,
  value,
  placeholder,
  options,
  onChange,
  disabled,
}: {
  label: string
  value: string | null
  /** Shown when nothing is chosen. */
  placeholder: string
  options: Option[]
  onChange: (value: string) => void
  disabled?: boolean
}) {
  const c = useColors()
  const styles = useStyles()
  const insets = useSafeAreaInsets()
  const [open, setOpen] = useState(false)
  const current = options.find((o) => o.value === value)?.label ?? placeholder
  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${current}`}
        accessibilityState={{ disabled }}
        disabled={disabled}
        onPress={() => setOpen(true)}
        style={({ pressed }) => [
          s.field,
          { borderColor: c.field, backgroundColor: c.surface },
          (pressed || disabled) && { opacity: disabled ? 0.45 : 0.7 },
        ]}
      >
        <Text style={[styles.text, { flex: 1 }]} numberOfLines={1}>
          {current}
        </Text>
        <Ionicons
          name="chevron-down"
          size={18}
          color={c.inkMuted}
          accessible={false}
        />
      </Pressable>
      <Modal
        visible={open}
        transparent
        animationType="slide"
        onRequestClose={() => setOpen(false)}
      >
        <Pressable
          accessibilityLabel="Close"
          style={s.scrim}
          onPress={() => setOpen(false)}
        />
        <View
          accessibilityViewIsModal
          style={[
            s.sheet,
            { backgroundColor: c.surface, paddingBottom: insets.bottom },
          ]}
        >
          <Text style={[styles.label, s.sheetTitle]} accessibilityRole="header">
            {label}
          </Text>
          <FlatList
            data={options}
            keyExtractor={(o) => o.value}
            renderItem={({ item }) => (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected: item.value === value }}
                onPress={() => {
                  setOpen(false)
                  onChange(item.value)
                }}
                style={({ pressed }) => [
                  s.option,
                  pressed && { backgroundColor: c.ledgerTint },
                ]}
              >
                <Text
                  style={[
                    styles.text,
                    item.value === value && { fontWeight: '700' },
                  ]}
                >
                  {item.label}
                </Text>
                {item.value === value && (
                  <Ionicons
                    name="checkmark"
                    size={20}
                    color={c.ledger}
                    accessible={false}
                  />
                )}
              </Pressable>
            )}
          />
          <Button
            title="Cancel"
            variant="secondary"
            onPress={() => setOpen(false)}
          />
        </View>
      </Modal>
    </>
  )
}

/** Money-out and money-in default categories of a person or merchant; '' clears one. */
export function DirectionDefaults({
  name,
  out,
  into,
  categories,
  onChange,
}: {
  name: string
  out: string | null
  into: string | null
  categories: Category[]
  onChange: (credit: boolean, categoryId: string | null) => void
}) {
  const styles = useStyles()
  const options = [
    { value: '', label: 'No default' },
    ...categories.map((c) => ({ value: c.id, label: c.name })),
  ]
  return (
    <>
      {[
        { credit: false, label: 'Money out', value: out },
        { credit: true, label: 'Money in', value: into },
      ].map(({ credit, label, value }) => (
        <View key={label} style={{ gap: space.xs }}>
          <Text style={styles.muted}>{label}</Text>
          <Picker
            label={`${label} default for ${name}`}
            value={value ?? ''}
            placeholder="No default"
            options={options}
            onChange={(v) => onChange(credit, v || null)}
          />
        </View>
      ))}
    </>
  )
}

/** A summary row that opens its details below it; only one tap target. */
export function Disclosure({
  title,
  summary,
  badge,
  first,
  children,
}: {
  title: string
  /** Muted line under the title, e.g. counts. */
  summary: string
  /** Short status beside the title (e.g. "Reported"). */
  badge?: string
  first?: boolean
  children: ReactNode
}) {
  const c = useColors()
  const styles = useStyles()
  const [open, setOpen] = useState(false)
  return (
    <View style={!first && styles.divider}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={[title, badge, summary].filter(Boolean).join(', ')}
        onPress={() => setOpen((o) => !o)}
        style={({ pressed }) => [styles.row, pressed && { opacity: 0.6 }]}
      >
        <View style={{ flex: 1 }}>
          <View style={s.titleLine}>
            <Text style={styles.rowText} numberOfLines={1}>
              {title}
            </Text>
            {badge ? (
              <Text style={[s.badge, { color: c.debit, borderColor: c.debit }]}>
                {badge}
              </Text>
            ) : null}
          </View>
          <Text style={styles.muted}>{summary}</Text>
        </View>
        <Ionicons
          name={open ? 'chevron-up' : 'chevron-down'}
          size={20}
          color={c.inkMuted}
          accessible={false}
        />
      </Pressable>
      {open && <View style={s.details}>{children}</View>}
    </View>
  )
}

/** A name with a Rename action that swaps in a field; resolves when saved. */
export function RenameField({
  name,
  label,
  onSave,
}: {
  name: string
  label: string
  onSave: (name: string) => Promise<void>
}) {
  const styles = useStyles()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(name)
  if (!editing)
    return (
      <View style={s.titleLine}>
        <Text style={[styles.text, { flex: 1 }]}>{name}</Text>
        <Button
          title="Rename"
          variant="secondary"
          accessibilityLabel={`Rename ${label} ${name}`}
          onPress={() => {
            setDraft(name)
            setEditing(true)
          }}
        />
      </View>
    )
  return (
    <View style={{ gap: space.sm }}>
      <Field
        label={`New name for ${name}`}
        value={draft}
        autoFocus
        maxLength={100}
        onChangeText={setDraft}
      />
      <View style={s.titleLine}>
        <Button
          title="Save"
          disabled={!draft.trim()}
          onPress={() =>
            onSave(draft.trim()).then(
              () => setEditing(false),
              () => {}, // the screen shows the message; keep the field open
            )
          }
        />
        <Button
          title="Cancel"
          variant="secondary"
          onPress={() => setEditing(false)}
        />
      </View>
    </View>
  )
}

const s = StyleSheet.create({
  field: {
    minHeight: TOUCH,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    borderWidth: 1,
    borderRadius: radius.sm,
    paddingHorizontal: space.md,
  },
  scrim: { flex: 1, backgroundColor: 'rgba(0,0,0,0.3)' },
  sheet: {
    maxHeight: '70%',
    padding: space.lg,
    gap: space.sm,
    borderTopLeftRadius: radius.md,
    borderTopRightRadius: radius.md,
  },
  sheetTitle: { marginBottom: space.xs },
  option: {
    minHeight: TOUCH,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.sm,
    borderRadius: radius.sm,
  },
  titleLine: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  badge: {
    fontSize: 12,
    fontWeight: '700',
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: space.sm,
  },
  details: { gap: space.md, paddingBottom: space.lg },
})
