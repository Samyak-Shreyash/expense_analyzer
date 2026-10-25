import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [react()],
    server: {
      // Same-origin /api in dev: the backend has no CORS config (see docs/frontend/FRONTEND-STACK.md).
      proxy: { '/api': env.VITE_BACKEND_URL ?? 'http://localhost:8080' },
    },
  }
})
