import { defineConfig } from '@playwright/test'
import base from './companion-animation.config.js'
export default defineConfig({
  ...base,
  testMatch: [...base.testMatch, 'companion-interaction.spec.js', 'shell.spec.js'],
  outputDir: '../../test-results/companion-interaction'
})
