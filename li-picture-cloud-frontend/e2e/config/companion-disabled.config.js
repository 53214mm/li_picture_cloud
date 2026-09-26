import { defineConfig, devices } from '@playwright/test'
import { tmpdir } from 'node:os'
import path from 'node:path'

process.env.R05_DISABLED_BUILD = 'true'
const output = path.join(tmpdir(), 'li-picture-cloud-r05-disabled')
export default defineConfig({
  testDir: '..', testMatch: 'companion-disabled.spec.js', workers: 1,
  outputDir: '../../test-results/companion-disabled',
  use: { ...devices['Desktop Chrome'], baseURL: 'http://127.0.0.1:15175' },
  webServer: {
    command: `node node_modules/vite/bin/vite.js build --outDir "${output}" && node node_modules/vite/bin/vite.js preview --outDir "${output}" --host 127.0.0.1 --port 15175 --strictPort`,
    cwd: path.resolve(import.meta.dirname, '../..'),
    url: 'http://127.0.0.1:15175', timeout: 120000, reuseExistingServer: false,
    env: { VITE_COMPANION_ENABLED: 'false' }
  }
})
