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
