import { test, expect } from '@playwright/test'

const ok = (route, data) => route.fulfill({ json: { code: 0, data } })

async function fixture(page) {
  const state = { calls: [], messages: [{ role: 'USER', content: '旧的话' }, { role: 'COMPANION', content: '旧版的长篇回复' }] }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request(); const path = new URL(request.url()).pathname
    state.calls.push({ path, method: request.method() })
    if (path === '/api/user/current') return ok(route, { id: '42', userName: '测试用户', userRole: 'user' })
    if (path === '/api/companion/chat/mode') return ok(route, { mode: 'DEMO', modelAvailable: false, modelSource: 'NONE', modelBroken: false })
    if (path === '/api/companion/chat/history' && request.method() === 'DELETE') {
      const deleted = state.messages.length; state.messages = []
      return ok(route, deleted)
    }
    if (path === '/api/companion/chat/history') return ok(route, { records: state.messages })
    return ok(route, { records: [], total: 0 })
  })
  return state
}
const deletes = state => state.calls.filter(call => call.method === 'DELETE' && call.path === '/api/companion/chat/history')

test('clearing the chat history needs a confirmation and refreshes an open chat', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion/rehearsal')
  await page.getByRole('button', { name: '打开普通聊天' }).click()
  await expect(page.getByText('旧版的长篇回复')).toBeVisible()

  const clear = page.getByTestId('clear-chat')
  await clear.getByRole('button', { name: '清空对话记录' }).click()
  await clear.getByRole('button', { name: '取消' }).click()
  expect(deletes(state)).toEqual([])

  await clear.getByRole('button', { name: '清空对话记录' }).click()
  await clear.getByRole('button', { name: '确定清空' }).click()
  await expect(clear.getByText('已清空 2 条聊天记录。')).toBeVisible()
  await expect(page.getByText('旧版的长篇回复')).toHaveCount(0)
  expect(deletes(state)).toHaveLength(1)
})

test('clearing works without an open chat and never touches presence', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/companion/rehearsal')
  const clear = page.getByTestId('clear-chat')
  await clear.getByRole('button', { name: '清空对话记录' }).click()
  await clear.getByRole('button', { name: '确定清空' }).click()
  await expect(clear.getByText('已清空 2 条聊天记录。')).toBeVisible()
  expect(state.calls.some(call => call.path.includes('/presence/'))).toBe(false)
})
