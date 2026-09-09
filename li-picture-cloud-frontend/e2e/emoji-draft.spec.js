import { test, expect } from '@playwright/test'

// 表情草稿当前暂未开放：文字表情候选必须来自"授权图片的视觉理解"（安全元素摘要），
// 不能用图片数量与分类模拟。本切片验证：前端明示"暂未开放（需要视觉理解能力）"，
// 没有生成/选中/保存按钮，全程零创作写请求（零模型调用、零成本）。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

test('shows the explicit emoji not-open notice and never calls any creator', async ({ page }) => {
  await login(page)
  const creationCalls = []
  page.on('request', request => {
    const url = request.url()
    if (url.includes('/api/creation/emoji')
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

    // 面板明示"文字表情草稿暂未开放（需要视觉理解）"，且不提供任何生成/保存入口。
    await expect(page.getByText('让伙伴从图片里挑一句俏皮话')).toBeVisible()
    await expect(page.getByTestId('emoji-unavailable')).toBeVisible()
    await expect(page.getByText(/文字表情草稿暂未开放/)).toBeVisible()
    await expect(page.getByText(/视觉理解/).first()).toBeVisible()
    await expect(page.getByRole('button', { name: '开始生成表情' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '生成候选' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '保存作品' })).toHaveCount(0)

    // 全程零表情草稿写请求（GET 列表之外没有任何请求）。
    await page.waitForTimeout(500)
    expect(creationCalls).toHaveLength(0)
  } finally {
    // 无路由/连接需要清理：本切片没有创建任何模型连接或任务。
  }
})
