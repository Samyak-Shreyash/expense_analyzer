import { useCallback, useEffect, useState } from 'react'

/** Loads data on mount; `reload` refetches (pull-to-refresh, after a change). */
export function useLoad<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // State is set only when the request settles, so the mount effect can call it.
  const fetchData = useCallback(
    () =>
      load().then(
        (d) => {
          setData(d)
          setError(null)
          setLoading(false)
        },
        (e) => {
          setError(e instanceof Error ? e.message : 'Could not load this data.')
          setLoading(false)
        },
      ),
    // `load` is expected to be stable for the screen's lifetime
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  )

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const reload = useCallback(() => {
    setLoading(true)
    return fetchData()
  }, [fetchData])

  return { data, error, loading, reload }
}
