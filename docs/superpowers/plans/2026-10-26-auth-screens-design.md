# Auth screens visual design — plan

Branch: `frontend-auth-design` (off `workflow-fix`). Frontend only; no backend or behaviour changes.

## Spec (design decisions — binding)

**Subject.** Expense Analyzer: a personal tool that reads a user's own bank statements
(default currency INR) and shows where the money went. **Audience:** individuals checking
their own finances, not analysts. **Job of these screens:** get the person in quickly and
make a money app feel calm and trustworthy.

**Idea.** The vernacular of a bank statement is the *running balance*: a column of numbers
that climbs and dips over a month. The one memorable element is a single drawn line — a
year of a running balance — rendered as an SVG stroke in ledger-teal ink. Everything else
is quiet: forms sit directly on paper-coloured ground, no cards, no shadows, no gradients.

**Revision after review against defaults.** First draft was a split screen: brand panel left,
form right, form inside a white card with a soft shadow. That is the SaaS-auth template.
Changed to: no card, no shadow; the balance line runs edge-to-edge as a horizon across the
top of the page, the form sits beneath it left-aligned in a narrow column.

### Tokens

| Name | Hex | Use |
|---|---|---|
| `--paper` | `#EEF2EF` | page background (cool sage-grey, not cream) |
| `--surface` | `#FFFFFF` | input fields only |
| `--ink` | `#173F3A` | text (deep green; text contrast on paper ≥ 10:1) |
| `--ink-muted` | `#4F6B66` | secondary text (≥ 4.5:1 on paper) |
| `--ledger` | `#0F766E` | the balance line, primary button, links, focus ring |
| `--rule` | `#C9D4CF` | input borders, the one divider |
| `--debit` | `#9B2C5A` | error text and invalid-field border |

Type:
- Display: **Bricolage Grotesque** (variable), weight 600, for page headings and the wordmark only.
- Body/UI: **Atkinson Hyperlegible Next** (or **Atkinson Hyperlegible** if the Next package is
  unavailable) — designed for legibility, which matters for numbers and form labels.
- Self-hosted via `@fontsource` packages (no third-party font requests from a finance app).
- Scale (rem): 0.875 small · 1 body · 1.25 lead · 2 h1 (2.5 at ≥ 48rem viewport). Body
  line-height 1.55; headings 1.1. Numbers use `font-variant-numeric: tabular-nums`.
- Sentence case everywhere. No all-caps labels, no eyebrows, no arrows in button text.

Layout:
```
┌──────────────────────────────────────────────┐
│ Expense Analyzer                             │  wordmark, top-left
│ ~~~~~~~~~/\~~~~~~/\/\~~~~~\__/~~~~~~~~~~~~~~ │  balance line, full width, ~120px tall
│                                              │
│   Log in                                     │  h1
│   See where your money went.                 │  one lead line
│   [ Email            ]                       │
│   [ Password         ]                       │  column max-width 22rem,
│   [ Log in ]                                 │  left edge aligned with wordmark
│   No account? Register                       │
└──────────────────────────────────────────────┘
```
Content left-aligned; page padding 1.5rem (3rem ≥ 48rem).

Principles:
1. One bold thing (the line); everything else recedes.
2. Accessibility floor: visible 3px `--ledger` focus outline with 2px offset on every
   interactive element; labels always visible above inputs; errors announced (`role="alert"`)
   and tied to the form; respects `prefers-reduced-motion`.
3. Motion: only the balance line draws in once on page load (stroke-dashoffset, 1.2s,
   ease-out). With reduced motion it appears drawn. Nothing else animates.
4. Copy is plain and specific; buttons name the action ("Log in", "Create account", "Log out").

## Global constraints

- Edit only `frontend/`. Do not change `frontend/src/services/api.ts` or auth behaviour in
  `AuthContext.tsx` / `RequireAuth.tsx`; tasks restyle and restructure markup only.
- Plain CSS (global `index.css` for tokens/base, CSS Modules `*.module.css` per component).
  No CSS framework, no component library.
- Every task ends green on `cd frontend && npm run ci` (ESLint + Prettier check + `tsc -b` + Vite build).
- Exact token values above are verbatim requirements.

## Task 1: Tokens, fonts and base styles

Files: `frontend/package.json` (+ lockfile), `frontend/src/main.tsx`, `frontend/src/index.css`.

1. Install `@fontsource-variable/bricolage-grotesque` and `@fontsource-variable/atkinson-hyperlegible-next`
   (check with `npm view <pkg> version`; if the Next package does not exist use
   `@fontsource/atkinson-hyperlegible` weights 400 and 700).
2. Import the fonts once in `main.tsx`, before `./index.css`.
3. Rewrite `index.css`: the seven tokens as CSS custom properties on `:root` with the exact hex
   values; `--font-display` and `--font-body` stacks (fallbacks `system-ui, sans-serif`); body
   uses `--paper` background, `--ink` colour, body font, 1rem/1.55; `h1` uses display font,
   weight 600, 2rem/1.1, 2.5rem at `min-width: 48rem`; `a` coloured `--ledger`, underline;
   global `:focus-visible { outline: 3px solid var(--ledger); outline-offset: 2px; }`;
   `@media (prefers-reduced-motion: reduce)` that sets `animation: none` and `transition: none`
   on `*`.
4. Verify: `npm run ci` passes.

## Task 2: Auth layout with the balance line; restyle login and register

Files: new `frontend/src/components/AuthLayout.tsx`, `frontend/src/components/AuthLayout.module.css`,
new `frontend/src/components/BalanceLine.tsx`, edit `LoginPage.tsx`, `RegisterPage.tsx`,
new `frontend/src/features/auth/AuthForm.module.css`. Remove `frontend/src/components/.gitkeep`.

1. `BalanceLine`: decorative inline SVG (`aria-hidden="true"`, `focusable="false"`),
   `viewBox="0 0 1200 120"`, `preserveAspectRatio="none"`, width 100%, height 120px.
   One `<path>`: a hard-coded polyline of 25 points (x every 50 units) describing a plausible
   year of a running balance — climbs on payday-like steps, dips gradually, never touches the
   top/bottom 10 units. Stroke `var(--ledger)`, width 2.5, `vector-effect: non-scaling-stroke`,
   no fill, round joins. Draw-in animation via `stroke-dasharray`/`stroke-dashoffset` keyframes
   (1.2s ease-out, once); reduced motion shows it fully drawn.
2. `AuthLayout({ title, lead, children })`: page wrapper with the wordmark "Expense Analyzer"
   (display font, 1.25rem, `--ink`) top-left, `<BalanceLine />` full width beneath it, then a
   `<main>` column (max-width 22rem, left edge aligned with the wordmark) containing `<h1>{title}</h1>`,
   a `<p>` lead in `--ink-muted` at 1.25rem, and `children`. Padding per spec.
3. `AuthForm.module.css`: vertical form with 1.25rem gap; labels block-level, 0.875rem, weight
   700, above their input; inputs full width, 2.75rem tall, `--surface` background, 1px `--rule`
   border, 6px radius, 1rem text; invalid inputs (`[aria-invalid="true"]`) get a `--debit`
   border; error paragraph in `--debit`; primary button full width, 2.75rem, `--ledger`
   background, white text, weight 700, 6px radius, disabled at 0.6 opacity with `cursor: wait`;
   footer line ("No account? Register") at 0.875rem in `--ink-muted`.
4. `LoginPage`: render inside `AuthLayout` with title "Log in" and lead "See where your money went.".
   Keep all existing logic, field names, `autoComplete`, `minLength`, `required`. Button text
   "Log in"; while busy "Logging in…". When an error is shown, set `aria-invalid="true"` on both
   inputs and link the error with `aria-describedby`.
5. `RegisterPage`: same layout, title "Create your account", lead "It takes under a minute.".
   Keep all existing logic. Button "Create account"; busy "Creating account…". Password hint
   "At least 8 characters" moves out of the label into a `<p>` (0.875rem, `--ink-muted`)
   linked via `aria-describedby`. Error handling as in login.
6. Verify: `npm run ci` passes.

## Task 3: Dashboard shell and forbidden page

Files: `frontend/src/features/dashboard/DashboardPage.tsx`, new
`frontend/src/features/dashboard/DashboardPage.module.css`, `frontend/src/App.tsx`
(forbidden route markup only).

1. Dashboard header: wordmark left; right side the signed-in email (0.875rem, `--ink-muted`,
   omitted until loaded) and a "Log out" text button styled as a link (`--ledger`, underline),
   separated from the content by one 1px `--rule` line. Keep existing data loading and logout logic.
2. Dashboard body: `<h1>Your spending</h1>` and an empty state: paragraph
   "No statements yet. Statement upload is coming in the next update; your spending will
   appear here once you add one." in `--ink-muted`, max-width 34rem.
3. Forbidden page (inline element in `App.tsx`): render inside `AuthLayout` with title
   "You can't open this page" and lead "Your account doesn't have access to it.", plus a link
   "Go to your dashboard" to `/dashboard`. Import `AuthLayout` from Task 2.
4. Verify: `npm run ci` passes.
