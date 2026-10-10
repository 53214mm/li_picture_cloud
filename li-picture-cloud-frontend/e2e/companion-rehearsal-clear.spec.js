import { test, expect } from '@playwright/test'

const ok = (route, data) => route.fulfill({ json: { code: 0, data } })

async function fixture(page) {
  const state = { status: { enabled: true, realEnabled: true, available: true, reason: 'READY', connection: { provider: 'OPENAI', modelCode: 'fixture-model' } }, sessionMode: 'DEMO', calls: [], messages: [{ role: 'USER', content: '旧的话' }, { role: 'COMPANION', content: '旧版的长篇回复' }] }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request(); const path = new URL(request.url()).pathname
    state.calls.push({ path, method: request.method(), body: request.postDataJSON() })
    if (path === '/api/companion/presence/status') return ok(route, state.status)
    if (path === '/api/companion/presence/session') {
      state.sessionMode = request.postDataJSON().mode
      return ok(route, { sessionId: 'fixture-session', mode: state.sessionMode })
    }
    if (path === '/api/companion/presence/decide') {
      const data = request.postDataJSON()
      return ok(route, { ...data, sequence: String(data.sequence), mode: state.sessionMode, action: 'SPEAK', text: '来自真实模式夹具的短句', reason: 'DECIDED', remainingDecisions: 11 })
    }
    if (path === '/api/user/current') return ok(route, { id: '42', userName: '测试用户', userRole: 'user' })
    if (path === '/api/companion/chat/mode') return ok(route, { mode: 'DEMO', modelAvailable: false, modelSource: 'NONE', modelBroken: false })
    if (path === '/api/companion/chat/history' && request.method() === 'DELETE') {
      const deleted = state.messages.length; state.messages = []
      return ok(route, deleted)
    }
    if (path === '/api/companion/chat/history') return ok(route, { records: state.messages })
    return ok(route, { records: [], total: 0 })
  })
  return state
}
const deletes = state => state.calls.filter(call => call.method === 'DELETE' && call.path === '/api/companion/chat/history')

test('clearing the chat history needs a confirmation and refreshes an open chat', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion/rehearsal')
  await page.getByRole('button', { name: '打开普通聊天' }).click()
  await expect(page.getByText('旧版的长篇回复')).toBeVisible()

  const clear = page.getByTestId('clear-chat')
  await clear.getByRole('button', { name: '清空对话记录' }).click()
  await clear.getByRole('button', { name: '取消' }).click()
  expect(deletes(state)).toEqual([])

  await clear.getByRole('button', { name: '清空对话记录' }).click()
  await clear.getByRole('button', { name: '确定清空' }).click()
  await expect(clear.getByText('已清空 2 条聊天记录。')).toBeVisible()
  await expect(page.getByText('旧版的长篇回复')).toHaveCount(0)
  expect(deletes(state)).toHaveLength(1)
})

test('clearing works without an open chat and never opens a presence session', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion/rehearsal')
  const clear = page.getByTestId('clear-chat')
  await clear.getByRole('button', { name: '清空对话记录' }).click()
  await clear.getByRole('button', { name: '确定清空' }).click()
  await expect(clear.getByText('已清空 2 条聊天记录。')).toBeVisible()
  expect(state.calls.some(call => call.path.includes('/presence/') && call.method !== 'GET')).toBe(false)
})

test('real mode only submits after selection, fresh opt-in and a manual scenario', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion/rehearsal')
  await expect(page.getByTestId('presence-status')).toContainText('OPENAI · fixture-model')
  await expect(page.getByTestId('presence-status').getByRole('link', { name: '前往模型设置 →' })).toHaveAttribute('href', '/model-gateway')
  expect(state.calls.filter(call => call.path.includes('/presence/')).map(call => call.method)).toEqual(['GET'])
  await page.getByRole('checkbox', { name: '开启本页模拟试演（默认关闭）' }).check()
  await page.getByRole('radio', { name: '真实模型 · BYOK（可能收费）' }).check()
  const enabled = page.getByRole('checkbox', { name: '开启本页真实模型试演（可能产生 BYOK 费用）' })
  await expect(enabled).not.toBeChecked()
  await expect(page.getByRole('button', { name: '上传完成', exact: true })).toBeDisabled()
  await enabled.check()
  expect(state.calls.filter(call => call.path.includes('/presence/') && call.method !== 'GET')).toEqual([])
  await page.getByRole('button', { name: '上传完成', exact: true }).click()
  await expect(page.getByText('来自真实模式夹具的短句')).toBeVisible()
  const writes = state.calls.filter(call => call.path.includes('/presence/') && call.method === 'POST')
  expect(writes.map(call => call.path)).toEqual(['/api/companion/presence/session', '/api/companion/presence/decide'])
  expect(writes[0].body.mode).toBe('BYOK')
  expect(Object.keys(writes[1].body).sort()).toEqual(['sessionId', 'eventId', 'sequence', 'observedAt', 'eventType', 'page'].sort())
  await page.getByRole('button', { name: '刷新模型状态' }).click()
  await expect(enabled).not.toBeChecked()
  await expect(page.getByText('来自真实模式夹具的短句')).toHaveCount(0)
  expect(state.calls.filter(call => call.path === '/api/companion/presence/decide')).toHaveLength(1)
  await page.getByRole('radio', { name: '服务端演示 · 非真实模型' }).check()
  await expect(page.getByRole('checkbox', { name: '开启本页模拟试演（默认关闭）' })).not.toBeChecked()
})

test('unavailable BYOK status has a settings path and readonly refresh without enabling paid calls', async ({ page }) => {
  const state = await fixture(page)
  state.status = { enabled: true, realEnabled: true, available: false, reason: 'BYOK_REQUIRED', connection: null }
  await page.goto('/companion/rehearsal')
  await expect(page.getByTestId('presence-status')).toContainText('配置并启用语言代理 BYOK')
  const real = page.getByRole('radio', { name: '真实模型 · BYOK（可能收费）' })
  await expect(real).toBeDisabled()
  state.status = { enabled: true, realEnabled: true, available: true, reason: 'READY', connection: { provider: 'OPENAI', modelCode: 'fixture-model' } }
  await page.getByRole('button', { name: '刷新模型状态' }).click()
  await expect(real).toBeEnabled()
  await expect(real).not.toBeChecked()
  await expect(page.getByRole('checkbox', { name: '开启本页模拟试演（默认关闭）' })).not.toBeChecked()
  expect(state.calls.filter(call => call.path.includes('/presence/') && call.method !== 'GET')).toEqual([])
})

test('real-mode controls and cost notice remain usable at narrow mobile width', async ({ page }, testInfo) => {
  await fixture(page)
  await page.setViewportSize({ width: 320, height: 900 })
  await page.goto('/companion/rehearsal')
  await page.getByRole('radio', { name: '真实模型 · BYOK（可能收费）' }).check()
  await expect(page.getByTestId('presence-status')).toContainText('取消也可能已产生费用')
  const overflow = await page.evaluate(() => globalThis.document.documentElement.scrollWidth > globalThis.innerWidth)
  expect(overflow).toBe(false)
  await page.screenshot({ path: testInfo.outputPath('rehearsal-byok-mobile.png'), fullPage: true })
})
