import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const css = readFileSync(join(root, 'src/style.css'), 'utf8')
const base = readFileSync(join(root, 'src/styles/base.css'), 'utf8')
const motion = readFileSync(join(root, 'src/styles/motion.css'), 'utf8')

test('defines phone and tablet responsive foundations', () => {
  assert.match(css, /@media \(max-width: 1024px\)/)
  assert.match(css, /@media \(max-width: 768px\)/)
  assert.match(css, /@media \(max-width: 480px\)/)
  assert.match(css, /min-height:\s*44px/)
  assert.match(base, /:focus-visible/)
  assert.match(css, /safe-area-inset/)
  assert.match(motion, /prefers-reduced-motion:\s*reduce/)
})
