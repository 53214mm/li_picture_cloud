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

test('emoji panel picks one picture and drives candidate selection', async () => {
  const panel = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionEmojiPanel.vue', import.meta.url)), 'utf8')

  assert.match(panel, /生成候选/)
  assert.match(panel, /选中保存/)
  assert.match(panel, /保存作品/)
  assert.match(panel, /role="radiogroup"/)
  assert.match(panel, /data-testid="emoji-result"/)
  // 单选一张来源图片。
  assert.match(panel, /type="radio" name="emoji-source"/)
  // 候选单选组必须按任务独立命名（动态绑定，不能是字面量）。
  assert.match(panel, /:name="`emoji-pick-\$\{task\.id\}`"/)
  assert.match(panel, /crypto\.randomUUID\(\)/)
})

test('fusion panel requires two pictures and an explicit target space', async () => {
  const panel = await readFile(fileURLToPath(new globalThis.URL('../src/components/companion/CompanionFusionPanel.vue', import.meta.url)), 'utf8')

  // 至少两张来源图片，多选。
  assert.match(panel, /至少需要 2 张图片/)
  assert.match(panel, /selectedIds\.length < 2/)
  assert.match(panel, /生成融合图/)
  assert.match(panel, /保存到图库/)
  assert.match(panel, /data-testid="fusion-result"/)
  // 目标空间必须显式选择（disabled 占位），作品名可选。
  assert.match(panel, /选择目标空间/)
  assert.match(panel, /!spaceSelections\[task\.id\]/)
  // 预览只含安全信息：mime 字节地址 + 修订号，绝不回显幂等键。
  assert.match(panel, /fusionPreviewUrl\(task\.id\)/)
  assert.doesNotMatch(panel, /\{\{ task\.idempotencyKey \}\}/)
  assert.match(panel, /crypto\.randomUUID\(\)/)
})
