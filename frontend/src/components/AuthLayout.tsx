import type { ReactNode } from 'react'
import { BalanceLine } from './BalanceLine'
import styles from './AuthLayout.module.css'

type Props = { title: string; lead: string; children: ReactNode }

export function AuthLayout({ title, lead, children }: Props) {
  return (
    <div className={styles.page}>
      <p className={styles.wordmark}>Expense Analyzer</p>
      <BalanceLine />
      <main className={styles.main}>
        <h1>{title}</h1>
        <p className={styles.lead}>{lead}</p>
        {children}
      </main>
    </div>
  )
}
