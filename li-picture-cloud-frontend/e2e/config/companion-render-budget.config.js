import { defineConfig } from '@playwright/test'
import base from './companion-quick-chat.config.js'
export default defineConfig({
  ...base,
  testMatch: [...base.testMatch, 'companion-render-budget.spec.js', 'companion-habitat.spec.js'],
  outputDir: '../../test-results/companion-render-budget'
})
