import { test, expect } from '@playwright/test'
const photo = { id: '11', userId: '42', name: '事件测试图片', url: '/favicon.svg', picFormat: 'svg', createTime: '2026-10-09T00:00:00Z' }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
async function fixture(page) {
  const state = { calls: [], delayDecision: null }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request(); const path = new URL(request.url()).pathname
    state.calls.push({ path, method: request.method(), body: request.postDataJSON() })
    if (path === '/api/user/current') return ok(route, { id: '42', userName: '测试用户', userRole: 'user' })
    if (path.includes('tag_category')) return ok(route, { tagList: [], categoryList: [] })
    if (path.includes('/picture/list/page')) return ok(route, { records: [photo], total: 1 })
    if (path === '/api/picture/get/vo') return ok(route, photo)
    if (path === '/api/companion/presence/session') return ok(route, { sessionId: 'site-demo', mode: 'DEMO' })
    if (path.startsWith('/api/companion/presence/session/')) return ok(route, true)
    if (path === '/api/companion/presence/decide') {
      if (state.delayDecision) await state.delayDecision
      const sent = request.postDataJSON()
      return ok(route, { ...sent, sequence: String(sent.sequence), mode: 'DEMO', action: 'EMOTE', emote: 'NOD', remainingDecisions: 11, reason: 'DEMO' })
    }
    if (path === '/api/companion/me') return ok(route, { companion: { id: '7001', lifeStage: 'LIGHT', level: 1, traits: {}, skills: [] }, recentGrowth: [], chatPolicy: 'DEMO', chatMode: { mode: 'DEMO' } })
    if (path === '/api/companion/chat/history') return ok(route, { records: [], total: 0 })
    return ok(route, { records: [], total: 0 })
  })
  return state
}
const widget = page => page.getByTestId('site-presence')
async function enable(page) {
  await widget(page).locator(':scope > details > summary').click()
  await widget(page).getByLabel('开启站内演示').check()
}
const presenceCalls = state => state.calls.filter(call => call.path.includes('/presence/'))
test('site presence defaults off across gallery and actual detail navigation', async ({ page }) => {
  const state = await fixture(page); await page.goto('/gallery')
  await expect(widget(page)).toBeVisible()
  await page.getByRole('link', { name: '查看图片：事件测试图片' }).click()
  await expect(page.locator('.pic-name')).toHaveText(photo.name)
  expect(presenceCalls(state)).toEqual([])
})
test('opted-in current detail emits only event metadata and reply reuses ordinary chat', async ({ page }) => {
  const state = await fixture(page); await page.goto('/gallery'); await enable(page)
  await page.getByRole('link', { name: '查看图片：事件测试图片' }).click()
  await expect(widget(page)).toContainText('EMOTE')
  const sent = state.calls.find(call => call.path.endsWith('/presence/decide')).body
  expect(Object.keys(sent).sort()).toEqual(['eventId', 'eventType', 'observedAt', 'page', 'sequence', 'sessionId'].sort())
  expect(sent.eventType).toBe('PICTURE_OPENED'); expect(sent.page).toBe('PICTURE_DETAIL')
  expect(JSON.stringify(sent)).not.toContain(photo.name); expect(sent.eventId.length).toBeLessThanOrEqual(64)
  await widget(page).getByRole('button', { name: '回复绫页 · 普通聊天' }).click()
  await expect(page.getByRole('dialog', { name: '和绫页互动' })).toBeVisible()
  expect(state.calls.filter(call => call.path.includes('/chat/') && call.method === 'POST')).toEqual([])
})
test('pause suppresses successful detail event while keeping ordinary page readable', async ({ page }) => {
  const state = await fixture(page); await page.goto('/gallery'); await enable(page)
  await widget(page).getByLabel('暂停', { exact: true }).check()
  await page.getByRole('link', { name: '查看图片：事件测试图片' }).click()
  await expect(page.locator('.pic-name')).toHaveText(photo.name)
  expect(presenceCalls(state)).toEqual([])
})
test('leaving detail discards a delayed response', async ({ page }) => {
  const state = await fixture(page); let release
  state.delayDecision = new Promise(resolve => { release = resolve })
  await page.goto('/gallery'); await enable(page)
  await page.getByRole('link', { name: '查看图片：事件测试图片' }).click()
  await expect.poll(() => state.calls.filter(call => call.path.endsWith('/presence/decide')).length).toBe(1)
  await page.locator('.back-btn').click(); release()
  await expect(page).toHaveURL(/\/gallery$/)
  await expect(widget(page)).not.toContainText('点点头')
})
