import { defineConfig } from '@playwright/test'
import base from '../../playwright.config.js'

// Rehearsal history clearing and site presence UI with API fixtures; no Java/Redis processes.
export default defineConfig({
  ...base,
  testDir: '..', testMatch: ['companion-rehearsal-clear.spec.js', 'companion-site-presence.spec.js'],
  outputDir: '../../test-results/companion-rehearsal-clear',
  reporter: [['list']],
  webServer: {
    ...base.webServer[1],
    command: 'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 15173 --strictPort',
    cwd: import.meta.dirname + '/../..'
  }
})
