import {
  type CashFlow,
  type CategoryBreakdown,
  getCashFlow,
  getCategories,
  getMerchants,
  getNonSpending,
  getSummary,
  getTrends,
  type MerchantBreakdown,
  type NonSpending,
  type Summary,
  type Trends,
} from '@expense-analyzer/api-client'
import { router } from 'expo-router'
import { useCallback, useEffect, useState } from 'react'
import { Pressable, RefreshControl, ScrollView, Text, View } from 'react-native'
import {
  Button,
  Card,
  Chip,
  ErrorText,
  Field,
  Heading,
  Row,
  Title,
  useColors,
  useStyles,
} from '../../components/ui'
import { radius, space } from '../../components/theme'
import {
  changeLabel,
  dayLabel,
  monthLabel,
  rangeFor,
  type RangePreset,
  rupees,
} from '../../lib/format'

const PRESETS: { value: RangePreset; label: string }[] = [
  { value: 'month', label: 'This month' },
  { value: '3m', label: 'Last 3 months' },
  { value: '12m', label: 'Last 12 months' },
]

type Data = {
  summary: Summary
  categories: CategoryBreakdown
  merchants: MerchantBreakdown
  cashFlow: CashFlow
  trends: Trends
  nonSpending: NonSpending
}

const ISO = /^\d{4}-\d{2}-\d{2}$/

/** Same sections as the web Overview; charts are drawn as bars. */
export default function Dashboard() {
  const styles = useStyles()
  const c = useColors()
  // Same default period as the web: the last 3 months.
  const [preset, setPreset] = useState<RangePreset | null>('3m')
  const [range, setRange] = useState(rangeFor('3m'))
  const [custom, setCustom] = useState(range)
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState(false)
  const [loading, setLoading] = useState(true)
  const { from, to } = range

  // State is set only once the requests settle, so the effect can call it.
  const fetchData = useCallback(
    () =>
      Promise.all([
        getSummary(from, to),
        getCategories(from, to),
        getMerchants(from, to),
        getCashFlow(from, to),
        getTrends(from, to),
        getNonSpending(from, to),
      ]).then(
        ([summary, categories, merchants, cashFlow, trends, nonSpending]) => {
          setData({
            summary,
            categories,
            merchants,
            cashFlow,
            trends,
            nonSpending,
          })
          setError(false)
          setLoading(false)
        },
        () => {
          setError(true)
          setLoading(false)
        },
      ),
    [from, to],
  )

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const toTransactions = (params: Record<string, string>) =>
    router.push({ pathname: '/transactions', params: { ...params, from, to } })

  const customValid =
    ISO.test(custom.from) && ISO.test(custom.to) && custom.from <= custom.to
  const s = data?.summary

  return (
    <ScrollView
      contentContainerStyle={styles.screen}
      refreshControl={
        <RefreshControl
          refreshing={loading}
          onRefresh={() => {
            setLoading(true)
            fetchData()
          }}
          tintColor={c.ledger}
          colors={[c.ledger]}
        />
      }
    >
      <Title>Overview</Title>
      <Text style={styles.muted}>
        {dayLabel(from)} to {dayLabel(to)}
      </Text>
      <View style={styles.chipRow} accessibilityRole="radiogroup">
        {PRESETS.map((p) => (
          <Chip
            key={p.value}
            label={p.label}
            selected={preset === p.value}
            onPress={() => {
              setPreset(p.value)
              setRange(rangeFor(p.value))
            }}
          />
        ))}
        <Chip
          label="Choose dates"
          selected={preset === null}
          onPress={() => {
            setPreset(null)
            setCustom(range)
          }}
        />
      </View>
      {preset === null && (
        <View style={styles.panel}>
          <Field
            label="From (YYYY-MM-DD)"
            value={custom.from}
            maxLength={10}
            keyboardType="numbers-and-punctuation"
            onChangeText={(v) => setCustom({ ...custom, from: v })}
          />
          <Field
            label="To (YYYY-MM-DD)"
            value={custom.to}
            maxLength={10}
            keyboardType="numbers-and-punctuation"
            onChangeText={(v) => setCustom({ ...custom, to: v })}
          />
          <Button
            title="Show these dates"
            disabled={!customValid}
            onPress={() => setRange(custom)}
          />
        </View>
      )}

      {error && (
        <ErrorText>Could not load your overview. Pull to try again.</ErrorText>
      )}
      {s && s.transactionCount === 0 && !error && (
        <Text style={styles.muted}>
          No transactions in this period. Upload a statement or choose a longer
          period.
        </Text>
      )}
      {data && s && s.transactionCount > 0 && !error && (
        <>
          <View style={[styles.panel, { backgroundColor: c.ledgerTint }]}>
            <Figure
              label="Net"
              value={s.net}
              note={`${rupees(s.previous.net)} from ${dayLabel(s.previous.from)} to ${dayLabel(s.previous.to)}`}
              large
            />
            <Figure
              label="Money in"
              value={s.inflow}
              color={c.ledger}
              note={changeLabel(s.inflowChangePercent)}
            />
            <Figure
              label="Money out"
              value={s.outflow}
              color={c.debit}
              note={changeLabel(s.outflowChangePercent)}
            />
            <Figure
              label="Not counted as spending"
              value={s.nonSpendingOutflow}
              note={`${rupees(s.previous.nonSpendingOutflow)} in the previous period`}
            />
            {s.loanReceived > 0 && (
              <Figure
                label="Loans received"
                value={s.loanReceived}
                note="Borrowed, so not counted as money in"
              />
            )}
            <Figure
              label="Moved between your accounts (out)"
              value={s.transfersOut}
              note={`${rupees(s.transfersIn)} in; not counted above`}
            />
          </View>

          <Heading>Cash flow by month</Heading>
          <Card style={{ paddingVertical: space.md, gap: space.md }}>
            {data.cashFlow.months.map((m) => {
              const max = Math.max(
                ...data.cashFlow.months.flatMap((x) => [x.inflow, x.outflow]),
                1,
              )
              return (
                <View
                  key={m.month}
                  accessible
                  accessibilityLabel={`${monthLabel(m.month)}: ${rupees(m.inflow)} in, ${rupees(m.outflow)} out, net ${rupees(m.net)}`}
                  style={{ gap: space.xs }}
                >
                  <View style={{ flexDirection: 'row' }}>
                    <Text style={[styles.label, { flex: 1 }]}>
                      {monthLabel(m.month)}
                    </Text>
                    <Text style={styles.amount}>{rupees(m.net)}</Text>
                  </View>
                  <Bar value={m.inflow} max={max} color={c.ledger} />
                  <Bar value={m.outflow} max={max} color={c.debit} />
                  <Text style={styles.muted}>
                    {rupees(m.inflow)} in · {rupees(m.outflow)} out
                  </Text>
                </View>
              )
            })}
          </Card>

          <Heading>Spending trend</Heading>
          <Card>
            {data.trends.months.map((m, i) => (
              <Row
                key={m.month}
                first={i === 0}
                left={monthLabel(m.month)}
                sub={
                  m.changePercent === null
                    ? undefined
                    : `${Math.abs(m.changePercent)}% ${m.changePercent >= 0 ? 'more' : 'less'} than the month before`
                }
                right={rupees(m.outflow)}
              />
            ))}
          </Card>

          <Heading>Not counted as spending</Heading>
          {data.nonSpending.items.length === 0 ? (
            <Text style={styles.muted}>
              Nothing in this period was set aside from spending.
            </Text>
          ) : (
            <>
              <Text style={styles.muted}>
                Money out in categories that do not count as spending, such as
                repayments. Total {rupees(data.nonSpending.total)}.
              </Text>
              <Card>
                {data.nonSpending.items.map((n, i) => (
                  <View key={n.category}>
                    <Row
                      first={i === 0}
                      left={n.category}
                      right={rupees(n.spend)}
                      accessibilityHint="Shows its transactions"
                      onPress={() => toTransactions({ category: n.category })}
                    />
                    {n.people.map((p) => (
                      <View
                        key={p.personId}
                        style={{
                          flexDirection: 'row',
                          paddingLeft: space.lg,
                          paddingBottom: space.sm,
                        }}
                      >
                        <Text style={[styles.muted, { flex: 1 }]}>
                          {p.name}
                        </Text>
                        <Text style={styles.muted}>{rupees(p.spend)}</Text>
                      </View>
                    ))}
                  </View>
                ))}
              </Card>
            </>
          )}

          <Heading>Where it went</Heading>
          <SpendBars
            empty="No spending in this period."
            bars={data.categories.items.map((x) => ({
              key: x.category,
              label: x.category,
              spend: x.spend,
              share: x.sharePercent,
              onPress: () => toTransactions({ category: x.category }),
            }))}
          />

          <Heading>Top merchants</Heading>
          <SpendBars
            empty="No spending in this period."
            bars={data.merchants.items.map((m) => ({
              key: m.merchantId ?? 'unmatched',
              label: m.name,
              spend: m.spend,
              share: m.sharePercent,
              onPress: m.merchantId
                ? () => toTransactions({ merchantId: m.merchantId! })
                : undefined,
            }))}
          />
        </>
      )}
    </ScrollView>
  )
}

function Figure({
  label,
  value,
  note,
  color,
  large,
}: {
  label: string
  value: number
  note: string
  color?: string
  large?: boolean
}) {
  const styles = useStyles()
  return (
    <View>
      <Text style={styles.muted}>{label}</Text>
      <Text
        style={{
          fontSize: large ? 30 : 20,
          fontWeight: '700',
          fontVariant: ['tabular-nums'],
          color: color ?? styles.text.color,
        }}
        adjustsFontSizeToFit
        numberOfLines={1}
      >
        {rupees(value)}
      </Text>
      <Text style={styles.muted}>{note}</Text>
    </View>
  )
}

function Bar({
  value,
  max,
  color,
}: {
  value: number
  max: number
  color: string
}) {
  const c = useColors()
  return (
    <View
      style={{ height: 8, borderRadius: radius.pill, backgroundColor: c.rule }}
    >
      <View
        style={{
          width: `${Math.round((value / max) * 100)}%`,
          height: 8,
          borderRadius: radius.pill,
          backgroundColor: color,
        }}
      />
    </View>
  )
}

/** Ranked spend with a bar each; a row with onPress opens its transactions. */
function SpendBars({
  bars,
  empty,
}: {
  bars: {
    key: string
    label: string
    spend: number
    share: number | null
    onPress?: () => void
  }[]
  empty: string
}) {
  const styles = useStyles()
  const c = useColors()
  if (bars.length === 0) return <Text style={styles.muted}>{empty}</Text>
  const max = Math.max(...bars.map((b) => b.spend), 1)
  return (
    <Card style={{ paddingVertical: space.sm }}>
      {bars.map((b) => (
        <Pressable
          key={b.key}
          disabled={!b.onPress}
          onPress={b.onPress}
          accessibilityRole={b.onPress ? 'link' : undefined}
          accessibilityLabel={`${b.label}: ${rupees(b.spend)}${b.share === null ? '' : `, ${b.share}%`}`}
          accessibilityHint={b.onPress ? 'Shows its transactions' : undefined}
          style={({ pressed }) => [
            { paddingVertical: space.sm, gap: space.xs, minHeight: 48 },
            pressed && { opacity: 0.6 },
          ]}
        >
          <View style={{ flexDirection: 'row', gap: space.sm }}>
            <Text style={[styles.rowText, { flex: 1 }]} numberOfLines={1}>
              {b.label}
            </Text>
            <Text style={styles.amount}>{rupees(b.spend)}</Text>
          </View>
          <Bar value={b.spend} max={max} color={c.ledger} />
          {b.share !== null && (
            <Text style={styles.muted}>{b.share}% of spending</Text>
          )}
        </Pressable>
      ))}
    </Card>
  )
}
