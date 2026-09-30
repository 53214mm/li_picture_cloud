import { defineConfig } from '@playwright/test'
import base from './companion-habitat.config.js'
export default defineConfig({
  ...base,
  testMatch: [...base.testMatch, 'companion-feeding.spec.js'],
  outputDir: '../../test-results/companion-feeding'
})
