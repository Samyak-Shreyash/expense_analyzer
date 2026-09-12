---
applyTo: "frontend/**/*.{ts,tsx}"
---

# Frontend TypeScript — Expense Analyzer

## Language & Config

- **TypeScript strict mode on.** `strict: true`, `noUncheckedIndexedAccess: true`, `noImplicitOverride: true`, `exactOptionalPropertyTypes: true`.
- **No `any`.** Use `unknown` + explicit narrowing when the type is genuinely unknown.
- **No non-null assertion (`!`)** except when you've just guarded and can prove it — prefer a narrow cast or early return.
- **`interface` for object shapes**, `type` for unions, intersections, mapped types.
- **`readonly`** on props, arrays, and tuples by default.
- **Named exports** for everything except Next.js pages/layouts (`app/**/page.tsx`, `layout.tsx`).

## React & Next.js

- **Next.js App Router.** Server Components by default; `"use client"` only when needed (state, effects, event handlers, browser APIs).
- **Function components only.** No class components.
- **Explicit prop interfaces**:
```tsx
interface TransactionListProps {
  readonly accountId: string;
  readonly onSelect?: (id: string) => void;
}

export function TransactionList({ accountId, onSelect }: TransactionListProps) { ... }
```
- **No `React.FC`.** No `React.PropsWithChildren` — declare `children?: React.ReactNode` explicitly.
- **Server Components** fetch via server-side API client (never `fetch` in a client component for initial data).
- **Client Components** fetch via TanStack Query.
- **Suspense boundaries** around any async Server Component or lazy-loaded Client Component.
- **Error boundaries** (`error.tsx`) at the route segment level.

## Data Fetching

- **All server state via TanStack Query.** No ad-hoc `useEffect` + `fetch`.
- **Query key factories** in `lib/api/queryKeys.ts`:
```ts
export const transactionKeys = {
  all: ["transactions"] as const,
  list: (accountId: string) => [...transactionKeys.all, "list", accountId] as const,
  detail: (id: string) => [...transactionKeys.all, "detail", id] as const,
};
```
- **Typed API client** in `lib/api/client.ts` — never call `fetch` directly from components.
- **Mutate + invalidate**:
```ts
const mutation = useMutation({
  mutationFn: api.transactions.create,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: transactionKeys.all }),
});
```

## State Management

- **Server state** → TanStack Query.
- **URL state** → `useSearchParams` / `useRouter` (Next.js). Filters, pagination, and sorting live in the URL, not in Zustand.
- **Client-only UI state** → Zustand stores in `lib/stores/`. One store per feature. Keep them small.
- **Form state** → React Hook Form. Never roll your own form state.

## Styling

- **Tailwind-first.** No inline `style={{}}` except for dynamic values that Tailwind can't express (e.g., a computed chart color).
- **`cn()` utility** (clsx + tailwind-merge) for conditional classes.
- **shadcn/ui primitives** for buttons, inputs, dialogs, tables, etc. **Never create a new primitive** when one exists.
- **No CSS modules, no styled-components, no emotion.**
- **Dark mode** via `class` strategy on `<html>`; use `dark:` variants.

## Forms & Validation

- **React Hook Form + Zod** for all forms.
- **One Zod schema per form**, colocated. Use `z.infer<typeof schema>` for the form type.
- **Server errors** map back to form fields via `setError`.
- **Never** validate in `onChange` with custom logic — use the schema.

```tsx
const schema = z.object({
  amount: z.coerce.number().positive(),
  description: z.string().min(1).max(200),
  occurredAt: z.coerce.date(),
});
type FormValues = z.infer<typeof schema>;
```

## Type Safety Across the Boundary

- **API types are generated from OpenAPI** (`openapi-typescript`) into `lib/api/schema.d.ts`. Never hand-write request/response shapes that mirror the backend.
- **Money** is `{ amount: string; currency: string }` from the API (backend serializes `BigDecimal` as string). Parse to a decimal library (`decimal.js`) for math. **Never `parseFloat` for money.**
- **Dates** from the API are ISO-8601 strings. Parse with `new Date(...)` or `Temporal` (when stable). Render with `Intl.DateTimeFormat`.
- **UUIDs** are strings — don't invent a branded type unless you also enforce it at the edge.

## Accessibility

- **Semantic HTML first.** `<button>`, not `<div onClick>`.
- **Every form input has a label.** Use `htmlFor` + `id`.
- **Keyboard navigation** must work for all interactive components.
- **`aria-live`** for async status updates (upload progress, errors).
- **Color alone never conveys state** — pair with icon/text.

## Testing

- **Vitest + React Testing Library** for components. Test behavior, not implementation.
- **Query by role/label/text** — `screen.getByRole("button", { name: /save/i })`.
- **`data-testid` only** when semantic queries aren't viable.
- **Playwright** for E2E: auth, CSV upload, dashboard render, budget CRUD.
- **MSW** for API mocking in component tests.

## Performance

- **Server Components** for data-heavy pages.
- **`next/image`** for all images.
- **Dynamic import** (`next/dynamic`) for heavy client-only components (charts, editors).
- **Memoize** with `useMemo`/`useCallback` only when profiling shows a need — not by default.
- **Virtualize** long lists (`@tanstack/react-virtual`).

## Security

- **Never render** `dangerouslySetInnerHTML` with user content.
- **Never store tokens in `localStorage`.** Use httpOnly cookies set by the backend.
- **CSRF:** rely on backend `SameSite=Lax` cookies + the backend's CSRF token if enabled.
- **No secrets in client code.** Anything in `NEXT_PUBLIC_*` is public.
- **Sanitize** any user-supplied URLs before rendering as `href`.

## File & Folder Conventions

```
frontend/
├── app/                      # Next.js routes
│   ├── (auth)/
│   ├── (dashboard)/
│   └── api/                  # Route handlers (proxy only, no business logic)
├── components/
│   ├── ui/                   # shadcn primitives (do not edit generated files)
│   └── features/             # Feature-specific components
├── lib/
│   ├── api/                  # client.ts, queryKeys.ts, schema.d.ts
│   ├── stores/               # Zustand stores
│   ├── hooks/                # custom hooks
│   └── utils/                # cn(), formatters, etc.
└── tests/
```

- **One component per file.** File name matches component name (`TransactionList.tsx`).
- **Colocate** tests next to components (`TransactionList.test.tsx`) or under `tests/` — pick one and stay consistent (prefer colocated).
- **Index files** (`index.ts`) only for re-exporting a public module surface. Never barrel-export everything.

## What Copilot Must Never Generate

- `any`, `as unknown as X` casts to silence the compiler.
- `useEffect` for data fetching when TanStack Query fits.
- `fetch` calls inside components — always through `lib/api/client.ts`.
- Inline styles or new CSS files.
- New UI primitives when shadcn/ui has one.
- `dangerouslySetInnerHTML` with user content.
- `localStorage` for auth tokens.
- `parseFloat` for money.
- Default exports for components (except Next.js pages/layouts).