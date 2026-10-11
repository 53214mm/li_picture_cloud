import { test, expect } from '@playwright/test'

const companion = { id: '7001', lifeStage: 'LIGHT', revision: '9', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', traits: {}, skills: [] }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
const OWN = { mode: 'MODEL', modelAvailable: true, modelSource: 'OWN', modelBroken: false, providerLabel: 'DeepSeek', modelCode: 'deepseek-chat' }
const DEMO_ONLY = { mode: 'DEMO', modelAvailable: false, modelSource: 'NONE', modelBroken: false, providerLabel: null, modelCode: null }
function gate() { let release; const promise = new Promise(resolve => { release = resolve }); return { promise, release } }

async function fixture(page, chatMode, { streamGate = null } = {}) {
  const state = { calls: [], errors: [], chatMode }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    state.calls.push({ path, method: request.method(), body: request.postDataJSON?.() ?? null })
    if (path === '/api/user/current') return ok(route, { id: '42', userName: 'Mode', userRole: 'user' })
    if (path === '/api/companion/me') {
      return ok(route, { companion, mood: null, relationship: null, recentGrowth: [], nutrition: {}, chatPolicy: 'DEMO_ONLY', chatMode: state.chatMode })
    }
    if (path === '/api/companion/chat/mode' && request.method() === 'PUT') {
      state.chatMode = { ...state.chatMode, mode: request.postDataJSON().mode }
      return ok(route, state.chatMode)
    }
    if (path === '/api/companion/chat/history') return ok(route, { records: [] })
    if (path === '/api/companion/chat/stream') {
      if (streamGate) await streamGate.promise
      return route.fulfill({ contentType: 'text/event-stream', body: 'data:你好呀。\n\nevent:done\ndata:\n\n' })
    }
    if (path === '/api/companion/proposals/active') return ok(route, null)
    if (path === '/api/companion/contract') return ok(route, { active: false, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 1 })
    if (path.includes('tag_category')) return ok(route, { tagList: [], categoryList: [] })
    return ok(route, { records: [], total: 0 })
  })
  return state
}

const homeChat = page => page.locator('#habitat-chat')
const switchButton = (scope, name) => scope.getByRole('group', { name: '聊天模式' }).getByRole('button', { name })

test('the switch toggles between demo and the own model and stays in sync with the drawer', async ({ page }, testInfo) => {
  const state = await fixture(page, OWN)
  await page.goto('/companion')
  const chat = homeChat(page)
  await chat.locator('.chat-mode summary').click()
  await expect(switchButton(chat, '真实模型')).toHaveAttribute('aria-pressed', 'true')
  await expect(chat.getByTestId('chat-mode-note')).toHaveText('真实模型：使用你的 DeepSeek · deepseek-chat。')
  await chat.getByTestId('chat-mode').screenshot({ path: testInfo.outputPath('chat-mode-model.png') })

  await switchButton(chat, '演示回复').click()
  await expect(switchButton(chat, '演示回复')).toHaveAttribute('aria-pressed', 'true')
  await expect(chat.getByTestId('chat-mode-note')).toContainText('演示回复：不调用模型，不产生费用。')
  expect(state.calls.filter(call => call.path === '/api/companion/chat/mode')).toEqual([
    { path: '/api/companion/chat/mode', method: 'PUT', body: { mode: 'DEMO' } }
  ])

  // The drawer's quick chat shows the same, already switched mode.
  await page.locator('.sidebar').getByRole('button', { name: '和绫页互动' }).click()
  const drawer = page.getByRole('dialog', { name: '和绫页互动' })
  await drawer.locator('.chat-mode summary').click()
  await expect(switchButton(drawer, '演示回复')).toHaveAttribute('aria-pressed', 'true')
  await switchButton(drawer, '真实模型').click()
  await expect(switchButton(drawer, '真实模型')).toHaveAttribute('aria-pressed', 'true')
  await page.keyboard.press('Escape')
  await expect(switchButton(chat, '真实模型')).toHaveAttribute('aria-pressed', 'true')
  expect(state.errors).toEqual([])
})

test('without any model the real-model option is disabled and points to setup', async ({ page }) => {
  const state = await fixture(page, DEMO_ONLY)
  await page.goto('/companion')
  const chat = homeChat(page)
  await chat.locator('.chat-mode summary').click()
  await expect(switchButton(chat, '真实模型')).toBeDisabled()
  await expect(chat.getByTestId('chat-mode-note')).toContainText('演示回复：不调用模型。')
  await expect(chat.getByTestId('chat-mode-note')).toContainText('平台模型未启用或暂不可用，也可以连接自己的模型。')
  await chat.getByRole('link', { name: '去连接模型 →' }).click()
  await expect(page).toHaveURL(/\/model-gateway$/)
  expect(state.calls.some(call => call.path === '/api/companion/chat/mode')).toBe(false)
})

test('the mode cannot change while a reply is still streaming', async ({ page }) => {
  const held = gate()
  await fixture(page, OWN, { streamGate: held })
  await page.goto('/companion')
  const chat = homeChat(page)
  await chat.locator('.chat-mode summary').click()
  await chat.getByLabel('对伙伴说的话').fill('在吗')
  await chat.getByRole('button', { name: '发送' }).click()
  await expect(switchButton(chat, '演示回复')).toBeDisabled()
  held.release()
  await expect(chat.getByText('你好呀。')).toBeVisible()
  await expect(switchButton(chat, '演示回复')).toBeEnabled()
})

test('a broken own model is called out with a fix link', async ({ page }) => {
  await fixture(page, { ...OWN, modelBroken: true, providerLabel: null, modelCode: null })
  await page.goto('/companion')
  const chat = homeChat(page)
  await expect(chat.getByTestId('chat-mode-note')).toContainText('你连接的模型不可用了')
  await expect(chat.getByRole('link', { name: '去修复模型连接 →' })).toBeVisible()
})
