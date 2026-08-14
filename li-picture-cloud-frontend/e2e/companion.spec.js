import { test, expect } from '@playwright/test'

// 同一个后端进程内的串行故事线：测试 1 完成唤醒与喂养，
// 测试 2 操作测试 1 留下的记忆候选，测试 3 开启契约并验证提案闭环。
test.describe.configure({ mode: 'serial' })

async function login(page) {
  const response = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(response.ok(), `login failed: ${response.status()} ${await response.text()}`).toBeTruthy()
}

test('awakens a companion and recovers one private-picture feed without double growth', async ({ page }) => {
  await login(page)

  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)
  await expect(page.getByText('实际来源会逐条写入成长档案')).toBeVisible()
  await expect(page.getByText('未读取图片内容，也未调用视觉模型')).toBeVisible()
  await page.getByRole('button', { name: '唤醒我的伙伴' }).click()
  await expect(page.getByText('光点', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: /旅行样片/ }).click()
  const keys = []
  let loseFirstResponse = true
  await page.route('**/api/companion/feed', async route => {
    const payload = route.request().postDataJSON()
    keys.push(payload.idempotencyKey)
    if (loseFirstResponse) {
      loseFirstResponse = false
      const backendResponse = await route.fetch()
      expect(backendResponse.ok()).toBeTruthy()
      await route.abort('failed')
      return
    }
    await route.continue()
  })

  await page.getByRole('button', { name: '喂给伙伴' }).evaluate(button => {
    button.click()
    button.click()
  })
  await expect(page.getByRole('button', { name: '重试这次喂养' })).toBeVisible()
  expect(keys).toHaveLength(1)
  await page.getByRole('button', { name: '重试这次喂养' }).click()

  await expect(page.getByText('这次喂养已安全完成，没有重复成长。')).toBeVisible()
  expect(keys).toHaveLength(2)
  expect(keys[1]).toBe(keys[0])
  await expect(page.getByText('42 / 100 生命经验')).toBeVisible()
  await expect(page.getByText('+42 生命经验')).toBeVisible()
  // 喂养故事、情绪摘要与记忆都统一使用"伙伴说"气泡；按文案过滤各自的展示。
  await expect(page.getByRole('group', { name: '伙伴说' })
    .filter({ hasText: '演示营养让伙伴练习了观察与叙事。' })).toBeVisible()
  await expect(page.getByTestId('growth-trait-curiosity').first()).toContainText('+0.60')
  await expect(page.getByTestId('skill-IMAGE_OBSERVATION')).toContainText('18 / 100')
  await expect(page.getByTestId('skill-STORY_CREATION')).toContainText('12 / 100')
  await expect(page.locator('.timeline-list').getByRole('link', { name: '图片 #102', exact: true }).first())
    .toHaveAttribute('href', '/picture/102')
  await expect(page.getByTestId('growth-nutrition-label').first())
    .toContainText('演示营养（未读取图片内容）')
  await expect(page.getByText('未进行内容理解').first()).toBeVisible()
  // Demo 喂养产生一条待确认记忆候选，情绪与关系面板也随喂养出现。
  await expect(page.getByTestId('memory-status').first()).toHaveText('待确认')
  await expect(page.getByText('伙伴记得一张让它练习了观察与叙事的演示图片，它把这次练习记进了档案。')).toBeVisible()
  // 站内对话与主动提案面板：Demo 档聊天不调模型，契约默认关闭所以没有主动提案。
  await expect(page.getByText('和伙伴说说话')).toBeVisible()
  await expect(page.getByText('伙伴现在没有主动提议。开启主动设置后，它会挑合适的时刻轻轻出现。')).toBeVisible()
  // Demo 档对话：发送一条消息收到确定性回复，用户气泡与伙伴气泡都在。
  await page.getByLabel('对伙伴说的话').fill('你好呀')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('我在听。你可以和我聊聊图片，或者从图库里挑一张喂给我，我会慢慢记住我们的经历。'))
    .toBeVisible()
  await expect(page.getByText('你好呀')).toBeVisible()

  const homeResponse = await page.request.get('/api/companion/me')
  expect(homeResponse.ok()).toBeTruthy()
  const homeBody = await homeResponse.json()
  expect(homeBody.data.companion.lifeExperience).toBe('42')
  expect(homeBody.data.companion.revision).toBe('1')
  expect(homeBody.data.recentGrowth).toHaveLength(1)

  await page.reload()
  await expect(page.getByText('42 / 100 生命经验')).toBeVisible()
  await expect(page.getByText('+42 生命经验')).toBeVisible()
  await expect(page.getByTestId('skill-IMAGE_OBSERVATION')).toContainText('18 / 100')
  await expect(page.locator('.timeline-list').getByRole('link', { name: '图片 #102', exact: true }).first())
    .toHaveAttribute('href', '/picture/102')
  await expect(page.getByTestId('growth-nutrition-label').first())
    .toContainText('演示营养（未读取图片内容）')
  await expect(page.getByText('未读取图片内容，也未调用视觉模型')).toBeVisible()

  const sourceResponse = await page.request.get('/api/picture/get/vo?id=102')
  expect(sourceResponse.ok()).toBeTruthy()
  const sourceBody = await sourceResponse.json()
  expect(sourceBody.data).toMatchObject({
    id: '102',
    name: '旅行样片',
    url: '/images/mosaic/travel.jpg',
    originalUrl: '/images/mosaic/travel.jpg',
    spaceId: '10'
  })

  // 用 API fixture 只替换主页读取结果，验证前端能区分视觉成功与显式降级；不访问外网也不改数据库。
  await page.route('**/api/companion/me', async route => {
    const response = await route.fetch()
    const body = await response.json()
    const base = body.data.recentGrowth[0]
    body.data.recentGrowth = [
      {
        ...base,
        id: '302',
        createdTime: '2026-08-13T08:02:00Z',
        nutritionMode: 'VISUAL_MODEL',
        contentUnderstood: true,
        providerCode: 'dashscope',
        modelCode: 'qwen3.6-flash',
        confidence: 0.84,
        fallbackReasonCode: null,
        nutritionLabel: 'Qwen 视觉营养 · 已分析图片内容'
      },
      {
        ...base,
        id: '301',
        createdTime: '2026-08-13T08:01:00Z',
        nutritionMode: 'METADATA_DETERMINISTIC',
        contentUnderstood: false,
        providerCode: 'internal',
        modelCode: 'metadata-v1',
        confidence: null,
        fallbackReasonCode: 'VISION_TIMEOUT',
        nutritionLabel: '视觉服务暂不可用，本次使用图片元数据营养'
      }
    ]
    await route.fulfill({ response, json: body })
  })

  await page.reload()
  const labels = page.getByTestId('growth-nutrition-label')
  await expect(labels.nth(0)).toHaveText('Qwen 视觉营养 · 已分析图片内容')
  await expect(labels.nth(1)).toHaveText('视觉服务暂不可用，本次使用图片元数据营养')
  await expect(page.getByText('来源 dashscope / qwen3.6-flash')).toBeVisible()
  await expect(page.getByText('置信度 0.84')).toBeVisible()
  // 成长档案内恰好两条喂养故事气泡（页面另有情绪/记忆气泡，不在档案区内计数）。
  const archiveBubbles = page.locator('.timeline-list').getByRole('group', { name: '伙伴说' })
  await expect(archiveBubbles).toHaveCount(2)
  // 档案内两条来源图片链接（记忆面板使用"来源图片 #102"文案，精确匹配区分）。
  await expect(page.locator('.timeline-list').getByRole('link', { name: '图片 #102', exact: true })).toHaveCount(2)

  await page.reload()
  await expect(labels.nth(0)).toHaveText('Qwen 视觉营养 · 已分析图片内容')
  await expect(labels.nth(1)).toHaveText('视觉服务暂不可用，本次使用图片元数据营养')
})

test('memory lifecycle supports confirm correct ignore and delete', async ({ page }) => {
  await login(page)
  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)

  // 测试 1 留下了一条待确认记忆候选。
  const status = page.getByTestId('memory-status').first()
  await expect(status).toHaveText('待确认')

  // 确认 → 已确认。
  await page.locator('[data-action="confirm"]').first().click()
  await expect(status).toHaveText('已确认')

  // 纠正：改写文案，保留最初候选。
  await page.locator('[data-action="correct"]').first().click()
  await page.getByLabel('纠正这条记忆').fill('伙伴重新想起：那是安静的清晨。')
  await page.getByRole('button', { name: '保存纠正' }).click()
  await expect(page.getByText('伙伴重新想起：那是安静的清晨。')).toBeVisible()
  await expect(status).toHaveText('已确认')

  // 忽略 → 已忽略；再确认回来。
  await page.locator('[data-action="dismiss"]').first().click()
  await expect(status).toHaveText('已忽略')
  await page.locator('[data-action="confirm"]').first().click()
  await expect(status).toHaveText('已确认')

  // 删除 → 从列表消失（删除是终态且不再展示）。
  await page.locator('[data-action="delete"]').first().click()
  await expect(page.getByTestId('memory-status')).toHaveCount(0)
  await expect(page.getByText('伙伴重新想起：那是安静的清晨。')).toBeHidden()
})

test('enabling the contract produces a gated weekly review proposal', async ({ page }) => {
  await login(page)
  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)

  // 默认契约关闭：没有主动提案。
  await expect(page.getByText('伙伴现在没有主动提议。开启主动设置后，它会挑合适的时刻轻轻出现。'))
    .toBeVisible()

  // 开启契约：全天允许（起止相同 = 不设安静时段），频率保持 72 小时。
  await page.getByRole('button', { name: '主动设置' }).click()
  await page.getByRole('checkbox', { name: /允许伙伴主动提议/ }).check()
  await page.locator('.contract-times input').nth(0).fill('00:00')
  await page.locator('.contract-times input').nth(1).fill('00:00')
  await page.getByRole('button', { name: '保存主动设置' }).click()

  // 契约保存后立即重新评估：测试 1 的喂养产生每周回顾提案。
  await expect(page.getByText('这周你喂了我 1 次。想听我讲一段我们的故事吗？')).toBeVisible()
  await expect(page.getByText('类型 每周影像回顾')).toBeVisible()

  // 接受提案 → 终态并给出正向反馈，提案消失。
  await page.getByTestId('proposal-accept').click()
  await expect(page.getByText('好呀，伙伴已经记下了。')).toBeVisible()
  await expect(page.getByText('伙伴现在没有主动提议。开启主动设置后，它会挑合适的时刻轻轻出现。'))
    .toBeVisible()

  // 关闭契约后刷新，保持关闭。
  await page.getByRole('button', { name: '主动设置' }).click()
  await page.getByRole('checkbox', { name: /允许伙伴主动提议/ }).uncheck()
  await page.getByRole('button', { name: '保存主动设置' }).click()
  await expect(page.getByText('伙伴现在没有主动提议。开启主动设置后，它会挑合适的时刻轻轻出现。'))
    .toBeVisible()
})
