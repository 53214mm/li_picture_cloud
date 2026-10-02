import { test, expect } from '@playwright/test'

const mood = { energy: 0, joy: 0, loneliness: 0, inspiration: 0, irritation: 0 }
const relationship = { familiarity: 0, trust: 0, closeness: 0, tacit: 0, recentFeedback: 0 }
const traits = { curiosity: -70, enthusiasm: 10, playfulness: 0, empathy: 30, creativity: 80 }
const companion = { id: '7001', lifeStage: 'LIGHT', lifeExperience: '42', level: 1, levelStartExperience: '0', nextLevelExperience: '100', revision: '9', traits, skills: [] }
const ok = (route, data) => route.fulfill({ json: { code: 0, data } })

async function fixture(page, overrides = {}) {
  const calls = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    calls.push(path)
    if (path === '/api/user/current') return ok(route, { id: '42', userName: 'R11', userRole: 'user' })
    if (path === '/api/companion/me') return ok(route, { companion, mood, relationship, recentGrowth: [], nutrition: {}, chatPolicy: 'DEMO', ...overrides })
    if (path === '/api/companion/proposals/active') return ok(route, null)
    if (path === '/api/companion/contract') return ok(route, { active: false, quietStart: '23:00', quietEnd: '08:00', maxFrequencyHours: 1 })
    if (path === '/api/space/list/page/vo') return ok(route, { records: [] })
    if (path.includes('tag_category')) return ok(route, { tagList: [], categoryList: [] })
    return ok(route, { records: [], total: 0 })
  })
  return { calls, errors }
}

test('R11 summaries lead; keyboard disclosure reveals exact signed values without new requests', async ({ page }, testInfo) => {
  const state = await fixture(page, { mood: { ...mood, joy: 80 }, relationship: { ...relationship, trust: 60, closeness: 60, recentFeedback: -100 } })
  await page.goto('/companion')
  await expect(page.getByTestId('mood-label')).toHaveText('心情明朗')
  await expect(page.getByTestId('relationship-label')).toHaveText('相处渐近')
  await expect(page.getByTestId('traits-label')).toHaveText('明显偏创造 · 明显偏谨慎')
  for (const section of ['mood', 'relationship', 'traits']) {
    const details = page.getByTestId(`${section}-details`)
    await expect(details).not.toHaveAttribute('open', '')
    await details.locator('summary').focus()
    await page.keyboard.press('Enter')
    await expect(details).toHaveAttribute('open', '')
  }
  await expect(page.getByTestId('mood-value-joy')).toBeVisible()
  await expect(page.getByTestId('mood-value-joy')).toHaveText('80')
  await expect(page.getByTestId('relationship-value-recentFeedback')).toHaveText('-100')
  await expect(page.getByTestId('trait-value-curiosity')).toHaveText('-70')
  await page.getByTestId('mood-details').locator('summary').focus()
  await page.keyboard.press('Space')
  await expect(page.getByTestId('mood-value-joy')).not.toBeVisible()
  await page.getByTestId('relationship-overview').scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('r11-desktop-panels.png') })
  expect(state.calls.filter(path => path === '/api/companion/me')).toHaveLength(1)
  expect(state.calls.some(path => path.includes('/chat/stream') || path.includes('/feed'))).toBe(false)
  expect(state.errors).toEqual([])
})

test('R11 missing and malformed snapshots do not show invented neutral axes', async ({ page }) => {
  await fixture(page, { mood: null, relationship: { ...relationship, recentFeedback: 101 }, companion: { ...companion, traits: {} } })
  await page.goto('/companion')
  await expect(page.getByTestId('mood-label')).toHaveText('暂无记录')
  await expect(page.getByTestId('relationship-label')).toHaveText('状态待更新')
  await expect(page.getByTestId('traits-label')).toHaveText('状态待更新')
  for (const section of ['mood', 'relationship', 'traits']) await expect(page.getByTestId(`${section}-details`)).toHaveCount(0)
  await expect(page.locator('.body-disposition')).toHaveCount(0)
})

test('R11 high loneliness remains passive and 320px reduced-motion disclosure stays usable', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 320, height: 800 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const state = await fixture(page, { mood: { ...mood, loneliness: 90 } })
  await page.goto('/companion')
  await expect(page.locator('.body-disposition')).toHaveText('有些孤单')
  await expect(page.locator('.sprite-player')).toHaveAttribute('data-playback', 'static')
  await expect(page.locator('.sprite-atlas')).toHaveCount(0)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByTestId('mood-details').locator('summary').click()
  await expect(page.getByTestId('mood-value-loneliness')).toBeVisible()
  await expect(page.getByTestId('mood-value-loneliness')).toHaveText('90')
  expect(await page.evaluate(() => globalThis.document.documentElement.scrollWidth <= globalThis.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('r11-mobile-reduced-motion.png') })
  expect(state.calls.some(path => path.includes('/chat/stream') || path.includes('/feed'))).toBe(false)
  expect(state.errors).toEqual([])
})

test('R11 energetic idle pauses and resumes the latest tone through the existing clock', async ({ page }) => {
  await fixture(page, { mood: { ...mood, energy: 80 } })
  await page.clock.install()
  await page.clock.pauseAt(new Date())
  await page.goto('/companion')
  const player = page.locator('.sprite-player')
  await expect(player).toHaveAttribute('data-playback', 'playing')
  await page.clock.runFor(2800)
  await expect(player).toHaveAttribute('data-animation-offset', '-2')
  await page.getByRole('button', { name: '暂停动作' }).click()
  await expect(player).toHaveAttribute('data-playback', 'static')
  await expect(player).toHaveAttribute('data-animation-offset', '0')
  await page.getByRole('button', { name: '恢复动作' }).click()
  await expect(player).toHaveAttribute('data-playback', 'playing')
  await page.clock.runFor(2800)
  await expect(player).toHaveAttribute('data-animation-offset', '-2')
})
