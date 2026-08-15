import { test, expect } from '@playwright/test'

// 多图融合纵向切片：选图(≥2) → 生成（E2E 图片 stub 返回固定 1x1 PNG）→
// 预览 → 选目标空间 → 保存回库（E2E 回库 stub 返回固定作品 ID，不走 COS）。
// 生成走 BYOK 图像路由：先用 API 建 OpenAI/gpt-image-2 连接并绑定
// IMAGE_CREATION 路由（连接探测为 stub），结束前清理，不影响后续模型控制中心故事线。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

async function setupImageRoute(page) {
  const credential = await page.request.post('/api/model/credentials', {
    data: { provider: 'OPENAI', apiKey: 'sk-fusion-e2e-key-1' }
  })
  expect(credential.ok(), `credential failed: ${credential.status()}`).toBeTruthy()
  // 雪花 ID 全程按字符串传递，绝不能经 Number() 折损精度。
  const credentialId = (await credential.json()).data.id

  const connection = await page.request.post('/api/model/connections', {
    data: {
      provider: 'OPENAI',
      displayName: 'Fusion E2E 连接',
      endpoint: 'https://api.openai.com/v1',
      modelCode: 'gpt-image-2',
      credentialId
    }
  })
  expect(connection.ok(), `connection failed: ${connection.status()}`).toBeTruthy()
  const connectionId = (await connection.json()).data.id

  const enable = await page.request.post(`/api/model/connections/${connectionId}/enable`)
  expect(enable.ok(), `enable failed: ${enable.status()}`).toBeTruthy()
  const probe = await page.request.post(`/api/model/connections/${connectionId}/test`)
  expect(probe.ok(), `probe failed: ${probe.status()}`).toBeTruthy()
  const routing = await page.request.put('/api/model/routing/IMAGE_CREATION', {
    data: { connectionId }
  })
  expect(routing.ok(), `routing failed: ${routing.status()}`).toBeTruthy()
  return { connectionId, credentialId }
}

async function teardownImageRoute(page, connectionId, credentialId) {
  await page.request.delete('/api/model/routing/IMAGE_CREATION')
  await page.request.delete(`/api/model/connections/${connectionId}`)
  await page.request.delete(`/api/model/credentials/${credentialId}`)
}

test('fuses two authorized pictures and saves the artwork to a chosen space', async ({ page }) => {
  await login(page)
  const route = await setupImageRoute(page)
  try {
    await page.goto('/companion')
    await expect(page).toHaveURL(/\/companion$/)
    // 等伙伴首页加载完成再判断是否唤醒（避免与首帧渲染竞态）。
    await expect(page.getByText('实际来源会逐条写入成长档案')).toBeVisible()
    // 融合面板随伙伴存在而出现；必要时先唤醒。
    const awaken = page.getByRole('button', { name: '唤醒我的伙伴' })
    if (await awaken.isVisible()) {
      await awaken.click()
      await expect(page.getByText('光点', { exact: true })).toBeVisible()
    }
    await expect(page.getByText('把多张图片融合成一张新作品')).toBeVisible()
    await expect(page.getByText('还没有融合作品。选好至少两张图片，开始第一次融合吧。')).toBeVisible()

    // 至少两张授权图片才能发起融合。
    await page.locator('.fusion-picture-choice').filter({ hasText: '旅行样片' })
      .locator('input').check()
    await page.locator('.fusion-picture-choice').filter({ hasText: '花园样片' })
      .locator('input').check()
    await page.getByRole('button', { name: '开始融合创作' }).click()

    // PENDING → 生成融合图 → 等待确认，展示图片 stub 生成的预览。
    const fusionList = page.getByTestId('fusion-list')
    await expect(fusionList.getByText('待开始')).toBeVisible()
    await fusionList.getByRole('button', { name: '生成融合图' }).click()
    await expect(fusionList.getByText('等待确认')).toBeVisible()

    const preview = fusionList.locator('.fusion-preview')
    await expect(preview).toBeVisible()
    await expect(preview).toHaveAttribute('src', /\/creation\/fusion\/\d+\/preview\?r=\d+/)

    // 必须显式选择目标空间；填写作品名后保存。
    await fusionList.locator('.fusion-space').selectOption({ label: '伙伴私有空间' })
    await fusionList.locator('.fusion-name').fill('融合纪念')
    await fusionList.getByRole('button', { name: '保存到图库' }).click()
    await expect(fusionList.getByText('已保存', { exact: true })).toBeVisible()
    await expect(fusionList.getByTestId('fusion-result')).toContainText('99001')

    // 刷新后保持已保存状态，预览仍可回看。
    await page.reload()
    // 先等融合面板锚点，再断言已保存状态，避免 reload 渲染竞态。
    await expect(page.getByText('把多张图片融合成一张新作品')).toBeVisible()
    await expect(page.getByTestId('fusion-list').getByText('已保存', { exact: true })).toBeVisible()

    const listResponse = await page.request.get('/api/creation/fusion')
    expect(listResponse.ok()).toBeTruthy()
    const body = await listResponse.json()
    expect(body.data).toHaveLength(1)
    expect(body.data[0].status).toBe('SAVED')
    expect(body.data[0].kind).toBe('IMAGE_FUSION')
    expect(body.data[0].sourcePictureIds).toContain('102')
    expect(body.data[0].sourcePictureIds).toContain('103')
  } finally {
    // 无论断言成败都清理图像路由，避免悬空规则/连接/凭据污染后续故事线。
    await teardownImageRoute(page, route.connectionId, route.credentialId)
  }
})
