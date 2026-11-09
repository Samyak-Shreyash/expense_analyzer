import { getProfile } from '@expense-analyzer/api-client'
import { Button, Text, View } from 'react-native'
import { ErrorText, styles, Title } from '../../components/ui'
import { useSignOut } from '../../features/auth/AuthGate'
import { useLoad } from '../../lib/useLoad'

export default function Profile() {
  const signOut = useSignOut()
  const { data, error } = useLoad(getProfile)

  return (
    <View style={[styles.screen, { gap: 8 }]}>
      <Title>Profile</Title>
      {error && <ErrorText>{error}</ErrorText>}
      {data && (
        <>
          {data.fullName ? (
            <Text style={styles.rowText}>{data.fullName}</Text>
          ) : null}
          <Text style={styles.muted}>{data.email}</Text>
        </>
      )}
      <View style={{ marginTop: 16 }}>
        <Button title="Log out" onPress={signOut} />
      </View>
    </View>
  )
}
