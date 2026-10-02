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

test('recipe delayed, failed, and successful empty reads have distinct copy and explicit recovery', async ({ page }) => {
  let release
  const held = new Promise(resolve => { release = resolve })
  let failed = true
  const mutations = await fixture(page, {
    '/api/recipe/templates': async route => {
      await held
      return failed ? route.fulfill({ json: { code: 50000, message: '模板暂不可用' } }) : ok(route, [])
    }
  })
  await page.goto('/recipes')
  await expect(page.getByText('正在加载模板…', { exact: true })).toBeVisible()
  await expect(page.getByText('暂无可用模板。')).toHaveCount(0)
  release()
  await expect(page.getByText('模板暂不可用', { exact: true })).toBeVisible()
  await expect(page.getByText('正在加载模板…', { exact: true })).toHaveCount(0)
  await expect(page.getByText('暂无可用模板。')).toHaveCount(0)
  failed = false
  await page.getByRole('button', { name: '重新加载模板', exact: true }).click()
  await expect(page.getByText('暂无可用模板。')).toBeVisible()
  await expect(page.getByText('模板暂不可用', { exact: true })).toHaveCount(0)
  expect(mutations).toEqual([])
})

test('recipe explains confirmation and record versions without sending an execution', async ({ page }) => {
  const recipe = { id: '91', name: '测试配方', status: 'ENABLED', latestVersion: 2 }
  const mutations = await fixture(page, {
    '/api/recipe': route => ok(route, [recipe]),
    '/api/recipe/capabilities': route => ok(route, [{ capability: 'STORY_DRAFT', open: true }]),
    '/api/recipe/91': route => ok(route, {
      recipe, latest: { version: 2, whenJson: '{"type":"WEEKLY_REVIEW"}', ifJson: '[]', thenJson: '{"capability":"STORY_DRAFT"}' }, versions: [{ version: 1 }, { version: 2 }]
    }),
    '/api/recipe/91/executions': route => ok(route, [{ id: '92', status: 'PENDING_CONFIRM', recipeVersion: 1, sourcePictureIds: ['100'], opportunityKey: 'weekly-1' }])
  })
  await page.goto('/recipes')
  await page.getByRole('button', { name: '查看', exact: true }).click()
  await expect(page.getByText('未添加额外条件。触发后仍需确认执行。')).toBeVisible()
  await expect(page.getByText('发布后，新试运行使用新版本；已有记录仍按记录中的版本确认执行。')).toBeVisible()
  await expect(page.getByRole('button', { name: /确认执行（使用试运行的 1 张图片）/ })).toBeEnabled()
  await expect(page.getByText('每次触发都会执行动作', { exact: false })).toHaveCount(0)
  expect(mutations).toEqual([])
})

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
  test(`model connection rules stay visible and metadata is keyboard-disclosable at ${width}px`, async ({ page }, testInfo) => {
    const mutations = await fixture(page, {
      '/api/model/credentials': route => ok(route, [{ id: 'key-1', provider: 'DEEPSEEK', tail4: '1234', algorithm: 'AES-GCM', revision: 1 }]),
      '/api/model/connections': route => ok(route, [{ id: 'connection-1', provider: 'DEEPSEEK', displayName: '测试连接', endpointUri: 'https://api.deepseek.com/v1', modelCode: 'deepseek-chat', enabled: false, revision: 1 }])
    })
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/model-gateway')
    await expect(page.getByRole('heading', { name: '模型连接', exact: true })).toBeVisible()
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
  await expect(page.getByText('还没有 API Key。填写供应商和密钥后保存。')).toBeVisible()
  await expect(page.getByText('还没有调用记录。测试连接或使用模型后，可在这里查看。')).toBeVisible()
  await expect(page.getByText('调用记录暂不可用', { exact: true })).toHaveCount(0)
  expect(mutations).toEqual([])
})
