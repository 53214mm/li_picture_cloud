import { test, expect } from '@playwright/test'

// 玩法配方工坊纵向切片：官方模板 → 创建配方 → 试运行（命中/报价）→
// 启用 → 真实执行（THEN=每周表情创建表情任务，不污染故事列表）→ 停用 → 删除。
// 执行走 E2E 图片/语言 stub 之外的纯业务管线，不发真实外网请求。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

async function createFromTemplate(page, templateName) {
  await page.locator('.template-card', { hasText: templateName })
    .getByRole('button', { name: '用这个模板创建' }).click()
  await expect(page.getByTestId('recipe-list').getByText(templateName, { exact: true }))
    .toBeVisible()
}

async function openRecipe(page, recipeName) {
  await page.getByTestId('recipe-list').locator('.recipe-row', { hasText: recipeName })
    .getByRole('button', { name: '查看' }).click()
}

test('creates recipes from templates, dry-runs, executes and replays', async ({ page }) => {
  await login(page)
  await page.goto('/recipes')
  await expect(page).toHaveURL(/\/recipes$/)
  await expect(page.getByText('玩法配方工坊')).toBeVisible()

  // 四个官方模板开箱即用。
  for (const name of ['旅行回顾', '生日故事', '每周表情', '旧照重制']) {
    await expect(page.locator('.template-card', { hasText: name })).toBeVisible()
  }
  await expect(page.getByTestId('recipe-empty')).toBeVisible()

  // 从模板创建两个配方：旅行回顾（带分类条件）与每周表情（无条件，用于真实执行）。
  await createFromTemplate(page, '旅行回顾')
  await createFromTemplate(page, '每周表情')
  const list = page.getByTestId('recipe-list')
  await expect(list.getByText('草稿', { exact: true })).toHaveCount(2)

  // 旅行回顾：详情展示定义，选择分类匹配的图片试运行 → 条件命中 + 报价。
  await openRecipe(page, '旅行回顾')
  await expect(page.getByTestId('recipe-definition')).toContainText('仅「旅行」分类')
  await expect(page.getByTestId('recipe-definition')).toContainText('生成故事草稿')
  await page.locator('.recipe-picture-choice').filter({ hasText: '旅行样片' })
    .locator('input').check()
  await page.getByRole('button', { name: '试运行' }).click()

  const travelExecutions = page.getByTestId('recipe-executions')
  await expect(travelExecutions.getByText('试运行')).toBeVisible()
  await expect(travelExecutions.getByText('生成故事草稿 · 平台额度 5 单位')).toBeVisible()
  await expect(travelExecutions.getByText('仅指定分类✓')).toBeVisible()

  // 每周表情：试运行 → 启用 → 确认执行 → 创作任务回放；随后停用。
  await openRecipe(page, '每周表情')
  await expect(page.getByTestId('recipe-definition')).toContainText('每周回顾时')
  await page.locator('.recipe-picture-choice').filter({ hasText: '旅行样片' })
    .locator('input').check()
  await page.getByRole('button', { name: '试运行' }).click()

  const weeklyExecutions = page.getByTestId('recipe-executions')
  await expect(weeklyExecutions.getByText('试运行')).toBeVisible()
  await expect(weeklyExecutions.getByText('生成表情候选 · 平台额度 1 单位')).toBeVisible()

  const weeklyRow = list.locator('.recipe-row', { hasText: '每周表情' })
  await weeklyRow.getByRole('button', { name: '启用' }).click()
  await expect(weeklyRow.getByText('已启用')).toBeVisible()

  await weeklyExecutions.getByRole('button', { name: /确认执行/ }).click()
  await expect(weeklyExecutions.getByText('已执行')).toBeVisible()
  await expect(weeklyExecutions.getByText(/创作任务 #\d+/)).toBeVisible()

  await weeklyRow.getByRole('button', { name: '停用' }).click()
  await expect(weeklyRow.getByText('已停用')).toBeVisible()

  // 接口回放：配方/版本/执行记录只含安全字段。
  const recipesResponse = await page.request.get('/api/recipe')
  expect(recipesResponse.ok()).toBeTruthy()
  const recipesBody = await recipesResponse.json()
  expect(recipesBody.data).toHaveLength(2)
  const weekly = recipesBody.data.find(item => item.name === '每周表情')
  expect(weekly.status).toBe('DISABLED')
  expect(weekly.latestVersion).toBe(1)
  const weeklyId = weekly.id

  const executionsResponse = await page.request.get(`/api/recipe/${weeklyId}/executions`)
  expect(executionsResponse.ok()).toBeTruthy()
  const executionsBody = await executionsResponse.json()
  // 一次执行 = 一条回放记录：试运行记录经确认执行后转为 EXECUTED 终态。
  expect(executionsBody.data).toHaveLength(1)
  const executed = executionsBody.data[0]
  expect(executed.status).toBe('EXECUTED')
  expect(executed.creationTaskId).toMatch(/^\d+$/)
  expect(executed.safeErrorCode).toBeNull()
  expect(executed.quoteJson).toContain('EMOJI_DRAFT')

  // 清理：删除两个配方回到空状态（执行记录随配方级联删除）。
  const rows = list.locator('.recipe-row')
  await rows.filter({ hasText: '旅行回顾' }).getByRole('button', { name: '删除' }).click()
  await rows.filter({ hasText: '每周表情' }).getByRole('button', { name: '删除' }).click()
  await expect(page.getByTestId('recipe-empty')).toBeVisible()
})
