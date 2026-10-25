import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.');
  return {
    plugins: [react()],
    // Same-origin /api in dev, so the browser never needs CORS (see FRONTEND-STACK.md).
    server: {
      proxy: { '/api': env.VITE_PROXY_TARGET || 'http://localhost:8080' },
    },
  };
});
