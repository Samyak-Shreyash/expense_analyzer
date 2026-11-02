import { Button, StyleSheet, Text, View } from 'react-native'
import { useSignOut } from '../features/auth/AuthGate'

export default function Home() {
  const signOut = useSignOut()
  return (
    <View style={styles.container}>
      <Text>Expense Analyzer</Text>
      <Button title="Log out" onPress={signOut} />
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
})
