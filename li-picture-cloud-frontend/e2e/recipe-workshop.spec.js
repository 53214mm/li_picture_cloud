import { test, expect } from '@playwright/test'

// 玩法配方工坊纵向切片（阶段 5 复审整改后）：
// 1) 官方模板与编辑器按"能力可用性"守门：未开放能力（表情草稿、多图融合）在前后端都被拒绝；
// 2) 试运行把来源图片绑定成快照，确认执行只认这份快照——改选图片必须重新试运行；
// 3) WHEN 真实触发：阶段 3 机会观察 + 契约守门通过后产生"待确认"执行记录，用户确认后才创建创作任务。
// 执行走 E2E 图片/语言/视觉 stub，不发真实外网请求。
test.describe.configure({ mode: 'serial' })

const RECIPE_USER = { userAccount: 'recipe_e2e', userPassword: 'LocalUser123!' }

async function login(page) {
  const response = await page.request.post('/api/user/login', { data: RECIPE_USER })
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

async function deleteRecipeViaApi(page, recipeId) {
  await page.request.delete(`/api/recipe/${recipeId}`)
}

test('gates official templates and editor actions by capability availability', async ({ page }) => {
  await login(page)
  await page.goto('/recipes')
  await expect(page).toHaveURL(/\/recipes$/)
  await expect(page.getByText('玩法配方工坊')).toBeVisible()

  // 四个官方模板仍然可见，但依赖未开放能力的两个必须显式禁用并给出原因。
  for (const name of ['旅行回顾', '生日故事', '每周表情', '旧照重制']) {
    await expect(page.locator('.template-card', { hasText: name })).toBeVisible()
  }
  const emojiTemplate = page.locator('.template-card', { hasText: '每周表情' })
  const fusionTemplate = page.locator('.template-card', { hasText: '旧照重制' })
  await expect(emojiTemplate.getByTestId('template-unavailable')).toContainText('未开放')
  await expect(fusionTemplate.getByTestId('template-unavailable')).toContainText('未开放')
  await expect(emojiTemplate.getByRole('button', { name: '用这个模板创建' })).toBeDisabled()
  await expect(fusionTemplate.getByRole('button', { name: '用这个模板创建' })).toBeDisabled()
  await expect(page.locator('.template-card', { hasText: '旅行回顾' })
    .getByRole('button', { name: '用这个模板创建' })).toBeEnabled()

  // 绕过前端直接调用也必须被服务端拒绝（不能只靠 UI 禁用），且不得留下空配方。
  const bypassFromTemplate = await page.request.post('/api/recipe/from-template', {
    data: { templateCode: 'weekly_emoji', name: '绕过模板' }
  })
  const bypassFromTemplateBody = await bypassFromTemplate.json()
  expect(bypassFromTemplateBody.code).not.toBe(0)
  expect(bypassFromTemplateBody.message).toContain('未开放')
  const afterBypassCreate = await (await page.request.get('/api/recipe')).json()
  expect(afterBypassCreate.data.filter(item => item.name === '绕过模板')).toHaveLength(0)

  await createFromTemplate(page, '旅行回顾')
  await openRecipe(page, '旅行回顾')
  await expect(page.getByTestId('recipe-definition')).toContainText('生成故事草稿')

  // 编辑器里未开放能力同样是禁用选项（<option> 不算表单控件，按 disabled 属性断言）。
  const capabilitySelect = page.getByTestId('recipe-editor').getByLabel('动作')
  await expect(capabilitySelect.locator('option[value="EMOJI_DRAFT"]'))
    .toHaveAttribute('disabled', '')
  await expect(capabilitySelect.locator('option[value="IMAGE_FUSION"]'))
    .toHaveAttribute('disabled', '')
  await expect(capabilitySelect.locator('option[value="STORY_DRAFT"]'))
    .not.toHaveAttribute('disabled', '')

  // 服务端同样拒绝把未开放能力发布成配方版本，版本号不得前进。
  const recipesBody = await (await page.request.get('/api/recipe')).json()
  const travel = recipesBody.data.find(item => item.name === '旅行回顾')
  const publishBypass = await page.request.post(`/api/recipe/${travel.id}/versions`, {
    data: { when: { type: 'SIMILAR_STORY' }, conditions: [], then: { capability: 'EMOJI_DRAFT' } }
  })
  const publishBypassBody = await publishBypass.json()
  expect(publishBypassBody.code).not.toBe(0)
  expect(publishBypassBody.message).toContain('未开放')
  const afterBypass = await (await page.request.get(`/api/recipe/${travel.id}`)).json()
  expect(afterBypass.data.versions).toHaveLength(1)

  await deleteRecipeViaApi(page, travel.id)
  await page.reload()
  await expect(page.getByTestId('recipe-empty')).toBeVisible()
})

test('binds the dry-run picture snapshot to the confirmed execution', async ({ page }) => {
  await login(page)
  await page.goto('/recipes')
  await createFromTemplate(page, '旅行回顾')
  await openRecipe(page, '旅行回顾')
  await expect(page.getByTestId('recipe-definition')).toContainText('仅「旅行」分类')

  // 试运行：用分类匹配的图片，记录命中与报价，并绑定来源图片快照。
  await page.locator('.recipe-picture-choice').filter({ hasText: '旅行样片' })
    .locator('input').check()
  await page.getByRole('button', { name: '试运行' }).click()

  const executions = page.getByTestId('recipe-executions')
  await expect(executions.getByText('试运行')).toBeVisible()
  await expect(executions.getByText('生成故事草稿 · 平台额度 5 单位')).toBeVisible()
  await expect(executions.getByText('仅指定分类✓')).toBeVisible()
  await expect(executions.getByText('来源图片 1 张')).toBeVisible()

  const list = page.getByTestId('recipe-list')
  const row = list.locator('.recipe-row', { hasText: '旅行回顾' })
  await row.getByRole('button', { name: '启用' }).click()
  await expect(row.getByText('已启用')).toBeVisible()

  // 改选另一张图片：不允许拿旧预览确认，必须重新试运行。
  await page.locator('.recipe-picture-choice').filter({ hasText: '旅行样片' })
    .locator('input').uncheck()
  await page.locator('.recipe-picture-choice').filter({ hasText: '花园样片' })
    .locator('input').check()
  await expect(executions.getByTestId('snapshot-changed')).toBeVisible()
  await expect(executions.getByRole('button', { name: /确认执行/ })).toBeDisabled()

  // 恢复成试运行时那一组图片后即可确认执行。
  await page.locator('.recipe-picture-choice').filter({ hasText: '花园样片' })
    .locator('input').uncheck()
  await page.locator('.recipe-picture-choice').filter({ hasText: '旅行样片' })
    .locator('input').check()
  await expect(executions.getByTestId('snapshot-changed')).toHaveCount(0)
  await executions.getByRole('button', { name: /确认执行/ }).click()
  await expect(executions.getByText('已执行')).toBeVisible()
  await expect(executions.getByText(/创作任务 #\d+/)).toBeVisible()

  // 接口回放：执行记录只认试运行时的那组图片，且只含安全字段。
  const recipesBody = await (await page.request.get('/api/recipe')).json()
  const travel = recipesBody.data.find(item => item.name === '旅行回顾')
  const executionsBody = await (await page.request.get(`/api/recipe/${travel.id}/executions`)).json()
  expect(executionsBody.data).toHaveLength(1)
  const executed = executionsBody.data[0]
  expect(executed.status).toBe('EXECUTED')
  expect(executed.sourcePictureIds).toEqual(['104'])
  expect(executed.opportunityKey).toBeNull()
  expect(executed.creationTaskId).toMatch(/^\d+$/)
  expect(executed.safeErrorCode).toBeNull()
  expect(executed.quoteJson).toContain('STORY_DRAFT')

  await deleteRecipeViaApi(page, travel.id)
})

test('a real WHEN opportunity produces a pending confirmation the user confirms', async ({ page }) => {
  await login(page)

  // 自建配方：WHEN=每周回顾，动作=生成故事草稿（未开放能力不能出现在这里）。
  const created = await (await page.request.post('/api/recipe', {
    data: { name: '每周回顾故事' }
  })).json()
  expect(created.code).toBe(0)
  const recipeId = created.data.id
  const published = await (await page.request.post(`/api/recipe/${recipeId}/versions`, {
    data: { when: { type: 'WEEKLY_REVIEW' }, conditions: [], then: { capability: 'STORY_DRAFT' } }
  })).json()
  expect(published.code).toBe(0)
  expect((await page.request.post(`/api/recipe/${recipeId}/enable`)).ok()).toBeTruthy()

  // 机会来源：唤醒伙伴并完整喂养一张图片，然后开启主动契约（00:00–00:00 = 不设安静时段）。
  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)
  await expect(page.getByText('实际来源会逐条写入成长档案')).toBeVisible()
  const awaken = page.getByRole('button', { name: '唤醒我的伙伴' })
  if (await awaken.isVisible()) {
    await awaken.click()
    await expect(page.getByText('光点', { exact: true })).toBeVisible()
  }
  await page.getByRole('button', { name: /旅行样片/ }).click()
  await page.getByRole('button', { name: '喂给伙伴' }).click()
  // 一次完整喂养后伙伴获得生命经验（E2E 走演示营养档，不读取图片内容、不调用视觉模型）。
  await expect(page.getByText(/\+\d+ 生命经验/).first()).toBeVisible()

  await page.getByRole('button', { name: '主动设置' }).click()
  await page.getByRole('checkbox', { name: /允许伙伴主动提议/ }).check()
  await page.locator('.contract-times input').nth(0).fill('00:00')
  await page.locator('.contract-times input').nth(1).fill('00:00')
  await page.getByRole('button', { name: '保存主动设置' }).click()

  // 机会真实存在（伙伴侧看到提案）+ 配方侧产生"待确认"执行记录（不创建任何创作任务）。
  await expect(page.getByText('类型 每周影像回顾')).toBeVisible()
  const proposed = await (await page.request.get(`/api/recipe/${recipeId}/executions`)).json()
  expect(proposed.data).toHaveLength(1)
  const pending = proposed.data[0]
  expect(pending.status).toBe('PENDING_CONFIRM')
  expect(pending.opportunityKey).toMatch(/^WEEKLY_REVIEW-/)
  expect(pending.sourcePictureIds).toEqual(['104'])
  expect(pending.creationTaskId).toBeNull()
  expect(pending.matchedJson).toContain('WEEKLY_REVIEW')

  // 用户确认后才调用 THEN：工坊里能看到机会触发的待确认记录并完成确认。
  await page.goto('/recipes')
  await openRecipe(page, '每周回顾故事')
  const executions = page.getByTestId('recipe-executions')
  await expect(executions.getByText('待确认（机会触发）')).toBeVisible()
  await expect(executions.getByTestId('execution-opportunity')).toContainText('WEEKLY_REVIEW-')
  await executions.getByRole('button', { name: /确认执行/ }).click()
  await expect(executions.getByText('已执行')).toBeVisible()
  await expect(executions.getByText(/创作任务 #\d+/)).toBeVisible()

  const confirmed = await (await page.request.get(`/api/recipe/${recipeId}/executions`)).json()
  expect(confirmed.data).toHaveLength(1)
  expect(confirmed.data[0].status).toBe('EXECUTED')
  expect(confirmed.data[0].opportunityKey).toMatch(/^WEEKLY_REVIEW-/)
  expect(confirmed.data[0].creationTaskId).toMatch(/^\d+$/)

  await deleteRecipeViaApi(page, recipeId)
})
