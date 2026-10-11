import { defineConfig } from '@playwright/test'
import base from './companion-disposition.config.js'
export default defineConfig({
  ...base,
  testMatch: [...base.testMatch, 'companion-quick-chat.spec.js', 'companion-interaction.spec.js'],
  outputDir: '../../test-results/companion-quick-chat'
})
