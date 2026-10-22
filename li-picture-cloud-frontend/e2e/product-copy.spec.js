import { test, expect } from '@playwright/test'

const runtimeErrors = new WeakMap()
test.afterEach(async ({ page }) => { expect(runtimeErrors.get(page) || []).toEqual([]) })

const member = { id: '42', userAccount: 'copy-test', userName: '文案测试', userRole: 'user' }
async function fixture(page, overrides = {}) {
  const mutations = []
  const errors = []
  runtimeErrors.set(page, errors)
  page.on('pageerror', error => errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const readOnlyPost = request.method() === 'POST' && [
      '/api/space/list/page/vo', '/api/picture/list/page/vo'
    ].includes(path)
    if (request.method() !== 'GET' && !readOnlyPost) mutations.push(path)
    if (overrides[path]) return overrides[path](route)
    let data = []
    if (path === '/api/user/current') data = member
    else if (path === '/api/model/quick/current') data = { status: 'PLATFORM' }
    else if (path === '/api/model/quick/providers') data = ['DEEPSEEK']
    else if (path === '/api/model/quick/vision-search') data = { forcedSearch: false, supported: true, modelCode: 'qwen3.8-max' }
    else if (path.includes('/list/page')) data = { records: [], total: 0 }
    else if (path === '/api/space/list/level') data = [
      { value: 0, text: '普通版', maxCount: 100, maxSize: 104857600 },
      { value: 1, text: '专业版', maxCount: 1000, maxSize: 1048576000 },
      { value: 2, text: '旗舰版', maxCount: 10000, maxSize: 10485760000 }
    ]
    return route.fulfill({ json: { code: 0, data } })
  })
  return mutations
}
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })

async function openAdvanced(page) {
  const advanced = page.getByTestId('gateway-advanced')
  await expect(advanced).not.toHaveAttribute('open')
  await advanced.locator('summary').focus()
  await page.keyboard.press('Enter')
  await expect(advanced).toHaveAttribute('open', '')
}

test('space tiers show finite limits without unlimited promises on a narrow screen', async ({ page }, testInfo) => {
  await fixture(page)
  await page.setViewportSize({ width: 320, height: 844 })
  await page.goto('/space/create')
  await expect(page.getByRole('heading', { name: '创建空间', exact: true })).toBeVisible()
  await expect(page.getByText('适合更大规模的图片管理，容量和数量上限见上方。')).toBeVisible()
  await expect(page.locator('.level-option')).toHaveCount(3)
  await expect(page.getByText(/不设限|海量/)).toHaveCount(0)
  expect(await page.evaluate(() => {
    // eslint-disable-next-line no-undef
    return document.documentElement.scrollWidth <= window.innerWidth
  })).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('space-copy-320.png'), fullPage: true })
})

for (const width of [1440, 320]) {
  test(`advanced model connection rules and metadata are keyboard-disclosable at ${width}px`, async ({ page }, testInfo) => {
    const mutations = await fixture(page, {
      '/api/model/credentials': route => ok(route, [{ id: 'key-1', provider: 'DEEPSEEK', tail4: '1234', algorithm: 'AES-GCM', revision: 1 }]),
      '/api/model/connections': route => ok(route, [{ id: 'connection-1', provider: 'DEEPSEEK', displayName: '测试连接', endpointUri: 'https://api.deepseek.com/v1', modelCode: 'deepseek-chat', enabled: false, revision: 1 }])
    })
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/model-gateway')
    await expect(page.getByRole('heading', { name: '模型连接', exact: true })).toBeVisible()
    await openAdvanced(page)
    await expect(page.getByText(/不会自动使用平台额度/)).toBeVisible()
    await expect(page.getByText(/平台白名单内的 HTTPS 端点/)).toBeVisible()
    await expect(page.getByText(/删除 API Key 后/)).toBeVisible()
    const details = page.getByTestId('credential-list').locator('details')
    await expect(details).not.toHaveAttribute('open')
    await details.locator('summary').focus()
    await page.keyboard.press('Enter')
    await expect(details).toHaveAttribute('open', '')
    await expect(details.getByText('加密算法 AES-GCM · 版本 1')).toBeVisible()
    await page.getByRole('button', { name: '更换 API Key', exact: true }).click()
    await expect(page.getByRole('form', { name: '更换 API Key' })).toContainText('旧密钥仍会保留，其他连接不受影响。')
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await expect(page.getByRole('form', { name: '更换 API Key' })).toHaveCount(0)
    expect(mutations).toEqual([])
    expect(await page.evaluate(() => {
      // eslint-disable-next-line no-undef
      return document.documentElement.scrollWidth <= window.innerWidth
    })).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`model-copy-${width}.png`), fullPage: true })
  })
}

test('model connection loading and read failure never claim there are no saved keys', async ({ page }) => {
  let release
  const held = new Promise(resolve => { release = resolve })
  let failed = true
  const mutations = await fixture(page, {
    '/api/model/usage': async route => {
      await held
      return failed ? route.fulfill({ json: { code: 50000, message: '调用记录暂不可用' } }) : ok(route, [])
    }
  })
  await page.goto('/model-gateway')
  await openAdvanced(page)
  await expect(page.getByText('正在加载模型连接…', { exact: true })).toBeVisible()
  await expect(page.getByText('还没有 API Key。填写供应商和密钥后保存。')).toHaveCount(0)
  await expect(page.getByText('还没有模型连接。填写端点和模型信息，添加后可启用并测试。')).toHaveCount(0)
  release()
  await expect(page.getByText('调用记录暂不可用', { exact: true })).toBeVisible()
  await expect(page.getByText('正在加载模型连接…', { exact: true })).toHaveCount(0)
  await expect(page.getByText('还没有 API Key。填写供应商和密钥后保存。')).toHaveCount(0)
  await expect(page.getByText('还没有调用记录。测试连接或使用模型后，可在这里查看。')).toHaveCount(0)
  failed = false
  await page.reload()
  await openAdvanced(page)
  await expect(page.getByText('还没有 API Key。填写供应商和密钥后保存。')).toBeVisible()
  await expect(page.getByText('还没有调用记录。测试连接或使用模型后，可在这里查看。')).toBeVisible()
  await expect(page.getByText('调用记录暂不可用', { exact: true })).toHaveCount(0)
  expect(mutations).toEqual([])
})
