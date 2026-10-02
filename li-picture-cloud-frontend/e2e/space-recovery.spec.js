import { test, expect } from '@playwright/test'

const id = '9223372036854775806'
const pictureId = '9223372036854775805'
const member = { id: '42', userAccount: 'space-test', userName: '测试用户', userRole: 'user' }
const space = { id, userId: '42', spaceName: '原有空间', spaceType: 0, spaceLevel: 0, totalCount: 1, maxCount: 100, totalSize: 10, maxSize: 1000 }
const picture = { id: pictureId, name: '原有图片', url: '/images/mosaic/nature.jpg', spaceId: id }
const errors = new WeakMap()
test.afterEach(async ({ page }) => expect(errors.get(page) || []).toEqual([]))
const success = (route, data) => route.fulfill({ json: { code: 0, data } })
const failure = (route, message) => route.fulfill({ status: 503, json: { code: 50000, message } })
async function fixture(page, overrides = {}, user = member) {
  const calls = []
  const pageErrors = []
  errors.set(page, pageErrors)
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    let body = null
    try { body = request.postDataJSON() } catch { /* GET or an empty body */ }
    calls.push({ path, method: request.method(), body, url: request.url() })
    if (overrides[path]) return overrides[path](route)
    let data = []
    if (path === '/api/user/current') data = user
    else if (path === '/api/space/get/vo') data = space
    else if (path === '/api/spaceUser/permissions') data = ['picture:view', 'picture:edit', 'picture:upload']
    else if (path === '/api/space/list/page/vo') data = { records: [space], total: 1 }
    else if (path === '/api/picture/list/page/vo') data = { records: [picture], total: 1 }
    else if (path === '/api/picture/tag_category') data = { categoryList: [], tagList: [] }
    else if (path === '/api/admin/companion/feed-runs/page') data = { records: [], total: 0 }
    return success(route, data)
  })
  return calls
}
const reads = new Set(['/api/space/list/page/vo', '/api/picture/list/page/vo', '/api/spaceUser/permissions', '/api/spaceUser/list/my', '/api/admin/companion/feed-runs/page'])
const writes = calls => calls.filter(call => call.method !== 'GET' && !reads.has(call.path))

test('batch editing keeps exact long space and picture IDs in the real HTTP payload', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto(`/space/${id}`)
  await expect(page.getByRole('heading', { name: '原有空间', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '批量管理', exact: true }).click()
  await page.getByLabel('全选当前页', { exact: true }).check()
  await page.getByRole('button', { name: '应用', exact: true }).click()
  await expect.poll(() => calls.filter(call => call.path === '/api/picture/edit/batch').length).toBe(1)
  const [write] = writes(calls)
  expect(write.path).toBe('/api/picture/edit/batch')
  expect(write.body.spaceId).toBe(id)
  expect(write.body.pictureIdList).toEqual([pictureId])
})

test('a failed space read is recoverable and is not described as deletion', async ({ page }) => {
  let broken = true
  const calls = await fixture(page, { '/api/space/get/vo': route => broken ? failure(route, '空间读取暂不可用') : success(route, space) })
  await page.goto(`/space/${id}`)
  await expect(page.getByRole('alert')).toContainText('空间读取暂不可用')
  await expect(page.getByText('空间不存在或已被删除')).toHaveCount(0)
  expect(calls.some(call => call.path === '/api/picture/list/page/vo')).toBe(false)
  broken = false
  await page.getByRole('button', { name: '重新加载空间', exact: true }).click()
  await expect(page.getByRole('heading', { name: '原有空间', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '查看图片：原有图片' })).toBeVisible()
  expect(writes(calls)).toEqual([])
})

test('picture read errors do not claim an empty space and keyboard retry restores results', async ({ page }) => {
  let broken = true
  const calls = await fixture(page, { '/api/picture/list/page/vo': route => broken ? failure(route, '图片读取暂不可用') : success(route, { records: [picture], total: 1 }) })
  await page.goto(`/space/${id}`)
  await expect(page.getByRole('alert')).toContainText('图片读取暂不可用')
  await expect(page.getByText(/空间暂无图片/)).toHaveCount(0)
  broken = false
  await page.getByRole('button', { name: '重新加载图片', exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('link', { name: '查看图片：原有图片' })).toBeVisible()
  await expect(page.getByText('图片读取暂不可用')).toHaveCount(0)
  expect(writes(calls)).toEqual([])
})

test('managed-space failure preserves the user filter and only shows empty after a successful retry', async ({ page }) => {
  let broken = true
  const calls = await fixture(page, { '/api/space/list/page/vo': route => broken ? failure(route, '空间列表暂不可用') : success(route, { records: [], total: 0 }) })
  await page.goto('/spaces')
  await expect(page.getByRole('alert')).toContainText('空间列表暂不可用')
  await expect(page.getByText('还没有空间，可以创建一个。')).toHaveCount(0)
  broken = false
  await page.getByRole('button', { name: '重新加载空间', exact: true }).click()
  await expect(page.getByText('还没有空间，可以创建一个。')).toBeVisible()
  expect(calls.filter(call => call.path === '/api/space/list/page/vo').every(call => call.body.userId === '42')).toBe(true)
  expect(writes(calls)).toEqual([])
})

test('owned-space failure leaves joined teams usable and exposes a scoped retry at 320px', async ({ page }, testInfo) => {
  let broken = true
  const joined = { space: { ...space, id: '9223372036854775804', userId: '77', spaceType: 1, spaceName: '已加入的团队' }, spaceRole: 'viewer' }
  const calls = await fixture(page, {
    '/api/space/list/page/vo': route => broken ? failure(route, '已创建空间读取失败') : success(route, { records: [space], total: 1 }),
    '/api/spaceUser/list/my': route => success(route, [joined])
  })
  await page.setViewportSize({ width: 320, height: 844 })
  await page.goto('/space/my')
  await expect(page.getByRole('alert')).toContainText('已创建空间读取失败')
  await expect(page.getByText('已加入的团队', { exact: true })).toBeVisible()
  await expect(page.getByText('你还没有私有空间，可以创建一个用于个人图片管理。')).toHaveCount(0)
  const joinedReads = calls.filter(call => call.path === '/api/spaceUser/list/my').length
  broken = false
  await page.getByRole('button', { name: '重新加载已创建空间', exact: true }).click()
  await expect(page.getByText('原有空间', { exact: true })).toBeVisible()
  expect(calls.filter(call => call.path === '/api/spaceUser/list/my').length).toBe(joinedReads)
  expect(writes(calls)).toEqual([])
  expect(await page.evaluate(() => {
    // eslint-disable-next-line no-undef
    return document.documentElement.scrollWidth <= window.innerWidth
  })).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('space-read-recovery-320.png'), fullPage: true })
})

test('admin picture-ID filters preserve exact decimals and reject invalid text without broadening the query', async ({ page }) => {
  const calls = await fixture(page, {}, { ...member, userRole: 'admin' })
  await page.goto('/admin/companion-feed-runs')
  await expect(page.getByRole('button', { name: '查询', exact: true })).toBeEnabled()
  await page.getByLabel('图片 ID', { exact: true }).fill(pictureId)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect.poll(() => calls.filter(call => call.path === '/api/admin/companion/feed-runs/page').at(-1)?.body.pictureId).toBe(pictureId)
  await expect(page.getByRole('button', { name: '查询', exact: true })).toBeEnabled()
  const count = calls.filter(call => call.path === '/api/admin/companion/feed-runs/page').length
  await page.getByLabel('图片 ID', { exact: true }).fill('9e18')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('请输入有效的图片 ID')
  expect(calls.filter(call => call.path === '/api/admin/companion/feed-runs/page').length).toBe(count)
})
