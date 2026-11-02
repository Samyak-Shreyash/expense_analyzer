import { Stack } from 'expo-router'
import { AuthGate } from '../features/auth/AuthGate'

export default function RootLayout() {
  return (
    <AuthGate>
      <Stack screenOptions={{ headerShown: false }} />
    </AuthGate>
  )
}
