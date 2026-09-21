import test from 'node:test'
import assert from 'node:assert/strict'
import { routeAccess, loginDestination } from '../src/utils/shellAccess.js'
import { buildShellBreadcrumb, shellMeta } from '../src/constants/shell.js'
import { buildAppNavigation, buildPublicNavigation } from '../src/constants/navigation.js'

test('protected content cannot mount before auth is ready, including transient failures', () => {
  const meta = { requiresAuth: true }
  assert.equal(routeAccess(meta, { authReady: false }), 'loading')
  assert.equal(routeAccess(meta, { authReady: false, authBootstrapError: new Error() }), 'auth-error')
  assert.equal(routeAccess(meta, { authReady: true, isLoggedIn: false }), 'login')
  assert.equal(routeAccess(meta, { authReady: true, isLoggedIn: true }), 'allow')
  assert.equal(routeAccess({ ...meta, requiresAdmin: true }, { authReady: true, isLoggedIn: true }), 'forbidden')
  assert.equal(routeAccess({ ...meta, requiresAdmin: true }, { authReady: true, isLoggedIn: true, isAdmin: true }), 'allow')
  assert.equal(routeAccess({ layout: 'public' }, { authReady: false }), 'allow')
})

test('login destination accepts resolved local pages only and preserves queries', () => {
  const resolve = value => ({ name: value.startsWith('/login') ? 'login' : value.startsWith('/register') ? 'register' : value.startsWith('/missing') ? 'unavailable' : 'gallery', matched: value.startsWith('/unknown') ? [] : [{}] })
  for (const value of [undefined, [], '//evil.test', 'https://evil.test', '/\\evil.test', '/%2f%2fevil.test', '/gallery\n', '/login?redirect=/gallery', '/register', '/missing', '/unknown']) {
    assert.equal(loginDestination(value, resolve), '/space/my', String(value))
  }
  assert.equal(loginDestination('/gallery?q=forest#images', resolve), '/gallery?q=forest#images')
  assert.equal(loginDestination('/upload?spaceId=1', resolve), '/upload?spaceId=1')
})

test('route metadata separates public, adaptive, protected and admin legacy workspaces', () => {
  assert.equal(shellMeta.home.layout, 'public')
  assert.equal(shellMeta.gallery.layout, 'adaptive')
  assert.equal(shellMeta.gallery.workspace, 'fluid')
  for (const name of ['my-space', 'space-create', 'space-detail', 'spaces', 'space-analyze', 'picture-upload', 'model-gateway', 'recipe-workshop', 'companion']) {
    assert.equal(shellMeta[name].requiresAuth, true, name)
    assert.equal(shellMeta[name].frame, 'legacy', name)
  }
  for (const name of ['admin-pictures', 'admin-users', 'admin-companion-feed-runs', 'admin-companion-feed-run-detail']) assert.equal(shellMeta[name].requiresAdmin, true)
})

test('app navigation groups tools and management, public navigation does not expose admin directory', () => {
  const regular = buildAppNavigation({ isAdmin: false, companionEnabled: false })
  assert.deepEqual(regular.map(group => group.id), ['spaces', 'gallery'])
  const admin = buildAppNavigation({ isAdmin: true, companionEnabled: true })
  assert.deepEqual(admin.map(group => group.id), ['spaces', 'gallery', 'tools', 'admin'])
  assert.equal(admin.find(group => group.id === 'tools').items.length, 2)
  assert.equal(admin.find(group => group.id === 'admin').items.length, 3)
  assert.equal(admin.find(group => group.id === 'spaces').to, undefined)
  assert.deepEqual(buildPublicNavigation(true).map(item => item.to), ['/', '/gallery', '/space/my'])
})

test('breadcrumbs use shell sections instead of a hard-coded space root', () => {
  assert.deepEqual(buildShellBreadcrumb(shellMeta['my-space']), [
    { label: '空间', to: '/space/my' },
    { label: '我的空间' }
  ])
  assert.deepEqual(buildShellBreadcrumb(shellMeta.gallery), [{ label: '图库' }])
  assert.deepEqual(buildShellBreadcrumb(shellMeta['picture-detail']), [
    { label: '图库', to: '/gallery' },
    { label: '图片详情' }
  ])
  assert.deepEqual(buildShellBreadcrumb(shellMeta['model-gateway']), [
    { label: '工具' },
    { label: '模型连接' }
  ])
  assert.deepEqual(buildShellBreadcrumb(shellMeta['admin-users']), [
    { label: '管理' },
    { label: '用户管理' }
  ])
  assert.deepEqual(buildShellBreadcrumb(shellMeta.companion), [{ label: '伙伴' }])
})
