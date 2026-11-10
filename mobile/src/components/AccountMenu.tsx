import Ionicons from '@expo/vector-icons/Ionicons'
import { getProfile } from '@expense-analyzer/api-client'
import { router } from 'expo-router'
import { useEffect, useState } from 'react'
import {
  DeviceEventEmitter,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { useSignOut } from '../features/auth/AuthGate'
import { radius, space, TOUCH, useColors } from './theme'

/** Emitted by the account screen after a save so the header shows the new name. */
export const PROFILE_UPDATED = 'profile-updated'

export type AccountSection = 'details' | 'preferences' | 'password' | 'delete'

const ITEMS: { section: AccountSection; label: string }[] = [
  { section: 'details', label: 'Edit profile' },
  { section: 'preferences', label: 'Preferences' },
  { section: 'password', label: 'Change password' },
]

type Me = { email: string; fullName: string | null }

/** Header right: the user's name opens the account options; the icon beside it logs out. */
export function AccountMenu() {
  const c = useColors()
  const insets = useSafeAreaInsets()
  const signOut = useSignOut()
  const [me, setMe] = useState<Me | null>(null)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    getProfile().then(setMe, () => {})
    const sub = DeviceEventEmitter.addListener(PROFILE_UPDATED, setMe)
    return () => sub.remove()
  }, [])

  const name = me?.fullName?.trim() || me?.email
  const go = (section: AccountSection) => {
    setOpen(false)
    router.push({ pathname: '/account', params: { section } })
  }

  return (
    <View style={s.bar}>
      {name ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Account: ${name}`}
          accessibilityHint="Opens profile, preferences, password and account options"
          accessibilityState={{ expanded: open }}
          onPress={() => setOpen(true)}
          style={({ pressed }) => [s.nameButton, pressed && { opacity: 0.6 }]}
        >
          <Text
            numberOfLines={1}
            style={[s.name, { color: c.ink }]}
            accessible={false}
          >
            {name}
          </Text>
          <Ionicons
            name="chevron-down"
            size={16}
            color={c.inkMuted}
            accessible={false}
          />
        </Pressable>
      ) : null}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Log out"
        onPress={signOut}
        style={({ pressed }) => [s.iconButton, pressed && { opacity: 0.6 }]}
      >
        <Ionicons
          name="log-out-outline"
          size={22}
          color={c.ink}
          accessible={false}
        />
      </Pressable>

      <Modal
        visible={open}
        transparent
        animationType="fade"
        onRequestClose={() => setOpen(false)}
      >
        <Pressable
          accessibilityLabel="Close menu"
          style={s.scrim}
          onPress={() => setOpen(false)}
        />
        <View
          accessibilityViewIsModal
          style={[
            s.menu,
            {
              top: insets.top + 52,
              backgroundColor: c.surface,
              borderColor: c.rule,
            },
          ]}
        >
          {me?.fullName ? (
            <Text style={[s.email, { color: c.inkMuted }]} numberOfLines={1}>
              {me.email}
            </Text>
          ) : null}
          {ITEMS.map((i) => (
            <MenuItem
              key={i.section}
              label={i.label}
              onPress={() => go(i.section)}
            />
          ))}
          <View style={[s.rule, { backgroundColor: c.rule }]} />
          <MenuItem
            label="Delete account"
            danger
            onPress={() => go('delete')}
          />
        </View>
      </Modal>
    </View>
  )
}

function MenuItem({
  label,
  danger,
  onPress,
}: {
  label: string
  danger?: boolean
  onPress: () => void
}) {
  const c = useColors()
  return (
    <Pressable
      accessibilityRole="menuitem"
      onPress={onPress}
      style={({ pressed }) => [
        s.item,
        pressed && { backgroundColor: c.ledgerTint },
      ]}
    >
      <Text style={{ fontSize: 16, color: danger ? c.debit : c.ink }}>
        {label}
      </Text>
    </Pressable>
  )
}

const s = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', marginRight: space.xs },
  nameButton: {
    minHeight: TOUCH,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    paddingHorizontal: space.sm,
  },
  name: { maxWidth: 140, fontSize: 15, fontWeight: '600' },
  iconButton: {
    width: TOUCH,
    height: TOUCH,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scrim: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.3)',
  },
  menu: {
    position: 'absolute',
    right: space.md,
    minWidth: 220,
    paddingVertical: space.xs,
    borderRadius: radius.md,
    borderWidth: StyleSheet.hairlineWidth,
    elevation: 8,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  email: {
    paddingHorizontal: space.lg,
    paddingVertical: space.sm,
    fontSize: 13,
  },
  item: {
    minHeight: TOUCH,
    justifyContent: 'center',
    paddingHorizontal: space.lg,
  },
  rule: { height: StyleSheet.hairlineWidth, marginVertical: space.xs },
})
