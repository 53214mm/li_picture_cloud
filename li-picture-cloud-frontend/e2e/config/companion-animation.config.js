import { defineConfig } from '@playwright/test'
import base from './companion-presentation.config.js'

export default defineConfig({
  ...base,
  testMatch: ['companion-animation.spec.js', 'companion-presentation.spec.js', 'companion-body.spec.js'],
  outputDir: '../../test-results/companion-animation'
})
