import shared from '../../components/shared.module.css'
import type { Category } from '../../services/finance'
import styles from './CategoriesPage.module.css'

/** The money-out and money-in default categories of a person or merchant. */
export function DirectionDefaults({
  name,
  out,
  into,
  categories,
  onChange,
}: {
  name: string
  out: string | null
  into: string | null
  categories: Category[]
  onChange: (credit: boolean, categoryId: string | null) => void
}) {
  return (
    <span className={styles.defaults}>
      {[
        { credit: false, label: 'Money out', value: out },
        { credit: true, label: 'Money in', value: into },
      ].map(({ credit, label, value }) => (
        <label key={label}>
          <span className={shared.label}>{label}</span>
          <select
            className={shared.input}
            aria-label={`${label} default for ${name}`}
            value={value ?? ''}
            onChange={(e) => onChange(credit, e.target.value || null)}
          >
            <option value="">No default</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      ))}
    </span>
  )
}
