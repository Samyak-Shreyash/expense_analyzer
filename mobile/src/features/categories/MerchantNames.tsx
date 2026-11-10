import {
  type Category,
  listMerchantNames,
  type MerchantName,
  type MerchantNames as Names,
  type NotShareableReason,
  renameMerchantName,
  setMerchantCategory,
  setMerchantNameShared,
  setMerchantNameSharing,
} from '@expense-analyzer/api-client'
import { useEffect, useState } from 'react'
import { Alert, FlatList, Switch, Text, View } from 'react-native'
import { Card, ErrorText, useColors, useStyles } from '../../components/ui'
import { space, TOUCH } from '../../components/theme'
import { DirectionDefaults, Disclosure, Picker, RenameField } from './controls'

// Same wording as the web Merchant names tab.
const SHARING_WARNING =
  'Shared names must be real merchant names. Offensive names are reported and can lose you the ability to share.'

const REASONS: Record<NotShareableReason, string> = {
  TRANSFER: 'Transfer to a person',
  NOT_UPI_MERCHANT: 'Not a UPI merchant payment',
  NAME_NOT_ALLOWED: "Names with digits, links or @ can't be shared",
  NAME_OFFENSIVE: "This name can't be shared",
}

const message = (e: unknown) =>
  e instanceof Error ? e.message : 'Could not save that.'

/** Your merchant names, one row per merchant; tap for defaults, rename, move and sharing. */
export function MerchantNames({ categories }: { categories: Category[] }) {
  const styles = useStyles()
  const c = useColors()
  const [data, setData] = useState<Names | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listMerchantNames().then(setData, () => setLoadError(true))
  }, [])

  async function run(task: () => Promise<void>) {
    setError(null)
    try {
      await task()
    } catch (e) {
      setError(message(e))
      throw e
    }
  }

  if (loadError)
    return <ErrorText>Could not load your merchant names.</ErrorText>
  if (!data) return <Text style={styles.muted}>Loading…</Text>

  const on = data.shareMerchantNames
  const revoked = data.sharingRevoked
  const toShare = data.items.filter((i) => i.shareable && !i.optedOut).length
  // Payees of one merchant sit together; each merchant is listed under its first name.
  const merchants = new Map<string, string>()
  data.items.forEach((i) => {
    if (!merchants.has(i.merchantId)) merchants.set(i.merchantId, i.name)
  })
  const groups = [...merchants].map(([merchantId, name]) => ({
    merchantId,
    name,
    items: data.items.filter((x) => x.merchantId === merchantId),
  }))

  const replace = (item: MerchantName) =>
    setData((d) => ({
      ...d!,
      items: d!.items.map((x) => (x.id === item.id ? item : x)),
    }))

  function setSharing(enabled: boolean) {
    const save = () =>
      run(async () => setData(await setMerchantNameSharing(enabled))).catch(
        () => {},
      )
    if (!enabled) return save()
    Alert.alert(
      'Share your merchant names?',
      `${toShare === 1 ? '1 name' : `${toShare} names`} will be shared. ${SHARING_WARNING}`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Share', onPress: save },
      ],
    )
  }

  return (
    <FlatList
      data={groups}
      keyExtractor={(g) => g.merchantId}
      contentContainerStyle={{ gap: space.md, paddingBottom: space.lg }}
      ListHeaderComponent={
        <View style={{ gap: space.sm }}>
          {revoked && (
            <ErrorText>
              Sharing has been turned off for your account because names you
              shared were reported as offensive.
            </ErrorText>
          )}
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: space.md,
              minHeight: TOUCH,
            }}
          >
            <Text style={[styles.text, { flex: 1 }]}>
              Share my merchant names to improve suggestions for other users
            </Text>
            <Switch
              accessibilityLabel="Share my merchant names to improve suggestions for other users"
              value={on}
              disabled={revoked}
              onValueChange={setSharing}
              trackColor={{ true: c.ledger, false: c.rule }}
            />
          </View>
          <Text style={styles.muted}>
            Only the merchant name and the payee it matches are shared — never
            your transactions, amounts or identity.
          </Text>
          {error && <ErrorText>{error}</ErrorText>}
          {data.items.length === 0 && (
            <Text style={styles.muted}>
              You haven’t named any merchants yet. Use “Add merchant” on a
              not-matched transaction on the web.
            </Text>
          )}
        </View>
      }
      renderItem={({ item: g }) => {
        const first = g.items[0]
        const count = g.items.reduce((n, i) => n + i.transactionCount, 0)
        return (
          <Card>
            <Disclosure
              first
              title={g.name}
              badge={g.items.some((i) => i.reported) ? 'Reported' : undefined}
              summary={`${g.items.length === 1 ? '1 payee' : `${g.items.length} payees`} · ${count} ${count === 1 ? 'transaction' : 'transactions'}`}
            >
              <Text style={styles.label} accessibilityRole="header">
                Default categories
              </Text>
              <DirectionDefaults
                name={g.name}
                out={first.categoryId}
                into={first.creditCategoryId}
                categories={categories}
                onChange={(credit, categoryId) =>
                  run(async () => {
                    await setMerchantCategory(g.merchantId, categoryId, credit)
                    const field = credit ? 'creditCategoryId' : 'categoryId'
                    // Names sharing a merchant share its categories.
                    setData((d) => ({
                      ...d!,
                      items: d!.items.map((x) =>
                        x.merchantId === g.merchantId
                          ? { ...x, [field]: categoryId }
                          : x,
                      ),
                    }))
                  }).catch(() => {})
                }
              />
              <Text style={styles.label} accessibilityRole="header">
                {g.items.length === 1 ? 'Payee' : `${g.items.length} payees`}
              </Text>
              {g.items.map((i) => (
                <View key={i.id} style={{ gap: space.sm }}>
                  <RenameField
                    name={i.name}
                    label="merchant"
                    onSave={(name) =>
                      run(async () =>
                        setData(await renameMerchantName(i.id, name)),
                      )
                    }
                  />
                  <Text style={styles.muted}>
                    {i.payee} · {i.transactionCount}{' '}
                    {i.transactionCount === 1 ? 'transaction' : 'transactions'}
                  </Text>
                  <Picker
                    label={`Move ${i.payee} to merchant`}
                    value={null}
                    placeholder="Move to…"
                    disabled={merchants.size < 2}
                    options={[...merchants]
                      .filter(([id]) => id !== i.merchantId)
                      .map(([, name]) => ({ value: name, label: name }))}
                    onChange={(name) =>
                      run(async () =>
                        setData(await renameMerchantName(i.id, name)),
                      ).catch(() => {})
                    }
                  />
                  <ShareRow
                    item={i}
                    on={on}
                    revoked={revoked}
                    onSave={(sharedNow) =>
                      run(async () =>
                        replace(await setMerchantNameShared(i.id, sharedNow)),
                      ).catch(() => {})
                    }
                  />
                </View>
              ))}
            </Disclosure>
          </Card>
        )
      }}
    />
  )
}

function ShareRow({
  item,
  on,
  revoked,
  onSave,
}: {
  item: MerchantName
  on: boolean
  revoked: boolean
  onSave: (shared: boolean) => void
}) {
  const styles = useStyles()
  const c = useColors()
  const hint = !item.shareable
    ? REASONS[item.notShareableReason ?? 'NOT_UPI_MERCHANT']
    : revoked
      ? 'Sharing is off for your account'
      : !on
        ? 'Turn on sharing above'
        : null
  const warn = item.reported || item.notShareableReason === 'NAME_OFFENSIVE'
  return (
    <View style={{ gap: space.xs }}>
      <View
        style={{ flexDirection: 'row', alignItems: 'center', minHeight: TOUCH }}
      >
        <Text style={[styles.text, { flex: 1 }]}>Share this name</Text>
        <Switch
          accessibilityLabel={`${item.reported && item.shared ? 'Unshare' : 'Share'} ${item.name}`}
          accessibilityHint={hint ?? undefined}
          value={on && item.shared}
          disabled={!on || revoked || !item.shareable}
          onValueChange={onSave}
          trackColor={{ true: c.ledger, false: c.rule }}
        />
      </View>
      {(hint || warn) && (
        <Text style={styles.muted}>
          {[hint, warn && SHARING_WARNING].filter(Boolean).join(' ')}
        </Text>
      )}
    </View>
  )
}
