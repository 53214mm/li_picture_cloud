import { defineConfig } from '@playwright/test'
import base from './product-copy.config.js'

export default defineConfig({
  ...base,
  testMatch: [...base.testMatch, 'space-recovery.spec.js', 'gallery.spec.js', 'shell.spec.js', 'auth-lifecycle.spec.js'],
  outputDir: '../../test-results/space-recovery'
})
