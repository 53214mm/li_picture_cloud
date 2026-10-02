import { test, expect } from '@playwright/test'

const companion = { id: '7001', lifeStage: 'LIGHT', revision: '9', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', traits: {}, skills: [] }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
const dialog = page => page.getByRole('dialog', { name: '和绫页互动' })
function gate() { let release; const promise = new Promise(resolve => { release = resolve }); return { promise, release } }
const shell = page => page.locator('.sidebar').getByRole('button', { name: '和绫页互动' })
async function fixture(page, overrides = {}) {
  const state = { calls: [], messages: [], errors: [], companion, proposal: null, user: { id: '42', userName: 'R12', userRole: 'user' }, ...overrides }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    state.calls.push({ path, method: route.request().method() })
    if (path === '/api/user/current' || path === '/api/user/login') return ok(route, state.user)
    if (path === '/api/user/logout') return ok(route, true)
    if (path === '/api/companion/me') return ok(route, { companion: state.companion, mood: null, relationship: null, recentGrowth: [], nutrition: {}, chatPolicy: 'DEMO' })
    if (path === '/api/companion/chat/history') return ok(route, { records: state.messages })
    if (path === '/api/companion/chat/stream') {
      const { message } = route.request().postDataJSON()
      state.messages.push({ role: 'USER', content: message }, { role: 'COMPANION', content: '听见了。' })
      return route.fulfill({ contentType: 'text/event-stream', body: 'data:听见了。\n\nevent:done\ndata:\n\n' })
    }
    if (path === '/api/companion/proposals/active') return ok(route, state.proposal)
    if (path === '/api/companion/contract') return ok(route, { active: false, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 1 })
    if (path.includes('tag_category')) return ok(route, { tagList: [], categoryList: [] })
    return ok(route, { records: [], total: 0 })
  })
  return state
}

test('R12 avatar opens chat in place, lazy reads only, and retains a draft across close/reopen', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/gallery')
  expect(state.calls.some(call => call.path.startsWith('/api/companion/'))).toBe(false)
  await shell(page).click()
  const input = dialog(page).getByLabel('对伙伴说的话')
  await expect(input).toBeEnabled()
  await expect(page).toHaveURL(/\/gallery$/)
  await input.fill('还没发送的草稿')
  await page.keyboard.press('Escape')
  await expect(shell(page)).toBeFocused()
  await shell(page).click()
  await expect(input).toBeEnabled()
  await expect(input).toHaveValue('还没发送的草稿')
  expect(state.calls.filter(call => call.method === 'POST')).toEqual([])
  expect(state.calls.filter(call => call.path.startsWith('/api/companion/')).every(call => ['/api/companion/me', '/api/companion/chat/history'].includes(call.path))).toBe(true)
  expect(state.errors).toEqual([])
})

test('R12 compact and full views share one history and one send, then return focus to the full form', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion')
  await expect(page.locator('#companion-chat-input')).toBeEnabled()
  await page.locator('.portrait-interaction').click()
  await expect(dialog(page).getByLabel('对伙伴说的话')).toBeEnabled()
  await dialog(page).getByLabel('对伙伴说的话').fill('同一个会话')
  await expect(dialog(page).locator('.chat-card')).toHaveAttribute('data-chat-ready', 'true')
  await dialog(page).locator('.chat-input').evaluate(form => { form.requestSubmit(); form.requestSubmit() })
  await expect(dialog(page).getByText('听见了。', { exact: true })).toBeVisible()
  await dialog(page).getByRole('button', { name: /打开完整对话/ }).click()
  await expect(page.locator('#companion-chat-input')).toBeFocused()
  await expect(page.locator('.chat-card').getByText('同一个会话', { exact: true })).toHaveCount(1)
  expect(state.calls.filter(call => call.path.endsWith('/chat/history'))).toHaveLength(1)
  expect(state.calls.filter(call => call.path.endsWith('/chat/stream'))).toHaveLength(1)
  expect(state.errors).toEqual([])
})

test('R12 absent companion does not load history or manufacture a chat session', async ({ page }) => {
  const state = await fixture(page, { companion: null })
  await page.goto('/gallery')
  await shell(page).click()
  await expect(dialog(page).getByText('还没有唤醒伙伴。可以先到伙伴空间看看。')).toBeVisible()
  await expect(dialog(page).getByLabel('对伙伴说的话')).toHaveCount(0)
  expect(state.calls.filter(call => call.path.endsWith('/chat/history') || call.method === 'POST')).toEqual([])
})

test('R12 uncertain chat is not retried and requires explicit history refresh before sending again', async ({ page }) => {
  const state = await fixture(page)
  await page.route('**/api/companion/chat/stream', async route => {
    state.calls.push({ path: '/api/companion/chat/stream', method: 'POST' })
    state.messages.push({ role: 'USER', content: '确认是否已经发出' })
    await route.abort('failed')
  })
  await page.goto('/gallery')
  await shell(page).click()
  const input = dialog(page).getByLabel('对伙伴说的话')
  await expect(input).toBeEnabled()
  await input.fill('确认是否已经发出')
  await dialog(page).getByRole('button', { name: '发送', exact: true }).click()
  await expect(dialog(page).getByRole('button', { name: '刷新对话' })).toBeVisible()
  await expect(input).toHaveValue('')
  await expect(input).toBeDisabled()
  await dialog(page).getByRole('button', { name: '刷新对话' }).click()
  await expect(input).toBeEnabled()
  await expect(dialog(page).getByText('确认是否已经发出', { exact: true })).toHaveCount(1)
  expect(state.calls.filter(call => call.path.endsWith('/chat/stream'))).toHaveLength(1)
})

test('R12 pending proposal is a passive avatar hint and previews only the existing observed proposal', async ({ page }) => {
  const state = await fixture(page, { proposal: { id: '91', status: 'PENDING', content: '一起看看最近的图片？', opportunityType: 'WEEKLY_REVIEW', impulseScore: 2 } })
  await page.goto('/companion')
  await expect(page.getByTestId('proposal-hint')).toBeVisible()
  await expect(dialog(page)).not.toBeVisible()
  await page.getByTestId('proposal-hint').click()
  await expect(dialog(page).getByText('一起看看最近的图片？', { exact: true })).toBeVisible()
  expect(state.calls.filter(call => call.path.endsWith('/proposals/active'))).toHaveLength(1)
  expect(state.calls.filter(call => call.path.endsWith('/contract'))).toHaveLength(1)
  expect(state.calls.filter(call => call.method === 'POST')).toEqual([])
  await dialog(page).getByRole('button', { name: '前往回应这条提议' }).click()
  await expect(page.locator('#proposal-title')).toBeFocused()
  expect(state.errors).toEqual([])
})

test('R12 320px reduced-motion entry supports keyboard disclosure and does not overflow', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 320, height: 800 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const state = await fixture(page)
  await page.goto('/gallery')
  await page.locator('.bottom-navigation .companion-tab').click()
  const input = dialog(page).getByLabel('对伙伴说的话')
  await expect(input).toBeEnabled()
  await input.fill('你好')
  await input.press('Enter')
  await expect(dialog(page).getByText('听见了。', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('r12-quick-chat-mobile.png') })
  await page.keyboard.press('Escape')
  await expect(page.locator('.bottom-navigation .companion-tab')).toBeFocused()
  expect(state.errors).toEqual([])
})

for (const stage of ['home', 'history']) test(`R12 closing a pending ${stage} read rejects its late response after reopen`, async ({ page }) => {
  const state = await fixture(page)
  const held = gate()
  let reads = 0
  let released = false
  const path = stage === 'home' ? '**/api/companion/me' : '**/api/companion/chat/history*'
  await page.route(path, async route => {
    reads += 1
    if (reads === 1) {
      await held.promise
      try { await ok(route, stage === 'home' ? { companion: null } : { records: [{ role: 'COMPANION', content: '旧读取，不应回来' }] }) } catch { /* closed request */ }
      finally { released = true }
    } else await ok(route, stage === 'home' ? { companion, chatPolicy: 'DEMO' } : { records: [] })
  })
  await page.goto('/gallery')
  await shell(page).click()
  await expect.poll(() => reads).toBe(1)
  await page.keyboard.press('Escape')
  await shell(page).click()
  await expect(dialog(page).getByLabel('对伙伴说的话')).toBeEnabled()
  held.release()
  await expect.poll(() => released).toBe(true)
  await expect(dialog(page).getByLabel('对伙伴说的话')).toBeEnabled()
  await expect(dialog(page).getByText('旧读取，不应回来')).toHaveCount(0)
  expect(state.errors).toEqual([])
})

test('R12 closing an in-flight send requires reconciliation on reopen and never resends automatically', async ({ page }) => {
  const state = await fixture(page)
  const held = gate()
  let sends = 0
  let released = false
  await page.route('**/api/companion/chat/stream', async route => {
    sends += 1
    state.messages.push({ role: 'USER', content: '离开前的一句话' })
    await held.promise
    try { await route.fulfill({ contentType: 'text/event-stream', body: 'data:迟到的旧回应\n\nevent:done\ndata:\n\n' }) } catch { /* closed request */ }
    finally { released = true }
  })
  await page.goto('/gallery')
  await shell(page).click()
  await dialog(page).getByLabel('对伙伴说的话').fill('离开前的一句话')
  await dialog(page).getByRole('button', { name: '发送', exact: true }).click()
  await expect.poll(() => sends).toBe(1)
  await page.keyboard.press('Escape')
  await shell(page).click()
  await expect(dialog(page).getByRole('button', { name: '刷新对话' })).toBeEnabled()
  held.release()
  await expect.poll(() => released).toBe(true)
  await expect(dialog(page).getByText('迟到的旧回应')).toHaveCount(0)
  await dialog(page).getByRole('button', { name: '刷新对话' }).click()
  await expect(dialog(page).getByLabel('对伙伴说的话')).toBeEnabled()
  await expect(dialog(page).getByText('离开前的一句话', { exact: true })).toHaveCount(1)
  expect(sends).toBe(1)
})

test('R12 logout and login as another account clear private draft and conversation state', async ({ page }) => {
  const state = await fixture(page, { messages: [{ role: 'COMPANION', content: '上一个账号的记录' }] })
  await page.goto('/gallery')
  await shell(page).click()
  await dialog(page).getByLabel('对伙伴说的话').fill('上一个账号的草稿')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '打开账户菜单' }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  state.user = { id: '43', userName: 'R12第二账号', userRole: 'user' }
  state.messages = []
  await page.getByLabel('账号', { exact: true }).fill('fixture-second-account')
  await page.getByLabel('密码', { exact: true }).fill('fixture-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.locator('.app-layout')).toBeVisible()
  await shell(page).click()
  await expect(dialog(page).getByLabel('对伙伴说的话')).toBeEnabled()
  await expect(dialog(page).getByLabel('对伙伴说的话')).toHaveValue('')
  await expect(dialog(page).getByText('上一个账号的记录')).toHaveCount(0)
  expect(state.errors).toEqual([])
})

test('R12 Gallery-to-full chat handoff focuses a writable draft while history is delayed', async ({ page }) => {
  const state = await fixture(page)
  const held = gate()
  let reads = 0
  await page.route('**/api/companion/chat/history*', async route => {
    reads += 1
    await held.promise
    try { await ok(route, { records: [] }) } catch { /* the drawer read may have been closed */ }
  })
  await page.goto('/gallery')
  await shell(page).click()
  await expect.poll(() => reads).toBe(1)
  await dialog(page).getByRole('button', { name: /打开完整对话/ }).click()
  await expect(page).toHaveURL(/\/companion$/)
  await expect(page.locator('#companion-chat-input')).toBeFocused()
  await page.locator('#companion-chat-input').fill('可以先写，稍后发送')
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeDisabled()
  held.release()
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
  await expect(page.locator('#companion-chat-input')).toHaveValue('可以先写，稍后发送')
  expect(state.calls.some(call => call.path.endsWith('/chat/stream'))).toBe(false)
})
