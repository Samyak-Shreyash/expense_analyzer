import { getCategories, getSummary } from '@expense-analyzer/api-client'
import { RefreshControl, ScrollView, Text, View } from 'react-native'
import { ErrorText, Row, styles, Title } from '../../components/ui'
import { monthRange, rupees } from '../../lib/format'
import { useLoad } from '../../lib/useLoad'

export default function Dashboard() {
  const { data, error, loading, reload } = useLoad(async () => {
    const { from, to } = monthRange()
    const [summary, categories] = await Promise.all([
      getSummary(from, to),
      getCategories(from, to),
    ])
    return { summary, categories }
  })

  return (
    <ScrollView
      contentContainerStyle={styles.screen}
      refreshControl={
        <RefreshControl refreshing={loading} onRefresh={reload} />
      }
    >
      <Title>This month</Title>
      {error && <ErrorText>{error}</ErrorText>}
      {data && (
        <>
          <View style={{ flexDirection: 'row', gap: 16, marginBottom: 16 }}>
            <Figure
              label="Money in"
              value={data.summary.inflow}
              style={styles.credit}
            />
            <Figure
              label="Money out"
              value={data.summary.outflow}
              style={styles.debit}
            />
            <Figure label="Net" value={data.summary.net} />
          </View>
          <Text style={[styles.title, { fontSize: 18 }]}>
            Spending by category
          </Text>
          {data.categories.items.length === 0 ? (
            <Text style={styles.muted}>No spending yet this month.</Text>
          ) : (
            data.categories.items.map((c) => (
              <Row
                key={c.category}
                left={c.category}
                sub={`${c.transactionCount} transactions`}
                right={rupees(c.spend)}
              />
            ))
          )}
        </>
      )}
    </ScrollView>
  )
}

function Figure({
  label,
  value,
  style,
}: {
  label: string
  value: number
  style?: object
}) {
  return (
    <View style={{ flex: 1 }}>
      <Text style={styles.muted}>{label}</Text>
      <Text style={[{ fontSize: 17, fontWeight: '700' }, style]}>
        {rupees(value)}
      </Text>
    </View>
  )
}
