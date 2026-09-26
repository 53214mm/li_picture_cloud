import { defineConfig } from '@playwright/test'
import base from '../../playwright.config.js'

// Body tests supply explicit API fixtures and need no Java/Redis processes.
export default defineConfig({
  ...base,
  testDir: '..', testMatch: 'companion-body.spec.js',
  outputDir: '../../test-results/companion-body',
  reporter: [['list']],
  webServer: { ...base.webServer[1], cwd: import.meta.dirname + '/../..' }
})
