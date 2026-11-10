import {
  changePassword,
  deleteAccount,
  getProfile,
  updateProfile,
  type Profile,
} from '@expense-analyzer/api-client'
import { Stack, useLocalSearchParams } from 'expo-router'
import { useState } from 'react'
import {
  DeviceEventEmitter,
  ScrollView,
  Switch,
  Text,
  View,
} from 'react-native'
import { type AccountSection, PROFILE_UPDATED } from '../components/AccountMenu'
import {
  Button,
  Chip,
  ErrorText,
  Field,
  useColors,
  useStyles,
} from '../components/ui'
import { useSignOut } from '../features/auth/AuthGate'
import { useLoad } from '../lib/useLoad'

const TITLES: Record<AccountSection, string> = {
  details: 'Edit profile',
  preferences: 'Preferences',
  password: 'Change password',
  delete: 'Delete account',
}

const message = (e: unknown) =>
  e instanceof Error ? e.message : 'Could not save that.'

/** One account option per visit, picked by the `section` param from the header menu. */
export default function Account() {
  const styles = useStyles()
  const c = useColors()
  const { section = 'details' } = useLocalSearchParams<{
    section?: AccountSection
  }>()
  const { data, error } = useLoad(getProfile)

  return (
    <ScrollView
      contentContainerStyle={styles.screen}
      keyboardShouldPersistTaps="handled"
    >
      <Stack.Screen
        options={{
          headerShown: true,
          title: TITLES[section] ?? 'Account',
          headerStyle: { backgroundColor: c.surface },
          headerTintColor: c.ink,
          headerShadowVisible: false,
        }}
      />
      {error && <ErrorText>{error}</ErrorText>}
      {!data && !error && <Text style={styles.muted}>Loading...</Text>}
      {data && section === 'details' && <Details profile={data} />}
      {data && section === 'preferences' && <Preferences profile={data} />}
      {data && section === 'password' && <Password />}
      {data && section === 'delete' && <Delete />}
    </ScrollView>
  )
}

function useSave() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const run = async (action: () => Promise<unknown>) => {
    setBusy(true)
    setError(null)
    setDone(false)
    try {
      await action()
      setDone(true)
      return true
    } catch (e) {
      setError(message(e))
      return false
    } finally {
      setBusy(false)
    }
  }
  return { busy, error, setError, done, run }
}

const saved = (p: Profile) => DeviceEventEmitter.emit(PROFILE_UPDATED, p)

function Details({ profile }: { profile: Profile }) {
  const styles = useStyles()
  const [fullName, setFullName] = useState(profile.fullName ?? '')
  const { busy, error, setError, done, run } = useSave()

  const save = () => {
    if (!fullName.trim()) return setError('Enter your name.')
    run(async () =>
      saved(await updateProfile(fullName.trim(), profile.preferences)),
    )
  }

  return (
    <View style={{ gap: 12 }}>
      <Text style={styles.muted}>{profile.email}</Text>
      <Field
        label="Full name"
        value={fullName}
        maxLength={255}
        autoComplete="name"
        textContentType="name"
        onChangeText={setFullName}
      />
      {error && <ErrorText>{error}</ErrorText>}
      {done && <Text accessibilityLiveRegion="polite">Profile saved.</Text>}
      <Button title="Save" busy={busy} onPress={save} />
    </View>
  )
}

function Preferences({ profile }: { profile: Profile }) {
  const styles = useStyles()
  const c = useColors()
  const [theme, setTheme] = useState(profile.preferences.theme ?? 'light')
  const [currency, setCurrency] = useState(
    profile.preferences.currency ?? 'inr',
  )
  const [notifications, setNotifications] = useState(
    profile.preferences.notifications ?? true,
  )
  const { busy, error, done, run } = useSave()

  const save = () =>
    run(async () =>
      saved(await updateProfile(null, { theme, currency, notifications })),
    )

  return (
    <View style={{ gap: 12 }}>
      <Text style={styles.label}>Theme</Text>
      <View style={styles.chipRow}>
        {['light', 'dark'].map((t) => (
          <Chip
            key={t}
            label={t === 'light' ? 'Light' : 'Dark'}
            selected={theme === t}
            onPress={() => setTheme(t)}
          />
        ))}
      </View>
      <Text style={styles.label}>Currency</Text>
      <View style={styles.chipRow}>
        {['inr', 'usd', 'eur'].map((cur) => (
          <Chip
            key={cur}
            label={cur.toUpperCase()}
            selected={currency === cur}
            onPress={() => setCurrency(cur)}
          />
        ))}
      </View>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          minHeight: 48,
        }}
      >
        <Text style={styles.text}>Notifications</Text>
        <Switch
          accessibilityLabel="Notifications"
          value={notifications}
          onValueChange={setNotifications}
          trackColor={{ true: c.ledger }}
        />
      </View>
      {error && <ErrorText>{error}</ErrorText>}
      {done && <Text accessibilityLiveRegion="polite">Preferences saved.</Text>}
      <Button title="Save" busy={busy} onPress={save} />
    </View>
  )
}

function Password() {
  const styles = useStyles()
  const signOut = useSignOut()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const { busy, error, setError, run } = useSave()

  const submit = async () => {
    if (!current || next.length < 8)
      return setError(
        'Enter your current password and a new one of at least 8 characters.',
      )
    if (next !== confirm) return setError('The new passwords do not match.')
    if (await run(() => changePassword(current, next))) await signOut()
  }

  return (
    <View style={{ gap: 12 }}>
      <Text style={styles.muted}>
        You will be signed out everywhere and can sign in again with the new
        password.
      </Text>
      <Field
        label="Current password"
        secureTextEntry
        autoComplete="current-password"
        textContentType="password"
        value={current}
        onChangeText={setCurrent}
      />
      <Field
        label="New password"
        secureTextEntry
        autoComplete="new-password"
        textContentType="newPassword"
        value={next}
        onChangeText={setNext}
      />
      <Text style={styles.muted}>At least 8 characters</Text>
      <Field
        label="Confirm new password"
        secureTextEntry
        autoComplete="new-password"
        textContentType="newPassword"
        value={confirm}
        onChangeText={setConfirm}
      />
      {error && <ErrorText>{error}</ErrorText>}
      <Button title="Change password" busy={busy} onPress={submit} />
    </View>
  )
}

function Delete() {
  const styles = useStyles()
  const signOut = useSignOut()
  const [password, setPassword] = useState('')
  const { busy, error, setError, run } = useSave()

  const submit = async () => {
    if (!password) return setError('Enter your password to confirm.')
    if (await run(() => deleteAccount(password))) await signOut()
  }

  return (
    <View style={{ gap: 12 }}>
      <Text style={styles.text}>
        This deletes your account, statements and transactions and cannot be
        undone. Deleted data remains in backups for up to 30 days, until those
        backups expire.
      </Text>
      <Field
        label="Password"
        secureTextEntry
        autoComplete="current-password"
        textContentType="password"
        value={password}
        onChangeText={setPassword}
      />
      {error && <ErrorText>{error}</ErrorText>}
      <Button
        title="Delete account"
        variant="danger"
        busy={busy}
        onPress={submit}
      />
    </View>
  )
}
