import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL, URL } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const navigationPath = join(root, 'src/constants/navigation.js')
const navSource = readFileSync(join(root, 'src/components/NavBar.vue'), 'utf8')
const routerSource = readFileSync(join(root, 'src/router/index.js'), 'utf8')

test('builds user and admin navigation from one model', async () => {
  assert.equal(existsSync(navigationPath), true, 'navigation model should exist')
  const { buildNavigationGroups } = await import(pathToFileURL(navigationPath))
  const user = buildNavigationGroups({ isLoggedIn: true, isAdmin: false })
  const admin = buildNavigationGroups({ isLoggedIn: true, isAdmin: true })
  assert.deepEqual(user.map(group => group.id), ['browse', 'workspace', 'account'])
  assert.deepEqual(admin.map(group => group.id), ['browse', 'workspace', 'admin', 'account'])
  assert.equal(user.flatMap(group => group.items).some(item => item.to === '/admin/users'), false)
  assert.equal(admin.flatMap(group => group.items).some(item => item.to === '/admin/users'), true)
  assert.equal(user.flatMap(group => group.items).some(item => item.to === '/admin/companion-feed-runs'), false)
  assert.equal(admin.flatMap(group => group.items).some(item => item.to === '/admin/companion-feed-runs'), true)
  assert.match(routerSource, /path: '\/admin\/companion-feed-runs'/)
  assert.match(routerSource, /path: '\/admin\/companion-feed-runs\/:runId'/)
  assert.equal(user.flatMap(group => group.items).some(item => item.to === '/companion'), true)
  assert.equal(buildNavigationGroups({ isLoggedIn: false, isAdmin: false })
    .flatMap(group => group.items).some(item => item.to === '/companion'), false)
  assert.equal(buildNavigationGroups({
    isLoggedIn: true, isAdmin: false, companionEnabled: false
  }).flatMap(group => group.items).some(item => item.to === '/companion'), false)
  assert.equal(buildNavigationGroups({
    isLoggedIn: true, isAdmin: true, companionEnabled: false
  }).flatMap(group => group.items).some(item => item.to === '/admin/companion-feed-runs'), false)
})

test('public navbar uses the public model; app drawer behavior is covered by shell browser tests', () => {
  assert.match(navSource, /buildPublicNavigation/)
  assert.match(navSource, /aria-label="官网导航"/)
  assert.doesNotMatch(navSource, /buildNavigationGroups/)
})
