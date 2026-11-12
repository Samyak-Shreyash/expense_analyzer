/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Root .env is shared with backend and compose; '' prefix keeps SERVER_PORT server-side.
  const env = loadEnv(mode, '..', '')
  return {
    plugins: [react()],
    test: {
      environment: 'jsdom',
      globals: true, // globals: RTL cleans up after each test
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/**/*.test.{ts,tsx}', 'src/main.tsx'],
        reporter: ['text-summary', 'html', 'lcov'],
        // Ratchet: baseline 2026-11-12. Raise as coverage grows; never lower.
        thresholds: { statements: 80, branches: 72, functions: 75, lines: 80 },
      },
    },
    server: {
      // Only the shared package: the repo root holds .env, which the dev server must not serve.
      fs: { allow: ['.', '../packages'] },
      // Same-origin /api in dev: the backend has no CORS config (see docs/frontend/FRONTEND-STACK.md).
      proxy: {
        '/api':
          env.VITE_BACKEND_URL ?? `http://localhost:${env.SERVER_PORT ?? 8080}`,
      },
    },
  }
})
