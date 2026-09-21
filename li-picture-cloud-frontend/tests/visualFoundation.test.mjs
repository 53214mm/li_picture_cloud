import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import test from 'node:test'

const root = process.cwd()
const baseCss = readFileSync(join(root, 'src/styles/base.css'), 'utf8')

test('headings inherit the text color of their surface', () => {
  const headingRule = baseCss.match(/:where\(h1, h2, h3\)\s*\{([^}]*)\}/)

  assert.ok(headingRule, 'the shared heading baseline should exist')
  assert.doesNotMatch(
    headingRule[1],
    /(?:^|;)\s*color\s*:/,
    'the heading baseline must not override semantic or dark surface colors'
  )
})
