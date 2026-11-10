import { Tabs } from 'expo-router'
import { useColors } from '../../components/ui'

export default function TabLayout() {
  const c = useColors()
  return (
    <Tabs
      screenOptions={{
        tabBarIconStyle: { display: 'none' },
        tabBarActiveTintColor: c.ledger,
        tabBarInactiveTintColor: c.inkMuted,
        tabBarActiveBackgroundColor: c.ledgerTint,
        tabBarLabelStyle: { fontSize: 13, fontWeight: '600' },
        tabBarStyle: { backgroundColor: c.surface, borderTopColor: c.rule },
        headerStyle: { backgroundColor: c.surface },
        headerTintColor: c.ink,
        headerShadowVisible: false,
        sceneStyle: { backgroundColor: c.paper },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Overview' }} />
      <Tabs.Screen name="transactions" options={{ title: 'Transactions' }} />
      <Tabs.Screen name="statements" options={{ title: 'Statements' }} />
      <Tabs.Screen name="categories" options={{ title: 'Categories' }} />
      <Tabs.Screen name="profile" options={{ title: 'Profile' }} />
    </Tabs>
  )
}
