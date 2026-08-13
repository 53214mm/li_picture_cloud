import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:15173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: [
    {
      command: 'node scripts/start-e2e-backend.mjs',
      url: 'http://127.0.0.1:18124/api/v3/api-docs',
      timeout: 180_000,
      reuseExistingServer: false
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
      url: 'http://127.0.0.1:15173',
      timeout: 60_000,
      reuseExistingServer: false,
      env: {
        VITE_API_PROXY_TARGET: 'http://127.0.0.1:18124',
        VITE_COMPANION_ENABLED: 'true'
      }
    }
  ],
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
})
