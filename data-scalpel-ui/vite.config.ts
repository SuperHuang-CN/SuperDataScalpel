import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 8887,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.BACKEND_ORIGIN ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
    testTimeout: 20_000,
    maxWorkers: 2,
  },
});
