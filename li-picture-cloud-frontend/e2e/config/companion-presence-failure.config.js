import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  testDir: fileURLToPath(new URL('..', import.meta.url)),
  testMatch: 'companion-presence-failure.spec.js',
  outputDir: fileURLToPath(new URL('../../test-results/presence-failure', import.meta.url)),
  workers: 1,
  use: { baseURL: process.env.PRESENCE_TEST_BASE_URL || 'http://localhost:5173',
    browserName: 'chromium', channel: process.env.PLAYWRIGHT_CHANNEL || undefined, headless: true }
})
