import { test, expect } from '@playwright/test'

const ok = (route, data) => route.fulfill({ json: { code: 0, data } })
const fail = (route, message) => route.fulfill({ json: { code: 50001, data: null, message } })
const KEY = 'sk-quick-e2e-0001'
const DEEPSEEK_MODELS = ['deepseek-chat', 'deepseek-reasoner']
const PROVIDERS = ['DEEPSEEK', 'OPENAI', 'ANTHROPIC', 'GOOGLE', 'MOONSHOT', 'DASHSCOPE', 'SILICONFLOW', 'ZHIPU']

async function fixture(page, { current = { status: 'PLATFORM' }, models = DEEPSEEK_MODELS, listed = true } = {}) {
  const state = { calls: [], errors: [], current }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const url = new URL(request.url())
    const body = request.postDataJSON?.() ?? null
    state.calls.push({ path: url.pathname, method: request.method(), query: url.search, body })
    if (url.pathname === '/api/user/current') return ok(route, { id: '42', userName: 'Quick', userRole: 'user' })
    if (url.pathname === '/api/model/quick/providers') {
      return ok(route, PROVIDERS.map(provider => ({ provider, label: provider, recommendedModel: 'x' })))
    }
    if (url.pathname === '/api/model/quick/current' && request.method() === 'GET') return ok(route, state.current)
    if (url.pathname === '/api/model/quick/current' && request.method() === 'DELETE') {
      state.current = { status: 'PLATFORM' }
      return ok(route, state.current)
    }
    if (url.pathname === '/api/model/quick/models') {
      if (body.apiKey.startsWith('sk-rejected')) return fail(route, 'API Key 无效或没有权限，请检查后重试')
      return ok(route, { provider: body.provider, providerLabel: 'DeepSeek', models, recommendedModel: models[0], listed })
    }
    if (url.pathname === '/api/model/quick/connect') {
      if (body.modelCode.includes('unavailable')) return fail(route, '这个模型不能用于对话，请换一个模型')
      state.current = { status: 'CONNECTED', provider: body.provider, providerLabel: 'DeepSeek', modelCode: body.modelCode, connectionId: '9' }
      return ok(route, state.current)
    }
    if (url.pathname.startsWith('/api/model/')) return ok(route, [])
    return ok(route, { records: [], total: 0 })
  })
  return state
}

const status = page => page.getByTestId('quick-status')

test('choose vendor, paste key, pick a model and connect in one flow', async ({ page }, testInfo) => {
  const state = await fixture(page)
  await page.goto('/model-gateway')
  await expect(page.getByRole('heading', { name: '模型连接', level: 1 })).toBeVisible()
  await expect(status(page)).toContainText('还没有连接自己的模型')
  // Manual management stays folded and unloaded until asked for.
  expect(state.calls.some(call => call.path === '/api/model/credentials')).toBe(false)

  await page.getByRole('button', { name: /DeepSeek/ }).click()
  await expect(page.getByRole('button', { name: /DeepSeek/ })).toHaveAttribute('aria-pressed', 'true')
  const key = page.getByLabel('DeepSeek API Key')
  await expect(key).toHaveAttribute('type', 'password')
  await expect(page.getByRole('link', { name: '去DeepSeek创建 Key ↗' })).toHaveAttribute('href', 'https://platform.deepseek.com/api_keys')
  await key.fill(KEY)
  await key.press('Enter')

  const models = page.getByTestId('quick-models')
  await expect(models.getByRole('radio', { name: /deepseek-chat/ })).toBeChecked()
  await expect(models.getByText('推荐')).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('quick-connect-models.png'), fullPage: true })
  await models.getByRole('radio', { name: /deepseek-reasoner/ }).check()
  await page.getByRole('button', { name: '完成连接' }).click()

  await expect(status(page)).toContainText('DeepSeek · deepseek-reasoner')
  await expect(page.getByText('已连接。伙伴聊天已切换为使用这个模型。')).toBeVisible()
  await expect(page.getByRole('button', { name: '完成连接' })).toHaveCount(0)
  await expect(key).toHaveCount(0)
  const connect = state.calls.find(call => call.path === '/api/model/quick/connect')
  expect(connect.body).toEqual({ provider: 'DEEPSEEK', apiKey: KEY, modelCode: 'deepseek-reasoner' })
  // The key is only ever sent in POST bodies, never in URLs or browser storage.
  expect(state.calls.every(call => !call.query.includes(KEY))).toBe(true)
  expect(await page.evaluate(() => JSON.stringify({ ...globalThis.localStorage }) + JSON.stringify({ ...globalThis.sessionStorage })))
    .not.toContain(KEY)
  await page.screenshot({ path: testInfo.outputPath('quick-connect-connected.png'), fullPage: true })
  expect(state.errors).toEqual([])
})

test('rejected keys and unusable models explain themselves without leaving the flow', async ({ page }) => {
  const state = await fixture(page, { models: ['deepseek-chat', 'stub-unavailable-model'] })
  await page.goto('/model-gateway')
  await page.getByRole('button', { name: /DeepSeek/ }).click()
  const key = page.getByLabel('DeepSeek API Key')
  await key.fill('sk-rejected-1')
  await page.getByRole('button', { name: '获取模型' }).click()
  await expect(page.getByRole('alert')).toHaveText('API Key 无效或没有权限，请检查后重试')
  await expect(page.getByTestId('quick-models')).toHaveCount(0)

  await key.fill(KEY)
  await page.getByRole('button', { name: '获取模型' }).click()
  await page.getByTestId('quick-models').getByRole('radio', { name: /stub-unavailable-model/ }).check()
  await page.getByRole('button', { name: '完成连接' }).click()
  await expect(page.getByRole('alert')).toHaveText('这个模型不能用于对话，请换一个模型')
  await expect(status(page)).toContainText('还没有连接自己的模型')

  // Switching vendor clears the key and the model list.
  await page.getByRole('button', { name: /智谱/ }).click()
  await expect(page.getByLabel('智谱 API Key')).toHaveValue('')
  await expect(page.getByTestId('quick-models')).toHaveCount(0)
  expect(state.errors).toEqual([])
})

test('long catalogs can be filtered and unlisted vendors say the list is suggested', async ({ page }) => {
  const many = Array.from({ length: 14 }, (_, index) => `model-${String(index).padStart(2, '0')}`)
    .concat(['Qwen/Qwen2.5-7B-Instruct'])
  await fixture(page, { models: many, listed: false })
  await page.goto('/model-gateway')
  await page.getByRole('button', { name: /硅基流动/ }).click()
  await page.getByLabel('硅基流动 API Key').fill(KEY)
  await page.getByRole('button', { name: '获取模型' }).click()
  await expect(page.getByText('没有提供模型目录，下面是常用模型')).toBeVisible()
  await page.getByLabel('筛选模型').fill('qwen')
  await expect(page.getByTestId('quick-models').getByRole('radio')).toHaveCount(1)
  await page.getByLabel('筛选模型').fill('nothing-matches')
  await expect(page.getByText('没有匹配的模型。')).toBeVisible()
})

test('connected models can be replaced or dropped back to the platform default', async ({ page }) => {
  const state = await fixture(page, {
    current: { status: 'CONNECTED', provider: 'DEEPSEEK', providerLabel: 'DeepSeek', modelCode: 'deepseek-chat', connectionId: '9' }
  })
  await page.goto('/model-gateway')
  await expect(status(page)).toContainText('DeepSeek · deepseek-chat')
  await expect(page.getByRole('button', { name: /OpenAI/ })).toHaveCount(0)
  await page.getByRole('button', { name: '更换模型' }).click()
  await expect(page.getByRole('button', { name: /OpenAI/ })).toBeVisible()
  await page.getByRole('button', { name: '取消更换' }).click()
  await expect(page.getByRole('button', { name: /OpenAI/ })).toHaveCount(0)

  await page.getByRole('button', { name: '改用平台默认' }).click()
  await expect(status(page)).toContainText('还没有连接自己的模型')
  await expect(page.getByText('已改用平台默认。之前的连接仍保留在高级设置里。')).toBeVisible()
  expect(state.calls.filter(call => call.path === '/api/model/quick/current' && call.method === 'DELETE')).toHaveLength(1)

  // Advanced settings load only when opened.
  await page.getByTestId('gateway-advanced').getByText('高级设置', { exact: true }).click()
  await expect(page.getByRole('heading', { name: 'API Key', level: 2 })).toBeVisible()
  expect(state.calls.some(call => call.path === '/api/model/credentials')).toBe(true)
  expect(state.errors).toEqual([])
})

test.describe('narrow screens', () => {
  test.use({ viewport: { width: 360, height: 780 }, hasTouch: true, isMobile: true })
  test('vendor grid and key form fit without horizontal scroll', async ({ page }, testInfo) => {
    await fixture(page)
    await page.goto('/model-gateway')
    await page.getByRole('button', { name: /DeepSeek/ }).tap()
    await page.getByLabel('DeepSeek API Key').fill(KEY)
    await page.getByRole('button', { name: '获取模型' }).tap()
    await expect(page.getByTestId('quick-models')).toBeVisible()
    expect(await page.evaluate(() => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('quick-connect-mobile.png'), fullPage: true })
  })
})
