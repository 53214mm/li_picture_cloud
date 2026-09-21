import { defineConfig } from '@playwright/test'

// Frontend-only checks with explicit API fixtures; never starts the dirty backend.
// Run npm run build first to exercise the default production feature-flag boundary.
export default defineConfig({
  testDir: './e2e',
  testMatch: 'shell.spec.js',
  timeout: 30000,
  workers: 2,
  use: { browserName: 'chromium', trace: 'retain-on-failure' },
  projects: [
    { name: 'development', use: { baseURL: 'http://127.0.0.1:15274' } },
    { name: 'production-default', use: { baseURL: 'http://127.0.0.1:15275' } }
  ],
  webServer: [
    { command: 'npm run dev -- --host 127.0.0.1 --port 15274 --strictPort', url: 'http://127.0.0.1:15274', reuseExistingServer: false },
    { command: 'npm run preview -- --host 127.0.0.1 --port 15275 --strictPort', url: 'http://127.0.0.1:15275', reuseExistingServer: false }
  ]
})
