import { defineConfig } from '@playwright/test'
import base from './companion-render-budget.config.js'

export default defineConfig({
  ...base,
  testMatch: [...base.testMatch, 'product-copy.spec.js'],
  outputDir: '../../test-results/product-copy'
})
