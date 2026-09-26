import { test, expect } from '@playwright/test'

const specimen = {
  id: '7001', lifeStage: 'LIGHT', lifeExperience: '42', level: 1,
  levelStartExperience: '0', nextLevelExperience: '100', revision: '9', traits: {}, skills: []
}
const baseMood = { energy: 0, joy: 8, loneliness: 0, inspiration: 0, irritation: 0, summary: '测试摘要' }
const baseRelationship = { familiarity: 25, trust: 0, closeness: 0, tacit: 0, recentFeedback: 0 }
const pendingProposal = { id: '7010', status: 'PENDING', content: '我很生气（仅文本，不是情绪信号）', opportunityType: 'WEEKLY_REVIEW', impulseScore: 2 }
function gate() {
  let release
  const promise = new Promise(resolve => { release = resolve })
  return { promise, release }
}
function ok(route, data) { return route.fulfill({ json: { code: 0, data } }) }
async function fixture(page) {
  const state = {
    home: { companion: specimen, mood: baseMood, relationship: baseRelationship, recentGrowth: [], nutrition: {}, chatPolicy: 'DEMO' },
    proposal: pendingProposal, calls: [], errors: []
  }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    state.calls.push(path)
    if (path === '/api/user/current') return ok(route, { id: '42', userAccount: 'r06', userName: 'R06', userRole: 'user' })
    if (path === '/api/companion/me') return ok(route, state.home)
    if (path === '/api/companion/proposals/active') return ok(route, state.proposal)
    if (path === '/api/companion/contract') return ok(route, { active: true, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 1 })
    if (path === '/api/space/list/page/vo') return ok(route, { records: [{ id: '51', userId: '42', spaceType: 0, spaceName: '私有空间', createTime: '2026-09-25T00:00:00Z' }] })
    if (path === '/api/picture/list/page/vo') return ok(route, { records: [{ id: '101', name: 'R06 图片', pictureUrl: '/favicon.svg' }] })
    if (path.includes('tag_category')) return ok(route, { tagList: [], categoryList: [] })
    return ok(route, { records: [], total: 0 })
  })
  // Mock only the transport. The production stream parser and component callbacks run.
  await page.addInitScript(() => {
    const original = globalThis.fetch.bind(globalThis)
    globalThis.fetch = (url, options) => {
      if (url !== '/api/companion/chat/stream') return original(url, options)
      return new Promise((resolve, reject) => {
        globalThis.r06ChatReject = reject
        globalThis.r06ChatOpen = () => {
          const stream = new ReadableStream({ start(controller) { globalThis.r06ChatController = controller } })
          resolve(new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }))
        }
      })
    }
  })
  return state
}
async function allSurfaces(page, key, value) {
  for (const selector of ['.sidebar .companion-presence', '.companion-body', '.companion-portrait']) {
    await expect(page.locator(selector)).toHaveAttribute(`data-presentation-${key}`, value)
  }
}
async function sendChat(page) {
  await page.getByLabel('对伙伴说的话').fill('回复内容不作为情绪信号')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await allSurfaces(page, 'activity', 'thinking')
  await page.evaluate(() => globalThis.r06ChatOpen())
}
async function chunk(page, text) {
  await page.evaluate(value => globalThis.r06ChatController.enqueue(new TextEncoder().encode(value)), text)
}

test('three surfaces consume one projection without extra Shell IO and release on navigation', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion')
  await allSurfaces(page, 'availability', 'ready')
  await allSurfaces(page, 'activity', 'idle')
  await allSurfaces(page, 'affect', 'cheerful')
  await allSurfaces(page, 'rapport', 'familiar')
  await allSurfaces(page, 'attention', 'proposal')
  expect(state.calls.filter(path => path === '/api/companion/me')).toHaveLength(1)
  expect(state.calls.filter(path => path === '/api/companion/proposals/active')).toHaveLength(1)
  await page.locator('.sidebar').getByRole('link', { name: '图库', exact: true }).click()
  await expect(page.locator('.sidebar .companion-presence')).toHaveAttribute('data-presentation-availability', 'unobserved')
  await expect(page.locator('.sidebar .companion-presence')).toHaveAttribute('data-presentation-affect', 'neutral')
  expect(state.calls.filter(path => path === '/api/companion/me')).toHaveLength(1)
  expect(state.errors).toEqual([])
})

test('loading, error, explicit absence and service unavailable never manufacture an instance', async ({ page }) => {
  const state = await fixture(page)
  const held = gate()
  await page.route('**/api/companion/me', async route => {
    await held.promise
    await route.fulfill({ status: 503, json: { code: 50000, message: '暂时不可用' } })
  })
  await page.goto('/companion')
  const shell = page.locator('.sidebar .companion-presence')
  await expect(shell).toHaveAttribute('data-presentation-availability', 'loading')
  held.release()
  await expect(shell).toHaveAttribute('data-presentation-availability', 'error')
  await page.unroute('**/api/companion/me')
  state.home = { companion: null }
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(shell).toHaveAttribute('data-presentation-availability', 'absent')
  await expect(page.locator('.companion-body')).toHaveCount(0)
  await page.route('**/api/companion/me', route => route.fulfill({ status: 404, json: { code: 40400, message: '未开放' } }))
  await page.reload()
  await expect(shell).toHaveAttribute('data-presentation-availability', 'unavailable')
  await expect(page.locator('.companion-portrait')).toHaveCount(0)
  expect(state.errors).toEqual([])
})

test('Chat waiting and stream outrank concurrent Feed, then return to Feed and authoritative mood', async ({ page }) => {
  const state = await fixture(page)
  const held = gate()
  await page.route('**/api/companion/feed', async route => {
    await held.promise
    state.home = { ...state.home, mood: { ...baseMood, inspiration: 20 }, companion: { ...specimen, revision: '10' } }
    await ok(route, { outcome: 'GROWN', companion: state.home.companion, growth: { id: '9001', pictureId: '101', createdTime: '2026-09-26T00:00:00Z', lifeExperienceDelta: 0, skillExperienceDelta: {}, traitDelta: {} } })
  })
  await page.goto('/companion')
  await page.getByRole('button', { name: /R06 图片/ }).click()
  await page.getByRole('button', { name: '喂给伙伴' }).click()
  await allSurfaces(page, 'activity', 'feeding')
  await sendChat(page)
  await chunk(page, 'data:我很生气，也很疲惫\n\n')
  await allSurfaces(page, 'activity', 'responding')
  await allSurfaces(page, 'affect', 'cheerful')
  await chunk(page, 'event:done\ndata:\n\n')
  await allSurfaces(page, 'activity', 'feeding')
  held.release()
  await allSurfaces(page, 'activity', 'idle')
  await allSurfaces(page, 'affect', 'inspired')
  await allSurfaces(page, 'freshness', 'fresh')
  expect(state.errors).toEqual([])
})

test('uncertain Feed retries the same key and refresh failure remains neutral without reporting failed Feed', async ({ page }) => {
  const state = await fixture(page)
  const keys = []
  await page.route('**/api/companion/feed', async route => {
    keys.push(route.request().postDataJSON().idempotencyKey)
    if (keys.length === 1) return route.abort('failed')
    await ok(route, { outcome: 'FAMILIARITY', companion: specimen, growth: { id: '9001', createdTime: '2026-09-26T00:00:00Z', lifeExperienceDelta: 0, skillExperienceDelta: {}, traitDelta: {} } })
  })
  await page.goto('/companion')
  await page.getByRole('button', { name: /R06 图片/ }).click()
  await page.getByRole('button', { name: '喂给伙伴' }).click()
  await allSurfaces(page, 'freshness', 'stale')
  await allSurfaces(page, 'affect', 'neutral')
  await page.route('**/api/companion/me', route => route.fulfill({ status: 503, json: { code: 50000, message: '刷新失败' } }))
  await page.getByRole('button', { name: '重试这次喂养' }).click()
  await expect(page.getByText('这次喂养已安全完成，没有重复成长。')).toBeVisible()
  await allSurfaces(page, 'activity', 'idle')
  await allSurfaces(page, 'freshness', 'stale')
  await allSurfaces(page, 'rapport', 'neutral')
  expect(keys).toHaveLength(2)
  expect(keys[0]).toBe(keys[1])
  expect(state.errors).toEqual([])
})

test('stream error and network failure leave neutral request activity and allow retry', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion')
  await sendChat(page)
  await chunk(page, 'data:正在处理\n\n')
  await allSurfaces(page, 'activity', 'responding')
  await chunk(page, 'event:error\ndata:模型暂时不可用\n\n')
  await allSurfaces(page, 'activity', 'idle')
  await allSurfaces(page, 'affect', 'cheerful')
  await page.getByLabel('对伙伴说的话').fill('再次尝试')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await allSurfaces(page, 'activity', 'thinking')
  await page.evaluate(() => globalThis.r06ChatReject(new Error('network offline')))
  await allSurfaces(page, 'activity', 'idle')
  await expect(page.getByLabel('对伙伴说的话')).toHaveValue('再次尝试')
  expect(state.errors).toEqual([])
})

test('proposal action is bounded by its request and suppressed proposal never implies anger', async ({ page }) => {
  const state = await fixture(page)
  const held = gate()
  await page.route('**/api/companion/proposals/7010/scold', async route => {
    await held.promise
    await ok(route, { ...pendingProposal, status: 'SUPPRESSED' })
  })
  await page.goto('/companion')
  await page.getByTestId('proposal-scold').click()
  await allSurfaces(page, 'activity', 'acknowledging')
  held.release()
  await allSurfaces(page, 'activity', 'idle')
  await allSurfaces(page, 'attention', 'none')
  await allSurfaces(page, 'affect', 'cheerful')
  await expect(page.getByText('伙伴安静了，这次提议已被止住。')).toBeVisible()
  expect(state.errors).toEqual([])
})

test('late chat chunks from the previous page cannot affect the next page or Shell', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion')
  await sendChat(page)
  await page.locator('.sidebar').getByRole('link', { name: '图库', exact: true }).click()
  await expect(page.locator('.sidebar .companion-presence')).toHaveAttribute('data-presentation-availability', 'unobserved')
  await page.locator('.sidebar').getByRole('link', { name: '伙伴空间', exact: true }).click()
  await allSurfaces(page, 'activity', 'idle')
  await chunk(page, 'data:我终于回来了\n\n')
  await chunk(page, 'event:done\ndata:\n\n')
  await allSurfaces(page, 'activity', 'idle')
  expect(state.errors).toEqual([])
})

test('Feed refresh cannot resurrect a proposal after a concurrent scold has completed', async ({ page }) => {
  const state = await fixture(page)
  const feedGate = gate()
  const actionGate = gate()
  const readGate = gate()
  let reads = 0
  let readReturned = false
  await page.goto('/companion')
  await allSurfaces(page, 'attention', 'proposal')
  await page.route('**/api/companion/proposals/active', async route => {
    reads += 1
    await readGate.promise
    await ok(route, pendingProposal)
    readReturned = true
  })
  await page.route('**/api/companion/feed', async route => {
    await feedGate.promise
    await ok(route, { outcome: 'FAMILIARITY', companion: specimen, growth: { id: '9001', createdTime: '2026-09-26T00:00:00Z', lifeExperienceDelta: 0, skillExperienceDelta: {}, traitDelta: {} } })
  })
  await page.route('**/api/companion/proposals/7010/scold', async route => {
    await actionGate.promise
    await ok(route, { ...pendingProposal, status: 'SUPPRESSED' })
  })
  await page.getByRole('button', { name: /R06 图片/ }).click()
  await page.getByRole('button', { name: '喂给伙伴' }).click()
  await page.getByTestId('proposal-scold').click()
  feedGate.release()
  await expect(page.getByRole('button', { name: '喂给伙伴' })).toBeEnabled()
  actionGate.release()
  await expect(page.getByText('伙伴安静了，这次提议已被止住。')).toBeVisible()
  readGate.release()
  if (reads) await expect.poll(() => readReturned).toBe(true)
  await allSurfaces(page, 'attention', 'none')
  await allSurfaces(page, 'activity', 'idle')
  expect(state.errors).toEqual([])
})
