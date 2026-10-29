# Expense Analyzer — Frontend

React + TypeScript + Vite. Stack decision: [FRONTEND-STACK.md](../docs/frontend/FRONTEND-STACK.md).

## Run locally

Requires Node 22+.

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api to the backend
```

Start the backend separately (`cd ../backend && ./gradlew bootRun`, port 8080).

## Configuration

Copy `.env.example` to `.env.local` to override:

- `VITE_API_BASE_URL` — base URL for API calls (default `/api`).
- `VITE_BACKEND_URL` — where the dev server proxies `/api` (default `http://localhost:8080`).

## Scripts

| Script                            | Does                                       |
| --------------------------------- | ------------------------------------------ |
| `npm run dev`                     | Dev server                                 |
| `npm run build`                   | Type-check and production build to `dist/` |
| `npm run lint`                    | ESLint                                     |
| `npm run format` / `format:check` | Prettier write / check                     |
| `npm run ci`                      | lint + format check + build (use in CI)    |

## Layout

`src/features/` feature modules, `src/components/` shared UI, `src/hooks/` shared hooks,
`src/services/` API access.

## Design tokens

Colors and fonts are CSS custom properties in `src/index.css`. Use the variable, never the hex value.

### Color

Contrast is measured against the background each color is used on. Text needs 4.5:1 (WCAG AA).

| Token               | Value     | Use                                 | Contrast                                       |
| ------------------- | --------- | ----------------------------------- | ---------------------------------------------- |
| `--paper`           | `#eef2ef` | Page background                     | —                                              |
| `--surface`         | `#ffffff` | Cards, inputs                       | —                                              |
| `--ink`             | `#173f3a` | Body text, headings                 | 10.3 on paper, 11.6 on surface                 |
| `--ink-muted`       | `#4f6b66` | Secondary text, input borders       | 5.1 on paper, 5.8 on surface                   |
| `--ledger`          | `#0f766e` | Links, money in, focus ring         | 4.8 on paper, 5.5 on surface                   |
| `--debit`           | `#9b2c5a` | Money out, destructive buttons      | 6.4 on paper, 7.2 on surface                   |
| `--rule`            | `#c9d4cf` | Dividers and chart tracks only      | 1.5: decorative, never text or control borders |
| `--night`           | `#0e3b36` | Dark panels (overview hero, charts) | —                                              |
| `--on-night`        | `#e6f2ee` | Text on `--night`                   | 10.8                                           |
| `--credit-on-night` | `#5fd3bc` | Money in on `--night`               | 6.8                                            |
| `--debit-on-night`  | `#f49ac1` | Money out on `--night`              | 6.0                                            |

Never use color alone to carry meaning: money in and out also differ by sign or label.

### Typography

| Token            | Font                           | Use                    |
| ---------------- | ------------------------------ | ---------------------- |
| `--font-display` | Bricolage Grotesque (variable) | `h1`, headline numbers |
| `--font-body`    | Atkinson Hyperlegible Next     | Everything else        |

Sizes in `rem`: body `1rem` (line height 1.55), small/labels `0.875rem`, section headings
`1.25rem`, `h1` `2rem` (`2.5rem` from 48rem wide). SVG chart labels use `px`.

### Spacing and layout

- Spacing steps: `0.25rem`, `0.5rem`, `0.75rem`, `1rem`, `1.5rem`, `2rem`, `3rem`. Pick from these.
- Page gutter: `--pad` on the layout (`1.25rem` on phones, `3rem` from 48rem wide).
- One breakpoint, `48rem` (768px). Styles are mobile-first: base rules target 375px, `@media (min-width: 48rem)` adds.
- Wide tables sit in `.tableWrap` (horizontal scroll) so the page itself never scrolls sideways.

### Accessibility rules

- Every interactive element shows the `:focus-visible` ring (3px `--ledger`).
- Hide visually but keep for screen readers with `.visuallyHidden`. It doesn't work on a
  `<table>` directly (tables ignore `width`), so wrap the table in a hidden `div`.
- Inline confirmations move focus to the safe choice, close on Escape, and return focus to the
  button that opened them.
- Animations respect `prefers-reduced-motion`.
