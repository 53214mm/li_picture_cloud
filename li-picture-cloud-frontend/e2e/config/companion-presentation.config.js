import { defineConfig } from '@playwright/test'
import base from '../../playwright.config.js'

export default defineConfig({
  ...base,
  testDir: '..', testMatch: ['companion-presentation.spec.js', 'companion-body.spec.js'],
  outputDir: '../../test-results/companion-presentation',
  reporter: [['list']],
  webServer: {
    ...base.webServer[1],
    command: 'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 15173 --strictPort',
    cwd: import.meta.dirname + '/../..'
  }
})
