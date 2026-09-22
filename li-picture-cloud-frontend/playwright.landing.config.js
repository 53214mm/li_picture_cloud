import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: 'landing.spec.js',
  timeout: 30_000,
  workers: 2,
  use: { browserName: 'chromium', trace: 'retain-on-failure' },
  projects: [
    { name: 'development', use: { baseURL: 'http://127.0.0.1:15276' } },
    { name: 'production-default', use: { baseURL: 'http://127.0.0.1:15277' } }
  ],
  webServer: [
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15276 --strictPort',
      url: 'http://127.0.0.1:15276',
      reuseExistingServer: false
    },
    {
      command: 'npm run preview -- --host 127.0.0.1 --port 15277 --strictPort',
      url: 'http://127.0.0.1:15277',
      reuseExistingServer: false
    }
  ]
})
