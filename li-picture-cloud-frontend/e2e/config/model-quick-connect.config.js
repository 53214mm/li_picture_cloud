import { defineConfig } from '@playwright/test'
import base from '../../playwright.config.js'

// Quick-connect and chat-mode UI with API fixtures; no Java/Redis processes.
export default defineConfig({
  ...base,
  testDir: '..', testMatch: ['model-quick-connect.spec.js', 'companion-chat-mode.spec.js'],
  outputDir: '../../test-results/model-quick-connect',
  reporter: [['list']],
  webServer: {
    ...base.webServer[1],
    command: 'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 15173 --strictPort',
    cwd: import.meta.dirname + '/../..'
  }
})
