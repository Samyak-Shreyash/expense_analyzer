/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Root .env is shared with backend and compose; '' prefix keeps SERVER_PORT server-side.
  const env = loadEnv(mode, '..', '')
  return {
    plugins: [react()],
    test: { environment: 'jsdom', globals: true }, // globals: RTL cleans up after each test
    server: {
      // Same-origin /api in dev: the backend has no CORS config (see docs/frontend/FRONTEND-STACK.md).
      proxy: {
        '/api':
          env.VITE_BACKEND_URL ?? `http://localhost:${env.SERVER_PORT ?? 8080}`,
      },
    },
  }
})
