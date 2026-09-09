import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { creationStatusLabel } from '../src/constants/creation.js'

test('creation status labels resolve and fall back safely', () => {
  assert.equal(creationStatusLabel('PENDING'), '待开始')
  assert.equal(creationStatusLabel('AWAITING_CONFIRM'), '等待确认')
  assert.equal(creationStatusLabel('SAVED'), '已保存')
  assert.equal(creationStatusLabel('EXPIRED'), '已过期')
  assert.equal(creationStatusLabel('UNKNOWN'), 'UNKNOWN')
  assert.equal(creationStatusLabel(null), '未知状态')
})

test('story api mirrors the creation endpoints', async () => {
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/creation.js', import.meta.url)), 'utf8')

  assert.match(api, /request\.post\('\/creation\/story', data\)/)
  assert.match(api, /request\.post\(`\/creation\/story\/\$\{id\}\/outline`\)/)
  assert.match(api, /request\.post\(`\/creation\/story\/\$\{id\}\/draft`\)/)
  assert.match(api, /request\.post\(`\/creation\/story\/\$\{id\}\/save`\)/)
  assert.match(api, /request\.get\('\/creation\/story'/)
  assert.match(api, /request\.post\('\/creation\/emoji', data\)/)
  assert.match(api, /request\.post\(`\/creation\/emoji\/\$\{id\}\/generate`\)/)
  assert.match(api, /request\.get\(`\/creation\/emoji\/\$\{id\}\/candidates`\)/)
  assert.match(api, /request\.post\(`\/creation\/emoji\/\$\{id\}\/select`/)
  assert.match(api, /request\.post\(`\/creation\/emoji\/\$\{id\}\/save`\)/)
  assert.match(api, /request\.post\('\/creation\/fusion', data\)/)
  assert.match(api, /request\.post\(`\/creation\/fusion\/\$\{id\}\/generate`\)/)
  assert.match(api, /request\.post\(`\/creation\/fusion\/\$\{id\}\/save`, data\)/)
  assert.match(api, /request\.get\('\/creation\/fusion'/)
  assert.match(api, /\/api\/creation\/fusion\/\$\{id\}\/preview/)
})

test('story panel drives the state machine without exposing secrets', async () => {
  const panel = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionStoryPanel.vue', import.meta.url)), 'utf8')

  // 状态机按钮与展示。
  assert.match(panel, /生成大纲/)
  assert.match(panel, /生成草稿/)
  assert.match(panel, /保存作品/)
  assert.match(panel, /creationStatusLabel\(task\.status\)/)
  assert.match(panel, /data-testid="story-result"/)
  // 幂等键本地生成，绝不出现在界面。
  assert.match(panel, /crypto\.randomUUID\(\)/)
  assert.doesNotMatch(panel, /\{\{ task\.idempotencyKey \}\}/)
  // 最多 12 张来源图片。
  assert.match(panel, /MAX_PICTURES = 12/)
})

test('emoji panel shows the explicit not-open notice and no model driving UI', async () => {
  const panel = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionEmojiPanel.vue', import.meta.url)), 'utf8')

  // 表情草稿暂未开放：面板明示需要视觉理解，且没有任何触发模型的动作/选择控件。
  assert.match(panel, /data-testid="emoji-unavailable"/)
  assert.match(panel, /文字表情草稿暂未开放/)
  assert.match(panel, /视觉理解/)
  assert.doesNotMatch(panel, /generateEmoji/)
  assert.doesNotMatch(panel, /createEmoji/)
  assert.doesNotMatch(panel, /type="radio" name="emoji-source"/)
  assert.doesNotMatch(panel, /crypto\.randomUUID\(\)/)
  // 历史任务仍以只读方式展示（含已保存结果）。
  assert.match(panel, /data-testid="emoji-result"/)
  assert.match(panel, /creationStatusLabel\(task\.status\)/)
})

test('fusion panel shows the explicit not-open notice and no model driving UI', async () => {
  const panel = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionFusionPanel.vue', import.meta.url)), 'utf8')

  // 真实多图融合能力未开放：面板明示原因，且没有选图/生成/保存等引导动作。
  assert.match(panel, /data-testid="fusion-unavailable"/)
  assert.match(panel, /真实多图融合能力尚未开放/)
  assert.doesNotMatch(panel, /开始融合创作/)
  assert.doesNotMatch(panel, /生成融合图/)
  assert.doesNotMatch(panel, /createFusion/)
  assert.doesNotMatch(panel, /generateFusion/)
  assert.doesNotMatch(panel, /保存到图库/)
  assert.doesNotMatch(panel, /crypto\.randomUUID\(\)/)
  // 历史任务仍以只读方式展示（含已保存结果与预览链接）。
  assert.match(panel, /data-testid="fusion-result"/)
  assert.match(panel, /fusionPreviewUrl\(task\.id\)/)
})
