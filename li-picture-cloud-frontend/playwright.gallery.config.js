import { defineConfig } from '@playwright/test'

// Production Gallery only, with API fixtures. Never starts or changes the backend.
// Run npm run build before the production-default project.
export default defineConfig({
  testDir: './e2e',
  testMatch: 'gallery.spec.js',
  timeout: 30_000,
  workers: 2,
  reporter: [['list'], ['json', { outputFile: 'test-results/gallery-results.json' }]],
  use: { browserName: 'chromium', trace: 'retain-on-failure' },
  projects: [
    { name: 'development', use: { baseURL: 'http://127.0.0.1:15278' } },
    { name: 'production-default', use: { baseURL: 'http://127.0.0.1:15279' } }
  ],
  webServer: [
    { command: 'npm run dev -- --host 127.0.0.1 --port 15278 --strictPort', url: 'http://127.0.0.1:15278', reuseExistingServer: false },
    { command: 'npm run preview -- --host 127.0.0.1 --port 15279 --strictPort', url: 'http://127.0.0.1:15279', reuseExistingServer: false }
  ]
})
