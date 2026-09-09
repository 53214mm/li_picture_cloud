import { test, expect } from '@playwright/test'

// 多图融合当前未开放：真实融合需要支持多图参考输入的图像适配器（供应商能力验证）。
// 本切片验证：前端明示"未开放"文案、没有创建/生成/保存按钮，全程不会发出任何
// 融合创作类请求（零模型调用、零成本）。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

test('shows the explicit fusion not-open notice and never calls the generator', async ({ page }) => {
  await login(page)
  const creationCalls = []
  page.on('request', request => {
    const url = request.url()
    if (url.includes('/api/creation/fusion')
        && (url.endsWith('/generate') || url.includes('/create') || request.method() !== 'GET')) {
      creationCalls.push(`${request.method()} ${url}`)
    }
  })
  try {
    await page.goto('/companion')
    await expect(page).toHaveURL(/\/companion$/)
    // 等伙伴首页加载完成再判断是否唤醒（避免与首帧渲染竞态）。
    await expect(page.getByText('实际来源会逐条写入成长档案')).toBeVisible()
    const awaken = page.getByRole('button', { name: '唤醒我的伙伴' })
    if (await awaken.isVisible()) {
      await awaken.click()
      await expect(page.getByText('光点', { exact: true })).toBeVisible()
    }

    // 面板明示"真实多图融合能力尚未开放"，且不提供任何创建/生成/保存入口。
    await expect(page.getByText('把多张图片融合成一张新作品')).toBeVisible()
    await expect(page.getByTestId('fusion-unavailable')).toBeVisible()
    await expect(page.getByText(/真实多图融合能力尚未开放/)).toBeVisible()
    await expect(page.getByRole('button', { name: '开始融合创作' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '生成融合图' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '保存到图库' })).toHaveCount(0)

    // 全程零融合创作请求（GET 列表之外没有任何写请求）。
    await page.waitForTimeout(500)
    expect(creationCalls).toHaveLength(0)
  } finally {
    // 无路由/连接需要清理：本切片没有创建任何模型连接或任务。
  }
})
