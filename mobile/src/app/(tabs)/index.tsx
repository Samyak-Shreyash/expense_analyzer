import { getCategories, getSummary } from '@expense-analyzer/api-client'
import { RefreshControl, ScrollView, Text, View } from 'react-native'
import {
  Card,
  ErrorText,
  Heading,
  Row,
  Title,
  useColors,
  useStyles,
} from '../../components/ui'
import { monthRange, rupees } from '../../lib/format'
import { useLoad } from '../../lib/useLoad'

export default function Dashboard() {
  const styles = useStyles()
  const c = useColors()
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
        <RefreshControl
          refreshing={loading}
          onRefresh={reload}
          tintColor={c.ledger}
          colors={[c.ledger]}
        />
      }
    >
      <Title>This month</Title>
      {error && <ErrorText>{error}</ErrorText>}
      {data && (
        <>
          <View
            style={{
              backgroundColor: c.ledgerTint,
              borderRadius: 12,
              padding: 16,
              gap: 12,
            }}
          >
            <Figure label="Net" value={data.summary.net} large />
            <View style={{ flexDirection: 'row', gap: 16 }}>
              <Figure
                label="Money in"
                value={data.summary.inflow}
                color={c.ledger}
              />
              <Figure
                label="Money out"
                value={data.summary.outflow}
                color={c.debit}
              />
            </View>
          </View>
          <Heading>Spending by category</Heading>
          {data.categories.items.length === 0 ? (
            <Text style={styles.muted}>
              No spending yet this month. Upload a statement to see where your
              money goes.
            </Text>
          ) : (
            <Card>
              {data.categories.items.map((cat, i) => (
                <Row
                  key={cat.category}
                  first={i === 0}
                  left={cat.category}
                  sub={`${cat.transactionCount} transactions`}
                  right={rupees(cat.spend)}
                />
              ))}
            </Card>
          )}
        </>
      )}
    </ScrollView>
  )
}

function Figure({
  label,
  value,
  color,
  large,
}: {
  label: string
  value: number
  color?: string
  large?: boolean
}) {
  const styles = useStyles()
  return (
    <View style={{ flex: 1 }}>
      <Text style={styles.muted}>{label}</Text>
      <Text
        style={{
          fontSize: large ? 30 : 18,
          fontWeight: '700',
          fontVariant: ['tabular-nums'],
          color: color ?? styles.text.color,
        }}
        adjustsFontSizeToFit
        numberOfLines={1}
      >
        {rupees(value)}
      </Text>
    </View>
  )
}
