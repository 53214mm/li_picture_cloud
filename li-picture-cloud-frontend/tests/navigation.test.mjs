import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL, URL } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const navigationPath = join(root, 'src/constants/navigation.js')
const navSource = readFileSync(join(root, 'src/components/NavBar.vue'), 'utf8')

test('builds user and admin navigation from one model', async () => {
  assert.equal(existsSync(navigationPath), true, 'navigation model should exist')
  const { buildNavigationGroups } = await import(pathToFileURL(navigationPath))
  const user = buildNavigationGroups({ isLoggedIn: true, isAdmin: false })
  const admin = buildNavigationGroups({ isLoggedIn: true, isAdmin: true })
  assert.deepEqual(user.map(group => group.id), ['browse', 'workspace', 'account'])
  assert.deepEqual(admin.map(group => group.id), ['browse', 'workspace', 'admin', 'account'])
  assert.equal(user.flatMap(group => group.items).some(item => item.to === '/admin/users'), false)
  assert.equal(admin.flatMap(group => group.items).some(item => item.to === '/admin/users'), true)
})

test('mobile drawer exposes accessible state and close controls', () => {
  assert.match(navSource, /aria-expanded/)
  assert.match(navSource, /aria-controls="mobile-navigation"/)
  assert.match(navSource, /@keydown\.esc/)
  assert.match(navSource, /class="mobile-nav-overlay"/)
})
