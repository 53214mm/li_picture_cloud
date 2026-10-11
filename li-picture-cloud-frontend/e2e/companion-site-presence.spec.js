import { test, expect } from '@playwright/test'
import { freezeClock } from './helpers/clock.js'

const photo = { id: '11', userId: '42', name: '观察测试图片', url: '/favicon.svg', updateTime: '2026-10-09T00:00:00Z' }
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
    if (path === '/api/companion/observation/status') return ok(route, {
      enabled: true, realVisualReady: false, textReceiver: { provider: 'TEXT_FIXTURE', model: 'no-paid-model', host: 'text.fixture.invalid', name: '模拟文本', token: 'text-fixture-v1' },
      visionReceiver: null, textMax: 500, visionMax: 100, textDayMax: 1000, visionDayMax: 200, counts: { text: 0, vision: 0 }
    })
    if (path === '/api/companion/observation/session' && request.method() === 'POST') {
      const sent = request.postDataJSON()
      expect(sent.textReceiverToken).toBe('text-fixture-v1'); expect(sent.visionLimit).toBe(0)
      return ok(route, { sessionId: 'observation-fixture', textRemaining: sent.textLimit, visionRemaining: 0, expiresAt: '2026-01-02T01:00:00Z' })
    }
    if (path.startsWith('/api/companion/observation/session/')) return ok(route, true)
    if (path === '/api/companion/observation/prepare') return ok(route, { snapshotId: 'snapshot-fixture', preview: null, visualSkip: 'VISION_OFF' })
    if (path === '/api/companion/observation/observe') {
      if (state.delayDecision) await state.delayDecision
      const sent = request.postDataJSON()
      try { return await ok(route, { ...sent, parsed: { action: 'SPEAK', text: '来自独立观察的模拟发言', emote: null }, reason: null,
        modelDispatched: true, visionDispatched: false, attemptReserved: true, textRemaining: 5, visionRemaining: 0, nextObservationSeconds: 90, daily: { text: 1, vision: 0 } }) } catch { return }
    }
    if (path === '/api/companion/me') return ok(route, { companion: { id: '7001', lifeStage: 'LIGHT', level: 1, traits: {}, skills: [] }, recentGrowth: [], chatPolicy: 'DEMO', chatMode: { mode: 'DEMO' } })
    return ok(route, { records: [], total: 0 })
  })
  return state
}
const widget = page => page.getByTestId('companion-observation')
const observationWrites = state => state.calls.filter(call => call.path.includes('/observation/') && call.method !== 'GET')
const observes = state => state.calls.filter(call => call.path.endsWith('/observation/observe'))
async function enable(page) {
  await widget(page).locator(':scope > summary').click()
  await expect(widget(page).getByTestId('observer-receivers')).toContainText('TEXT_FIXTURE')
  await widget(page).getByRole('button', { name: '明确授权开启本轮自动观察', exact: true }).click()
  await expect(widget(page).locator(':scope > summary')).toContainText('已开启')
}
async function openDetail(page) {
  await page.getByRole('link', { name: '查看图片：观察测试图片' }).click()
  await expect(page.locator('.pic-name')).toHaveText(photo.name)
  // The installed clock also pauses Vue's route transition cleanup.
  await page.clock.runFor(400)
  await expect(page.getByRole('link', { name: '查看图片：观察测试图片' })).toHaveCount(0)
}

test('independent observation defaults off across gallery and actual detail navigation', async ({ page }) => {
  await freezeClock(page); const state = await fixture(page); await page.goto('/gallery')
  await expect(widget(page)).toBeVisible(); await openDetail(page); await page.clock.fastForward(90000)
  expect(observationWrites(state)).toEqual([])
  expect(state.calls.filter(call => call.path.includes('/presence/') && call.method !== 'GET')).toEqual([])
})

test('explicit observation uses one snapshot on cadence; clicks do not dispatch and reply opens ordinary chat', async ({ page }) => {
  await freezeClock(page); const state = await fixture(page); await page.goto('/gallery'); await enable(page); await openDetail(page)
  expect(observes(state)).toEqual([]); await page.clock.fastForward(89999 - 400); expect(observes(state)).toEqual([])
  await page.clock.fastForward(1); await expect.poll(() => observes(state).length).toBe(1)
  const sent = observes(state)[0].body
  expect(sent).toEqual({ sessionId: 'observation-fixture', snapshotId: 'snapshot-fixture', sequence: 1 })
  const preparations = state.calls.filter(call => call.path.endsWith('/observation/prepare'))
  expect(preparations).toHaveLength(1)
  expect(preparations[0].body).toEqual({ sessionId: 'observation-fixture', page: { page: 'PICTURE_DETAIL', pictureIds: ['11'], rects: [] } })
  await expect(widget(page).getByTestId('observation-speech-body')).toContainText('来自独立观察的模拟发言')
  await expect(widget(page)).toContainText('本轮文本剩余 5')
  await widget(page).getByRole('button', { name: '回复绫页 · 普通聊天', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '和绫页互动' })).toBeVisible()
  expect(state.calls.filter(call => call.path.includes('/chat/') && call.method === 'POST')).toEqual([])
  expect(state.calls.filter(call => call.path.includes('/presence/') && call.method !== 'GET')).toEqual([])
})

test('manual pause prevents detail and timer dispatch while keeping the page readable', async ({ page }) => {
  await freezeClock(page); const state = await fixture(page); await page.goto('/gallery'); await enable(page)
  await widget(page).getByRole('button', { name: '暂停并结束本轮', exact: true }).click(); await openDetail(page); await page.clock.fastForward(900000)
  expect(observes(state)).toEqual([])
  await expect(widget(page).locator(':scope > summary')).toContainText('已暂停')
  expect(observationWrites(state).map(call => call.method)).toEqual(['POST', 'DELETE'])
})

test('leaving detail discards late speech and accounts the attempt without reopening the grant', async ({ page }) => {
  await freezeClock(page); const state = await fixture(page); let release
  state.delayDecision = new Promise(resolve => { release = resolve })
  await page.goto('/gallery'); await enable(page); await openDetail(page); await page.clock.fastForward(90000)
  await expect.poll(() => observes(state).length).toBe(1)
  await page.locator('.back-btn').click(); await expect(page).toHaveURL(/\/gallery$/); release()
  await expect(widget(page)).toContainText('本轮文本剩余 5')
  await expect(widget(page).getByTestId('observation-speech-body')).toHaveCount(0)
  await expect(widget(page).locator(':scope > summary')).toContainText('已开启')
  expect(observationWrites(state).filter(call => call.path.endsWith('/session'))).toHaveLength(1)
  expect(observationWrites(state).filter(call => call.method === 'DELETE')).toEqual([])
  expect(observes(state)).toHaveLength(1)
})
