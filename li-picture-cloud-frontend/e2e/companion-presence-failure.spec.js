import { test, expect } from '@playwright/test'

async function fixture(page, reason) {
  let decisions = 0
  await page.route('**/*', async route => {
    const request = route.request(); const url = new URL(request.url())
    if (!['localhost', '127.0.0.1'].includes(url.hostname)) return route.abort()
    if (url.pathname.startsWith('/api/')) {
      let data = { records: [], total: 0 }
      if (url.pathname === '/api/user/current') data = { id: '42', userName: '本地夹具', userRole: 'user' }
      if (url.pathname === '/api/companion/presence/status') data = { enabled: true, realEnabled: true, available: true, reason: 'READY', connection: { provider: 'DEEPSEEK', modelCode: 'fixture-only' } }
      if (url.pathname === '/api/companion/presence/session') data = { sessionId: 'fixture-session', mode: 'BYOK' }
      if (url.pathname === '/api/companion/presence/decide') {
        decisions++
        const sent = request.postDataJSON()
        data = { ...sent, sequence: String(sent.sequence), mode: 'BYOK', action: 'SILENT', text: null,
          emote: null, reason, remainingDecisions: 7, expiresAt: new Date(Date.now() + 600000).toISOString() }
      }
      return route.fulfill({ json: { code: 0, data } })
    }
    if (!['GET', 'HEAD'].includes(request.method())) return route.abort()
    return route.continue()
  })
  await page.goto('/companion/rehearsal')
  await page.getByRole('radio', { name: '真实模型 · BYOK（可能收费）' }).check()
  await page.getByRole('checkbox', { name: '开启本页真实模型试演（可能产生 BYOK 费用）' }).check()
  await page.getByRole('button', { name: '上传完成', exact: true }).click()
  await expect(page.locator('.result')).toContainText('服务端剩余 7 次决策')
  expect(decisions).toBe(1)
  return page.locator('.result')
}

for (const reason of ['UNAVAILABLE', 'MODEL_REQUEST_REJECTED', 'EMPTY_OUTPUT', 'INVALID_DECISION', 'ACCOUNTING_FAILED']) {
  test(`${reason} is a failed rehearsal rather than voluntary model silence`, async ({ page }) => {
    const result = await fixture(page, reason)
    await expect(result.locator('strong')).toHaveText('未完成')
    await expect(result).toContainText('真实试演 · BYOK')
    await expect(result).not.toContainText('这会儿先不插话')
    await expect(result.getByRole('button', { name: '回复绫页 · 打开普通聊天' })).toHaveCount(0)
  })
}

test('normal model SILENT is still a successful DECIDED response', async ({ page }) => {
  const result = await fixture(page, 'DECIDED')
  await expect(result.locator('strong')).toHaveText('SILENT')
  await expect(result).toContainText('真实模型 · BYOK')
  await expect(result).toContainText('这会儿先不插话')
  await expect(result).toContainText('真实模型已完成本次决策')
})
