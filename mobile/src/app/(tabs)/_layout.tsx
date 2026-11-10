import Ionicons from '@expo/vector-icons/Ionicons'
import { Tabs } from 'expo-router'
import type { ComponentProps } from 'react'
import type { ColorValue } from 'react-native'
import { useColors } from '../../components/ui'

type IconName = ComponentProps<typeof Ionicons>['name']

/** Outline when inactive, filled when active; the label carries the meaning. */
function icon(name: string) {
  return function TabIcon({
    focused,
    color,
    size,
  }: {
    focused: boolean
    color: ColorValue
    size: number
  }) {
    return (
      <Ionicons
        name={(focused ? name : `${name}-outline`) as IconName}
        // The tab bar passes the string tint colours set in screenOptions.
        color={color as string}
        size={size}
        accessible={false}
      />
    )
  }
}

export default function TabLayout() {
  const c = useColors()
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: c.ledger,
        tabBarInactiveTintColor: c.inkMuted,
        tabBarLabelStyle: { fontSize: 12, fontWeight: '600' },
        tabBarStyle: { backgroundColor: c.surface, borderTopColor: c.rule },
        headerStyle: { backgroundColor: c.surface },
        headerTintColor: c.ink,
        headerShadowVisible: false,
        sceneStyle: { backgroundColor: c.paper },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{ title: 'Overview', tabBarIcon: icon('pie-chart') }}
      />
      <Tabs.Screen
        name="transactions"
        options={{ title: 'Transactions', tabBarIcon: icon('swap-vertical') }}
      />
      <Tabs.Screen
        name="statements"
        options={{ title: 'Statements', tabBarIcon: icon('document-text') }}
      />
      <Tabs.Screen
        name="categories"
        options={{ title: 'Categories', tabBarIcon: icon('pricetags') }}
      />
      <Tabs.Screen
        name="profile"
        options={{ title: 'Profile', tabBarIcon: icon('person-circle') }}
      />
    </Tabs>
  )
}
