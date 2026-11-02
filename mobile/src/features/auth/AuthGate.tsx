import {
  isEmailAvailable,
  login,
  logout,
  register,
  setAuthHandlers,
} from '@expense-analyzer/api-client'
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react'
import {
  AppState,
  Button,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'
import { secureTokens, startApiClient } from '../../services/api'
import { shouldLock } from '../../services/appLock'
import { appLock, unlockWithBiometrics } from '../../services/deviceLock'
import { restoreSession } from '../../services/session'

type Status = 'loading' | 'anonymous' | 'setup' | 'locked' | 'open'

const SignOutContext = createContext<() => Promise<void>>(async () => {})
export const useSignOut = () => useContext(SignOutContext)

/**
 * Renders the app only when signed in and unlocked. Every entry point, including deep links
 * and the share sheet, passes through here, so nothing can show financial data while locked.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading')
  const backgroundedAt = useRef<number | null>(null)

  const signOut = useCallback(async () => {
    // Local sign-out must hold whatever the server does, so it does not wait for the network.
    setStatus('anonymous')
    await appLock.clear()
    void logout().catch(() => {})
  }, [])

  useEffect(() => {
    startApiClient()
    setAuthHandlers({
      onAuthLost: () => {
        setStatus('anonymous')
        Promise.all([appLock.clear(), secureTokens.save(null)]).catch(() => {})
      },
      onForbidden: () => {},
    })
    start().catch(() => setStatus('anonymous'))

    async function start() {
      if (!(await restoreSession(secureTokens.load))) {
        await appLock.clear()
        setStatus('anonymous')
      } else if (!(await appLock.isSetUp())) {
        // A PIN is only ever chosen straight after a password login.
        await secureTokens.save(null)
        setStatus('anonymous')
      } else if (await appLock.isExhausted()) {
        await signOut()
      } else {
        setStatus('locked')
      }
    }
  }, [signOut])

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'background') {
        backgroundedAt.current = Date.now()
      } else if (state === 'active' && backgroundedAt.current !== null) {
        if (shouldLock(backgroundedAt.current, Date.now())) {
          setStatus((s) => (s === 'open' ? 'locked' : s))
        }
        backgroundedAt.current = null
      }
    })
    return () => subscription.remove()
  }, [])

  if (status === 'loading') return null
  if (status === 'anonymous') {
    return (
      <AuthForm
        onSignedIn={async () => {
          await appLock.clear()
          setStatus('setup')
        }}
      />
    )
  }
  if (status === 'setup') {
    return <LockSetup onDone={() => setStatus('open')} />
  }
  if (status === 'locked') {
    return (
      <Unlock
        onOpen={() => setStatus((s) => (s === 'locked' ? 'open' : s))}
        onGiveUp={signOut}
      />
    )
  }
  return (
    <SignOutContext.Provider value={signOut}>
      {children}
    </SignOutContext.Provider>
  )
}

function Field(props: {
  label: string
  value: string
  onChange: (value: string) => void
  secure?: boolean
  numeric?: boolean
  email?: boolean
}) {
  return (
    <View>
      <Text>{props.label}</Text>
      <TextInput
        style={styles.input}
        accessibilityLabel={props.label}
        value={props.value}
        onChangeText={props.onChange}
        secureTextEntry={props.secure}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType={
          props.numeric
            ? 'number-pad'
            : props.email
              ? 'email-address'
              : 'default'
        }
      />
    </View>
  )
}

function ErrorText({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <Text accessibilityRole="alert" role="alert" style={styles.error}>
      {message}
    </Text>
  )
}

function AuthForm({ onSignedIn }: { onSignedIn: () => Promise<void> }) {
  const [registering, setRegistering] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function signIn() {
    if (await login(email, password)) await onSignedIn()
    else setError('Invalid email or password.')
  }

  async function create() {
    if (password.length < 8) {
      setError('Use a password of at least 8 characters.')
      return
    }
    if (!(await isEmailAvailable(email))) {
      setError('That email is already registered.')
      return
    }
    const failure = await register(email, password)
    if (failure) setError(failure)
    else await signIn()
  }

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      await (registering ? create() : signIn())
    } catch (err) {
      setError(
        err instanceof Error && err.message.startsWith('Too many')
          ? err.message
          : 'Could not reach the server. Try again in a moment.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={styles.form}>
      <Field label="Email" value={email} onChange={setEmail} email />
      <Field label="Password" value={password} onChange={setPassword} secure />
      <ErrorText message={error} />
      <Button
        title={registering ? 'Register' : 'Log in'}
        disabled={busy}
        onPress={submit}
      />
      <Button
        title={registering ? 'I have an account' : 'Create an account'}
        onPress={() => {
          setRegistering(!registering)
          setError(null)
        }}
      />
    </View>
  )
}

function LockSetup({ onDone }: { onDone: () => void }) {
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)

  return (
    <View style={styles.form}>
      <Text>
        Choose a PIN of at least 6 digits to protect your data on this device.
        You can also unlock with biometrics if your device has them set up.
      </Text>
      <Field label="New PIN" value={pin} onChange={setPin} secure numeric />
      <ErrorText message={error} />
      <Button
        title="Set PIN"
        onPress={() =>
          appLock.setPin(pin).then(onDone, () => {
            setError('Use at least 6 digits.')
          })
        }
      />
    </View>
  )
}

function Unlock(props: { onOpen: () => void; onGiveUp: () => Promise<void> }) {
  const { onOpen, onGiveUp } = props
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    unlockWithBiometrics().then(async (unlocked) => {
      if (!unlocked || cancelled) return
      await appLock.resetFailures()
      onOpen()
    })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- prompt once per lock
  }, [])

  async function submit() {
    const result = await appLock.verify(pin)
    if (result === 'ok') onOpen()
    else if (result === 'exhausted') await onGiveUp()
    else {
      setPin('')
      setError('Wrong PIN.')
    }
  }

  return (
    <View style={styles.form}>
      <Field label="PIN" value={pin} onChange={setPin} secure numeric />
      <ErrorText message={error} />
      <Button title="Unlock" onPress={submit} />
      <Button title="Forgot PIN? Log in again" onPress={onGiveUp} />
    </View>
  )
}

const styles = StyleSheet.create({
  form: { flex: 1, justifyContent: 'center', gap: 12, padding: 24 },
  input: { borderWidth: 1, borderRadius: 6, padding: 10, marginTop: 4 },
  error: { color: '#b00020' },
})
