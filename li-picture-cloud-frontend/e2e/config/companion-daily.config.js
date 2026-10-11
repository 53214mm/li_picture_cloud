import { defineConfig } from '@playwright/test'
import base from './companion-presentation.config.js'

export default defineConfig({
  ...base,
  testMatch: ['companion-quick-chat.spec.js', 'companion-chat-mode.spec.js', 'companion-habitat.spec.js'],
  outputDir: '../../test-results/companion-daily'
})
