import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: process.env.DATASCALPEL_TEST_BASE_URL ?? 'http://localhost:8887',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Reuse the development environment started with the root start-local-dev.sh.
  // Tests must never silently start a second frontend or backend.
});
