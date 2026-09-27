import { defineConfig } from '@playwright/test'
import base from './companion-interaction.config.js'
export default defineConfig({
  ...base,
  testMatch: [...base.testMatch, 'companion-habitat.spec.js'],
  outputDir: '../../test-results/companion-habitat'
})
