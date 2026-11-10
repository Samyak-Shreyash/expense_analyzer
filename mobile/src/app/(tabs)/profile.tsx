import { getProfile } from '@expense-analyzer/api-client'
import { ScrollView, Text, View } from 'react-native'
import { Button, Card, ErrorText, Title, useStyles } from '../../components/ui'
import { useSignOut } from '../../features/auth/AuthGate'
import { useLoad } from '../../lib/useLoad'

export default function Profile() {
  const styles = useStyles()
  const signOut = useSignOut()
  const { data, error } = useLoad(getProfile)

  return (
    <ScrollView contentContainerStyle={styles.screen}>
      <Title>Profile</Title>
      {error && <ErrorText>{error}</ErrorText>}
      {data && (
        <Card style={{ paddingVertical: 16, gap: 4 }}>
          {data.fullName ? (
            <Text style={[styles.rowText, { fontWeight: '600' }]}>
              {data.fullName}
            </Text>
          ) : null}
          <Text style={styles.muted}>{data.email}</Text>
        </Card>
      )}
      <View style={{ marginTop: 24 }}>
        <Button title="Log out" variant="danger" onPress={signOut} />
      </View>
    </ScrollView>
  )
}
